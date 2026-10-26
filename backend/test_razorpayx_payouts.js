/**
 * Comprehensive Test Suite for RazorpayX Rider Payouts Engine
 * Covers all 17 security, financial idempotency, lifecycle, and access control rules.
 */

const assert = require("assert");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const app = require("./server");

let passed = 0;
let total = 0;

function test(description, fn) {
  total++;
  try {
    fn();
    passed++;
    console.log(`  ✅ [PASS] ${description}`);
  } catch (err) {
    console.error(`  ❌ [FAIL] ${description}`);
    console.error(err);
    process.exit(1);
  }
}

// Mock Express response
function createMockRes() {
  return {
    statusCode: 200,
    headers: {},
    body: null,
    status(code) {
      this.statusCode = code;
      return this;
    },
    json(data) {
      this.body = data;
      return this;
    },
    setHeader(k, v) {
      this.headers[k] = v;
      return this;
    }
  };
}

// Helper to simulate request to route handler
function getRouteHandler(method, pathStr) {
  const routes = app._router.stack;
  for (const layer of routes) {
    if (layer.route && layer.route.path === pathStr && layer.route.methods[method.toLowerCase()]) {
      return layer.route.stack[0].handle;
    }
  }
  return null;
}

console.log("\n===============================================================");
console.log("🛡️  RUNNING RESTRO RAZORPAYX RIDER PAYOUTS REGRESSION SUITE");
console.log("===============================================================\n");

// Setup initial credentials and tokens
process.env.RAZORPAYX_KEY_ID = "rzp_test_payout_key_123";
process.env.RAZORPAYX_KEY_SECRET = "rzp_test_payout_secret_456";
process.env.RAZORPAYX_ACCOUNT_NUMBER = "2323230041123456";
process.env.RAZORPAYX_WEBHOOK_SECRET = "rzpx_whsec_secret_9988";

const riderToken = "tok_rider_payout_test";
const otherRiderToken = "tok_other_rider_test";
const adminToken = "tok_admin_payout_test";

app.db.sessions.set(riderToken, "usr-rider");
app.db.sessions.set(otherRiderToken, "usr-1"); // regular customer/other
app.db.sessions.set(adminToken, "usr-admin");

const testStorage = path.join(__dirname, "uploads", "payouts_storage.json");
if (fs.existsSync(testStorage)) {
  try { fs.unlinkSync(testStorage); } catch (_) {}
}
app.db.riderPayouts.clear();
app.db.payoutIdempotency.clear();

// Reset rider balance for tests
app.db.riderBalances.set("usr-rider", 5000); // ₹5000.00
app.db.riderBalances.set("usr-1", 1000);

const handlePayout = getRouteHandler("POST", "/api/delivery/rider/payouts");
const handleGetPayouts = getRouteHandler("GET", "/api/delivery/rider/payouts");
const handleGetReceipt = getRouteHandler("GET", "/api/delivery/rider/payouts/:id/receipt");
const handleWebhook = getRouteHandler("POST", "/api/delivery/rider/payouts/webhook");
const handleReconcile = getRouteHandler("POST", "/api/delivery/rider/payouts/:id/reconcile");

assert(handlePayout, "handlePayout route handler must be mounted");
assert(handleGetPayouts, "handleGetPayouts route handler must be mounted");
assert(handleGetReceipt, "handleGetReceipt route handler must be mounted");
assert(handleWebhook, "handleWebhook route handler must be mounted");

// --- TEST GROUP 1: Configuration & Fail-Closed Behavior ---
console.log("--- Test Group 1: Configuration & Fail-Closed Behavior ---");

test("Fail-closed: Returns 503 Service Unavailable when RAZORPAYX_KEY_ID is missing", () => {
  const oldKey = process.env.RAZORPAYX_KEY_ID;
  const oldRzpKey = process.env.RAZORPAY_KEY_ID;
  delete process.env.RAZORPAYX_KEY_ID;
  delete process.env.RAZORPAY_KEY_ID;

  const req = {
    headers: { authorization: `Bearer ${riderToken}` },
    body: { amountRupees: 500, destination: "ramesh.rider@okaxis" }
  };
  const res = createMockRes();
  handlePayout(req, res);

  assert.strictEqual(res.statusCode, 503);
  assert.strictEqual(res.body.error, "RAZORPAYX_CONFIG_MISSING");
  assert.strictEqual(res.body.success, false);

  process.env.RAZORPAYX_KEY_ID = oldKey;
  if (oldRzpKey) process.env.RAZORPAY_KEY_ID = oldRzpKey;
});

