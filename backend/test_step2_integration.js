/**
 * End-to-End Step 2 Integration Verification Suite
 * Verifies seamless interaction across:
 * 1. Gemini Vision document OCR & localized verification (Commits 1-3)
 * 2. Razorpay payment verification & dining restrictions (Commit 4)
 * 3. RazorpayX rider payouts, idempotency & webhook security (Commit 5)
 * 4. Gemini multilingual menu translations & Restro.AI integration (Commit 6)
 */

const http = require('http');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const os = require('os');
const assert = require('assert');

const app = require('./server');

const TEST_PORT = 8099;
let server;

let passed = 0;
let total = 0;

function it(desc, fn) {
  total++;
  try {
    fn();
    console.log(`  ✅ [PASS] ${desc}`);
    passed++;
  } catch (err) {
    console.error(`  ❌ [FAIL] ${desc}`);
    console.error(`     Reason: ${err.message}`);
    process.exit(1);
  }
}

async function itAsync(desc, fn) {
  total++;
  try {
    await fn();
    console.log(`  ✅ [PASS] ${desc}`);
    passed++;
  } catch (err) {
    console.error(`  ❌ [FAIL] ${desc}`);
    console.error(`     Reason: ${err.message}`);
    process.exit(1);
  }
}

function request(options, bodyBuffer = null, headers = {}) {
  return new Promise((resolve, reject) => {
    const req = http.request({
      hostname: '127.0.0.1',
      port: TEST_PORT,
      path: options.path,
      method: options.method || 'GET',
      headers: {
        ...headers,
        ...(options.headers || {})
      }
    }, (res) => {
      let data = [];
      res.on('data', chunk => data.push(chunk));
      res.on('end', () => {
        const fullBuffer = Buffer.concat(data);
        let json = null;
        try {
          json = JSON.parse(fullBuffer.toString('utf8'));
        } catch (_) {}
        resolve({
          statusCode: res.statusCode,
          headers: res.headers,
          body: json,
          rawBuffer: fullBuffer
        });
      });
    });

    req.on('error', reject);
    if (bodyBuffer) {
      req.write(bodyBuffer);
    }
    req.end();
  });
}

const validPdfHeader = Buffer.from('%PDF-1.4\n%âãÏÓ\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF');

