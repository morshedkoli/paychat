import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions";

/**
 * Attaches history recorded against a phone number to the account that finally
 * claims it.
 *
 * Someone can record money against a person who does not use PayChat, by name
 * and number alone. That lives in a one-sided thread owned by the person who
 * wrote it. When the number registers, those threads become real two-party
 * conversations and the new user is shown the history to review.
 *
 * The trigger is the phoneIndex document, because that is the write that
 * claims a number, and the security rules only allow a user to create it for
 * a number Firebase itself verified by OTP. Triggering on the profile instead
 * would fire before the number was proven.
 *
 * The transactions are not rewritten. They were already created with
 * `unconfirmed: true` on a one-sided thread, which is exactly the state the
 * review screen looks for, and rewriting them would mean touching an unbounded
 * number of documents inside one trigger.
 */
export const attachHistoryOnRegistration = onDocumentCreated(
  {
    document: "phoneIndex/{phone}",
    region: "asia-south1",
  },
  async (event) => {
    const phone = event.params.phone;
    const newUid = event.data?.get("uid") as string | undefined;
    if (!newUid) {
      logger.warn("phoneIndex document has no uid", { phone });
      return;
    }

    const db = getFirestore();
    const threads = await db
      .collection("threads")
      .where("isLocal", "==", true)
      .where("localContact.phone", "==", phone)
      .get();

    if (threads.empty) return;

    // A batch is capped at 500 writes and each thread costs two, so the work
    // is chunked. Someone with a very large address book is rare but a
    // half-applied handover would be worse than a slow one.
    const WRITES_PER_THREAD = 2;
    const CHUNK = Math.floor(450 / WRITES_PER_THREAD);

    for (let i = 0; i < threads.docs.length; i += CHUNK) {
      const batch = db.batch();

      for (const thread of threads.docs.slice(i, i + CHUNK)) {
        const members = (thread.get("members") as string[]) ?? [];
        const ownerUid = members[0];
        if (!ownerUid || ownerUid === newUid) continue;

        batch.update(thread.ref, {
          members: [ownerUid, newUid].sort(),
          isLocal: false,
          promotedAt: Date.now(),
        });

        // The owner's contact entry records which account it turned out to be,
        // so their contact list stops offering to invite someone who has
        // already joined.
        const contactId = contactIdFrom(thread.id, ownerUid);
        if (contactId) {
          batch.set(
            db.doc(`users/${ownerUid}/localContacts/${contactId}`),
            { linkedUid: newUid, linkedAt: FieldValue.serverTimestamp() },
            { merge: true }
          );
        }
      }

      await batch.commit();
    }

    logger.info("attached history to a new account", {
      phone,
      newUid,
      threads: threads.size,
    });
  }
);

/**
 * A one-sided thread is identified as `<ownerUid>_local_<contactId>`, which is
 * the only place the contact id is recorded on the thread.
 */
function contactIdFrom(threadId: string, ownerUid: string): string | null {
  const prefix = `${ownerUid}_local_`;
  return threadId.startsWith(prefix) ? threadId.slice(prefix.length) : null;
}