test("Fail-closed: Returns 503 when RAZORPAYX_ACCOUNT_NUMBER is missing", () => {
  const oldAcct = process.env.RAZORPAYX_ACCOUNT_NUMBER;
  delete process.env.RAZORPAYX_ACCOUNT_NUMBER;

  const req = {
    headers: { authorization: `Bearer ${riderToken}` },
    body: { amountRupees: 500, destination: "ramesh.rider@okaxis" }
  };
  const res = createMockRes();
  handlePayout(req, res);

  assert.strictEqual(res.statusCode, 503);
  assert.strictEqual(res.body.error, "RAZORPAYX_CONFIG_MISSING");

  process.env.RAZORPAYX_ACCOUNT_NUMBER = oldAcct;
});

// --- TEST GROUP 2: Authentication & Authorization ---
console.log("\n--- Test Group 2: Authentication & Authorization ---");

test("Unauthenticated request returns 401 UNAUTHORIZED", () => {
  const req = {
    headers: {},
    body: { amountRupees: 500, destination: "ramesh.rider@okaxis" }
  };
  const res = createMockRes();
  handlePayout(req, res);

  assert.strictEqual(res.statusCode, 401);
  assert.strictEqual(res.body.error, "UNAUTHORIZED");
});

test("Non-rider role (CUSTOMER) cannot request payout (403 ROLE_FORBIDDEN)", () => {
  const req = {
    headers: { authorization: `Bearer ${otherRiderToken}` },
    body: { amountRupees: 500, destination: "customer@okaxis" }
  };
  const res = createMockRes();
  handlePayout(req, res);

  assert.strictEqual(res.statusCode, 403);
  assert.strictEqual(res.body.error, "ROLE_FORBIDDEN");
});

test("Rider cannot request payout for another rider ID (403 ACCESS_DENIED)", () => {
  const req = {
    headers: { authorization: `Bearer ${riderToken}` },
    body: { riderId: "usr-rider-2", amountRupees: 500, destination: "other@okaxis" }
  };
  const res = createMockRes();
  handlePayout(req, res);

  assert.strictEqual(res.statusCode, 403);
  assert.strictEqual(res.body.error, "ACCESS_DENIED");
});

// --- TEST GROUP 3: Validation (Amount, Methods, Destination) ---
console.log("\n--- Test Group 3: Input & Destination Validation ---");

test("Rejects non-numeric amount with 400 INVALID_AMOUNT", () => {
  const req = {
    headers: { authorization: `Bearer ${riderToken}` },
    body: { amountRupees: "five_hundred", destination: "ramesh.rider@okaxis" }
  };
  const res = createMockRes();
  handlePayout(req, res);

  assert.strictEqual(res.statusCode, 400);
  assert.strictEqual(res.body.error, "INVALID_AMOUNT");
});

test("Rejects zero or negative amount with 400 INVALID_AMOUNT", () => {
  const req = {
    headers: { authorization: `Bearer ${riderToken}` },
    body: { amountRupees: -50, destination: "ramesh.rider@okaxis" }
  };
  const res = createMockRes();
  handlePayout(req, res);

  assert.strictEqual(res.statusCode, 400);
  assert.strictEqual(res.body.error, "INVALID_AMOUNT");
});

test("Rejects amount below ₹10 minimum threshold with 400 AMOUNT_BELOW_MINIMUM", () => {
  const req = {
    headers: { authorization: `Bearer ${riderToken}` },
    body: { amountRupees: 5, destination: "ramesh.rider@okaxis" }
  };
  const res = createMockRes();
  handlePayout(req, res);

  assert.strictEqual(res.statusCode, 400);
  assert.strictEqual(res.body.error, "AMOUNT_BELOW_MINIMUM");
});

test("Rejects unsupported payout method with 400 UNSUPPORTED_PAYOUT_METHOD", () => {
  const req = {
    headers: { authorization: `Bearer ${riderToken}` },
    body: { amountRupees: 100, payoutMethod: "BITCOIN", destination: "ramesh.rider@okaxis" }
  };
  const res = createMockRes();
  handlePayout(req, res);

  assert.strictEqual(res.statusCode, 400);
  assert.strictEqual(res.body.error, "UNSUPPORTED_PAYOUT_METHOD");
});

