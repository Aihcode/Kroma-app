import * as admin from "firebase-admin";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { onRequest, HttpsError } from "firebase-functions/v2/https";
import * as crypto from "node:crypto";

admin.initializeApp();

const DATABASE_ID =
  process.env.FIRESTORE_DATABASE_ID ||
  "ai-studio-android-kromastu-c3be64a7-4d89-4051-8688-54bf4b6242db";

const db = getFirestore(admin.app(), DATABASE_ID);

const MIN_PAYOUT_CENTS = 1000; // $10.00 USD
const CREATOR_SHARE_RATIO = 0.7; // 70% creator, 30% platform fee

function getPayPalBaseUrl(): string {
  return process.env.PAYPAL_MODE === "live"
    ? "https://api-m.paypal.com"
    : "https://api-m.sandbox.paypal.com";
}

async function getPayPalAccessToken(): Promise<string> {
  const clientId = process.env.PAYPAL_CLIENT_ID;
  const clientSecret = process.env.PAYPAL_CLIENT_SECRET;
  if (!clientId || !clientSecret) {
    throw new HttpsError(
      "failed-precondition",
      "PayPal server credentials are not configured."
    );
  }

  const credentials = Buffer.from(`${clientId}:${clientSecret}`).toString("base64");
  const response = await fetch(`${getPayPalBaseUrl()}/v1/oauth2/token`, {
    method: "POST",
    headers: {
      Authorization: `Basic ${credentials}`,
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: "grant_type=client_credentials",
  });

  if (!response.ok) {
    const errText = await response.text();
    throw new HttpsError("internal", `PayPal OAuth failed: ${response.status} ${errText}`);
  }

  const json = (await response.json()) as { access_token?: string };
  if (!json.access_token) {
    throw new HttpsError("internal", "Missing PayPal access_token");
  }
  return json.access_token;
}

async function verifyFirebaseBearerToken(authHeader?: string): Promise<string> {
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    throw new HttpsError("unauthenticated", "Missing Authorization Bearer token.");
  }
  const idToken = authHeader.slice("Bearer ".length).trim();
  const decoded = await admin.auth().verifyIdToken(idToken);
  return decoded.uid;
}

/**
 * 1. HTTP Endpoint: requestPayPalPayout
 * Authenticates Firebase ID token, atomically deducts availableBalanceCents via Firestore Transaction,
 * calls PayPal Payouts REST API (POST /v1/payments/payouts), and writes payout receipt.
 * Automatically refunds balance if the PayPal API request rejects before batch creation.
 */
