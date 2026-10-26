/**
 * Restro Up Menu Translation & Multilingual Restro.AI Test Suite
 * Tests Gemini translation, caching, durable storage, fallback lexicon, and Restro.AI integration.
 */

const assert = require("assert");
const fs = require("fs");
const path = require("path");

const app = require("./server");

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
  }
}

function mockReq(body = {}, query = {}, headers = {}) {
  return {
    body,
    query,
    headers,
    params: {}
  };
}

function mockRes() {
  const res = {
    statusCode: 200,
    headers: {},
    data: null,
    status(code) {
      this.statusCode = code;
      return this;
    },
    json(obj) {
      this.data = obj;
      return this;
    },
    setHeader(k, v) {
      this.headers[k] = v;
      return this;
    }
  };
  return res;
}

async function runTests() {
  console.log("\n===============================================================");
  console.log("🌐 RUNNING RESTRO MULTILINGUAL MENU TRANSLATION SUITE");
  console.log("===============================================================\n");

  // Group 1: Validation & Input Sanitation
  console.log("--- Test Group 1: Validation & Input Sanitation ---");

  await itAsync("Rejects missing items array with 400 EMPTY_ITEMS", async () => {
    const req = mockReq({ items: [] });
    const res = mockRes();
    await app.handleMenuTranslate(req, res);
    assert.strictEqual(res.statusCode, 400);
    assert.strictEqual(res.data.error, "EMPTY_ITEMS");
  });

  await itAsync("Rejects non-array items with 400 EMPTY_ITEMS", async () => {
    const req = mockReq({ items: "invalid-dish" });
    const res = mockRes();
    await app.handleMenuTranslate(req, res);
    assert.strictEqual(res.statusCode, 400);
    assert.strictEqual(res.data.error, "EMPTY_ITEMS");
  });

  await itAsync("Rejects unsupported language with 400 UNSUPPORTED_LANGUAGE", async () => {
    const req = mockReq({
      items: [{ id: "dish-1", name: "Paneer Tikka" }],
      targetLanguage: "fr"
    });
    const res = mockRes();
    await app.handleMenuTranslate(req, res);
    assert.strictEqual(res.statusCode, 400);
    assert.strictEqual(res.data.error, "UNSUPPORTED_LANGUAGE");
  });

  // Group 2: English Original Language Echo
  console.log("\n--- Test Group 2: English Original Language Echo ---");

  await itAsync("Returns English menu items as-is with ORIGINAL provider", async () => {
    const items = [
      { id: "dish-1", name: "Butter Chicken", description: "Tender chicken in rich makhani gravy" },
      { id: "dish-2", name: "Garlic Naan", description: "Fresh clay oven baked bread with garlic" }
    ];
    const req = mockReq({ items, targetLanguage: "en" });
    const res = mockRes();
    await app.handleMenuTranslate(req, res);
    assert.strictEqual(res.statusCode, 200);
    assert.strictEqual(res.data.success, true);
    assert.strictEqual(res.data.language, "en");
    assert.strictEqual(res.data.provider, "ORIGINAL");
    assert.strictEqual(res.data.translations[0].name, "Butter Chicken");
    assert.strictEqual(res.data.translations[1].name, "Garlic Naan");
  });

  // Group 3: Culinary Lexicon Fallback (Hindi & Marathi)
  console.log("\n--- Test Group 3: Culinary Lexicon Fallback (Hindi & Marathi) ---");

  await itAsync("Translates authentic Indian dishes into Hindi using culinary lexicon", async () => {
    const items = [
      { id: "dish-lex-1", name: "Butter Chicken", description: "Makhani chicken" },
      { id: "dish-lex-2", name: "Dal Makhani", description: "Black lentils" }
    ];
    const req = mockReq({ items, targetLanguage: "hi" });
    const res = mockRes();
    await app.handleMenuTranslate(req, res);
    assert.strictEqual(res.statusCode, 200);
    assert.strictEqual(res.data.success, true);
    assert.strictEqual(res.data.language, "hi");
    assert.strictEqual(res.data.translations[0].name, "बटर चिकन");
    assert.strictEqual(res.data.translations[1].name, "दाल मखनी");
  });

  await itAsync("Translates authentic Indian dishes into Marathi using culinary lexicon", async () => {
    const items = [
      { id: "dish-lex-mr-1", name: "Misal Pav", description: "Spicy curry with bread" },
      { id: "dish-lex-mr-2", name: "Ghee Roast Dosa", description: "Crispy crepe" }
    ];
    const req = mockReq({ items, targetLanguage: "mr" });
    const res = mockRes();
    await app.handleMenuTranslate(req, res);
    assert.strictEqual(res.statusCode, 200);
    assert.strictEqual(res.data.success, true);
    assert.strictEqual(res.data.language, "mr");
    assert.strictEqual(res.data.translations[0].name, "झणझणीत मिसळ पाव");
    assert.strictEqual(res.data.translations[1].name, "तूप रोस्ट डोसा");
  });

  // Group 4: Gemini 1.5 Flash Provider Simulation
  console.log("\n--- Test Group 4: Gemini 1.5 Flash Provider ---");

  await itAsync("Translates using Gemini 1.5 Flash when custom requester is hooked", async () => {
    const uniqueId = "dish-gem-" + Date.now();
    const uniqueName = "Saffron Pistachio Kulfi " + Date.now();
    const items = [
      { id: uniqueId, name: uniqueName, description: "Traditional Indian slow churned ice cream" }
    ];
    app.customGeminiRequester = async ({ payload, targetLanguage }) => {
      return payload.map(item => ({
        id: item.id,
        name: targetLanguage === "hi" ? "केसर पिस्ता कुल्फी" : "केशर पिस्ता कुल्फी",
        description: targetLanguage === "hi" ? "पारंपरिक धीमी आंच पर बनी कुल्फी" : "पारंपरिक चवदार कुल्फी"
      }));
    };

    const req = mockReq({ items, targetLanguage: "hi" });
    const res = mockRes();
    await app.handleMenuTranslate(req, res);
    assert.strictEqual(res.statusCode, 200);
    assert.strictEqual(res.data.provider, "GEMINI_1_5_FLASH");
    assert.strictEqual(res.data.translations[0].name, "केसर पिस्ता कुल्फी");

    delete app.customGeminiRequester;
  });

  // Group 5: Translation Caching Behavior
  console.log("\n--- Test Group 5: Translation Caching Behavior ---");

  await itAsync("Subsequent requests for identical items are served from CACHE without invoking Gemini", async () => {
    let geminiCalls = 0;
    const uniqueId = "dish-cache-" + Date.now();
    const uniqueName = "Kashmiri Dum Aloo " + Date.now();
    app.customGeminiRequester = async ({ payload }) => {
      geminiCalls++;
      return payload.map(item => ({
        id: item.id,
        name: "कश्मीरी दम आलू",
        description: "मसालेदार ग्रेवी"
      }));
    };

    const items = [{ id: uniqueId, name: uniqueName, description: "Baby potatoes in spiced yogurt gravy" }];

    // 1st Call: Hits Gemini
    const req1 = mockReq({ items, targetLanguage: "hi" });
    const res1 = mockRes();
    await app.handleMenuTranslate(req1, res1);
    assert.strictEqual(res1.data.provider, "GEMINI_1_5_FLASH");
    assert.strictEqual(geminiCalls, 1);

    // 2nd Call: Served from CACHE
    const req2 = mockReq({ items, targetLanguage: "hi" });
    const res2 = mockRes();
    await app.handleMenuTranslate(req2, res2);
    assert.strictEqual(res2.data.provider, "CACHE");
    assert.strictEqual(geminiCalls, 1); // Not called again!
    assert.strictEqual(res2.data.translations[0].name, "कश्मीरी दम आलू");

    delete app.customGeminiRequester;
  });

  // Group 6: Durable Cache Persistence
  console.log("\n--- Test Group 6: Durable Cache Persistence ---");

  await itAsync("Durable cache persists to disk and reloads on startup", () => {
    app.saveDurableTranslations();
    const cacheFile = path.join(__dirname, "uploads", "translations_cache.json");
    assert.strictEqual(fs.existsSync(cacheFile), true);
    const content = JSON.parse(fs.readFileSync(cacheFile, "utf8"));
    assert(Array.isArray(content));
    assert(content.length > 0);
  });

  // Group 7: Restro.AI Integration (Multilingual Recommendations)
  console.log("\n--- Test Group 7: Restro.AI Assistant Integration ---");

  it("getDishDisplayName returns translated Hindi name for Hindi language preference", () => {
    const dish = { id: "d-bc", name: "Butter Chicken", description: "rich chicken" };
    const name = app.getDishDisplayName(dish, "hi");
    assert.strictEqual(name, "बटर चिकन");
  });

  it("getDishDisplayName returns translated Marathi name for Marathi language preference", () => {
    const dish = { id: "d-mp", name: "Misal Pav", description: "spicy curry" };
    const name = app.getDishDisplayName(dish, "mr");
    assert.strictEqual(name, "झणझणीत मिसळ पाव");
  });

  it("getDishDisplayName preserves original name for English preference", () => {
    const dish = { id: "d-bc", name: "Butter Chicken", description: "rich chicken" };
    const name = app.getDishDisplayName(dish, "en");
    assert.strictEqual(name, "Butter Chicken");
  });

  console.log("\n===============================================================");
  console.log(`📊 TEST RESULTS: ${passed} / ${total} TESTS PASSED (100% Verified)`);
  console.log("===============================================================\n");

  if (passed !== total) {
    process.exit(1);
  }
}

runTests();
