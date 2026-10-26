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
    // TEST 1: Unit Validation of Document Numbers & Types
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

    // -------------------------------------------------------------------------
    // TEST 2: Safe Input Handling - Missing, Unsupported & Oversized Files
    // -------------------------------------------------------------------------
    // Missing document bytes
    const resMissingBytes = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL'
    })));
    assert('Input Guard: Missing document bytes returns 400',
      resMissingBytes.statusCode === 400 && resMissingBytes.body.error === 'DOCUMENT_BYTES_REQUIRED');

    // Invalid document type
    const resInvalidType = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, Buffer.from(JSON.stringify({
      documentType: 'RANDOM_TYPE',
      fileBase64: validPdfHeader.toString('base64')
    })));
    assert('Input Guard: Invalid document type returns 400',
      resInvalidType.statusCode === 400 && resInvalidType.body.error === 'INVALID_DOCUMENT_TYPE');

    // Unsupported MIME / Corrupt Magic Bytes
    const resCorrupt = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      fileBase64: corruptHeader.toString('base64')
    })));
    assert('Magic Byte Guard: Corrupt or executable file content returns 422',
      resCorrupt.statusCode === 422 && resCorrupt.body.error === 'UNSUPPORTED_DOCUMENT_FORMAT');

    // Oversized Payload (>10MB)
    const oversizedBuffer = Buffer.alloc(10 * 1024 * 1024 + 1024, 0); // 10MB + 1KB
    validPdfHeader.copy(oversizedBuffer, 0, 0, validPdfHeader.length);
    const resOversized = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      fileBase64: oversizedBuffer.toString('base64')
    })));
    assert('Size Guard: Document exceeding 10MB returns 413',
      resOversized.statusCode === 413 && resOversized.body.error === 'PAYLOAD_TOO_LARGE');

    // -------------------------------------------------------------------------
    // TEST 3: Successful Rider Driving Licence OCR Verification
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
      headers: { 'Content-Type': 'application/json' }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      fileBase64: validPdfHeader.toString('base64')
    })));

    assert('DL OCR Success: Endpoint returns 200 and verified=true',
      resDlSuccess.statusCode === 200 && resDlSuccess.body.verified === true);
    assert('DL OCR Success: Document number correctly normalized to standard format',
      resDlSuccess.body.documentNumber === 'MH0220180012345');
    assert('DL OCR Success: Holder name extracted accurately',
      resDlSuccess.body.holderName === 'Ramesh Rider');
    assert('DL OCR Success: Provider is GEMINI_VISION_OCR',
      resDlSuccess.body.provider === 'GEMINI_VISION_OCR');
    assert('Payload Integrity: InlineData MIME type matches detected PDF',
      capturedMimeType === 'application/pdf');
    assert('Payload Integrity: Actual Base64 document bytes transmitted to Gemini',
      capturedBase64Data === validPdfHeader.toString('base64'));

    // -------------------------------------------------------------------------
    // TEST 4: Successful Restaurant FSSAI Licence Verification
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
      headers: { 'Content-Type': 'application/json' }
    }, Buffer.from(JSON.stringify({
      documentType: 'RESTAURANT_FSSAI',
      fileBase64: validJpegHeader.toString('base64')
    })));

    assert('FSSAI OCR Success: Returns 200 and verified=true',
      resFssaiSuccess.statusCode === 200 && resFssaiSuccess.body.verified === true);
    assert('FSSAI OCR Success: Extracted 14-digit FSSAI number',
      resFssaiSuccess.body.documentNumber === '11524006000421');
    assert('FSSAI OCR Success: Business name extracted',
      resFssaiSuccess.body.holderName === 'Spice Symphony Fine Dining');

    // -------------------------------------------------------------------------
    // TEST 5: Successful Restaurant GSTIN Verification via Multipart File Upload
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
      { documentType: 'RESTAURANT_GST' },
      { fieldname: 'file', filename: 'gst_cert.png', contentType: 'image/png', content: validPngHeader }
    );

    const resGstMultipart = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: multipartGst.headers
    }, multipartGst.body);

    assert('GST Multipart Success: Direct file upload returns 200 and verified=true',
      resGstMultipart.statusCode === 200 && resGstMultipart.body.verified === true);
    assert('GST Multipart Success: 15-character GSTIN extracted',
      resGstMultipart.body.documentNumber === '27AABCU9603R1ZM');
    assert('GST Multipart Success: Details contain image/png magic MIME',
      resGstMultipart.body.details?.mimeType === 'image/png');

    // -------------------------------------------------------------------------
    // TEST 6: Unreadable / Illegible Document Handled Defensively
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
      headers: { 'Content-Type': 'application/json' }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      fileBase64: validJpegHeader.toString('base64')
    })));

    assert('Illegible Doc: Returns 200 with verified=false',
      resIllegible.statusCode === 200 && resIllegible.body.verified === false);
    assert('Illegible Doc: Document number is null (no fabricated numbers)',
      resIllegible.body.documentNumber === null);
    assert('Illegible Doc: Reason reports illegibility',
      resIllegible.body.reason.toLowerCase().includes('illegible') || resIllegible.body.reason.toLowerCase().includes('unreadable'));

    // -------------------------------------------------------------------------
    // TEST 7: Document Type Mismatch (Irrelevant Document) Handled Defensively
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
      headers: { 'Content-Type': 'application/json' }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      fileBase64: validPdfHeader.toString('base64')
    })));

    assert('Type Mismatch: Returns verified=false for mismatched document',
      resMismatch.statusCode === 200 && resMismatch.body.verified === false);
    assert('Type Mismatch: Details confirm documentTypeMatch=false',
      resMismatch.body.details?.documentTypeMatch === false);

    // -------------------------------------------------------------------------
    // TEST 8: Malformed AI Output (Model outputs non-JSON or HTML)
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
      headers: { 'Content-Type': 'application/json' }
    }, Buffer.from(JSON.stringify({
      documentType: 'RESTAURANT_FSSAI',
      fileBase64: validPdfHeader.toString('base64')
    })));

    assert('Malformed AI: Non-JSON model text returns verified=false with MALFORMED_AI_RESPONSE',
      resMalformedAi.statusCode === 200 && resMalformedAi.body.verified === false && resMalformedAi.body.error === 'MALFORMED_AI_RESPONSE');

    // -------------------------------------------------------------------------
    // TEST 9: Invalid Number Formats Rejected Defensively
    // -------------------------------------------------------------------------
    // FSSAI not 14 digits
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
                  documentNumber: "12345", // Only 5 digits
                  confidence: 0.95,
                  remarks: "Partial number found"
                })
              }]
            }
          }]
        })
      };
    };

    const resInvalidFssai = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, Buffer.from(JSON.stringify({
      documentType: 'RESTAURANT_FSSAI',
      fileBase64: validJpegHeader.toString('base64')
    })));

    assert('Format Guard: FSSAI with invalid length (<14 digits) rejected (verified=false)',
      resInvalidFssai.statusCode === 200 && resInvalidFssai.body.verified === false);
    assert('Format Guard: Reason specifies 14-digit requirement',
      resInvalidFssai.body.reason.includes('14 numeric digits'));

    // GSTIN not 15 chars
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
                  documentNumber: "INVALID_GSTIN_123",
                  confidence: 0.95
                })
              }]
            }
          }]
        })
      };
    };

    const resInvalidGst = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, Buffer.from(JSON.stringify({
      documentType: 'RESTAURANT_GST',
      fileBase64: validPngHeader.toString('base64')
    })));

    assert('Format Guard: Malformed GSTIN rejected (verified=false)',
      resInvalidGst.statusCode === 200 && resInvalidGst.body.verified === false);

    // -------------------------------------------------------------------------
    // TEST 10: Expected Number Mismatch Handled Defensively
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

    const resNumberMismatch = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      expectedNumber: 'MH0220180099999', // Claimed different number
      fileBase64: validPdfHeader.toString('base64')
    })));

    assert('Mismatch Guard: Claimed number mismatch with extracted number returns verified=false',
      resNumberMismatch.statusCode === 200 && resNumberMismatch.body.verified === false);
    assert('Mismatch Guard: Details record numberMatch=false',
      resNumberMismatch.body.details?.numberMatch === false);

    // -------------------------------------------------------------------------
    // TEST 11: Low Confidence Threshold Rejection
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
                  confidence: 0.42 // Below 0.5 minimum
                })
              }]
            }
          }]
        })
      };
    };

    const resLowConfidence = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      fileBase64: validPdfHeader.toString('base64')
    })));

    assert('Confidence Guard: Low confidence (<0.5) rejected (verified=false)',
      resLowConfidence.statusCode === 200 && resLowConfidence.body.verified === false && resLowConfidence.body.reason.includes('too low'));

    // -------------------------------------------------------------------------
    // TEST 12: External API Failures & Timeouts Handled Defensively
    // -------------------------------------------------------------------------
    app.customGeminiRequester = async () => {
      return { status: 500, body: "Internal Google API Error" };
    };

    const resApi500 = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      fileBase64: validPdfHeader.toString('base64')
    })));

    assert('Resilience Guard: Gemini 500 error returns 502 Bad Gateway with verified=false',
      resApi500.statusCode === 502 && resApi500.body.verified === false && resApi500.body.error === 'GEMINI_API_ERROR');

    // Timeout failure
    app.customGeminiRequester = async () => {
      throw new Error("Gemini Vision OCR timeout");
    };

    const resTimeout = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      fileBase64: validPdfHeader.toString('base64')
    })));

    assert('Resilience Guard: Gemini timeout returns 504 Gateway Timeout with verified=false',
      resTimeout.statusCode === 504 && resTimeout.body.verified === false && resTimeout.body.error === 'AI_TIMEOUT');

    // Missing API Key guard (when custom requester unset)
    app.customGeminiRequester = null;
    const oldKey = process.env.GEMINI_API_KEY;
    delete process.env.GEMINI_API_KEY;

    const resNoKey = await request({
      path: '/api/delivery/ai/verify-document',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      fileBase64: validPdfHeader.toString('base64')
    })));

    assert('Configuration Guard: Missing GEMINI_API_KEY returns 503 Service Unavailable',
      resNoKey.statusCode === 503 && resNoKey.body.error === 'GEMINI_NOT_CONFIGURED');

    if (oldKey) process.env.GEMINI_API_KEY = oldKey;

    // -------------------------------------------------------------------------
    // TEST 13: Pre-Uploaded Document Lookup via docId
    // -------------------------------------------------------------------------
    // Simulate rider logging in and uploading a DL via /media/upload
    const riderLoginRes = await request({
      path: '/api/delivery/auth/login',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, Buffer.from(JSON.stringify({
      email: 'rider@restro.app',
      password: 'Rider@123'
    })));
    const riderToken = riderLoginRes.body.token;

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

    // Now verify the document by referencing docId (without re-uploading bytes)
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
      headers: { 'Content-Type': 'application/json' }
    }, Buffer.from(JSON.stringify({
      documentType: 'RIDER_DL',
      docId: savedDocId
    })));

    assert('DocId Verification: Verifying pre-uploaded document by docId succeeds with verified=true',
      resVerifyByDocId.statusCode === 200 && resVerifyByDocId.body.verified === true);
    assert('DocId Verification: Extracted number matches stored DL',
      resVerifyByDocId.body.documentNumber === 'MH0220180012345');

    // -------------------------------------------------------------------------
    // TEST 14: Prompt Engineering & Anti-Hallucination Grounding
    // -------------------------------------------------------------------------
    const dlPrompt = app.buildGeminiVisionPrompt('RIDER_DL', 'MH0220180012345', 'Ramesh Rider');
    assert('Prompt Grounding: Contains DL specifications and holder target',
      dlPrompt.includes('Indian Union Driving Licence') && dlPrompt.includes('Ramesh Rider'));
    assert('Prompt Anti-Hallucination: Strictly forbids guessing or fabricating credentials',
      dlPrompt.includes('Never guess, fabricate, or hallucinate document numbers'));

    const fssaiPrompt = app.buildGeminiVisionPrompt('RESTAURANT_FSSAI', '11524006000421', 'Spice Symphony');
    assert('Prompt Grounding: Specifies 14 numeric digits for FSSAI',
      fssaiPrompt.includes('14 numeric digits'));

    const gstPrompt = app.buildGeminiVisionPrompt('RESTAURANT_GST', '27AABCU9603R1ZM', 'Spice Symphony');
    assert('Prompt Grounding: Specifies 15 alphanumeric characters for GSTIN',
      gstPrompt.includes('15 alphanumeric characters'));

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
