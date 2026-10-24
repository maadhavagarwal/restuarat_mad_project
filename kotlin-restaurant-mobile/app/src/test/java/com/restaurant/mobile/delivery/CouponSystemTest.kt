package com.restaurant.mobile.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end verification and regression test suite for the Restro Coupon System.
 * Tests:
 * 1. WELCOME50 percentage-based discount with hard cap.
 * 2. FEAST threshold validation and flat discount.
 * 3. FEAST100 tiered discount progression.
 * 4. FREEDEL exact delivery partner fee waiver.
 * 5. Invalid and unrecognized coupon rejection.
 * 6. Minimum subtotal requirement calculation and error messaging.
 */
class CouponSystemTest {

    // -------------------------------------------------------------
    // 1. WELCOME50 — 50% OFF UP TO ₹50
    // -------------------------------------------------------------

    @Test
    fun testWelcome50_smallOrderGivesHalfOff() {
        val subtotal = 8000L // ₹80.00
        val delivery = 4000L // ₹40.00
        val discount = CouponRules.calculateDiscount("WELCOME50", subtotal, delivery)

        // 50% of ₹80 is ₹40 (4000 paise)
        assertEquals(4000L, discount)
    }

    @Test
    fun testWelcome50_largeOrderCapsAtFiftyRupees() {
        val subtotal = 35000L // ₹350.00
        val delivery = 4000L  // ₹40.00
        val discount = CouponRules.calculateDiscount("WELCOME50", subtotal, delivery)

        // Capped at ₹50.00 (5000 paise)
        assertEquals(5000L, discount)
    }

    @Test
    fun testWelcome50_zeroSubtotalGivesZeroDiscount() {
        val subtotal = 0L
        val delivery = 4000L
        val discount = CouponRules.calculateDiscount("WELCOME50", subtotal, delivery)

        assertEquals(0L, discount)
    }

    // -------------------------------------------------------------
    // 2. FEAST — FLAT ₹50 OFF ON MINIMUM ORDER OF ₹200
    // -------------------------------------------------------------

    @Test
    fun testFeast_belowThresholdYieldsZeroDiscount() {
        val subtotal = 19900L // ₹199.00
        val delivery = 4000L
        val discount = CouponRules.calculateDiscount("FEAST", subtotal, delivery)

        assertEquals(0L, discount)
    }

    @Test
    fun testFeast_atOrAboveThresholdYieldsFiftyRupeesDiscount() {
        val subtotal = 20000L // ₹200.00
        val delivery = 4000L
        val discount = CouponRules.calculateDiscount("FEAST", subtotal, delivery)

        assertEquals(5000L, discount)

        val higherSubtotal = 45000L // ₹450.00
        val higherDiscount = CouponRules.calculateDiscount("FEAST", higherSubtotal, delivery)
        assertEquals(5000L, higherDiscount)
    }

    // -------------------------------------------------------------
    // 3. FEAST100 — TIERED DISCOUNT PROGRESSION
    // -------------------------------------------------------------

    @Test
    fun testFeast100_belowMinOrderGivesZero() {
        val subtotal = 15000L // ₹150.00
        val delivery = 4000L
        val discount = CouponRules.calculateDiscount("FEAST100", subtotal, delivery)

        assertEquals(0L, discount)
    }

    @Test
    fun testFeast100_tier1Between200And400GivesFiftyRupees() {
        val subtotal = 25000L // ₹250.00
        val delivery = 4000L
        val discount = CouponRules.calculateDiscount("FEAST100", subtotal, delivery)

        assertEquals(5000L, discount)
    }

    @Test
    fun testFeast100_tier2Above400GivesHundredRupees() {
        val subtotal = 40000L // ₹400.00
        val delivery = 4000L
        val discount = CouponRules.calculateDiscount("FEAST100", subtotal, delivery)

        assertEquals(10000L, discount)
    }

    // -------------------------------------------------------------
    // 4. FREEDEL — COMPLETE DELIVERY FEE WAIVER
    // -------------------------------------------------------------

    @Test
    fun testFreedel_exactlyMatchesDeliveryFee() {
        val subtotal = 12000L // ₹120.00
        val delivery = 3500L  // ₹35.00
        val discount = CouponRules.calculateDiscount("FREEDEL", subtotal, delivery)

        assertEquals(3500L, discount)

        // Verify total after delivery waiver equals subtotal + tax
        val tax = (subtotal * 5) / 100 // 600 paise
        val total = subtotal + delivery + tax - discount
        assertEquals(subtotal + tax, total)
    }

    @Test
    fun testFreedel_worksWithDifferentRestaurantDeliveryCharges() {
        val subtotal = 50000L
        val deliveryFees = listOf(0L, 2500L, 4000L, 7500L)

        for (fee in deliveryFees) {
            val discount = CouponRules.calculateDiscount("FREEDEL", subtotal, fee)
            assertEquals("Discount must precisely equal delivery fee", fee, discount)
        }
    }

    // -------------------------------------------------------------
    // 5. UNKNOWN AND INVALID COUPONS
    // -------------------------------------------------------------

    @Test
    fun testInvalidCoupons_neverAwardDiscounts() {
        val subtotal = 50000L
        val delivery = 4000L
        val invalidCodes = listOf("BOGUS50", "HACKER", "FREEFOOD", "SAVE99", "", "   ")

        for (code in invalidCodes) {
            val discount = CouponRules.calculateDiscount(code, subtotal, delivery)
            assertEquals("Invalid coupon '$code' must yield 0 discount", 0L, discount)
        }
    }

    // -------------------------------------------------------------
    // 6. VALIDATION AND USER FEEDBACK ENGINE
    // -------------------------------------------------------------

    @Test
    fun testValidation_emptyCouponFails() {
        val (isValid, errorMsg) = CouponRules.validateCoupon("", 25000L)
        assertFalse(isValid)
        assertNotNull(errorMsg)
    }

    @Test
    fun testValidation_unknownCouponFailsWithHelpfulList() {
        val (isValid, errorMsg) = CouponRules.validateCoupon("DISCOUNT", 25000L)
        assertFalse(isValid)
        assertTrue(errorMsg?.contains("Available") == true)
        assertTrue(errorMsg?.contains("WELCOME50") == true)
    }

    @Test
    fun testValidation_feastBelow200ProvidesDeficitAmount() {
        val subtotal = 16000L // ₹160 (short by ₹40)
        val (isValid, errorMsg) = CouponRules.validateCoupon("FEAST", subtotal)
        assertFalse(isValid)
        assertTrue("Error message must inform user they need ₹40 more", errorMsg?.contains("₹40") == true)
    }

    @Test
    fun testValidation_feastAtOrAbove200Succeeds() {
        val (isValid, errorMsg) = CouponRules.validateCoupon("FEAST", 22000L)
        assertTrue(isValid)
        assertNull(errorMsg)
    }

    @Test
    fun testValidation_welcome50AndFreedelAlwaysSucceedOnPositiveSubtotal() {
        assertTrue(CouponRules.validateCoupon("WELCOME50", 5000L).first)
        assertTrue(CouponRules.validateCoupon("FREEDEL", 5000L).first)
    }

    @Test
    fun testCaseInsensitiveAndWhitespaceResilience() {
        assertEquals(5000L, CouponRules.calculateDiscount("  welcome50  ", 30000L, 4000L))
        assertEquals(5000L, CouponRules.calculateDiscount("feast", 25000L, 4000L))
        assertEquals(4000L, CouponRules.calculateDiscount("  FreeDel  ", 25000L, 4000L))
    }
}
