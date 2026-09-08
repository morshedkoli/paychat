import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { enforceRateLimit } from "./limits";

/** E.164: a plus, a non-zero country digit, then 7 to 14 more digits. */
const E164 = /^\+[1-9]\d{7,14}$/;

const HOUR_MS = 60 * 60 * 1000;

/**
 * Answers whether a phone number already has an account.
 *
 * Deliberately unauthenticated: the whole point is to answer this before
 * anybody can sign in, so that a returning user is asked for a password
 * instead of an SMS, and a new user is never shown a wrong-password error.
 *
 * `phoneIndex` itself stays readable only to signed in clients, because making
 * it public would let anyone enumerate which numbers use PayChat. This handler
 * reads it with admin credentials and returns one bit and nothing else: no
 * name, no uid, no timestamps.
 *
 * The one bit is still worth rationing, so it is rated twice — per caller,
 * which bounds bulk enumeration from one source, and per number, which bounds
 * attention on a single number even from many sources.
 */
export const phoneLookup = onCall(
  {
    region: "asia-south1",
    enforceAppCheck: false, // Turn on once App Check is configured.
  },
  async (request) => {
    const phone = String(request.data?.phone ?? "");
    if (!E164.test(phone)) {
      throw new HttpsError("invalid-argument", "That is not a phone number.");
    }

    const ip = request.rawRequest.ip ?? "unknown";
    await enforceRateLimit({
      uid: `ip:${ip}`,
      action: "phoneLookup",
      limit: 30,
      windowMs: HOUR_MS,
      message: "Too many attempts. Try again in a while.",
    });
    await enforceRateLimit({
      uid: `phone:${phone}`,
      action: "phoneLookup",
      limit: 6,
      windowMs: HOUR_MS,
      message: "Too many attempts. Try again in a while.",
    });

    return { exists: await accountExists(phone) };
  }
);

/**
 * Whether anything would stop this number registering again.
 *
 * Firebase Auth is asked first because it is the authority registration
 * itself collides against: `completeRegistration` signs in with the phone
 * credential and refuses when the account is not new. An account can exist
 * in Auth with no `phoneIndex` document behind it - a registration that
 * failed after the credential was created, or an account made before this
 * index existed - and answering from the index alone sent those people to
 * registration only to be turned away after they had waited for an SMS.
 *
 * The index is still consulted, so a profile whose Auth user was removed
 * out of band does not read as free either.
 */
async function accountExists(phone: string): Promise<boolean> {
  try {
    await getAuth().getUserByPhoneNumber(phone);
    return true;
  } catch (error) {
    const code = (error as { code?: string }).code;
    if (code !== "auth/user-not-found") {
      // Anything else - a network blip, a permission problem - must not be
      // reported as "this number is free". Saying so would send someone into
      // registration to fail at the far end again.
      throw new HttpsError("unavailable", "Could not check that number.");
    }
  }

  const snapshot = await getFirestore().collection("phoneIndex").doc(phone).get();
  return snapshot.exists;
}
