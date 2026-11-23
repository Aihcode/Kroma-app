const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_creator_123";
const BOB_UID = "bob_creator_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("media_posts").get());
  await assertFails(unauthDb.collection("transactions").get());
  await assertFails(unauthDb.collection("payouts").get());
});

test("User profile: owner can create with zero balances and update PayPal email, but cannot tamper balances or verifiedCreator", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  // Valid creation
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Kroma",
      handle: "@alicekroma",
      preferredPayoutCurrency: "USD",
      verifiedCreator: false,
      availableBalanceCents: 0,
      pendingBalanceCents: 0,
      totalEarnedCents: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid partial update (linking PayPal email)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      paypalEmail: "alice@paypal-test.com",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject balance manipulation from client
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      availableBalanceCents: 999999,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject self-verification privilege escalation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      verifiedCreator: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject cross-user PII read
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});

test("Media posts: owner CRUD and query alignment succeed; ghost fields & earnings tampering fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("media_posts").doc("post_01").set({
      userId: ALICE_UID,
      creatorName: "Alice Kroma",
      title: "Reel BTS con CapCut",
      description: "Editado externamente y monetizado",
      mediaType: "VIDEO",
      visibility: "PRIVATE",
      monetizationType: "PAY_PER_VIEW",
      priceCents: 499,
      tags: ["bts", "capcut"],
      mediaUri: "content://com.aistudio.kromastudio.vxqmtz.fileprovider/media/clip.mp4",
      editedExternally: true,
      externalEditorName: "CapCut",
      uploadState: "PUBLISHED",
      uploadProgress: 100,
      viewsCount: 0,
      earningsCents: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Query aligned with Kotlin repository
  await assertSucceeds(
    aliceDb.collection("media_posts").where("userId", "==", ALICE_UID).get()
  );

  // Unfiltered list query fails for private posts
  await assertFails(bobDb.collection("media_posts").get());

  // Cross-user private get fails
  await assertFails(bobDb.collection("media_posts").doc("post_01").get());

  // Tampering with earningsCents fails
  await assertFails(
    aliceDb.collection("media_posts").doc("post_01").update({
      earningsCents: 50000,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Live streams & chat: lifecycle terminal lock and scoped chat queries", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("live_streams").doc("live_01").set({
      userId: ALICE_UID,
      creatorName: "Alice Kroma",
      title: "Estudio en Vivo M3",
      rtmpIngestUrl: "rtmps://global-live.mux.com:443/app/stream_key",
      playbackUrl: "https://stream.mux.com/playback_id.m3u8",
      status: "LIVE",
      viewerCount: 0,
      tipsEarnedCents: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob can post a zero-tip chat message into active live stream
  await assertSucceeds(
    bobDb.collection("live_streams").doc("live_01").collection("chat_messages").doc("msg_1").set({
      streamId: "live_01",
      userId: BOB_UID,
      senderName: "Bob Viewer",
      message: "Brutal el setup!",
      tipCents: 0,
      createdAt: now,
    })
  );

  // Bob cannot spoof a paid tip directly from client
  await assertFails(
    bobDb.collection("live_streams").doc("live_01").collection("chat_messages").doc("msg_2").set({
      streamId: "live_01",
      userId: BOB_UID,
      senderName: "Bob Viewer",
      message: "Fake tip",
      tipCents: 1000,
      createdAt: now,
    })
  );

  // Aligned chat query succeeds
  await assertSucceeds(
    aliceDb
      .collection("live_streams")
      .doc("live_01")
      .collection("chat_messages")
      .where("streamId", "==", "live_01")
      .get()
  );

  // End live stream -> terminal state locked
  await assertSucceeds(
    aliceDb.collection("live_streams").doc("live_01").update({
      status: "ENDED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(
    aliceDb.collection("live_streams").doc("live_01").update({
      status: "LIVE",
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("Transactions & payouts: strictly read-only for owner, client writes rejected", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("transactions").doc("tx_1").set({
      userId: ALICE_UID,
      sourceType: "LIVE_GIFT",
      title: "Regalo en Live",
      referenceId: "live_01",
      grossAmountCents: 1000,
      platformFeeCents: 300,
      netAmountCents: 700,
      status: "AVAILABLE",
      createdAt: new Date(Date.now() - 5000),
    });
    await db.collection("payouts").doc("pay_1").set({
      userId: ALICE_UID,
      paypalEmail: "alice@paypal.com",
      amountCents: 2500,
      currency: "USD",
      payoutBatchId: "BATCH_999",
      status: "PENDING",
      createdAt: new Date(Date.now() - 5000),
      updatedAt: new Date(Date.now() - 5000),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("transactions").where("userId", "==", ALICE_UID).get()
  );
  await assertSucceeds(
    aliceDb.collection("payouts").where("userId", "==", ALICE_UID).get()
  );

  // Cross-user and unfiltered queries fail
  await assertFails(bobDb.collection("transactions").doc("tx_1").get());
  await assertFails(aliceDb.collection("transactions").get());

  // Client writes to financial collections always fail
  await assertFails(
    aliceDb.collection("transactions").doc("tx_fake").set({
      userId: ALICE_UID,
      sourceType: "VIDEO_VIEWS",
      title: "Fake",
      referenceId: "x",
      grossAmountCents: 1000,
      platformFeeCents: 0,
      netAmountCents: 1000,
      status: "AVAILABLE",
      createdAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("payouts").doc("pay_fake").set({
      userId: ALICE_UID,
      paypalEmail: "alice@paypal.com",
      amountCents: 1000,
      currency: "USD",
      payoutBatchId: "FAKE",
      status: "SUCCESS",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});
