const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');
const app = require('./server');

const TEST_PORT = 8092;
let server;

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

// Multipart helper
function buildMultipartPayload(fields, file) {
  const boundary = '----WebKitFormBoundary' + Math.random().toString(36).substring(2);
  const parts = [];

  for (const [key, value] of Object.entries(fields)) {
    parts.push(Buffer.from(
      `--${boundary}\r\nContent-Disposition: form-data; name="${key}"\r\n\r\n${value}\r\n`
    ));
  }

  if (file) {
    parts.push(Buffer.from(
      `--${boundary}\r\nContent-Disposition: form-data; name="${file.fieldname}"; filename="${file.filename}"\r\nContent-Type: ${file.contentType}\r\n\r\n`
    ));
    parts.push(file.content);
    parts.push(Buffer.from('\r\n'));
  }

  parts.push(Buffer.from(`--${boundary}--\r\n`));
  const fullBody = Buffer.concat(parts);

  return {
    boundary,
    body: fullBody,
    headers: {
      'Content-Type': `multipart/form-data; boundary=${boundary}`,
      'Content-Length': fullBody.length
    }
  };
}

async function runTests() {
  server = app.listen(TEST_PORT);
  console.log(`\n===============================================================`);
  console.log(`🛡️  RUNNING RESTRO COMPLIANCE MEDIA UPLOAD & ACCESS SUITE`);
  console.log(`===============================================================\n`);

  let passed = 0;
  let total = 0;

  function assert(name, condition, extra = '') {
    total++;
    if (condition) {
      passed++;
      console.log(`  ✅ [PASS] ${name}`);
    } else {
      console.error(`  ❌ [FAIL] ${name} ${extra}`);
    }
  }

  try {
    // 1. Obtain Tokens for different roles
    // Customer
    const custLogin = await request({
      path: '/api/delivery/auth/login',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, Buffer.from(JSON.stringify({ email: 'aarav@example.com', password: 'password123' })));
    const custToken = custLogin.body.token;

    // Rider
    const riderLogin = await request({
      path: '/api/delivery/auth/login',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, Buffer.from(JSON.stringify({ email: 'rider@restro.app', password: 'Rider@123' })));
    const riderToken = riderLogin.body.token;

    // Admin
    const adminLogin = await request({
      path: '/api/delivery/auth/login',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, Buffer.from(JSON.stringify({ email: 'admin@restro.app', password: 'Admin@123' })));
    const adminToken = adminLogin.body.token;

    // Master Admin
    const masterLogin = await request({
      path: '/api/delivery/auth/login',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, Buffer.from(JSON.stringify({ email: 'master@restro.app', password: 'Master@123' })));
    const masterToken = masterLogin.body.token;

    // Valid sample files
    const validPdfBuffer = Buffer.from('%PDF-1.4\n%âãÏÓ\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF');
    const validJpgBuffer = Buffer.from([0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01]);
    const validPngBuffer = Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D]);
    const invalidFakeFileBuffer = Buffer.from('NOT_A_VALID_FILE_JUST_PLAIN_TEXT_PAYLOAD');

    // TEST 1: Unauthenticated upload rejected
    const test1 = await request({
      path: '/api/delivery/media/upload',
      method: 'POST'
    });
    assert('Auth Security: Unauthenticated upload rejected with 401', test1.statusCode === 401);

    // TEST 2: Missing category rejected
    const { body: b2, headers: h2 } = buildMultipartPayload({}, {
      fieldname: 'file',
      filename: 'sample.pdf',
      contentType: 'application/pdf',
      content: validPdfBuffer
    });
    const test2 = await request({
      path: '/api/delivery/media/upload',
      method: 'POST',
      headers: { ...h2, 'Authorization': `Bearer ${riderToken}` }
    }, b2);
    assert('Validation: Missing category rejected with 400', test2.statusCode === 400 && test2.body.error === 'INVALID_CATEGORY');

    // TEST 3: Invalid category rejected
    const { body: b3, headers: h3 } = buildMultipartPayload({ category: 'RANDOM_AVATAR' }, {
      fieldname: 'file',
      filename: 'sample.pdf',
      contentType: 'application/pdf',
      content: validPdfBuffer
    });
    const test3 = await request({
      path: '/api/delivery/media/upload',
      method: 'POST',
      headers: { ...h3, 'Authorization': `Bearer ${riderToken}` }
    }, b3);
    assert('Validation: Invalid category rejected with 400', test3.statusCode === 400 && test3.body.error === 'INVALID_CATEGORY');

    // TEST 4: Missing file payload rejected
    const { body: b4, headers: h4 } = buildMultipartPayload({ category: 'RIDER_DL' }, null);
    const test4 = await request({
      path: '/api/delivery/media/upload',
      method: 'POST',
      headers: { ...h4, 'Authorization': `Bearer ${riderToken}` }
    }, b4);
    assert('Validation: Missing file rejected with 400', test4.statusCode === 400 && test4.body.error === 'MISSING_FILE');

    // TEST 5: Customer attempting to upload Rider DL blocked (403)
    const { body: b5, headers: h5 } = buildMultipartPayload({ category: 'RIDER_DL' }, {
      fieldname: 'file',
      filename: 'licence.pdf',
      contentType: 'application/pdf',
      content: validPdfBuffer
    });
    const test5 = await request({
      path: '/api/delivery/media/upload',
      method: 'POST',
      headers: { ...h5, 'Authorization': `Bearer ${custToken}` }
    }, b5);
    assert('Role Authorization: Customer uploading RIDER_DL blocked with 403', test5.statusCode === 403 && test5.body.error === 'FORBIDDEN');

    // TEST 6: Rider attempting to upload Restaurant FSSAI blocked (403)
    const { body: b6, headers: h6 } = buildMultipartPayload({ category: 'RESTAURANT_FSSAI' }, {
      fieldname: 'file',
      filename: 'fssai.pdf',
      contentType: 'application/pdf',
      content: validPdfBuffer
    });
    const test6 = await request({
      path: '/api/delivery/media/upload',
      method: 'POST',
      headers: { ...h6, 'Authorization': `Bearer ${riderToken}` }
    }, b6);
    assert('Role Authorization: Rider uploading RESTAURANT_FSSAI blocked with 403', test6.statusCode === 403 && test6.body.error === 'FORBIDDEN');

    // TEST 7: Cross-rider ownership spoofing blocked (403)
    const { body: b7, headers: h7 } = buildMultipartPayload({ category: 'RIDER_DL', ownerId: 'usr-different-rider' }, {
      fieldname: 'file',
      filename: 'licence.pdf',
      contentType: 'application/pdf',
      content: validPdfBuffer
    });
    const test7 = await request({
      path: '/api/delivery/media/upload',
      method: 'POST',
      headers: { ...h7, 'Authorization': `Bearer ${riderToken}` }
    }, b7);
    assert('Ownership Security: Cross-rider DL upload blocked with 403', test7.statusCode === 403 && test7.body.error === 'FORBIDDEN');

    // TEST 8: Cross-restaurant admin ownership spoofing blocked (403)
    const { body: b8, headers: h8 } = buildMultipartPayload({ category: 'RESTAURANT_FSSAI', ownerId: 'rest-foreign-99' }, {
      fieldname: 'file',
      filename: 'fssai.pdf',
      contentType: 'application/pdf',
      content: validPdfBuffer
    });
    const test8 = await request({
      path: '/api/delivery/media/upload',
      method: 'POST',
      headers: { ...h8, 'Authorization': `Bearer ${adminToken}` }
    }, b8);
    assert('Ownership Security: Cross-restaurant FSSAI upload blocked with 403', test8.statusCode === 403 && test8.body.error === 'FORBIDDEN');

    // TEST 9: Fake file with invalid magic bytes rejected (422)
    const { body: b9, headers: h9 } = buildMultipartPayload({ category: 'RIDER_DL' }, {
      fieldname: 'file',
      filename: 'dl.pdf',
      contentType: 'application/pdf',
      content: invalidFakeFileBuffer
    });
    const test9 = await request({
      path: '/api/delivery/media/upload',
      method: 'POST',
      headers: { ...h9, 'Authorization': `Bearer ${riderToken}` }
    }, b9);
    assert('Content Integrity: File with corrupted/fake magic bytes rejected with 422', test9.statusCode === 422 && test9.body.error === 'UNPROCESSABLE_ENTITY');

    // TEST 10: Oversized file (>10MB) rejected (413)
    const oversizedBuffer = Buffer.alloc(11 * 1024 * 1024, 0x00);
    const { body: b10, headers: h10 } = buildMultipartPayload({ category: 'RIDER_DL' }, {
      fieldname: 'file',
      filename: 'large.pdf',
      contentType: 'application/pdf',
      content: oversizedBuffer
    });
    const test10 = await request({
      path: '/api/delivery/media/upload',
      method: 'POST',
      headers: { ...h10, 'Authorization': `Bearer ${riderToken}` }
    }, b10);
    assert('Size Guard: File exceeding 10MB rejected with 413', test10.statusCode === 413 && test10.body.error === 'PAYLOAD_TOO_LARGE');

    // TEST 11: Valid Rider DL (PDF) uploaded successfully (201)
    const { body: b11, headers: h11 } = buildMultipartPayload({ category: 'RIDER_DL' }, {
      fieldname: 'file',
      filename: 'driving_licence.pdf',
      contentType: 'application/pdf',
      content: validPdfBuffer
    });
    const test11 = await request({
      path: '/api/delivery/media/upload',
      method: 'POST',
      headers: { ...h11, 'Authorization': `Bearer ${riderToken}` }
    }, b11);
    assert('Upload Flow: Valid Rider Driving Licence PDF upload returns 201', test11.statusCode === 201 && test11.body.success === true);
    assert('Upload Flow: Storage key matches rider convention', test11.body.storageKey.startsWith('documents/riders/usr-rider/dl/dl_'));
    assert('Upload Flow: Document marked as private', test11.body.isPrivate === true);
    const riderDocId = test11.body.docId;

    // TEST 12: Valid Restaurant FSSAI (JPEG) uploaded successfully (201)
    const { body: b12, headers: h12 } = buildMultipartPayload({ category: 'RESTAURANT_FSSAI' }, {
      fieldname: 'file',
      filename: 'fssai_cert.jpg',
      contentType: 'image/jpeg',
      content: validJpgBuffer
    });
    const test12 = await request({
      path: '/api/delivery/media/upload',
      method: 'POST',
      headers: { ...h12, 'Authorization': `Bearer ${adminToken}` }
    }, b12);
    assert('Upload Flow: Valid Restaurant FSSAI JPEG upload returns 201', test12.statusCode === 201 && test12.body.success === true);
    assert('Upload Flow: Storage key matches restaurant convention', test12.body.storageKey.startsWith('documents/restaurants/rest-1/fssai/fssai_'));
    const fssaiDocId = test12.body.docId;

    // TEST 13: Valid Restaurant GST (PNG) uploaded successfully (201)
    const { body: b13, headers: h13 } = buildMultipartPayload({ category: 'RESTAURANT_GST' }, {
      fieldname: 'file',
      filename: 'gst_cert.png',
      contentType: 'image/png',
      content: validPngBuffer
    });
    const test13 = await request({
      path: '/api/delivery/media/upload',
      method: 'POST',
      headers: { ...h13, 'Authorization': `Bearer ${adminToken}` }
    }, b13);
    assert('Upload Flow: Valid Restaurant GST PNG upload returns 201', test13.statusCode === 201 && test13.body.success === true);
    assert('Upload Flow: Storage key matches restaurant GST convention', test13.body.storageKey.startsWith('documents/restaurants/rest-1/gst/gst_'));

    // TEST 14: Private download by owner rider succeeds (200)
    const test14 = await request({
      path: `/api/delivery/media/download/${riderDocId}`,
      method: 'GET',
      headers: { 'Authorization': `Bearer ${riderToken}` }
    });
    assert('Download Security: Owner rider can download their own DL (200)', test14.statusCode === 200);
    assert('Download Privacy: Cache-Control header is private, no-store', test14.headers['cache-control'] === 'private, no-store');
    assert('Download Content: Returned buffer matches uploaded content', test14.rawBuffer.equals(validPdfBuffer));

    // TEST 15: Cross-user download blocked (403)
    const test15 = await request({
      path: `/api/delivery/media/download/${riderDocId}`,
      method: 'GET',
      headers: { 'Authorization': `Bearer ${custToken}` }
    });
    assert('Download Privacy: Unauthorized customer accessing rider DL blocked with 403', test15.statusCode === 403 && test15.body.error === 'FORBIDDEN');

    // TEST 16: Non-existent document download returns 404
    const test16 = await request({
      path: '/api/delivery/media/download/doc-non-existent-999',
      method: 'GET',
      headers: { 'Authorization': `Bearer ${riderToken}` }
    });
    assert('Download Flow: Non-existent docId returns 404', test16.statusCode === 404 && test16.body.error === 'NOT_FOUND');

    // TEST 17: Master Admin can access any document
    const test17 = await request({
      path: `/api/delivery/media/download/${riderDocId}`,
      method: 'GET',
      headers: { 'Authorization': `Bearer ${masterToken}` }
    });
    assert('Admin Privilege: Master Admin can download rider compliance document (200)', test17.statusCode === 200);

  } catch (err) {
    console.error('Fatal test error:', err);
  } finally {
    server.close();
    console.log(`\n===============================================================`);
    console.log(`📊 TEST RESULTS: ${passed} / ${total} TESTS PASSED (${passed === total ? '100% Verified' : 'FAILURES PRESENT'})`);
    console.log(`===============================================================\n`);
    process.exit(passed === total ? 0 : 1);
  }
}

runTests();
