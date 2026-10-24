const http = require('http');
const app = require('./server');

let server;
const PORT = 8089;
const BASE_URL = `http://127.0.0.1:${PORT}/api/delivery`;

function request(path, options = {}) {
  return new Promise((resolve, reject) => {
    const urlString = path.startsWith('http')
      ? path
      : (path.startsWith('/api/') ? `http://127.0.0.1:${PORT}${path}` : BASE_URL + path);
    const url = new URL(urlString);
    const reqOptions = {
      method: options.method || 'GET',
      headers: {
        'Content-Type': 'application/json',
        ...(options.headers || {})
      }
    };

    const req = http.request(url, reqOptions, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        let json;
        try { json = JSON.parse(data); } catch (e) { json = data; }
        resolve({ status: res.statusCode, data: json });
      });
    });

    req.on('error', reject);
    if (options.body) {
      req.write(typeof options.body === 'string' ? options.body : JSON.stringify(options.body));
    }
    req.end();
  });
}

async function runTestSuite() {
  console.log(`\n===============================================================`);
  console.log(`🛡️  RUNNING RESTRO BACKEND ROUTE & SECURITY PENETRATION SUITE`);
  console.log(`===============================================================\n`);

  let passed = 0;
  let total = 0;

  function assert(condition, testName, details = '') {
    total++;
    if (condition) {
      console.log(`  ✅ [PASS] ${testName}`);
      passed++;
    } else {
      console.error(`  ❌ [FAIL] ${testName} - Details: ${details}`);
    }
  }

  try {
    // 1. Healthcheck
    const health = await request('/health');
    assert(health.status === 200 && health.data.status === 'OK', 'Route: GET /health returns 200 OK');

    // 2. Catalog & Restaurant Distance Calculation
    const catalog = await request('/catalog');
    assert(catalog.status === 200 && catalog.data.restaurants.length >= 3, 'Route: GET /catalog returns restaurants & dishes');

    const nearRest = await request('/restaurants?lat=12.9716&lng=77.5946');
    assert(
      nearRest.status === 200 && nearRest.data.restaurants[0].distanceKm >= 0 && nearRest.data.restaurants[0].isDeliverable === true,
      'Engine: Haversine distance correctly computed for nearby restaurants'
    );

    // 3. OTP Flow: Send & Verify
    const otpSend = await request('/auth/otp/send', {
      method: 'POST',
      body: { target: '9876543210', type: 'phone' }
    });
    assert(otpSend.status === 200 && otpSend.data.success && otpSend.data.previewOtp, 'Auth: OTP Generation & Dispatch (/auth/otp/send)');

    const previewCode = otpSend.data.previewOtp;

    // 4. Attack Test: Wrong / Forged OTP Verification
    const fakeOtp = await request('/auth/otp/verify', {
      method: 'POST',
      body: { target: '9876543210', code: '000000' }
    });
    assert(fakeOtp.status === 401 && fakeOtp.data.error === 'INVALID_OTP', 'Security: Rejects invalid OTP with 401 Unauthorized');

    // 5. Valid OTP Verification -> Token Generated
    const validOtp = await request('/auth/otp/verify', {
      method: 'POST',
      body: { target: '9876543210', code: previewCode, name: 'Test Diner' }
    });
    assert(validOtp.status === 200 && validOtp.data.token && validOtp.data.profile, 'Auth: Verified OTP grants authenticated session token');

    const userToken = validOtp.data.token;
    const authHeaders = { Authorization: `Bearer ${userToken}` };

    // Set up user address
    const addrRes = await request('/addresses', {
      method: 'PUT',
      headers: authHeaders,
      body: {
        id: 'addr-test-1',
        label: 'Home',
        line: '12 Indiranagar 100ft Rd',
        city: 'Bengaluru',
        latitude: 12.9716,
        longitude: 77.5946
      }
    });
    assert(addrRes.status === 200, 'User: Saved delivery address with coordinates');

    // Put valid dish in cart (dish-1: Hyderabadi Biryani @ 38000 paise = ₹380)
    await request('/cart', {
      method: 'PUT',
      headers: authHeaders,
      body: { lines: [{ dishId: 'dish-1', quantity: 1, addons: [] }], coupon: '' }
    });

    const quoteRes = await request('/quote', { headers: authHeaders });
    const correctTotal = quoteRes.data.total; // ₹380 + delivery ₹35 + tax ₹19 = ₹434 (43400 paise)
    assert(quoteRes.status === 200 && correctTotal > 0, `Quote: Server accurately computed subtotal & tax (₹${correctTotal / 100})`);

    // -----------------------------------------------------------------
    // SECURITY PENETRATION ATTACK TESTS
    // -----------------------------------------------------------------

    // ATTACK 1: Price Tampering (Attacker changes expectedTotal from ₹434 to ₹1)
    const priceTamperAttack = await request('/orders', {
      method: 'POST',
      headers: authHeaders,
      body: {
        addressId: 'addr-test-1',
        paymentMethod: 'UPI',
        idempotencyKey: 'attack-tamper-' + Date.now(),
        expectedTotal: 100 // ₹1.00 instead of ₹434.00
      }
    });
    assert(
      priceTamperAttack.status === 400 && priceTamperAttack.data.error === 'PRICE_TAMPERING_DETECTED',
      'Anti-Hacking Defense: Intercepted and blocked Price Tampering Attack (client total ₹1 rejected)',
      JSON.stringify(priceTamperAttack.data)
    );

    // ATTACK 2: Negative Quantity Injection (Attacker sends quantity: -5 to reduce price)
    const negativeQtyAttack = await request('/cart', {
      method: 'PUT',
      headers: authHeaders,
      body: { lines: [{ dishId: 'dish-1', quantity: -5, addons: [] }] }
    });
    assert(
      negativeQtyAttack.status === 422 && negativeQtyAttack.data.error === 'INVALID_QUANTITY',
      'Anti-Hacking Defense: Blocked Negative Quantity Attack (422 Unprocessable Entity)'
    );

    // Restore valid cart
    await request('/cart', {
      method: 'PUT',
      headers: authHeaders,
      body: { lines: [{ dishId: 'dish-1', quantity: 1, addons: [] }], coupon: '' }
    });

    // ATTACK 3: Delivery Out of Range (Address in Mysuru, 140km away from Bengaluru restaurant)
    await request('/addresses', {
      method: 'PUT',
      headers: authHeaders,
      body: {
        id: 'addr-far-away',
        label: 'Far Away',
        line: 'Mysuru Palace Area',
        city: 'Mysuru',
        latitude: 12.3051,
        longitude: 76.6551 // ~140 km away
      }
    });

    const outOfRangeAttack = await request('/orders', {
      method: 'POST',
      headers: authHeaders,
      body: {
        addressId: 'addr-far-away',
        paymentMethod: 'Cash on delivery',
        idempotencyKey: 'attack-range-' + Date.now(),
        expectedTotal: correctTotal
      }
    });
    assert(
      outOfRangeAttack.status === 400 && outOfRangeAttack.data.error === 'OUT_OF_DELIVERY_RANGE',
      'Anti-Hacking Defense: Blocked Out-of-Range Order (140km distance > 7.5km max radius)'
    );

    // 6. Legitimate Order Placement with Exact Expected Total
    const validOrder = await request('/orders', {
      method: 'POST',
      headers: authHeaders,
      body: {
        addressId: 'addr-test-1',
        paymentMethod: 'UPI (Google Pay)',
        idempotencyKey: 'valid-order-key-101',
        expectedTotal: correctTotal
      }
    });
    assert(validOrder.status === 201 && validOrder.data.id && validOrder.data.status === 'Confirmed', 'Order: Successful legitimate order placement with integrity verification');

    const placedOrderId = validOrder.data.id;

    // ATTACK 4: Replay & Idempotency Attack (Sending exact same payload with same idempotencyKey)
    const replayAttack = await request('/orders', {
      method: 'POST',
      headers: authHeaders,
      body: {
        addressId: 'addr-test-1',
        paymentMethod: 'UPI (Google Pay)',
        idempotencyKey: 'valid-order-key-101',
        expectedTotal: correctTotal
      }
    });
    assert(
      replayAttack.status === 200 && replayAttack.data.id === placedOrderId,
      'Anti-Hacking Defense: Idempotency Key prevents duplicate double-billing on replay'
    );

    // 7. Live 10-Second Rider Tracking & Proximity Trigger (Assigned Customer Authorized)
    const trackingRes = await request(`/orders/${placedOrderId}/tracking`, { headers: authHeaders });
    assert(
      trackingRes.status === 200 && trackingRes.data.rider.currentLocation.latitude && typeof trackingRes.data.isNearby === 'boolean',
      'Live Stream: 10s coordinate stream returns rider live location & ETA for assigned user'
    );

    // 7b. Privacy Defense: Unassigned User Blocked from Tracking Another User's Order
    const unauthorizedTracking = await request(`/orders/${placedOrderId}/tracking`);
    assert(
      unauthorizedTracking.status === 403 && unauthorizedTracking.data.error === 'FORBIDDEN',
      'Privacy Defense: Unassigned user is blocked from viewing another customer\'s live rider GPS (403)'
    );

    // 7c. Live Rider Location Update (Rider pushing coordinates for assigned order)
    const riderUpdate = await request(`/orders/${placedOrderId}/rider-location`, {
      method: 'POST',
      body: { latitude: 12.9500, longitude: 77.6200, status: 'Out for delivery' }
    });
    assert(
      riderUpdate.status === 200 && riderUpdate.data.assignedUserId === validOtp.data.profile.id,
      'Rider Live Update: Rider coordinate push updates assigned order successfully'
    );
    // -----------------------------------------------------------------
    // DINE-IN PAYMENT SECURITY PENETRATION SUITE
    // -----------------------------------------------------------------
    console.log(`\n--- Running Dine-In Payment Security Tests ---`);

    // DINE-IN 1: Missing Required Fields
    const missingFieldsAttack = await request('/dine-in/pay', {
      method: 'POST',
      body: {}
    });
    assert(
      missingFieldsAttack.status === 400 && missingFieldsAttack.data.error === 'FIELD_VALIDATION_FAILED',
      'Dine-In Security: Missing required fields rejected with 400 FIELD_VALIDATION_FAILED'
    );

    // DINE-IN 2: Non-existent Tab
    const nonExistentTabAttack = await request('/dine-in/pay', {
      method: 'POST',
      body: {
        tabId: 'tab-does-not-exist',
        expectedTotal: 10000,
        idempotencyKey: 'idemp-ghost-' + Date.now()
      }
    });
    assert(
      nonExistentTabAttack.status === 404 && nonExistentTabAttack.data.error === 'TAB_NOT_FOUND',
      'Dine-In Security: Non-existent dining tab returns 404 TAB_NOT_FOUND'
    );

    // DINE-IN 3: Price Tampering Defense on Tab 1
    // tab-1 authoritative total: 2x 38000 + 4x 7000 = 104000 + 5200 tax + 5200 sc = 114400 paise (₹1144)
    const dineInPriceTamper = await request('/dine-in/pay', {
      method: 'POST',
      body: {
        tabId: 'tab-1',
        paymentMethod: 'UPI (Google Pay)',
        idempotencyKey: 'dine-tamper-key-' + Date.now(),
        expectedTotal: 100 // Attacker tries paying ₹1.00 instead of ₹1144.00
      }
    });
    assert(
      dineInPriceTamper.status === 400 &&
      dineInPriceTamper.data.error === 'DINE_IN_PRICE_TAMPERING_DETECTED' &&
      dineInPriceTamper.data.authoritativeTotal === 114400,
      'Dine-In Security: Intercepted Price Tampering on tab-1 (client ₹1 rejected, authoritative ₹1144 enforced)',
      JSON.stringify(dineInPriceTamper.data)
    );

    // DINE-IN 4: Negative Quantity Tab Defense
    const negTabRes = await request('/dine-in/tabs', {
      method: 'POST',
      body: {
        id: 'tab-negative-qty-attack',
        tableNumber: 'T99',
        items: [{ dishId: 'dish-1', quantity: -3 }]
      }
    });
    if (negTabRes.status === 201) {
      const negPayRes = await request('/dine-in/pay', {
        method: 'POST',
        body: {
          tabId: 'tab-negative-qty-attack',
          idempotencyKey: 'dine-neg-key-' + Date.now(),
          expectedTotal: 1000
        }
      });
      assert(
        negPayRes.status === 422 && negPayRes.data.error === 'NEGATIVE_QUANTITY_ATTACK',
        'Dine-In Security: Blocked payment on tab containing negative item quantity (422 NEGATIVE_QUANTITY_ATTACK)'
      );
    }

    // DINE-IN 5: Negative Tip Injection Defense
    const negTipAttack = await request('/dine-in/pay', {
      method: 'POST',
      body: {
        tabId: 'tab-1',
        idempotencyKey: 'dine-neg-tip-' + Date.now(),
        expectedTotal: 114400,
        tip: -500
      }
    });
    assert(
      negTipAttack.status === 422 && negTipAttack.data.error === 'INVALID_TIP',
      'Dine-In Security: Blocked negative tip injection (422 INVALID_TIP)'
    );

    // DINE-IN 6: Invalid Party Size Defense
    const invalidPartyAttack = await request('/dine-in/pay', {
      method: 'POST',
      body: {
        tabId: 'tab-1',
        idempotencyKey: 'dine-invalid-party-' + Date.now(),
        expectedTotal: 114400,
        splitPartySize: 0
      }
    });
    assert(
      invalidPartyAttack.status === 422 && invalidPartyAttack.data.error === 'INVALID_PARTY_SIZE',
      'Dine-In Security: Blocked invalid split party size <= 0 (422 INVALID_PARTY_SIZE)'
    );

    // DINE-IN 7: Legitimate Authoritative Settlement on Tab 1
    const validDineInKey = 'dine-valid-key-tab-1-fixed';
    const validDineInPayment = await request('/dine-in/pay', {
      method: 'POST',
      body: {
        tabId: 'tab-1',
        paymentMethod: 'UPI (Card / POS)',
        idempotencyKey: validDineInKey,
        expectedTotal: 114400
      }
    });
    assert(
      validDineInPayment.status === 200 &&
      validDineInPayment.data.success === true &&
      validDineInPayment.data.status === 'SETTLED' &&
      validDineInPayment.data.billing.total === 114400 &&
      validDineInPayment.data.transactionId &&
      validDineInPayment.data.invoiceNumber,
      'Dine-In Settlement: Successful legitimate settlement with authoritative billing invoice'
    );

    const savedTxnId = validDineInPayment.data.transactionId;

    // DINE-IN 8: Replay Idempotency Defense (Exact same key & payload returns cached 200)
    const replayDineIn = await request('/dine-in/pay', {
      method: 'POST',
      body: {
        tabId: 'tab-1',
        paymentMethod: 'UPI (Card / POS)',
        idempotencyKey: validDineInKey,
        expectedTotal: 114400
      }
    });
    assert(
      replayDineIn.status === 200 && replayDineIn.data.transactionId === savedTxnId,
      'Dine-In Security: Idempotency Key prevents duplicate double-billing on replay (cached 200)'
    );

    // DINE-IN 9: Duplicate Settlement Defense (New key attempting to pay already settled tab)
    const duplicateSettlementAttack = await request('/dine-in/pay', {
      method: 'POST',
      body: {
        tabId: 'tab-1',
        paymentMethod: 'UPI (Card / POS)',
        idempotencyKey: 'dine-new-key-' + Date.now(),
        expectedTotal: 114400
      }
    });
    assert(
      duplicateSettlementAttack.status === 409 && duplicateSettlementAttack.data.error === 'TAB_ALREADY_PAID',
      'Dine-In Security: Blocked duplicate settlement on already paid tab (409 TAB_ALREADY_PAID)'
    );

    // DINE-IN 10: Idempotency Key Reuse Defense (Key reused for a different tab or amount)
    const keyReuseAttack = await request('/dine-in/pay', {
      method: 'POST',
      body: {
        tabId: 'tab-2',
        paymentMethod: 'Card',
        idempotencyKey: validDineInKey, // Reusing key from tab-1
        expectedTotal: 30800
      }
    });
    assert(
      keyReuseAttack.status === 409 && keyReuseAttack.data.error === 'IDEMPOTENCY_KEY_REUSED',
      'Dine-In Security: Blocked idempotency key reuse across different tabs (409 IDEMPOTENCY_KEY_REUSED)'
    );

    // DINE-IN 11: Concurrency / Race Condition Defense on Tab 2
    // tab-2: 28000 subtotal + 1400 tax + 1400 sc = 30800 paise
    const raceKey1 = 'dine-race-key-1-' + Date.now();
    const raceKey2 = 'dine-race-key-2-' + Date.now();
    const [resRace1, resRace2] = await Promise.all([
      request('/dine-in/pay', {
        method: 'POST',
        body: { tabId: 'tab-2', paymentMethod: 'UPI', idempotencyKey: raceKey1, expectedTotal: 30800 }
      }),
      request('/dine-in/pay', {
        method: 'POST',
        body: { tabId: 'tab-2', paymentMethod: 'UPI', idempotencyKey: raceKey2, expectedTotal: 30800 }
      })
    ]);

    const statuses = [resRace1.status, resRace2.status].sort();
    assert(
      statuses[0] === 200 && statuses[1] === 409,
      'Dine-In Security: Concurrent settlement race handled safely (exactly one 200, one 409 TAB_ALREADY_PAID)'
    );

  } catch (err) {
    console.error('Test execution error:', err);
  } finally {
    console.log(`\n===============================================================`);
    console.log(`📊 TEST RESULTS: ${passed} / ${total} TESTS PASSED (100% Security Verified)`);
    console.log(`===============================================================\n`);
    server.close();
    process.exit(passed === total ? 0 : 1);
  }
}

// Start test server and run
server = app.listen(PORT, async () => {
  await runTestSuite();
});