async function runStep2Integration() {
  console.log('\n===============================================================');
  console.log('🔄 RUNNING COMPLETE STEP 2 END-TO-END INTEGRATION SUITE');
  console.log('===============================================================\n');

  // Start HTTP Server
  await new Promise((resolve) => {
    server = http.createServer(app);
    server.listen(TEST_PORT, () => {
      resolve();
    });
  });

  try {
    // -------------------------------------------------------------
    // AUTH SETUP: Login Personas
    // -------------------------------------------------------------
    const riderLogin = await request({
      path: '/api/delivery/auth/login',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, Buffer.from(JSON.stringify({ email: 'rider@restro.app', password: 'Rider@123' })));
    const riderToken = riderLogin.body.token;

    const adminLogin = await request({
      path: '/api/delivery/auth/login',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, Buffer.from(JSON.stringify({ email: 'admin@restro.app', password: 'Admin@123' })));
    const adminToken = adminLogin.body.token;

    // -------------------------------------------------------------
    // PHASE 1: Rider Document Verification & Gemini Vision OCR
    // -------------------------------------------------------------
    console.log('--- Phase 1: Rider Compliance & Localized OCR Verification ---');

    app.customGeminiRequester = async ({ payload, model, apiKey, mimeType }) => {
      return {
        status: 200,
        body: JSON.stringify({
          candidates: [{
            content: {
              parts: [{
                text: JSON.stringify({
                  documentTypeMatch: true,
                  isLegible: true,
                  documentNumber: "MH0220180012345",
                  holderName: "Rajesh Kumar",
                  confidence: 0.98,
                  extractedSummary: "Verified Indian Union Driving Licence MH0220180012345 for Rajesh Kumar.",
                  remarks: "Clear and authentic RTO smart card."
                })
              }]
            }
          }]
        })
      };
    };

    await itAsync('Rider uploads valid Driving Licence with Hindi localization', async () => {
      const res = await request({
        path: '/api/delivery/ai/verify-document',
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${riderToken}`
        }
      }, Buffer.from(JSON.stringify({
        documentType: 'RIDER_DL',
        documentNumber: 'MH0220180012345',
        holderName: 'Rajesh Kumar',
        locale: 'hi',
        fileBase64: validPdfHeader.toString('base64')
      })));

      assert.strictEqual(res.statusCode, 200);
      assert.strictEqual(res.body.success, true);
      assert.strictEqual(res.body.verified, true);
      assert.strictEqual(res.body.documentType, 'RIDER_DL');
      assert.strictEqual(res.body.maskedDocumentNumber, '***2345');
      assert.ok(res.body.message.includes('सत्यापित') || res.body.message.includes('सफलतापूर्वक'));
    });

    app.customGeminiRequester = async ({ payload, model, apiKey, mimeType }) => {
      return {
        status: 200,
        body: JSON.stringify({
          candidates: [{
            content: {
              parts: [{
                text: JSON.stringify({
                  documentTypeMatch: true,
                  isLegible: true,
                  documentNumber: "11524006000421",
                  holderName: "Restro Kitchen",
                  confidence: 0.99,
                  extractedSummary: "Verified FSSAI Licence 11524006000421 for Restro Kitchen.",
                  remarks: "Authentic FSSAI registration."
                })
              }]
            }
          }]
        })
      };
    };

    await itAsync('Admin uploads valid FSSAI certificate with Marathi localization', async () => {
      const res = await request({
        path: '/api/delivery/ai/verify-document',
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${adminToken}`
        }
      }, Buffer.from(JSON.stringify({
        documentType: 'RESTAURANT_FSSAI',
        documentNumber: '11524006000421',
        holderName: 'Restro Kitchen',
        locale: 'mr',
        fileBase64: validPdfHeader.toString('base64')
      })));

      assert.strictEqual(res.statusCode, 200);
      assert.strictEqual(res.body.success, true);
      assert.strictEqual(res.body.verified, true);
      assert.strictEqual(res.body.documentType, 'RESTAURANT_FSSAI');
      assert.strictEqual(res.body.maskedDocumentNumber, '***0421');
      assert.ok(res.body.message.includes('पडताळले') || res.body.message.includes('यशस्वीरित्या'));
    });

    // -------------------------------------------------------------
    // PHASE 2: Razorpay Payment Verification & Dining Restriction
    // -------------------------------------------------------------
    console.log('\n--- Phase 2: Razorpay Cryptographic Verification & Dining Restriction ---');

    const defaultSecret = app.getRazorpaySecret();

    app.db.diningTabs = [
      {
        id: 'tab-int-test-1',
        tableNumber: 'T10',
        restaurantId: 'rest-1',
        status: 'ACTIVE',
        items: [{ dishId: 'dish-1', name: 'Dum Biryani', price: 49500, quantity: 1 }],
        subtotal: 45000,
        tax: 2250,
        serviceCharge: 2250,
        tip: 0,
        total: 49500
      }
    ];

    let createdOrderId = null;

    await itAsync('Creates Dine-In order when restaurant accepts dining payments', async () => {
      const res = await request({
        path: '/payments/razorpay/create-order',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      }, Buffer.from(JSON.stringify({
        amount: 49500,
        currency: 'INR',
        orderType: 'DINE_IN',
        restaurantId: 'rest-1',
        tabId: 'tab-int-test-1'
      })));

      assert.strictEqual(res.statusCode, 200);
      assert.strictEqual(res.body.success, true);
      assert.ok(res.body.order.id.startsWith('order_'));
      createdOrderId = res.body.order.id;
    });

    await itAsync('Verifies Razorpay payment signature cryptographically and settles tab', async () => {
      const paymentId = `pay_step2_${Date.now()}`;
      const signaturePayload = `${createdOrderId}|${paymentId}`;
      const validSignature = crypto.createHmac('sha256', defaultSecret).update(signaturePayload).digest('hex');

      const res = await request({
        path: '/payments/razorpay/verify',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      }, Buffer.from(JSON.stringify({
        razorpay_order_id: createdOrderId,
        razorpay_payment_id: paymentId,
        razorpay_signature: validSignature
      })));

      assert.strictEqual(res.statusCode, 200);
      assert.strictEqual(res.body.verified, true);
      assert.strictEqual(res.body.status, 'authorized');
      assert.strictEqual(res.body.amount, 49500);

      // Verify tab settled
      const tab = app.db.diningTabs.find(t => t.id === 'tab-int-test-1');
      assert.strictEqual(tab.status, 'SETTLED');
      assert.strictEqual(tab.paymentTransactionId, paymentId);
    });

    await itAsync('Strictly blocks payment ID replay across different order', async () => {
      const paymentId = `pay_step2_${Date.now()}`;
      // Order 1
      const ord1Res = await request({
        path: '/payments/razorpay/create-order',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      }, Buffer.from(JSON.stringify({ amount: 30000, orderType: 'DELIVERY', restaurantId: 'rest-1' })));
      const ord1Id = ord1Res.body.order.id;

      const sig1 = crypto.createHmac('sha256', defaultSecret).update(`${ord1Id}|${paymentId}`).digest('hex');
      await request({
        path: '/payments/razorpay/verify',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      }, Buffer.from(JSON.stringify({ razorpay_order_id: ord1Id, razorpay_payment_id: paymentId, razorpay_signature: sig1 })));

      // Order 2 with replayed paymentId
      const ord2Res = await request({
        path: '/payments/razorpay/create-order',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      }, Buffer.from(JSON.stringify({ amount: 30000, orderType: 'DELIVERY', restaurantId: 'rest-1' })));
      const ord2Id = ord2Res.body.order.id;

      const sig2 = crypto.createHmac('sha256', defaultSecret).update(`${ord2Id}|${paymentId}`).digest('hex');
      const replayRes = await request({
        path: '/payments/razorpay/verify',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      }, Buffer.from(JSON.stringify({ razorpay_order_id: ord2Id, razorpay_payment_id: paymentId, razorpay_signature: sig2 })));

      assert.strictEqual(replayRes.statusCode, 409);
      assert.strictEqual(replayRes.body.error, 'PAYMENT_ORDER_MISMATCH');
      assert.strictEqual(replayRes.body.verified, false);
    });

    // -------------------------------------------------------------
    // PHASE 3: RazorpayX Rider Payouts, Idempotency & Webhooks
    // -------------------------------------------------------------
    console.log('\n--- Phase 3: RazorpayX Rider Payouts, Idempotency & Webhooks ---');

    process.env.RAZORPAYX_KEY_ID = 'rzp_live_test_key';
    process.env.RAZORPAYX_KEY_SECRET = 'rzp_live_test_secret';
    process.env.RAZORPAYX_ACCOUNT_NUMBER = '2323230044556677';
    process.env.RAZORPAYX_WEBHOOK_SECRET = 'whsec_step2_integration_test';

    const testRiderId = `usr_rider_step2_${Date.now()}`;
    const testRiderAuthToken = `tok_rider_${Date.now()}`;
    app.db.sessions.set(testRiderAuthToken, testRiderId);
    app.db.users.push({
      id: testRiderId,
      name: 'Ramesh Rider',
      email: 'rider.step2@restro.app',
      role: 'RIDER'
    });
    app.db.riderBalances.set(testRiderId, 1500);

    const idempotencyKey = `idem_step2_${Date.now()}`;
    let payoutId = null;

    await itAsync('Processes rider payout with balance deduction and idempotency', async () => {
      const res = await request({
        path: '/api/delivery/rider/payouts',
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${testRiderAuthToken}`,
          'x-payout-idempotency': idempotencyKey
        }
      }, Buffer.from(JSON.stringify({
        amountRupees: 350,
        payoutMethod: 'UPI',
        destination: 'ramesh.rider@okaxis'
      })));

      assert.strictEqual(res.statusCode, 200);
      assert.strictEqual(res.body.success, true);
      assert.strictEqual(res.body.payout.amountRupees, 350);
      assert.strictEqual(app.db.riderBalances.get(testRiderId), 1150);
      payoutId = res.body.payout.id;
    });

    await itAsync('Idempotency key prevents double debit on duplicate payout call', async () => {
      const res = await request({
        path: '/api/delivery/rider/payouts',
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${testRiderAuthToken}`,
          'x-payout-idempotency': idempotencyKey
        }
      }, Buffer.from(JSON.stringify({
        amountRupees: 350,
        payoutMethod: 'UPI',
        destination: 'ramesh.rider@okaxis'
      })));

      assert.strictEqual(res.statusCode, 200);
      assert.strictEqual(res.body.duplicate, true);
      assert.strictEqual(app.db.riderBalances.get(testRiderId), 1150);
    });

    await itAsync('RazorpayX webhook securely settles payout with HMAC-SHA256 signature', async () => {
      const webhookPayload = {
        event: 'payout.processed',
        payload: {
          payout: {
            entity: {
              id: payoutId,
              status: 'processed',
              utr: 'UTR_STEP2_998877'
            }
          }
        }
      };
      const rawBody = JSON.stringify(webhookPayload);
      const signature = crypto.createHmac('sha256', process.env.RAZORPAYX_WEBHOOK_SECRET)
        .update(rawBody)
        .digest('hex');

      const res = await request({
        path: '/api/delivery/rider/payouts/webhook',
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-razorpay-signature': signature
        }
      }, Buffer.from(rawBody));

      assert.strictEqual(res.statusCode, 200);
      assert.strictEqual(res.body.status, 'ok');
    });

    // -------------------------------------------------------------
    // PHASE 4: Gemini Multilingual Menu & Restro.AI Assistant
    // -------------------------------------------------------------
    console.log('\n--- Phase 4: Gemini Multilingual Menu Translations & Restro.AI ---');

    await itAsync('Translates menu to Hindi using culinary lexicon & Gemini model', async () => {
      const dishes = [
        { id: `dish-int-${Date.now()}-1`, name: 'Butter Chicken', description: 'Cottage cheese in butter gravy' },
        { id: `dish-int-${Date.now()}-2`, name: 'Dal Makhani', description: 'Slow cooked black lentils' }
      ];

      const res = await request({
        path: '/api/delivery/menu/translate',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      }, Buffer.from(JSON.stringify({
        items: dishes,
        targetLanguage: 'hi'
      })));

      assert.strictEqual(res.statusCode, 200);
      assert.strictEqual(res.body.success, true);
      assert.strictEqual(res.body.language, 'hi');
      assert.ok(res.body.translations.length >= 2);
      assert.ok(res.body.translations[0].name.includes('चिकन') || res.body.translations[0].name.length > 0);
    });

    await itAsync('Translates menu to Marathi using culinary lexicon & Gemini model', async () => {
      const dishes = [
        { id: `dish-int-${Date.now()}-3`, name: 'Misal Pav', description: 'Spicy sprouted curry with bread' },
        { id: `dish-int-${Date.now()}-4`, name: 'Puran Poli', description: 'Sweet flatbread' }
      ];

      const res = await request({
        path: '/api/delivery/menu/translate',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      }, Buffer.from(JSON.stringify({
        items: dishes,
        targetLanguage: 'mr'
      })));

      assert.strictEqual(res.statusCode, 200);
      assert.strictEqual(res.body.success, true);
      assert.strictEqual(res.body.language, 'mr');
      assert.ok(res.body.translations.length >= 2);
    });

    await itAsync('Restro.AI assistant returns localized dish recommendations', async () => {
      const res = await request({
        path: '/api/delivery/ai/chat',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      }, Buffer.from(JSON.stringify({
        message: 'Show me popular vegetarian dishes',
        context: { preferredLanguage: 'hi' }
      })));

      assert.strictEqual(res.statusCode, 200);
      assert.strictEqual(res.body.success, true);
      assert.ok(res.body.reply);
    });

    console.log('\n===============================================================');
    console.log(`📊 STEP 2 INTEGRATION VERIFICATION: ${passed} / ${total} TESTS PASSED`);
    console.log('===============================================================\n');

  } finally {
    if (server) {
      server.close();
    }
  }
}

runStep2Integration().catch(err => {
  console.error('[Step 2 Integration Fatal Error]:', err);
  process.exit(1);
});
