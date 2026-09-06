import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { logger } from "firebase-functions";

const REGION = "asia-south1";

/**
 * Deletes the caller's account.
 *
 * What goes: the profile, the phone number's claim on it, the push token, the
 * private per-user collections, and the Firebase Auth user itself. After this
 * the number can be registered again, by anyone, as a fresh account.
 *
 * What stays: the transactions in a shared conversation. They are not this
 * user's data alone — each one is a record of money between two people, and
 * the other party's ledger has to keep adding up. Their author is marked as
 * departed on the thread so the app can show that the person is gone, and the
 * name and picture that identified them are removed from the profile they
 * used to have.
 *
 * This is the same reason a bank keeps a closed account's statements: the
 * other side of every entry is somebody else's record.
 */
export const deleteAccount = onCall({ region: REGION }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }

  const db = getFirestore();
  const user = await db.collection("users").doc(uid).get();
  const phone = user.get("phone") as string | undefined;

  // Mark the conversations first. If anything later fails, the other party
  // still sees an accurate "this person has left" rather than a live account
  // that never answers.
  const threads = await db.collection("threads").where("members", "array-contains", uid).get();
  for (let i = 0; i < threads.docs.length; i += CHUNK) {
    const batch = db.batch();
    for (const thread of threads.docs.slice(i, i + CHUNK)) {
      batch.update(thread.ref, { departed: FieldValue.arrayUnion(uid) });
    }
    await batch.commit();
  }

  await deleteCollection(db.collection("users").doc(uid).collection("threadBalances"));
  await deleteCollection(db.collection("users").doc(uid).collection("localContacts"));

  if (phone) {
    const claim = await db.collection("phoneIndex").doc(phone).get();
    // Only if it still points here. A number that has somehow been re-claimed
    // is not this account's to release.
    if (claim.exists && claim.get("uid") === uid) {
      await claim.ref.delete();
    }
  }

  await db.collection("users").doc(uid).delete();

  // Last, because everything above needs the account to still exist if it has
  // to be retried.
  await getAuth().deleteUser(uid);

  logger.info("account deleted", { uid, threads: threads.size });
});

/** Firestore does not delete a collection, only documents, and in batches. */
async function deleteCollection(
  collection: FirebaseFirestore.CollectionReference
): Promise<void> {
  const db = getFirestore();
  for (;;) {
    const page = await collection.limit(CHUNK).get();
    if (page.empty) return;

    const batch = db.batch();
    page.docs.forEach((doc) => batch.delete(doc.ref));
    await batch.commit();
  }
}

/** Well under the 500 write cap, leaving room for retries. */
const CHUNK = 400;
