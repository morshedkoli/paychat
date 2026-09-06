import { initializeApp } from "firebase-admin/app";

initializeApp();

export { signMediaUpload } from "./media";
export { attachHistoryOnRegistration } from "./handover";
export {
  pushOnMessage,
  pushOnTransactionCreated,
  pushOnTransactionResolved,
} from "./notifications";
export { remindDueTransactions } from "./reminders";
export { fileReport } from "./reports";