export const requestPayPalPayout = onRequest({ cors: false }, async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).json({ error: "Method Not Allowed" });
    return;
  }

  try {
    const uid = await verifyFirebaseBearerToken(req.headers.authorization);
    const { amountCents, paypalEmail, currency = "USD" } = req.body as {
      amountCents?: number;
      paypalEmail?: string;
      currency?: string;
    };

    if (
      typeof amountCents !== "number" ||
      !Number.isInteger(amountCents) ||
      amountCents < MIN_PAYOUT_CENTS
    ) {
      res.status(400).json({ error: "Minimum payout is $10.00 USD (1000 cents)." });
      return;
    }

    if (!paypalEmail || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(paypalEmail)) {
      res.status(400).json({ error: "Valid PayPal email is required." });
      return;
    }

    if (!["USD", "EUR", "MXN"].includes(currency)) {
      res.status(400).json({ error: "Unsupported payout currency." });
      return;
    }

    const payoutId = `pay_${crypto.randomUUID().replace(/-/g, "").slice(0, 20)}`;
    const senderBatchId = `KROMA_${uid.slice(0, 8)}_${Date.now()}`;
    const userRef = db.collection("users").doc(uid);
    const payoutRef = db.collection("payouts").doc(payoutId);
    const txRef = db.collection("transactions").doc(`tx_${payoutId}`);

    // Step A: Atomic balance validation and debit
    await db.runTransaction(async (tx) => {
      const userSnap = await tx.get(userRef);
      if (!userSnap.exists) {
        throw new HttpsError("not-found", "Creator profile does not exist.");
      }

      const data = userSnap.data()!;
      const available = Number(data.availableBalanceCents ?? 0);
      if (available < amountCents) {
        throw new HttpsError("failed-precondition", "Insufficient available balance.");
      }

      tx.update(userRef, {
        availableBalanceCents: FieldValue.increment(-amountCents),
        paypalEmail,
        updatedAt: FieldValue.serverTimestamp(),
      });

      tx.set(payoutRef, {
        userId: uid,
        paypalEmail,
        amountCents,
        currency,
        payoutBatchId: senderBatchId,
        status: "REQUESTED",
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });

      tx.set(txRef, {
        userId: uid,
        sourceType: "PAYOUT_DEBIT",
        title: `Retiro PayPal (${paypalEmail})`,
        referenceId: payoutId,
        grossAmountCents: amountCents,
        platformFeeCents: 0,
        netAmountCents: amountCents,
        status: "PAID_OUT",
        createdAt: FieldValue.serverTimestamp(),
      });
    });

    // Step B: Execute PayPal Payouts REST API call
    try {
      const accessToken = await getPayPalAccessToken();
      const amountDecimal = (amountCents / 100).toFixed(2);

      const ppResponse = await fetch(`${getPayPalBaseUrl()}/v1/payments/payouts`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
          "PayPal-Request-Id": senderBatchId,
        },
        body: JSON.stringify({
          sender_batch_header: {
            sender_batch_id: senderBatchId,
            email_subject: "Tu retiro de Kroma Studio está en camino",
            email_message: "Gracias por crear y monetizar en Kroma Studio.",
          },
          items: [
            {
              recipient_type: "EMAIL",
              amount: {
                value: amountDecimal,
                currency,
              },
              note: `Kroma Studio Payout ${payoutId}`,
              sender_item_id: payoutId,
              receiver: paypalEmail,
            },
          ],
        }),
      });

      const ppJson = (await ppResponse.json()) as {
        batch_header?: { payout_batch_id?: string; batch_status?: string };
        message?: string;
      };

      if (!ppResponse.ok || !ppJson.batch_header?.payout_batch_id) {
        throw new Error(ppJson.message || `PayPal rejected payout (${ppResponse.status})`);
      }

      const batchId = ppJson.batch_header.payout_batch_id;
      await payoutRef.update({
        payoutBatchId: batchId,
        status: "PENDING",
        updatedAt: FieldValue.serverTimestamp(),
      });

      res.status(200).json({
        payoutId,
        payoutBatchId: batchId,
        status: "PENDING",
        amountCents,
        currency,
      });
    } catch (paypalError) {
      // Compensating atomic refund on PayPal API failure
      const reason =
        paypalError instanceof Error ? paypalError.message : "PayPal API failure";
      await db.runTransaction(async (tx) => {
        tx.update(userRef, {
          availableBalanceCents: FieldValue.increment(amountCents),
          updatedAt: FieldValue.serverTimestamp(),
        });
        tx.update(payoutRef, {
          status: "FAILED",
          failureReason: reason.slice(0, 240),
          updatedAt: FieldValue.serverTimestamp(),
        });
      });

      res.status(502).json({ error: reason, payoutId, status: "FAILED" });
    }
  } catch (err) {
    if (err instanceof HttpsError) {
      const code =
        err.code === "unauthenticated"
          ? 401
          : err.code === "failed-precondition"
          ? 400
          : err.code === "not-found"
          ? 404
          : 500;
      res.status(code).json({ error: err.message });
      return;
    }
    res.status(500).json({
      error: err instanceof Error ? err.message : "Internal Server Error",
    });
  }
});

/**
 * 2. PayPal Webhook Listener: paypalPayoutWebhook
 * Updates payout status to SUCCESS or FAILED (refunding balance on failure).
 */
