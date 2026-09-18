import { createHash } from "crypto";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { enforceRateLimit } from "./limits";

const cloudinaryApiKey = defineSecret("CLOUDINARY_API_KEY");
const cloudinaryApiSecret = defineSecret("CLOUDINARY_API_SECRET");
const cloudinaryCloudName = defineSecret("CLOUDINARY_CLOUD_NAME");

/** Cloudinary resource types PayChat uploads. Audio is a "video" resource. */
type ResourceType = "image" | "video";

const MAX_BYTES: Record<ResourceType, number> = {
  image: 10 * 1024 * 1024,
  video: 20 * 1024 * 1024,
};

/**
 * Signs one Cloudinary upload.
 *
 * The API secret stays here. The alternative, an unsigned upload preset, would
 * let anyone holding the APK upload arbitrary files to the account and exhaust
 * the free quota, so it is not used.
 *
 * The signature covers the folder and public id as well as the timestamp, so a
 * caller cannot take a signature issued for their own folder and use it to
 * overwrite somebody else's file.
 */
export const signMediaUpload = onCall(
  {
    secrets: [cloudinaryApiKey, cloudinaryApiSecret, cloudinaryCloudName],
    region: "asia-south1",
    enforceAppCheck: false, // Turn on once App Check is configured.
  },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) {
      throw new HttpsError("unauthenticated", "Sign in first.");
    }

    const resourceType = request.data?.resourceType as ResourceType | undefined;
    if (resourceType !== "image" && resourceType !== "video") {
      throw new HttpsError("invalid-argument", "resourceType must be image or video.");
    }

    const messageId = String(request.data?.messageId ?? "");
    if (!/^[A-Za-z0-9-]{1,64}$/.test(messageId)) {
      throw new HttpsError("invalid-argument", "messageId is not a valid id.");
    }

    const bytes = Number(request.data?.bytes ?? 0);
    if (!Number.isFinite(bytes) || bytes <= 0 || bytes > MAX_BYTES[resourceType]) {
      throw new HttpsError("invalid-argument", "That file is too large.");
    }

    // A signature is what lets a file reach the storage account, so the
    // number handed out is capped. The ceiling is far above what sending
    // photos and voice notes all day would reach.
    await enforceRateLimit({
      uid,
      action: "upload",
      limit: 120,
      windowMs: 60 * 60 * 1000,
      message: "Too many uploads in a short time. Try again later.",
    });

    // The uid is part of the path, so a signature can only ever write into the
    // caller's own folder.
    const folder = `paychat/${uid}`;
    const publicId = `${folder}/${messageId}`;
    const timestamp = Math.floor(Date.now() / 1000);

    const signature = sign(
      { public_id: publicId, timestamp: String(timestamp) },
      secret(cloudinaryApiSecret.value())
    );

    return {
      cloudName: secret(cloudinaryCloudName.value()),
      apiKey: secret(cloudinaryApiKey.value()),
      publicId,
      timestamp,
      signature,
      resourceType,
    };
  }
);

/**
 * A secret as it was meant to be stored.
 *
 * Setting a secret by piping a file or pasting into a terminal carries the
 * line ending with it, and Secret Manager stores whatever it is given. A key
 * with a newline on the end is rejected by Cloudinary as a bad key, and a
 * secret with one produces a signature that never matches — both surface only
 * as a 401 from an upload, with nothing to point at the cause.
 */
function secret(value: string): string {
  return value.trim();
}

/**
 * Cloudinary's scheme: the parameters that were sent, sorted by name, joined
 * as key=value with "&", with the API secret appended, hashed with SHA-1.
 */
function sign(params: Record<string, string>, apiSecret: string): string {
  const payload = Object.keys(params)
    .sort()
    .map((key) => `${key}=${params[key]}`)
    .join("&");
  return createHash("sha1").update(payload + apiSecret).digest("hex");
}
