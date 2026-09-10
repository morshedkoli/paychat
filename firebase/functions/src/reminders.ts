import { getFirestore } from "firebase-admin/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions";
import { Channels, displayName, formatAmount, push } from "./notifications";

const REGION = "asia-south1";

/** Dhaka time, because that is where the due date was chosen. */
const TIME_ZONE = "Asia/Dhaka";

const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * Reminds both parties, the day before, about money that falls due.
 *
 * It runs in the evening rather than at midnight so that the reminder lands
 * while people are awake, and it covers exactly one day so that no
 * transaction is reminded about twice: the window it scans moves forward by
 * the same amount the clock does.
 *
 * Only outstanding transactions qualify. A rejected or cancelled one is not a
 * debt, and an already reversed one has been settled.
 */
export const remindDueTransactions = onSchedule(
  { schedule: "0 18 * * *", timeZone: TIME_ZONE, region: REGION },
  async () => {
    const now = Date.now();
    const start = startOfDhakaDay(now + DAY_MS);
    const end = start + DAY_MS;

    const due = await getFirestore()
      .collectionGroup("transactions")
      .where("dueDate", ">=", start)
      .where("dueDate", "<", end)
      .get();

    if (due.empty) return;

    // Several transactions in one conversation fall due on the same day more
    // often than not, and the scan is a nightly job over every account, so
    // each thread is read once rather than once per entry.
    const membersByThread = new Map<string, string[] | null>();
    const membersOf = async (threadId: string): Promise<string[] | null> => {
      const cached = membersByThread.get(threadId);
      if (cached !== undefined) return cached;

      const thread = await getFirestore().collection("threads").doc(threadId).get();
      const members =
        thread.exists && thread.get("isLocal") !== true
          ? ((thread.get("members") as string[]) ?? [])
          : null;
      membersByThread.set(threadId, members);
      return members;
    };

    let sent = 0;
    for (const txn of due.docs) {
      const status = txn.get("status") as string | undefined;
      if (status !== "PENDING" && status !== "ACCEPTED") continue;
      if (txn.get("reversedBy")) continue;
      // Inherited unconfirmed history has not been agreed to by the recipient yet.
      if (txn.get("unconfirmed") === true) continue;

      const threadId = txn.ref.parent.parent?.id;
      if (!threadId) continue;

      const members = await membersOf(threadId);
      if (!members || members.length < 2) continue;

      const createdBy = txn.get("createdBy") as string | undefined;
      const direction = txn.get("direction") as string | undefined;
      if (!createdBy || !direction) continue;

      // In PayChat's direction model:
      // SENT: createdBy claims they gave money (createdBy is lender, other member is borrower).
      // RECEIVED: createdBy claims they got money (createdBy is borrower, other member is lender).
      const lenderUid = direction === "SENT" ? createdBy : members.find((m) => m !== createdBy);
      const borrowerUid = direction === "SENT" ? members.find((m) => m !== createdBy) : createdBy;
      if (!lenderUid || !borrowerUid) continue;

      const amount = formatAmount(txn.get("amountMinor") as number | undefined);
      const note = (txn.get("note") as string | undefined)?.trim();
      const noteSuffix = note ? ` (${note})` : "";

      const lenderName = await displayName(lenderUid);
      const borrowerName = await displayName(borrowerUid);

      // Notification for the borrower who owes the money
      await push([borrowerUid], {
        threadId,
        title: "Due tomorrow",
        body: `You owe ${lenderName} ${amount} tomorrow${noteSuffix}.`,
        channel: Channels.reminders,
      });

      // Notification for the lender who is expecting the repayment
      await push([lenderUid], {
        threadId,
        title: "Due tomorrow",
        body: `${borrowerName} owes you ${amount} tomorrow${noteSuffix}.`,
        channel: Channels.reminders,
      });

      sent += 2;
    }

    logger.info("due date reminders sent", { scanned: due.size, sent });
  }
);

/**
 * Midnight in Dhaka, as an epoch millisecond value.
 *
 * Dhaka has a fixed +06:00 offset with no daylight saving, so the arithmetic
 * needs no time zone database.
 */
function startOfDhakaDay(atMillis: number): number {
  const OFFSET_MS = 6 * 60 * 60 * 1000;
  const local = atMillis + OFFSET_MS;
  return local - (local % DAY_MS) - OFFSET_MS;
}
