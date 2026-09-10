import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, it } from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, updateDoc } from "firebase/firestore";

/**
 * The rules are the only thing between a user and someone else's money, so
 * they are tested against the real rules engine rather than read carefully
 * and hoped over.
 *
 * Each test states an invariant from docs/SPEC.md section 6 as something that
 * must be refused, because a rule that allows too much fails silently while a
 * rule that allows too little is noticed the first time the app is used.
 */

const ALICE = "alice";
const BOB = "bob";
const MALLORY = "mallory";
const THREAD = "alice_bob";

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: "paychat-tests",
    firestore: {
      rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8"),
      host: "127.0.0.1",
      port: 8080,
    },
  });
});

after(async () => {
  await testEnv?.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, "threads", THREAD), {
      members: [ALICE, BOB],
      isLocal: false,
      updatedAt: 1,
    });
    await setDoc(doc(db, "users", ALICE), { phone: "+8801700000001", name: "Alice" });
    await setDoc(doc(db, "users", BOB), { phone: "+8801700000002", name: "Bob" });
  });
});

/** A signed in client for one uid, with a verified phone number claim. */
function as(uid, phone) {
  return testEnv.authenticatedContext(uid, { phone_number: phone }).firestore();
}

const message = (from) => ({
  senderId: from,
  type: "TEXT",
  text: "hello",
  createdAt: 1,
  deliveredTo: [],
  readBy: [],
});

const transaction = (from, overrides = {}) => ({
  createdBy: from,
  direction: "SENT",
  amountMinor: 5000,
  status: "PENDING",
  unconfirmed: false,
  createdAt: 1,
  ...overrides,
});

describe("threads", () => {
  it("a stranger cannot read a conversation they are not in", async () => {
    await assertFails(getDoc(doc(as(MALLORY), "threads", THREAD)));
  });

  it("a member can read it", async () => {
    await assertSucceeds(getDoc(doc(as(ALICE), "threads", THREAD)));
  });

  it("membership cannot be changed by a client", async () => {
    await assertFails(
      updateDoc(doc(as(ALICE), "threads", THREAD), { members: [ALICE, BOB, MALLORY] })
    );
  });

  it("a member may block, adding only their own uid", async () => {
    await assertSucceeds(updateDoc(doc(as(ALICE), "threads", THREAD), { blockedBy: [ALICE] }));
  });

  it("a member cannot block on somebody else's behalf", async () => {
    await assertFails(updateDoc(doc(as(ALICE), "threads", THREAD), { blockedBy: [BOB] }));
  });

  it("a one-sided thread is created with only its owner", async () => {
    await assertSucceeds(
      setDoc(doc(as(ALICE), "threads", "alice_local_c1"), {
        members: [ALICE],
        isLocal: true,
        localContact: { name: "Rana", phone: "+8801700000009" },
        updatedAt: 1,
      })
    );
  });

  // Without this a caller could mark a two-party thread one-sided, which is
  // the branch that lets a SENT claim start accepted instead of pending.
  it("a two-party thread cannot be created as one-sided", async () => {
    await assertFails(
      setDoc(doc(as(ALICE), "threads", "alice_local_c2"), {
        members: [ALICE, BOB],
        isLocal: true,
        updatedAt: 1,
      })
    );
  });

  it("a one-sided thread cannot be created for somebody else", async () => {
    await assertFails(
      setDoc(doc(as(ALICE), "threads", "bob_local_c3"), {
        members: [BOB],
        isLocal: true,
        updatedAt: 1,
      })
    );
  });

  it("a client cannot forge a departure", async () => {
    await assertFails(updateDoc(doc(as(ALICE), "threads", THREAD), { departed: [BOB] }));
  });

  it("a client cannot forge a handover", async () => {
    await assertFails(updateDoc(doc(as(ALICE), "threads", THREAD), { promotedAt: 5 }));
  });

  it("a thread cannot change from one-sided to two-party", async () => {
    await assertFails(updateDoc(doc(as(ALICE), "threads", THREAD), { isLocal: true }));
  });

  it("an unknown field on a thread is refused", async () => {
    await assertFails(updateDoc(doc(as(ALICE), "threads", THREAD), { admin: true }));
  });

  it("a member may announce that they are typing", async () => {
    await assertSucceeds(
      updateDoc(doc(as(ALICE), "threads", THREAD), { typing: { [ALICE]: 1000 } })
    );
  });

  it("a member cannot type on somebody else's behalf", async () => {
    await assertFails(
      updateDoc(doc(as(ALICE), "threads", THREAD), { typing: { [BOB]: 1000 } })
    );
  });
});

