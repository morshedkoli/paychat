import { initializeApp } from "firebase-admin/app";

initializeApp();

export { signMediaUpload } from "./media";
export { attachHistoryOnRegistration } from "./handover";
