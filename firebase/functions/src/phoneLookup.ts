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

    const snapshot = await getFirestore().collection("phoneIndex").doc(phone).get();
    return { exists: snapshot.exists };
  }
);