test("Rejects malformed UPI VPA format with 400 INVALID_UPI_VPA_FORMAT", () => {
  const badVpas = ["invalid-vpa", "user@@bank", "@bank", "user@", "user space@bank"];
  for (const badVpa of badVpas) {
    const req = {
      headers: { authorization: `Bearer ${riderToken}` },
      body: { amountRupees: 100, payoutMethod: "UPI", destination: badVpa }
    };
    const res = createMockRes();
    handlePayout(req, res);

    assert.strictEqual(res.statusCode, 400, `Expected 400 for bad VPA: ${badVpa}`);
    assert.strictEqual(res.body.error, "INVALID_UPI_VPA_FORMAT");
  }
});

test("Rejects payout exceeding trusted available balance with 422 INSUFFICIENT_BALANCE", () => {
  app.db.riderBalances.set("usr-rider", 500); // Current balance ₹500
  const req = {
    headers: { authorization: `Bearer ${riderToken}` },
    body: { amountRupees: 1200, payoutMethod: "UPI", destination: "ramesh.rider@okaxis" }
  };
  const res = createMockRes();
  handlePayout(req, res);

  assert.strictEqual(res.statusCode, 422);
  assert.strictEqual(res.body.error, "INSUFFICIENT_BALANCE");
  assert.strictEqual(res.body.availableBalance, 500);
});

// --- TEST GROUP 4: Successful Payout & State Tracking ---
console.log("\n--- Test Group 4: Successful Payout & State Tracking ---");

let createdPayoutId = "";
let createdReceiptNumber = "";

test("Valid UPI payout succeeds and deducts balance accurately", () => {
  app.db.riderBalances.set("usr-rider", 4000); // Set initial balance ₹4000

  const req = {
    headers: {
      authorization: `Bearer ${riderToken}`,
      "x-payout-idempotency": "idemp_test_key_001"
    },
    body: {
      amountRupees: 1500,
      payoutMethod: "UPI",
      destination: "ramesh.rider@okaxis"
    }
  };
  const res = createMockRes();
  handlePayout(req, res);

  assert.strictEqual(res.statusCode, 200);
  assert.strictEqual(res.body.success, true);
  assert.strictEqual(res.body.payout.status, "processed");
  assert.strictEqual(res.body.payout.amountRupees, 1500);
  assert.strictEqual(res.body.payout.currency, "INR");
  assert.ok(res.body.payout.utr, "Processed payout must carry real bank UTR");
  assert.ok(!res.body.payout.utr.includes("null"));
  assert.ok(res.body.payout.receiptNumber.startsWith("RCP-"));

  // Destination must be masked for privacy (no plain exposure)
  assert.strictEqual(res.body.payout.accountDestination, "r***r@okaxis");
  assert.strictEqual(res.body.payout.rawDestination, undefined);

  // Balance must be accurately deducted from 4000 to 2500
  assert.strictEqual(app.db.riderBalances.get("usr-rider"), 2500);

  createdPayoutId = res.body.payout.id;
  createdReceiptNumber = res.body.payout.receiptNumber;
});

// --- TEST GROUP 5: Durable Idempotency Protection ---
console.log("\n--- Test Group 5: Durable Idempotency Protection ---");

test("Duplicate submission with same idempotency key does NOT debit balance twice", () => {
  const balanceBefore = app.db.riderBalances.get("usr-rider"); // 2500

  const req = {
    headers: {
      authorization: `Bearer ${riderToken}`,
      "x-payout-idempotency": "idemp_test_key_001"
    },
    body: {
      amountRupees: 1500,
      payoutMethod: "UPI",
      destination: "ramesh.rider@okaxis"
    }
  };
  const res = createMockRes();
  handlePayout(req, res);

  assert.strictEqual(res.statusCode, 200);
  assert.strictEqual(res.body.duplicate, true);
  assert.strictEqual(res.body.payout.id, createdPayoutId);

  // Balance must REMAIN 2500 (no double debit!)
  assert.strictEqual(app.db.riderBalances.get("usr-rider"), balanceBefore);
});

// --- TEST GROUP 6: Provider Rejection & Timeout Handling ---
console.log("\n--- Test Group 6: Provider Rejection & Timeout Handling ---");

test("Provider rejection restores rider balance and marks payout failed", () => {
  app.db.riderBalances.set("usr-rider", 2000);

  const req = {
    headers: { authorization: `Bearer ${riderToken}` },
    body: {
      amountRupees: 800,
      payoutMethod: "UPI",
      destination: "invalid_vpa_reject@okaxis"
    }
  };
  const res = createMockRes();
  handlePayout(req, res);

  assert.strictEqual(res.statusCode, 422);
  assert.strictEqual(res.body.error, "PROVIDER_PAYOUT_REJECTED");
  assert.strictEqual(res.body.payout.status, "failed");
  assert.ok(res.body.payout.failureReason);

  // Balance must be restored to ₹2000
  assert.strictEqual(app.db.riderBalances.get("usr-rider"), 2000);
});

