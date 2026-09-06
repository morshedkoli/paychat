import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";

/**
 * A fixed-window rate limit, counted server side.
 *
 * Rules cannot count: they see one write at a time and have no memory, so
 * anything of the form "no more than N per hour" has to live here, where the
 * counter is a document only admin credentials can touch. A client holding
 * the APK and a valid token cannot forge it.
 *
 * The window is fixed rather than sliding. A sliding window would need every
 * timestamp kept, and the point is to stop a flood, not to meter usage to the
 * second.
 */
export async function enforceRateLimit(options: {
  uid: string;
  action: string;
  limit: number;
  windowMs: number;
  message: string;
}): Promise<void> {
  const { uid, action, limit, windowMs, message } = options;
  const db = getFirestore();
  const ref = db.collection("rateLimits").doc(`${uid}_${action}`);

  await db.runTransaction(async (tx) => {
    const now = Date.now();
    const snapshot = await tx.get(ref);
    const startedAt = (snapshot.get("startedAt") as number | undefined) ?? 0;
    const count = (snapshot.get("count") as number | undefined) ?? 0;

    // A window that has run out starts again from this call.
    const withinWindow = now - startedAt < windowMs;
    const next = withinWindow ? count + 1 : 1;

    if (withinWindow && next > limit) {
      throw new HttpsError("resource-exhausted", message);
    }

    tx.set(ref, {
      count: next,
      startedAt: withinWindow ? startedAt : now,
      updatedAt: Timestamp.now(),
    });
  });
}
