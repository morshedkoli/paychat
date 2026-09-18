import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions";

const REGION = "asia-south1";

/** Channel ids, matching app/src/main/res/values/strings.xml. */
export const Channels = {
  messages: "messages",
  transactions: "transactions",
  reminders: "reminders",
} as const;

/**
 * A push for every message the other party did not send.
 *
 * The payload is data-only. A notification payload would be drawn by the
 * system while the app is in the background, which would defeat the client's
 * rule that the conversation currently on screen is not notified about, and
 * would lose the tap target that opens the right chat.
 */
export const pushOnMessage = onDocumentCreated(
  { document: "threads/{threadId}/messages/{messageId}", region: REGION },
  async (event) => {
    const message = event.data;
    if (!message) return;

    const threadId = event.params.threadId;
    const senderId = message.get("senderId") as string | undefined;
    if (!senderId) return;

    const recipients = await counterparties(threadId, senderId);
    if (recipients.length === 0) return;

    const senderName = await displayName(senderId);
    const body = previewOf(
      message.get("type") as string | undefined,
      message.get("text") as string | undefined
    );

    await push(recipients, {
      threadId,
      title: senderName,
      body,
      channel: Channels.messages,
    });
  }
);

/**
 * A push when money is recorded against someone, and again when they decide.
 *
 * Both halves live here because they are the same conversation seen from two
 * sides: the counterparty needs to know a transaction is waiting, and the
 * person who recorded it needs to know it was accepted or refused.
 */
export const pushOnTransactionCreated = onDocumentCreated(
  { document: "threads/{threadId}/transactions/{txnId}", region: REGION },
  async (event) => {
    const txn = event.data;
    if (!txn) return;

    const threadId = event.params.threadId;
    const createdBy = txn.get("createdBy") as string | undefined;
    if (!createdBy) return;

    const recipients = await counterparties(threadId, createdBy);
    if (recipients.length === 0) return;

    const amount = formatAmount(txn.get("amountMinor") as number | undefined);
    const name = await displayName(createdBy);

    // Direction is written from the recorder's point of view, so it reads
    // backwards for the person being notified.
    const body =
      txn.get("direction") === "SENT"
        ? `${name} recorded giving you ${amount}. Accept or reject it.`
        : `${name} recorded receiving ${amount} from you.`;

    await push(recipients, {
      threadId,
      title: "New transaction",
      body,
      channel: Channels.transactions,
    });
  }
);

export const pushOnTransactionResolved = onDocumentUpdated(
  { document: "threads/{threadId}/transactions/{txnId}", region: REGION },
  async (event) => {
    const before = event.data?.before;
    const after = event.data?.after;
    if (!before || !after) return;

    const status = after.get("status") as string | undefined;
    // Only the move out of PENDING is news. Later edits are not.
    if (!status || status === before.get("status") || status === "PENDING") return;

    const createdBy = after.get("createdBy") as string | undefined;
    const resolvedBy = after.get("resolvedBy") as string | undefined;
    if (!createdBy) return;

    // Whoever acted already knows; tell the other side.
    const recipient = resolvedBy && resolvedBy !== createdBy ? createdBy : null;
    if (!recipient) return;

    const amount = formatAmount(after.get("amountMinor") as number | undefined);
    const name = await displayName(resolvedBy);
    const verb =
      status === "ACCEPTED" ? "accepted" : status === "REJECTED" ? "rejected" : "cancelled";

    await push([recipient], {
      threadId: event.params.threadId,
      title: "Transaction " + verb,
      body: `${name} ${verb} the ${amount} transaction.`,
      channel: Channels.transactions,
    });
  }
);

// --------------------------------------------------------------- helpers

/** Members of a thread other than [excludeUid], skipping one-sided threads. */
async function counterparties(threadId: string, excludeUid: string): Promise<string[]> {
  const thread = await getFirestore().collection("threads").doc(threadId).get();
  if (!thread.exists) return [];
  // A local thread has no second party to notify yet.
  if (thread.get("isLocal") === true) return [];

  const members = (thread.get("members") as string[]) ?? [];
  return members.filter((uid) => uid !== excludeUid);
}

export async function displayName(uid: string | undefined): Promise<string> {
  if (!uid) return "Someone";
  const user = await getFirestore().collection("users").doc(uid).get();
  return (user.get("name") as string | undefined) || "Someone";
}

/** Amounts are stored in poisha; notifications show taka. */
export function formatAmount(amountMinor: number | undefined): string {
  const minor = Math.abs(amountMinor ?? 0);
  const taka = Math.floor(minor / 100);
  const poisha = String(minor % 100).padStart(2, "0");
  return `৳${taka.toLocaleString("en-US")}.${poisha}`;
}

function previewOf(type: string | undefined, text: string | undefined): string {
  switch (type) {
    case "IMAGE":
      return "Photo";
    case "VOICE":
      return "Voice message";
    case "TXN":
      return "Transaction";
    default:
      return text?.slice(0, 140) || "New message";
  }
}

export interface PushPayload {
  threadId: string;
  title: string;
  body: string;
  channel: string;
}

/**
 * Sends to each recipient's one registered device.
 *
 * A token that the transport rejects is deleted rather than retried: it
 * belongs to an uninstalled app or a device that has since been replaced, and
 * a stale token would otherwise be sent to on every future message.
 */
export async function push(recipientUids: string[], payload: PushPayload): Promise<void> {
  const db = getFirestore();
  const users = await Promise.all(
    recipientUids.map((uid) => db.collection("users").doc(uid).get())
  );

  const targets = users
    .map((user) => ({ uid: user.id, token: user.get("fcmToken") as string | undefined }))
    .filter((target): target is { uid: string; token: string } => !!target.token);

  // Silence here is indistinguishable from a push that was sent and ignored,
  // and an account with no token is the likeliest reason a notification never
  // arrives, so it is said out loud.
  const untargeted = users.filter((user) => !user.get("fcmToken"));
  if (untargeted.length > 0) {
    logger.warn("no push token", { uids: untargeted.map((user) => user.id) });
  }

  if (targets.length === 0) return;

  const data: Record<string, string> = {
    threadId: payload.threadId,
    title: payload.title,
    body: payload.body,
    channel: payload.channel,
  };

  const response = await getMessaging().sendEach(
    targets.map((target) => ({
      token: target.token,
      data,
      android: { priority: "high" as const },
    }))
  );

  await Promise.all(
    response.responses.map(async (result, index) => {
      if (result.success) return;
      const code = result.error?.code;
      logger.warn("push failed", { uid: targets[index].uid, code });
      if (
        code === "messaging/registration-token-not-registered" ||
        code === "messaging/invalid-argument"
      ) {
        await db.collection("users").doc(targets[index].uid).update({ fcmToken: null });
      }
    })
  );
}