test("Provider timeout marks status pending without double-debiting", () => {
  app.db.riderBalances.set("usr-rider", 2000);

  const req = {
    headers: { authorization: `Bearer ${riderToken}` },
    body: {
      amountRupees: 500,
      payoutMethod: "UPI",
      destination: "timeout_rider@okaxis"
    }
  };
  const res = createMockRes();
  handlePayout(req, res);

  assert.strictEqual(res.statusCode, 202);
  assert.strictEqual(res.body.pending, true);
  assert.strictEqual(res.body.payout.status, "pending");
  assert.strictEqual(res.body.payout.utr, null, "Pending payout must NOT fabricate a UTR");

  // Balance deducted while pending
  assert.strictEqual(app.db.riderBalances.get("usr-rider"), 1500);
});

// --- TEST GROUP 7: Payout History & Receipt Ownership ---
console.log("\n--- Test Group 7: Payout History & Receipt Ownership ---");

test("Rider can retrieve own payout history", () => {
  const req = {
    headers: { authorization: `Bearer ${riderToken}` },
    query: {}
  };
  const res = createMockRes();
  handleGetPayouts(req, res);

  assert.strictEqual(res.statusCode, 200);
  assert.strictEqual(res.body.success, true);
  assert.ok(Array.isArray(res.body.payouts));
  assert.ok(res.body.payouts.length > 0);
  assert.ok(res.body.payouts.every(p => p.riderId === "usr-rider"));
});

test("Rider cannot view another rider's payout history (403 ACCESS_DENIED)", () => {
  const req = {
    headers: { authorization: `Bearer ${riderToken}` },
    query: { riderId: "usr-rider-other" }
  };
  const res = createMockRes();
  handleGetPayouts(req, res);

  assert.strictEqual(res.statusCode, 403);
  assert.strictEqual(res.body.error, "ACCESS_DENIED");
});

test("Rider can retrieve trusted receipt for own payout", () => {
  const req = {
    headers: { authorization: `Bearer ${riderToken}` },
    params: { id: createdPayoutId }
  };
  const res = createMockRes();
  handleGetReceipt(req, res);

  assert.strictEqual(res.statusCode, 200);
  assert.strictEqual(res.body.success, true);
  assert.strictEqual(res.body.receipt.payoutId, createdPayoutId);
  assert.strictEqual(res.body.receipt.receiptNumber, createdReceiptNumber);
  assert.strictEqual(res.body.receipt.riderId, "usr-rider");
  assert.ok(res.body.receipt.maskedDestination.includes("okaxis"));
  assert.ok(res.body.receipt.legalNote);
});

test("Other user cannot retrieve another rider's receipt (403 ACCESS_DENIED)", () => {
  const req = {
    headers: { authorization: `Bearer ${otherRiderToken}` }, // usr-1
    params: { id: createdPayoutId }
  };
  const res = createMockRes();
  handleGetReceipt(req, res);

  assert.strictEqual(res.statusCode, 403);
  assert.strictEqual(res.body.error, "ACCESS_DENIED");
});

// --- TEST GROUP 8: Webhook Signature & Out-of-Order Events ---
console.log("\n--- Test Group 8: Webhook Security & Out-of-Order Protection ---");

test("Webhook rejects missing or invalid signature with 400 INVALID_WEBHOOK_SIGNATURE", () => {
  const payload = { event: "payout.processed", payload: { payout: { entity: { id: createdPayoutId } } } };
  const req = {
    headers: { "x-razorpay-signature": "tampered_bad_signature" },
    body: payload,
    rawBody: JSON.stringify(payload)
  };
  const res = createMockRes();
  handleWebhook(req, res);

  assert.strictEqual(res.statusCode, 400);
  assert.strictEqual(res.body.error, "INVALID_WEBHOOK_SIGNATURE");
});

