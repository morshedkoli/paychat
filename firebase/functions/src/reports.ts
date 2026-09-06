import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { enforceRateLimit } from "./limits";

const REGION = "asia-south1";

const REASONS = ["SPAM", "HARASSMENT", "FRAUD", "OTHER"] as const;
type Reason = (typeof REASONS)[number];

/** A report is a pointer for a human to follow, not an essay. */
const DETAIL_LIMIT = 500;

/**
 * Files a report about a conversation.
 *
 * This is a callable rather than a client write because a report needs two
 * things rules cannot give it: a rate limit, so the collection cannot be
 * flooded, and a check that the reporter is actually in the thread they are
 * reporting, which needs a read of another document on every write.
 *
 * The conversation itself is not copied. A moderator with admin credentials
 * can read the thread from its id, and duplicating messages into a second
 * collection would spread the same private content over more places.
 */
export const fileReport = onCall({ region: REGION }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }

  const threadId = String(request.data?.threadId ?? "");
  if (!/^[A-Za-z0-9_-]{1,128}$/.test(threadId)) {
    throw new HttpsError("invalid-argument", "That conversation id is not valid.");
  }

  const reason = String(request.data?.reason ?? "") as Reason;
  if (!REASONS.includes(reason)) {
    throw new HttpsError("invalid-argument", "That is not a reason we recognise.");
  }

  const detail = request.data?.detail ? String(request.data.detail).slice(0, DETAIL_LIMIT) : null;

  const db = getFirestore();
  const thread = await db.collection("threads").doc(threadId).get();
  if (!thread.exists) {
    throw new HttpsError("not-found", "That conversation no longer exists.");
  }

  const members = (thread.get("members") as string[]) ?? [];
  if (!members.includes(uid)) {
    throw new HttpsError("permission-denied", "That is not your conversation.");
  }

  await enforceRateLimit({
    uid,
    action: "report",
    limit: 5,
    windowMs: 60 * 60 * 1000,
    message: "You have filed several reports already. Try again later.",
  });

  await db.collection("reports").add({
    reportedBy: uid,
    threadId,
    reportedUid: members.find((member) => member !== uid) ?? null,
    reason,
    detail,
    createdAt: FieldValue.serverTimestamp(),
  });
});
