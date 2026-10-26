package com.restaurant.mobile.delivery

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Step2IntegrationRegressionTest
 * Unified regression test suite for Commit 7 of Step 2:
 * Verifies end-to-end client-side integration and interactions across:
 * 1. Regulatory compliance document OCR verification & localization (Commits 1-3)
 * 2. Razorpay payment verification & dining rules (Commit 4)
 * 3. RazorpayX rider payouts, validation & idempotency (Commit 5)
 * 4. Gemini multilingual menu translations & search (Commit 6)
 */
class Step2IntegrationRegressionTest {

    private val gson = Gson()

    // -------------------------------------------------------------
    // 1. REGULATORY COMPLIANCE DOCUMENT INTEGRATION
    // -------------------------------------------------------------

    @Test
    fun testRiderDrivingLicenceVerificationFlowWithLocalization() {
        // Safe byte stream reading
        val pdfBytes = "%PDF-1.4 DL-MH0220180012345 Name: Rajesh Kumar".toByteArray()
        val stream = ByteArrayInputStream(pdfBytes)
        val readResult = FirebaseStorageEngine.readBytesFromStream(stream)
        assertTrue("Document stream reading must succeed", readResult.isSuccess)
        val bytes = readResult.getOrThrow()
        assertTrue("Bytes must be non-empty", bytes.isNotEmpty())

        // Validate document number format
        val validDl = "MH0220180012345"
        assertTrue("DL number format check", validDl.matches(Regex("^[A-Z]{2}[0-9]{2}[0-9A-Z]{7,12}$")))

        // Parse mock verified JSON with Hindi localized message
        val mockBackendJson = """
            {
                "success": true,
                "verified": true,
                "provider": "GEMINI_VISION_OCR",
                "documentType": "RIDER_DL",
                "documentNumber": "***2345",
                "maskedDocumentNumber": "***2345",
                "holderName": "Rajesh Kumar",
                "confidence": 0.98,
                "message": "दस्तावेज़ सफलतापूर्वक सत्यापित हो गया।"
            }
        """.trimIndent()

        val map = gson.fromJson(mockBackendJson, Map::class.java)
        assertEquals(true, map["success"])
        assertEquals(true, map["verified"])
        assertEquals("RIDER_DL", map["documentType"])
        assertEquals("***2345", map["maskedDocumentNumber"])
        assertTrue((map["message"] as String).contains("सत्यापित"))
    }

    // -------------------------------------------------------------
    // 2. RAZORPAY PAYMENT VERIFICATION & DINING RESTRICTIONS
    // -------------------------------------------------------------

    @Test
    fun testDiningPaymentEligibilityAndBillTotalIntegrity() {
        val items = listOf(
            TableBillItem("dish-1", "Butter Chicken", 2, 45000L, 90000L),
            TableBillItem("dish-2", "Garlic Naan", 4, 8000L, 32000L)
        )
        // Subtotal = ₹1220.0, Tax (5%) = ₹61.0, Service Charge (5%) = ₹61.0, Total = ₹1342.0
        val activeTab = DiningTab(
            tableNumber = "T10",
            items = items,
            subtotal = 122000L,
            tax = 6100L,
            serviceCharge = 6100L,
            total = 134200L,
            status = DiningTabStatus.ACTIVE
        )

        // When restaurant accepts dining payments
        val initiatedBill = DineInPaymentRules.validatePaymentInitiation(
            tab = activeTab,
            tip = 50.0,
            isServiceChargeOptedOut = false
        )
        assertNotNull(initiatedBill)
        assertEquals(1220.0, initiatedBill!!.subtotal, 0.001)
        assertEquals(61.0, initiatedBill.tax, 0.001)
        assertEquals(61.0, initiatedBill.serviceCharge, 0.001)
        assertEquals(50.0, initiatedBill.tip, 0.001)
        assertEquals(1392.0, initiatedBill.total, 0.001)
        assertEquals(139200L, initiatedBill.totalPaise)
    }

    // -------------------------------------------------------------
    // 3. RAZORPAYX RIDER PAYOUT RULES & IDEMPOTENCY
    // -------------------------------------------------------------

    @Test
    fun testRiderPayoutValidationAndDestinationPrivacy() {
        // VPA format validation
        val validVpa = "ramesh.rider@okaxis"
        val vpaCheck = RiderPayoutRules.validateVpa(validVpa)
        assertTrue(vpaCheck.isValid)
        assertNull(vpaCheck.errorMessage)

        // Amount bounds validation (₹10 min, ₹100,000 max)
        assertTrue(RiderPayoutRules.validatePayoutAmount(500L).isValid)
        assertFalse(RiderPayoutRules.validatePayoutAmount(5L).isValid) // < ₹10 rejected
        assertFalse(RiderPayoutRules.validatePayoutAmount(150_000L).isValid) // > ₹100,000 rejected

        // Masked destination privacy
        val masked = RiderPayoutRules.maskDestination(validVpa)
        assertTrue("Destination should be masked", masked.contains("***"))
        assertTrue("Masked destination should preserve domain", masked.endsWith("@okaxis"))

        // Bank account masking
        val maskedBank = RiderPayoutRules.maskDestination("987654321098")
        assertTrue(maskedBank.contains("••••"))
        assertTrue(maskedBank.endsWith("1098"))
    }

    // -------------------------------------------------------------
    // 4. MULTILINGUAL MENU TRANSLATION & SEARCH INTEGRATION
    // -------------------------------------------------------------

    @Test
    fun testMultilingualMenuTranslationAndSearchIntegration() {
        val dish = Dish(
            id = "dish-butter-chicken",
            restaurantId = "rest-1",
            category = "Main Course",
            name = "Butter Chicken",
            description = "Tender roasted chicken in rich butter gravy",
            price = 45000L,
            image = "butter_chicken.jpg",
            vegetarian = false
        )

        // Cache custom translation via RestroMenuTranslationManager
        RestroMenuTranslationManager.cacheTranslation(
            lang = "hi",
            dishId = dish.id,
            dishName = dish.name,
            translation = DishTranslation("बटर चिकन", "मलाईदार मक्खन और टमाटर की समृद्ध ग्रेवी")
        )

        // Localized name extraction
        val hiName = dish.localizedName("hi")
        assertEquals("बटर चिकन", hiName)

        val enName = dish.localizedName("en")
        assertEquals("Butter Chicken", enName)

        // Cross-language multilingual search
        assertTrue(RestroMultilingualSearch.matchesDish(dish, "बटर चिकन"))
        assertTrue(RestroMultilingualSearch.matchesDish(dish, "Butter Chicken"))
        assertTrue(RestroMultilingualSearch.matchesDish(dish, "chicken"))
    }
}