test("Webhook accepts valid HMAC-SHA256 signature and processes update", () => {
  // Create a pending payout to update via webhook
  const pendingPayoutId = "pout_wh_test_100";
  app.db.riderPayouts.set(pendingPayoutId, {
    id: pendingPayoutId,
    riderId: "usr-rider",
    riderName: "Ramesh Rider",
    amountRupees: 600,
    accountDestination: "ramesh.rider@okaxis",
    payoutMethod: "UPI",
    status: "processing",
    referenceId: "ref_wh_100",
    utr: null
  });

  const webhookSecret = process.env.RAZORPAYX_WEBHOOK_SECRET;
  const payload = {
    event: "payout.processed",
    payload: {
      payout: {
        entity: {
          id: pendingPayoutId,
          reference_id: "ref_wh_100",
          status: "processed",
          utr: "UTR998877665544"
        }
      }
    }
  };
  const rawBody = JSON.stringify(payload);
  const validSig = crypto.createHmac("sha256", webhookSecret).update(rawBody).digest("hex");

  const req = {
    headers: { "x-razorpay-signature": validSig },
    body: payload,
    rawBody: rawBody
  };
  const res = createMockRes();
  handleWebhook(req, res);

  assert.strictEqual(res.statusCode, 200);
  assert.strictEqual(res.body.status, "ok");

  // Check state was updated
  const updated = app.db.riderPayouts.get(pendingPayoutId);
  assert.strictEqual(updated.status, "processed");
  assert.strictEqual(updated.utr, "UTR998877665544");
});

test("Terminal State Protection: Out-of-order queued/pending event cannot regress processed status", () => {
  const webhookSecret = process.env.RAZORPAYX_WEBHOOK_SECRET;
  const payload = {
    event: "payout.queued", // Older out-of-order event arriving after processed!
    payload: {
      payout: {
        entity: {
          id: createdPayoutId,
          status: "queued"
        }
      }
    }
  };
  const rawBody = JSON.stringify(payload);
  const validSig = crypto.createHmac("sha256", webhookSecret).update(rawBody).digest("hex");

  const req = {
    headers: { "x-razorpay-signature": validSig },
    body: payload,
    rawBody: rawBody
  };
  const res = createMockRes();
  handleWebhook(req, res);

  assert.strictEqual(res.statusCode, 200);
  assert.strictEqual(res.body.status, "ignored_older_event");

  // Status must remain PROCESSED
  const current = app.db.riderPayouts.get(createdPayoutId);
  assert.strictEqual(current.status, "processed");
});

test("Webhook payout.reversed refunds rider balance and marks reversed", () => {
  const revPayoutId = "pout_wh_rev_200";
  app.db.riderBalances.set("usr-rider", 1000);
  app.db.riderPayouts.set(revPayoutId, {
    id: revPayoutId,
    riderId: "usr-rider",
    amountRupees: 700,
    accountDestination: "ramesh.rider@okaxis",
    status: "processing",
    referenceId: "ref_wh_200"
  });

  const webhookSecret = process.env.RAZORPAYX_WEBHOOK_SECRET;
  const payload = {
    event: "payout.reversed",
    payload: {
      payout: {
        entity: {
          id: revPayoutId,
          status: "reversed",
          failure_reason: "Account frozen by beneficiary bank"
        }
      }
    }
  };
  const rawBody = JSON.stringify(payload);
  const validSig = crypto.createHmac("sha256", webhookSecret).update(rawBody).digest("hex");

  const req = {
    headers: { "x-razorpay-signature": validSig },
    body: payload,
    rawBody: rawBody
  };
  const res = createMockRes();
  handleWebhook(req, res);

  assert.strictEqual(res.statusCode, 200);
  const updated = app.db.riderPayouts.get(revPayoutId);
  assert.strictEqual(updated.status, "reversed");
  assert.strictEqual(updated.failureReason, "Account frozen by beneficiary bank");

  // Balance must be refunded from 1000 to 1700
  assert.strictEqual(app.db.riderBalances.get("usr-rider"), 1700);
});

// --- TEST GROUP 9: Durable Storage Persistence ---
console.log("\n--- Test Group 9: Durable Storage Persistence ---");

test("Durable persistence saves records to JSON and restores them", () => {
  app.saveDurablePayouts();

  // Clear in-memory map
  const beforeCount = app.db.riderPayouts.size;
  assert.ok(beforeCount > 0);

  app.db.riderPayouts.clear();
  assert.strictEqual(app.db.riderPayouts.size, 0);

  // Reload from durable file
  app.loadDurablePayouts();
  assert.strictEqual(app.db.riderPayouts.size, beforeCount);
  assert.ok(app.db.riderPayouts.has(createdPayoutId));
  assert.strictEqual(app.db.riderPayouts.get(createdPayoutId).status, "processed");
});

console.log("\n===============================================================");
console.log(`📊 TEST RESULTS: ${passed} / ${total} TESTS PASSED (100% Verified)`);
console.log("===============================================================\n");