describe("messages", () => {
  it("a member can send one", async () => {
    await assertSucceeds(
      setDoc(doc(as(ALICE), "threads", THREAD, "messages", "m1"), message(ALICE))
    );
  });

  it("a message cannot be sent as somebody else", async () => {
    await assertFails(
      setDoc(doc(as(ALICE), "threads", THREAD, "messages", "m1"), message(BOB))
    );
  });

  it("a stranger cannot send into the conversation", async () => {
    await assertFails(
      setDoc(doc(as(MALLORY), "threads", THREAD, "messages", "m1"), message(MALLORY))
    );
  });

  it("an enormous message is refused", async () => {
    await assertFails(
      setDoc(doc(as(ALICE), "threads", THREAD, "messages", "m1"), {
        ...message(ALICE),
        text: "x".repeat(4001),
      })
    );
  });

  it("an unknown field is refused", async () => {
    await assertFails(
      setDoc(doc(as(ALICE), "threads", THREAD, "messages", "m1"), {
        ...message(ALICE),
        payload: "x".repeat(100),
      })
    );
  });

  it("blocking closes the conversation to new messages", async () => {
    await updateDoc(doc(as(BOB), "threads", THREAD), { blockedBy: [BOB] });
    await assertFails(
      setDoc(doc(as(ALICE), "threads", THREAD, "messages", "m1"), message(ALICE))
    );
  });

  it("history stays readable after a block", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(
        doc(context.firestore(), "threads", THREAD, "messages", "m0"),
        message(ALICE)
      );
    });
    await updateDoc(doc(as(BOB), "threads", THREAD), { blockedBy: [BOB] });

    await assertSucceeds(getDoc(doc(as(ALICE), "threads", THREAD, "messages", "m0")));
  });
});

describe("transactions", () => {
  it("a claim that money was given starts pending", async () => {
    await assertSucceeds(
      setDoc(doc(as(ALICE), "threads", THREAD, "transactions", "t1"), transaction(ALICE))
    );
  });

  it("a claim that money was given cannot start accepted", async () => {
    await assertFails(
      setDoc(
        doc(as(ALICE), "threads", THREAD, "transactions", "t1"),
        transaction(ALICE, { status: "ACCEPTED" })
      )
    );
  });

  it("the author cannot accept their own", async () => {
    await setDoc(doc(as(ALICE), "threads", THREAD, "transactions", "t1"), transaction(ALICE));

    await assertFails(
      updateDoc(doc(as(ALICE), "threads", THREAD, "transactions", "t1"), {
        status: "ACCEPTED",
        resolvedBy: ALICE,
      })
    );
  });

  it("the counterparty can accept it", async () => {
    await setDoc(doc(as(ALICE), "threads", THREAD, "transactions", "t1"), transaction(ALICE));

    await assertSucceeds(
      updateDoc(doc(as(BOB), "threads", THREAD, "transactions", "t1"), {
        status: "ACCEPTED",
        resolvedBy: BOB,
      })
    );
  });

  it("an accepted amount cannot be edited afterwards", async () => {
    await setDoc(doc(as(ALICE), "threads", THREAD, "transactions", "t1"), transaction(ALICE));
    await updateDoc(doc(as(BOB), "threads", THREAD, "transactions", "t1"), {
      status: "ACCEPTED",
      resolvedBy: BOB,
    });

    await assertFails(
      updateDoc(doc(as(ALICE), "threads", THREAD, "transactions", "t1"), { amountMinor: 1 })
    );
  });

  it("an accepted entry can be marked as reversed by a later one", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(
        doc(context.firestore(), "threads", THREAD, "transactions", "t1"),
        transaction(ALICE, { status: "ACCEPTED" })
      );
    });

    await assertSucceeds(
      updateDoc(doc(as(ALICE), "threads", THREAD, "transactions", "t1"), { reversedBy: "t2" })
    );
  });

  it("inherited history is reviewed by the person who inherited it", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(
        doc(context.firestore(), "threads", THREAD, "transactions", "t1"),
        transaction(ALICE, { status: "ACCEPTED", unconfirmed: true })
      );
    });

    // The author first: clearing the flag on their own claim would be
    // confirming their own record of money. Bob after, because once the flag
    // is cleared Alice's write is a no-op that the rules allow on purpose.
    await assertFails(
      updateDoc(doc(as(ALICE), "threads", THREAD, "transactions", "t1"), {
        unconfirmed: false,
        status: "ACCEPTED",
        resolvedBy: ALICE,
      })
    );
    await assertSucceeds(
      updateDoc(doc(as(BOB), "threads", THREAD, "transactions", "t1"), {
        unconfirmed: false,
        status: "ACCEPTED",
        resolvedBy: BOB,
      })
    );
  });

  // `unconfirmed` keeps an entry out of the other person's balance. On a real
  // thread there is no history to inherit, so an author who could set it would
  // be recording a repayment that never reached the payer's figures.
  it("an entry on a real thread cannot start unconfirmed", async () => {
    await assertFails(
      setDoc(
        doc(as(ALICE), "threads", THREAD, "transactions", "t1"),
        transaction(ALICE, { direction: "RECEIVED", status: "ACCEPTED", unconfirmed: true })
      )
    );
  });

  it("accepting cannot rewrite the note in the same write", async () => {
    await setDoc(
      doc(as(ALICE), "threads", THREAD, "transactions", "t1"),
      transaction(ALICE, { note: "lunch" })
    );

    await assertFails(
      updateDoc(doc(as(BOB), "threads", THREAD, "transactions", "t1"), {
        status: "ACCEPTED",
        resolvedBy: BOB,
        note: "rent",
      })
    );
  });

  it("an unknown field on a transaction is refused", async () => {
    await assertFails(
      setDoc(
        doc(as(ALICE), "threads", THREAD, "transactions", "t1"),
        transaction(ALICE, { settled: true })
      )
    );
  });

  // A correction that the counterparty refuses leaves the original standing,
  // so the pointer to it has to come off again or the entry could never be
  // corrected a second time.
  it("a refused correction releases the entry to be corrected again", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(
        doc(context.firestore(), "threads", THREAD, "transactions", "t1"),
        transaction(ALICE, { status: "ACCEPTED", reversedBy: "t2" })
      );
    });

    await assertSucceeds(
      updateDoc(doc(as(ALICE), "threads", THREAD, "transactions", "t1"), { reversedBy: null })
    );
  });

  it("a correction pointer cannot carry another change with it", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(
        doc(context.firestore(), "threads", THREAD, "transactions", "t1"),
        transaction(ALICE, { status: "ACCEPTED" })
      );
    });

    await assertFails(
      updateDoc(doc(as(ALICE), "threads", THREAD, "transactions", "t1"), {
        reversedBy: "t2",
        status: "CANCELLED",
      })
    );
  });

  it("a zero or negative amount is refused", async () => {
    await assertFails(
      setDoc(
        doc(as(ALICE), "threads", THREAD, "transactions", "t1"),
        transaction(ALICE, { amountMinor: 0 })
      )
    );
  });

  it("an absurd amount is refused", async () => {
    await assertFails(
      setDoc(
        doc(as(ALICE), "threads", THREAD, "transactions", "t1"),
        transaction(ALICE, { amountMinor: 1000000001 })
      )
    );
  });
});

