const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');
const app = require('./server');

const TEST_PORT = 8094;
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
  console.log(`🛡️  RUNNING RESTRO GEMINI VISION OCR VERIFICATION SUITE`);
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

  // Sample valid mock headers
  const validPdfHeader = Buffer.from('%PDF-1.4\n%âãÏÓ\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF');
  const validJpegHeader = Buffer.from([0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0xFF, 0xD9]);
  const validPngHeader = Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52]);
  const corruptHeader = Buffer.from([0x4D, 0x5A, 0x90, 0x00, 0x03, 0x00, 0x00, 0x00]); // MZ executable

  try {
    // -------------------------------------------------------------------------
    // SETUP: Authenticate Test Personas (Rider, Admin, Customer, Master Admin)
    // -------------------------------------------------------------------------
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

    const customerLogin = await request({
      path: '/api/delivery/auth/login',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, Buffer.from(JSON.stringify({ email: 'aarav@example.com', password: 'password123' })));
    const customerToken = customerLogin.body.token;

    const masterLogin = await request({
      path: '/api/delivery/auth/login',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, Buffer.from(JSON.stringify({ email: 'master@restro.app', password: 'Master@123' })));
    const masterToken = masterLogin.body.token;

    // Register a second rider for ownership isolation tests
    const rider2Reg = await request({
      path: '/api/delivery/auth/register',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, Buffer.from(JSON.stringify({
      email: 'rider2@restro.app',
      password: 'Password@123',
      name: 'Second Rider',
      role: 'RIDER',
      phone: '+91 99999 88881'
    })));
    const rider2Token = rider2Reg.body.token;
    const rider2Id = rider2Reg.body.profile.id;

    // -------------------------------------------------------------------------
    // TEST 1: Unit Validation of Document Numbers, Types & Localization
    // -------------------------------------------------------------------------
    assert('Format Validator: 14-digit FSSAI number accepted',
      app.validateFssaiNumber('11524006000421') === '11524006000421');
    assert('Format Validator: FSSAI with spaces cleaned and accepted',
      app.validateFssaiNumber('1152 4006 0004 21') === '11524006000421');
    assert('Format Validator: Short 10-digit FSSAI rejected',
      app.validateFssaiNumber('1152400600') === null);
    assert('Format Validator: Non-numeric FSSAI rejected',
      app.validateFssaiNumber('1152400600042A') === null);

    assert('Format Validator: Valid 15-char GSTIN accepted',
      app.validateGstin('27AABCU9603R1ZM') === '27AABCU9603R1ZM');
    assert('Format Validator: GSTIN with dashes cleaned and accepted',
      app.validateGstin('27-AABCU-9603-R-1-Z-M') === '27AABCU9603R1ZM');
    assert('Format Validator: Invalid 12-char GSTIN rejected',
      app.validateGstin('27AABCU9603R') === null);

    assert('Format Validator: Valid Indian DL accepted',
      app.validateDrivingLicence('MH0220180012345') === 'MH0220180012345');
    assert('Format Validator: DL with dashes cleaned and accepted',
      app.validateDrivingLicence('MH-02-2018-0012345') === 'MH0220180012345');
    assert('Format Validator: Short invalid DL rejected',
      app.validateDrivingLicence('MH1234') === null);

    assert('Type Normalizer: Maps RIDER_DL aliases',
      app.normalizeComplianceDocType('driving_license') === 'RIDER_DL');
    assert('Type Normalizer: Maps FSSAI aliases',
      app.normalizeComplianceDocType('RESTAURANT_FSSAI') === 'RESTAURANT_FSSAI');
    assert('Type Normalizer: Maps GST aliases',
      app.normalizeComplianceDocType('gstin') === 'RESTAURANT_GST');
    assert('Type Normalizer: Rejects unknown types',
      app.normalizeComplianceDocType('PASSPORT') === null);

    assert('Masking Helper: Masks DL correctly',
      app.maskDocumentNumber('MH0220180012345') === '***2345');
    assert('Masking Helper: Masks FSSAI correctly',
      app.maskDocumentNumber('11524006000421') === '***0421');
    assert('Masking Helper: Masks GSTIN correctly',
      app.maskDocumentNumber('27AABCU9603R1ZM') === '***R1ZM');

    assert('Localization Helper: Success message in en',
      app.getLocalizedDocumentMessage('VERIFICATION_SUCCESS', 'en') === 'Document verified successfully.');
    assert('Localization Helper: Success message in hi',
      app.getLocalizedDocumentMessage('VERIFICATION_SUCCESS', 'hi') === 'दस्तावेज़ सफलतापूर्वक सत्यापित हो गया।');
    assert('Localization Helper: Success message in mr',
      app.getLocalizedDocumentMessage('VERIFICATION_SUCCESS', 'mr') === 'दस्तऐवज यशस्वीरित्या पडताळले गेले.');
    assert('Localization Helper: Fallback for unsupported locale returns en',
      app.getLocalizedDocumentMessage('VERIFICATION_SUCCESS', 'fr') === 'Document verified successfully.');

    // -------------------------------------------------------------------------
    // TEST 2: Authentication & Authorization Security Checks (401 & 403)
    // -------------------------------------------------------------------------
    // 2.1 Missing Authentication rejected with 401
    const resUnauth = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      documentNumber: 'MH0220180012345',
      fileBase64: validPdfHeader.toString('base64')
    })));
    assert('Auth Security: Missing authentication is rejected with 401',
      resUnauth.statusCode === 401 && resUnauth.body.error === 'UNAUTHORIZED');

    // 2.2 Invalid/expired session token rejected with 401
    const resBadToken = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer invalid_tampered_token_999'
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      documentNumber: 'MH0220180012345',
      fileBase64: validPdfHeader.toString('base64')
    })));
    assert('Auth Security: Invalid session token is rejected with 401',
      resBadToken.statusCode === 401 && resBadToken.body.error === 'UNAUTHORIZED');

    // 2.3 Role authorization: Customer verifying DL rejected with 403
    const resCustomerDl = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${customerToken}`
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      documentNumber: 'MH0220180012345',
      fileBase64: validPdfHeader.toString('base64')
    })));
    assert('Role Auth: Customer role cannot verify RIDER_DL (403)',
      resCustomerDl.statusCode === 403 && resCustomerDl.body.error === 'FORBIDDEN');

    // 2.4 Role authorization: Customer verifying FSSAI rejected with 403
    const resCustomerFssai = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${customerToken}`
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RESTAURANT_FSSAI',
      documentNumber: '11524006000421',
      fileBase64: validPdfHeader.toString('base64')
    })));
    assert('Role Auth: Customer role cannot verify RESTAURANT_FSSAI (403)',
      resCustomerFssai.statusCode === 403 && resCustomerFssai.body.error === 'FORBIDDEN');

    // 2.5 Role authorization: Rider verifying FSSAI rejected with 403
    const resRiderFssai = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${riderToken}`
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RESTAURANT_FSSAI',
      documentNumber: '11524006000421',
      fileBase64: validPdfHeader.toString('base64')
    })));
    assert('Role Auth: Rider role cannot verify RESTAURANT_FSSAI (403)',
      resRiderFssai.statusCode === 403 && resRiderFssai.body.error === 'FORBIDDEN');

    // 2.6 Ownership isolation: Rider verifying document for another rider account rejected with 403
    let geminiCalledForForbidden = false;
    app.customGeminiRequester = async () => {
      geminiCalledForForbidden = true;
      return { status: 200, body: "{}" };
    };

    const resCrossRider = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${riderToken}`
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      documentNumber: 'MH0220180012345',
      ownerId: rider2Id, // Pretending to verify rider2's document
      fileBase64: validPdfHeader.toString('base64')
    })));
    assert('Ownership Security: Cross-rider document verification blocked with 403',
      resCrossRider.statusCode === 403 && resCrossRider.body.error === 'FORBIDDEN');
    assert('Resource Protection: Unauthorized request did not trigger Gemini processing',
      geminiCalledForForbidden === false);

    // 2.7 Ownership isolation: Restaurant Admin verifying another restaurant's certificates rejected with 403
    const resCrossRestaurant = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${adminToken}`
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RESTAURANT_FSSAI',
      documentNumber: '11524006000421',
      restaurantId: 'rest-2', // admin belongs to rest-1
      fileBase64: validPdfHeader.toString('base64')
    })));
    assert('Ownership Security: Cross-restaurant FSSAI verification blocked with 403',
      resCrossRestaurant.statusCode === 403 && resCrossRestaurant.body.error === 'FORBIDDEN');

    // -------------------------------------------------------------------------
    // TEST 3: Safe Input Handling - Missing, Unsupported & Oversized Files
    // -------------------------------------------------------------------------
    // 3.1 Missing document number returns 400
    const resMissingDocNum = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${riderToken}`
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      fileBase64: validPdfHeader.toString('base64')
    })));
    assert('Input Guard: Missing document number returns 400',
      resMissingDocNum.statusCode === 400 && resMissingDocNum.body.error === 'MISSING_DOCUMENT_NUMBER');

    // 3.2 Missing document bytes returns 400
    const resMissingBytes = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${riderToken}`
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      documentNumber: 'MH0220180012345'
    })));
    assert('Input Guard: Missing document bytes returns 400',
      resMissingBytes.statusCode === 400 && resMissingBytes.body.error === 'DOCUMENT_BYTES_REQUIRED');

    // 3.3 Invalid document type returns 400
    const resInvalidType = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${riderToken}`
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RANDOM_TYPE',
      documentNumber: 'MH0220180012345',
      fileBase64: validPdfHeader.toString('base64')
    })));
    assert('Input Guard: Invalid document type returns 400',
      resInvalidType.statusCode === 400 && resInvalidType.body.error === 'INVALID_DOCUMENT_TYPE');

    // 3.4 Unsupported MIME / Corrupt Magic Bytes returns 422
    const resCorrupt = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${riderToken}`
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      documentNumber: 'MH0220180012345',
      fileBase64: corruptHeader.toString('base64')
    })));
    assert('Magic Byte Guard: Corrupt or executable file content returns 422',
      resCorrupt.statusCode === 422 && resCorrupt.body.error === 'UNSUPPORTED_DOCUMENT_FORMAT');

    // 3.5 Oversized Payload (>10MB) returns 413
    const oversizedBuffer = Buffer.alloc(10 * 1024 * 1024 + 1024, 0);
    validPdfHeader.copy(oversizedBuffer, 0, 0, validPdfHeader.length);
    const resOversized = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${riderToken}`
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      documentNumber: 'MH0220180012345',
      fileBase64: oversizedBuffer.toString('base64')
    })));
    assert('Size Guard: Document exceeding 10MB returns 413',
      resOversized.statusCode === 413 && resOversized.body.error === 'PAYLOAD_TOO_LARGE');

    // -------------------------------------------------------------------------
    // TEST 4: Successful Rider Driving Licence OCR Verification (Strict Match)
    // -------------------------------------------------------------------------
    let capturedMimeType = null;
    let capturedBase64Data = null;

    app.customGeminiRequester = async ({ payload, model, apiKey, mimeType }) => {
      capturedMimeType = mimeType;
      const parsed = JSON.parse(payload);
      capturedBase64Data = parsed.contents[0].parts[1].inlineData.data;

      return {
        status: 200,
        body: JSON.stringify({
          candidates: [{
            content: {
              parts: [{
                text: JSON.stringify({
                  documentTypeMatch: true,
                  isLegible: true,
                  documentNumber: "MH-02-2018-0012345",
                  holderName: "Ramesh Rider",
                  confidence: 0.98,
                  extractedSummary: "Verified Indian Union Driving Licence MH0220180012345 for Ramesh Rider (Transport/LMV Valid).",
                  remarks: "Clear and authentic RTO smart card."
                })
              }]
            }
          }]
        })
      };
    };

    const resDlSuccess = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${riderToken}`
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      documentNumber: 'MH 02 2018 0012345', // Tested with whitespace formatting
      fileBase64: validPdfHeader.toString('base64')
    })));

    assert('DL OCR Success: Endpoint returns 200 and verified=true',
      resDlSuccess.statusCode === 200 && resDlSuccess.body.verified === true);
    assert('DL OCR Success: Document number correctly masked for privacy',
      resDlSuccess.body.documentNumber === '***2345');
    assert('DL OCR Success: Masked document number property populated',
      resDlSuccess.body.maskedDocumentNumber === '***2345');
    assert('DL OCR Success: Holder name extracted accurately',
      resDlSuccess.body.holderName === 'Ramesh Rider');
    assert('DL OCR Success: Localized English message returned',
      resDlSuccess.body.message === 'Document verified successfully.');
    assert('Payload Integrity: Actual Base64 document bytes transmitted to Gemini',
      capturedBase64Data === validPdfHeader.toString('base64'));

    // -------------------------------------------------------------------------
    // TEST 5: Successful Restaurant FSSAI Licence Verification
    // -------------------------------------------------------------------------
    app.customGeminiRequester = async ({ payload, mimeType }) => {
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
                  businessName: "Spice Symphony Fine Dining",
                  confidence: 0.96,
                  extractedSummary: "FSSAI Food Safety Licence 11524006000421 verified for Spice Symphony Fine Dining.",
                  remarks: "Form C certificate active."
                })
              }]
            }
          }]
        })
      };
    };

    const resFssaiSuccess = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${adminToken}`
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RESTAURANT_FSSAI',
      documentNumber: '1152 4006 0004 21', // Space formatted input
      fileBase64: validJpegHeader.toString('base64')
    })));

    assert('FSSAI OCR Success: Returns 200 and verified=true',
      resFssaiSuccess.statusCode === 200 && resFssaiSuccess.body.verified === true);
    assert('FSSAI OCR Success: Masked number returned',
      resFssaiSuccess.body.documentNumber === '***0421');
    assert('FSSAI OCR Success: Business name extracted',
      resFssaiSuccess.body.holderName === 'Spice Symphony Fine Dining');

    // -------------------------------------------------------------------------
    // TEST 6: Successful Restaurant GSTIN Verification via Multipart File Upload
    // -------------------------------------------------------------------------
    app.customGeminiRequester = async ({ mimeType }) => {
      return {
        status: 200,
        body: JSON.stringify({
          candidates: [{
            content: {
              parts: [{
                text: JSON.stringify({
                  documentTypeMatch: true,
                  isLegible: true,
                  documentNumber: "27AABCU9603R1ZM",
                  businessName: "Spice Symphony Private Limited",
                  confidence: 0.99,
                  extractedSummary: "Government of India GST REG-06 verified: GSTIN 27AABCU9603R1ZM.",
                  remarks: "Active taxpayer status confirmed."
                })
              }]
            }
          }]
        })
      };
    };

    const multipartGst = buildMultipartPayload(
      { documentType: 'RESTAURANT_GST', documentNumber: '27-AABCU-9603-R-1-Z-M' },
      { fieldname: 'file', filename: 'gst_cert.png', contentType: 'image/png', content: validPngHeader }
    );

    const resGstMultipart = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        ...multipartGst.headers,
        'Authorization': `Bearer ${adminToken}`
      }
    }, multipartGst.body);

    assert('GST Multipart Success: Direct file upload returns 200 and verified=true',
      resGstMultipart.statusCode === 200 && resGstMultipart.body.verified === true);
    assert('GST Multipart Success: Masked GSTIN extracted',
      resGstMultipart.body.documentNumber === '***R1ZM');
    assert('GST Multipart Success: Details contain image/png magic MIME',
      resGstMultipart.body.details?.mimeType === 'image/png');

    // -------------------------------------------------------------------------
    // TEST 7: Strict Number Mismatch Handling (422 DOCUMENT_NUMBER_MISMATCH)
    // -------------------------------------------------------------------------
    app.customGeminiRequester = async () => {
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
                  confidence: 0.95
                })
              }]
            }
          }]
        })
      };
    };

    // User claims MH0220180099999, but OCR extracted MH0220180012345
    const resMismatchNumber = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${riderToken}`
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      documentNumber: 'MH0220180099999',
      fileBase64: validPdfHeader.toString('base64')
    })));

    assert('Strict Match: Mismatched DL number returns 422 DOCUMENT_NUMBER_MISMATCH',
      resMismatchNumber.statusCode === 422 && resMismatchNumber.body.error === 'DOCUMENT_NUMBER_MISMATCH');
    assert('Strict Match: verified=false on mismatch',
      resMismatchNumber.body.verified === false);

    // Partial match test: OCR extracted 14 digits, but user entered only partial 6 digits -> rejected
    const resPartialMismatch = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${adminToken}`
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RESTAURANT_FSSAI',
      documentNumber: '115240', // Partial match
      fileBase64: validPdfHeader.toString('base64')
    })));
    assert('Strict Match: Partial number match rejected (422 INVALID_DOCUMENT_NUMBER_FORMAT)',
      resPartialMismatch.statusCode === 422 && resPartialMismatch.body.error === 'INVALID_DOCUMENT_NUMBER_FORMAT');

    // -------------------------------------------------------------------------
    // TEST 8: Multilingual Localization (English, Hindi, Marathi, Fallback)
    // -------------------------------------------------------------------------
    // 8.1 Success in Hindi
    app.customGeminiRequester = async () => {
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
                  confidence: 0.98
                })
              }]
            }
          }]
        })
      };
    };

    const resHindiSuccess = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${riderToken}`
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      documentNumber: 'MH0220180012345',
      locale: 'hi',
      fileBase64: validPdfHeader.toString('base64')
    })));
    assert('Localization: Hindi success message returned',
      resHindiSuccess.statusCode === 200 && resHindiSuccess.body.message === 'दस्तावेज़ सफलतापूर्वक सत्यापित हो गया।');

    // 8.2 Success in Marathi
    const resMarathiSuccess = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${riderToken}`
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      documentNumber: 'MH0220180012345',
      locale: 'mr',
      fileBase64: validPdfHeader.toString('base64')
    })));
    assert('Localization: Marathi success message returned',
      resMarathiSuccess.statusCode === 200 && resMarathiSuccess.body.message === 'दस्तऐवज यशस्वीरित्या पडताळले गेले.');

    // 8.3 Number mismatch in Hindi
    const resHindiMismatch = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${riderToken}`
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      documentNumber: 'MH0220180099999',
      locale: 'hi',
      fileBase64: validPdfHeader.toString('base64')
    })));
    assert('Localization: Hindi number mismatch message returned',
      resHindiMismatch.statusCode === 422 && resHindiMismatch.body.message === 'दस्तावेज़ संख्या मेल नहीं खाती। निकाला गया नंबर सबमिट किए गए नंबर से मेल नहीं खाता।');
    assert('Localization: Error code DOCUMENT_NUMBER_MISMATCH is stable across languages',
      resHindiMismatch.body.error === 'DOCUMENT_NUMBER_MISMATCH');

    // 8.4 Missing document number in Marathi
    const resMarathiMissing = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${riderToken}`
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      locale: 'mr',
      fileBase64: validPdfHeader.toString('base64')
    })));
    assert('Localization: Marathi missing number message returned',
      resMarathiMissing.statusCode === 400 && resMarathiMissing.body.message === 'पडताळणीसाठी दस्तऐवज क्रमांक आवश्यक आहे.');

    // 8.5 Unauthenticated in Hindi via Accept-Language header
    const resHindiUnauth = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept-Language': 'hi'
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      documentNumber: 'MH0220180012345'
    })));
    assert('Localization: Hindi unauthenticated error message via Accept-Language header',
      resHindiUnauth.statusCode === 401 && resHindiUnauth.body.message === 'प्रमाणीकरण टोकन आवश्यक है। दस्तावेज़ सत्यापित करने के लिए कृपया साइन इन करें।');

    // 8.6 Forbidden in Marathi via Accept-Language header
    const resMarathiForbidden = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${customerToken}`,
        'Accept-Language': 'mr'
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      documentNumber: 'MH0220180012345'
    })));
    assert('Localization: Marathi forbidden error message via Accept-Language header',
      resMarathiForbidden.statusCode === 403 && resMarathiForbidden.body.message === 'प्रवेश नाकारला: आपल्याला हा दस्तऐवज पडताळण्याची परवानगी नाही.');

    // -------------------------------------------------------------------------
    // TEST 9: Unreadable / Blurry Document Handled Defensively (422)
    // -------------------------------------------------------------------------
    app.customGeminiRequester = async () => {
      return {
        status: 200,
        body: JSON.stringify({
          candidates: [{
            content: {
              parts: [{
                text: JSON.stringify({
                  documentTypeMatch: true,
                  isLegible: false,
                  documentNumber: null,
                  confidence: 0.25,
                  remarks: "Severely blurred camera capture. Text cannot be read."
                })
              }]
            }
          }]
        })
      };
    };

    const resIllegible = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${riderToken}`
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      documentNumber: 'MH0220180012345',
      fileBase64: validJpegHeader.toString('base64')
    })));

    assert('Illegible Doc: Returns 422 with verified=false',
      resIllegible.statusCode === 422 && resIllegible.body.verified === false);
    assert('Illegible Doc: Error code is DOCUMENT_UNREADABLE',
      resIllegible.body.error === 'DOCUMENT_UNREADABLE');
    assert('Illegible Doc: Document number is null (no fabricated numbers)',
      resIllegible.body.documentNumber === null);

    // -------------------------------------------------------------------------
    // TEST 10: Document Type Mismatch (422 DOCUMENT_TYPE_MISMATCH)
    // -------------------------------------------------------------------------
    app.customGeminiRequester = async () => {
      return {
        status: 200,
        body: JSON.stringify({
          candidates: [{
            content: {
              parts: [{
                text: JSON.stringify({
                  documentTypeMatch: false,
                  isLegible: true,
                  documentNumber: null,
                  confidence: 0.9,
                  remarks: "Uploaded document is an electricity bill, not a Driving Licence."
                })
              }]
            }
          }]
        })
      };
    };

    const resMismatch = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${riderToken}`
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      documentNumber: 'MH0220180012345',
      fileBase64: validPdfHeader.toString('base64')
    })));

    assert('Type Mismatch: Returns 422 with verified=false',
      resMismatch.statusCode === 422 && resMismatch.body.verified === false);
    assert('Type Mismatch: Error code is DOCUMENT_TYPE_MISMATCH',
      resMismatch.body.error === 'DOCUMENT_TYPE_MISMATCH');

    // -------------------------------------------------------------------------
    // TEST 11: Malformed AI Output (502 MALFORMED_AI_RESPONSE)
    // -------------------------------------------------------------------------
    app.customGeminiRequester = async () => {
      return {
        status: 200,
        body: JSON.stringify({
          candidates: [{
            content: {
              parts: [{
                text: "I cannot parse this document. Please refer to standard guidance at https://example.com"
              }]
            }
          }]
        })
      };
    };

    const resMalformedAi = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${adminToken}`
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RESTAURANT_FSSAI',
      documentNumber: '11524006000421',
      fileBase64: validPdfHeader.toString('base64')
    })));

    assert('Malformed AI: Non-JSON model text returns 502 Bad Gateway with verified=false',
      resMalformedAi.statusCode === 502 && resMalformedAi.body.verified === false && resMalformedAi.body.error === 'MALFORMED_AI_RESPONSE');

    // -------------------------------------------------------------------------
    // TEST 12: External API Failures & Timeouts Handled Defensively (502, 503, 504)
    // -------------------------------------------------------------------------
    // 12.1 500 error from upstream Gemini
    app.customGeminiRequester = async () => {
      return { status: 500, body: "Internal Google API Error" };
    };

    const resApi500 = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${riderToken}`
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      documentNumber: 'MH0220180012345',
      fileBase64: validPdfHeader.toString('base64')
    })));

    assert('Resilience Guard: Gemini 500 error returns 502 Bad Gateway with verified=false',
      resApi500.statusCode === 502 && resApi500.body.verified === false && resApi500.body.error === 'GEMINI_API_ERROR');

    // 12.2 Timeout failure returns 504
    app.customGeminiRequester = async () => {
      throw new Error("Gemini Vision OCR timeout");
    };

    const resTimeout = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${riderToken}`
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      documentNumber: 'MH0220180012345',
      fileBase64: validPdfHeader.toString('base64')
    })));

    assert('Resilience Guard: Gemini timeout returns 504 Gateway Timeout with verified=false',
      resTimeout.statusCode === 504 && resTimeout.body.verified === false && resTimeout.body.error === 'AI_TIMEOUT');

    // 12.3 Missing API key returns 503
    app.customGeminiRequester = null;
    const oldKey = process.env.GEMINI_API_KEY;
    delete process.env.GEMINI_API_KEY;

    const resNoKey = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${riderToken}`
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      documentNumber: 'MH0220180012345',
      fileBase64: validPdfHeader.toString('base64')
    })));

    assert('Configuration Guard: Missing GEMINI_API_KEY returns 503 Service Unavailable',
      resNoKey.statusCode === 503 && resNoKey.body.error === 'GEMINI_NOT_CONFIGURED');

    if (oldKey) process.env.GEMINI_API_KEY = oldKey;

    // -------------------------------------------------------------------------
    // TEST 13: Pre-Uploaded Document Lookup via docId with Authorization
    // -------------------------------------------------------------------------
    const uploadPayload = buildMultipartPayload(
      { category: 'RIDER_DL' },
      { fieldname: 'file', filename: 'rider_dl.pdf', contentType: 'application/pdf', content: validPdfHeader }
    );

    const uploadRes = await request({
      path: '/api/delivery/media/upload',
      method: 'POST',
      headers: {
        ...uploadPayload.headers,
        'Authorization': `Bearer ${riderToken}`
      }
    }, uploadPayload.body);

    const savedDocId = uploadRes.body.docId;
    assert('Pre-Upload Setup: Successfully uploaded rider DL file',
      uploadRes.statusCode === 201 && savedDocId && savedDocId.startsWith('doc-'));

    // Rider 2 attempts to verify Rider 1's document by docId -> rejected with 403
    const resRider2StealsDoc = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${rider2Token}`
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      documentNumber: 'MH0220180012345',
      docId: savedDocId
    })));
    assert('DocId Authorization: Other rider cannot verify another user docId (403)',
      resRider2StealsDoc.statusCode === 403 && resRider2StealsDoc.body.error === 'FORBIDDEN');

    // Legitimate owner (Rider 1) verifies their docId -> succeeds
    app.customGeminiRequester = async ({ mimeType }) => {
      assert('DocId Lookup: File stream accurately identifies application/pdf', mimeType === 'application/pdf');
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
                  holderName: "Ramesh Rider",
                  confidence: 0.97,
                  extractedSummary: "Verified Indian Union Driving Licence MH0220180012345."
                })
              }]
            }
          }]
        })
      };
    };

    const resVerifyByDocId = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${riderToken}`
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      documentNumber: 'MH0220180012345',
      docId: savedDocId
    })));

    assert('DocId Verification: Verifying own pre-uploaded document succeeds with verified=true',
      resVerifyByDocId.statusCode === 200 && resVerifyByDocId.body.verified === true);
    assert('DocId Verification: Extracted number is masked for privacy',
      resVerifyByDocId.body.documentNumber === '***2345');

    // Master Admin can also verify pre-uploaded document
    const resMasterVerifyDoc = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${masterToken}`
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      documentNumber: 'MH0220180012345',
      docId: savedDocId
    })));
    assert('Master Admin Privilege: Master admin can verify any document by docId (200)',
      resMasterVerifyDoc.statusCode === 200 && resMasterVerifyDoc.body.verified === true);

    // -------------------------------------------------------------------------
    // TEST 14: Anti-Spoofing & Client-Side Bypass Resistance
    // -------------------------------------------------------------------------
    // Client sends verified: true in request body, but OCR fails due to number mismatch
    const resSpoofAttempt = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${riderToken}`
      }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      documentNumber: 'MH0220180099999',
      verified: true, // Spoofed field
      success: true, // Spoofed field
      confidence: 1.0, // Spoofed field
      fileBase64: validPdfHeader.toString('base64')
    })));
    assert('Anti-Spoofing: Client-sent verified=true is completely ignored by backend',
      resSpoofAttempt.statusCode === 422 && resSpoofAttempt.body.verified === false);

  } catch (err) {
    console.error(`💥 Unexpected test runner exception: ${err.message}`, err);
  } finally {
    app.customGeminiRequester = null;
    server.close();
  }

  console.log(`\n===============================================================`);
  console.log(`📊 TEST RESULTS: ${passed} / ${total} TESTS PASSED (${Math.round((passed / total) * 100)}% Verified)`);
  console.log(`===============================================================\n`);

  if (passed !== total) {
    process.exit(1);
  }
}

runTests();