export const paypalPayoutWebhook = onRequest({ cors: false }, async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).send("Method Not Allowed");
    return;
  }

  try {
    const webhookId = process.env.PAYPAL_WEBHOOK_ID;
    if (!webhookId) {
      res.status(503).json({ error: "PAYPAL_WEBHOOK_ID not configured" });
      return;
    }

    const accessToken = await getPayPalAccessToken();
    const verifyResp = await fetch(
      `${getPayPalBaseUrl()}/v1/notifications/verify-webhook-signature`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          auth_algo: req.headers["paypal-auth-algo"],
          cert_url: req.headers["paypal-cert-url"],
          transmission_id: req.headers["paypal-transmission-id"],
          transmission_sig: req.headers["paypal-transmission-sig"],
          transmission_time: req.headers["paypal-transmission-time"],
          webhook_id: webhookId,
          webhook_event: req.body,
        }),
      }
    );

    const verifyJson = (await verifyResp.json()) as { verification_status?: string };
    if (verifyJson.verification_status !== "SUCCESS") {
      res.status(401).json({ error: "Invalid PayPal webhook signature" });
      return;
    }

    const eventType = req.body?.event_type as string | undefined;
    const senderItemId = req.body?.resource?.payout_item?.sender_item_id as string | undefined;
    if (!senderItemId) {
      res.status(200).json({ ignored: true });
      return;
    }

    const payoutRef = db.collection("payouts").doc(senderItemId);
    await db.runTransaction(async (tx) => {
      const snap = await tx.get(payoutRef);
      if (!snap.exists) return;

      const payout = snap.data()!;
      if (payout.status === "SUCCESS" || payout.status === "FAILED") return;

      if (eventType === "PAYMENT.PAYOUTS-ITEM.SUCCEEDED") {
        tx.update(payoutRef, {
          status: "SUCCESS",
          updatedAt: FieldValue.serverTimestamp(),
        });
      } else if (
        eventType === "PAYMENT.PAYOUTS-ITEM.FAILED" ||
        eventType === "PAYMENT.PAYOUTS-ITEM.RETURNED" ||
        eventType === "PAYMENT.PAYOUTS-ITEM.BLOCKED"
      ) {
        const userRef = db.collection("users").doc(payout.userId);
        tx.update(userRef, {
          availableBalanceCents: FieldValue.increment(Number(payout.amountCents)),
          updatedAt: FieldValue.serverTimestamp(),
        });
        tx.update(payoutRef, {
          status: "FAILED",
          failureReason: eventType,
          updatedAt: FieldValue.serverTimestamp(),
        });
      }
    });

    res.status(200).json({ received: true });
  } catch (err) {
    res.status(500).json({
      error: err instanceof Error ? err.message : "Webhook processing failed",
    });
  }
});

/**
 * 3. Server-Side Revenue Settlement: processRevenueEvent
 * Computes 70/30 revenue split server-side and updates creator balances + media/live metrics atomically.
 */
export const processRevenueEvent = onRequest({ cors: false }, async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).json({ error: "Method Not Allowed" });
    return;
  }

  try {
    const callerUid = await verifyFirebaseBearerToken(req.headers.authorization);
    const { creatorId, sourceType, referenceId, title, grossAmountCents } = req.body as {
      creatorId?: string;
      sourceType?: "VIDEO_VIEWS" | "EXCLUSIVE_UNLOCK" | "LIVE_GIFT";
      referenceId?: string;
      title?: string;
      grossAmountCents?: number;
    };

    if (
      !creatorId ||
      !referenceId ||
      !title ||
      !sourceType ||
      !["VIDEO_VIEWS", "EXCLUSIVE_UNLOCK", "LIVE_GIFT"].includes(sourceType) ||
      typeof grossAmountCents !== "number" ||
      !Number.isInteger(grossAmountCents) ||
      grossAmountCents <= 0 ||
      grossAmountCents > 100000
    ) {
      res.status(400).json({ error: "Invalid monetization payload." });
      return;
    }

    const netAmountCents = Math.floor(grossAmountCents * CREATOR_SHARE_RATIO);
    const platformFeeCents = grossAmountCents - netAmountCents;
    const txId = `tx_${crypto.randomUUID().replace(/-/g, "").slice(0, 20)}`;

    const userRef = db.collection("users").doc(creatorId);
    const txRef = db.collection("transactions").doc(txId);

    await db.runTransaction(async (tx) => {
      const userSnap = await tx.get(userRef);
      if (!userSnap.exists) {
        throw new HttpsError("not-found", "Creator not found.");
      }

      tx.update(userRef, {
        availableBalanceCents: FieldValue.increment(netAmountCents),
        totalEarnedCents: FieldValue.increment(netAmountCents),
        updatedAt: FieldValue.serverTimestamp(),
      });

      tx.set(txRef, {
        userId: creatorId,
        payerUid: callerUid,
        sourceType,
        title: title.slice(0, 120),
        referenceId,
        grossAmountCents,
        platformFeeCents,
        netAmountCents,
        status: "AVAILABLE",
        createdAt: FieldValue.serverTimestamp(),
      });

      if (sourceType === "LIVE_GIFT") {
        const liveRef = db.collection("live_streams").doc(referenceId);
        tx.update(liveRef, {
          tipsEarnedCents: FieldValue.increment(netAmountCents),
          updatedAt: FieldValue.serverTimestamp(),
        });
      } else {
        const postRef = db.collection("media_posts").doc(referenceId);
        tx.update(postRef, {
          earningsCents: FieldValue.increment(netAmountCents),
          viewsCount: FieldValue.increment(sourceType === "VIDEO_VIEWS" ? 1 : 0),
          updatedAt: FieldValue.serverTimestamp(),
        });
      }
    });

    res.status(200).json({
      txId,
      netAmountCents,
      platformFeeCents,
    });
  } catch (err) {
    res.status(500).json({
      error: err instanceof Error ? err.message : "Revenue settlement failed",
    });
  }
});