describe("identity", () => {
  it("a number can only be claimed by the account that verified it", async () => {
    await assertFails(
      setDoc(doc(as(MALLORY, "+8801700000009"), "phoneIndex", "+8801700000001"), {
        uid: MALLORY,
      })
    );
  });

  it("a number claimed by its owner is allowed", async () => {
    await assertSucceeds(
      setDoc(doc(as(MALLORY, "+8801700000009"), "phoneIndex", "+8801700000009"), {
        uid: MALLORY,
      })
    );
  });

  it("a claim cannot be taken over once made", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "phoneIndex", "+8801700000009"), { uid: ALICE });
    });

    await assertFails(
      setDoc(doc(as(MALLORY, "+8801700000009"), "phoneIndex", "+8801700000009"), {
        uid: MALLORY,
      })
    );
  });

  it("a device can claim its own session without a profile existing", async () => {
    // The session claim is what signs other devices out, and it has to work
    // for an account whose profile document is absent - otherwise signing in
    // and resetting a password both fail on a permission error.
    await assertSucceeds(
      setDoc(doc(as(MALLORY, "+8801700000009"), "sessions", MALLORY), {
        activeSessionId: "session-1",
        updatedAt: 1,
      })
    );
  });

  it("a device cannot claim somebody else's session", async () => {
    await assertFails(
      setDoc(doc(as(MALLORY, "+8801700000009"), "sessions", ALICE), {
        activeSessionId: "session-1",
        updatedAt: 1,
      })
    );
  });

  it("a session id is not readable by anyone else", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "sessions", ALICE), {
        activeSessionId: "session-1",
        updatedAt: 1,
      });
    });

    await assertFails(getDoc(doc(as(MALLORY, "+8801700000009"), "sessions", ALICE)));
  });

  it("a signed out client cannot read the phone index", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "phoneIndex", "+8801700000001"), { uid: ALICE });
    });

    await assertFails(
      getDoc(doc(testEnv.unauthenticatedContext().firestore(), "phoneIndex", "+8801700000001"))
    );
  });

  it("a profile cannot be edited by anybody else", async () => {
    await assertFails(updateDoc(doc(as(MALLORY), "users", ALICE), { name: "Not Alice" }));
  });

  it("a phone number is fixed once registered", async () => {
    await assertFails(
      updateDoc(doc(as(ALICE, "+8801700000001"), "users", ALICE), {
        name: "Alice",
        phone: "+8801700000003",
      })
    );
  });
});

describe("server-only collections", () => {
  it("a client cannot file a report directly", async () => {
    await assertFails(
      setDoc(doc(as(ALICE), "reports", "r1"), { reportedBy: ALICE, threadId: THREAD })
    );
  });

  it("a client cannot read reports", async () => {
    await assertFails(getDoc(doc(as(ALICE), "reports", "r1")));
  });

  it("a client cannot touch its own rate limit counter", async () => {
    await assertFails(setDoc(doc(as(ALICE), "rateLimits", `${ALICE}_upload`), { count: 0 }));
  });
});
