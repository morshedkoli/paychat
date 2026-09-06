import { getFirestore } from "firebase-admin/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions";
import { Channels, formatAmount, push } from "./notifications";

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

    let sent = 0;
    for (const txn of due.docs) {
      const status = txn.get("status") as string | undefined;
      if (status !== "PENDING" && status !== "ACCEPTED") continue;
      if (txn.get("reversedBy")) continue;

      const threadId = txn.ref.parent.parent?.id;
      if (!threadId) continue;

      const thread = await getFirestore().collection("threads").doc(threadId).get();
      if (!thread.exists || thread.get("isLocal") === true) continue;

      const members = (thread.get("members") as string[]) ?? [];
      if (members.length === 0) continue;

      const amount = formatAmount(txn.get("amountMinor") as number | undefined);
      const note = (txn.get("note") as string | undefined)?.trim();

      await push(members, {
        threadId,
        title: "Due tomorrow",
        body: note ? `${amount} — ${note}` : `${amount} falls due tomorrow.`,
        channel: Channels.reminders,
      });
      sent += 1;
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
