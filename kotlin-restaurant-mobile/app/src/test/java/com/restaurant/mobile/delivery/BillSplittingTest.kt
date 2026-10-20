package com.restaurant.mobile.delivery

import org.junit.Assert.*
import org.junit.Test

class BillSplittingTest {

    // -------------------------------------------------------------
    // 1. BASIC SPLITS AND EQUAL DIVISION
    // -------------------------------------------------------------

    @Test
    fun testSoloDinerPaysFullBill() {
        val total = 1250.0
        val split = BillSplittingRules.splitBill(total, partySize = 1)

        assertTrue(split.isValid)
        assertEquals(1, split.partySize)
        assertEquals(1250.0, split.totalAmount, 0.001)
        assertEquals(1250.0, split.perPersonBaseAmount, 0.001)
        assertEquals(0.0, split.remainderAmount, 0.001)
        assertEquals(1, split.individualShares.size)
        assertEquals(1250.0, split.individualShares[0], 0.001)
        assertTrue(split.isReconciled())
        assertEquals(1250.0, split.totalReconciled, 0.001)
    }

    @Test
    fun testEvenDivisionAmongParty() {
        // ₹1000 split among 4 diners = exactly ₹250 each, 0 remainder
        val total = 1000.0
        val split = BillSplittingRules.splitBill(total, partySize = 4)

        assertTrue(split.isValid)
        assertEquals(4, split.partySize)
        assertEquals(250.0, split.perPersonBaseAmount, 0.001)
        assertEquals(0.0, split.remainderAmount, 0.001)
        assertEquals(4, split.individualShares.size)
        split.individualShares.forEach { share ->
            assertEquals(250.0, share, 0.001)
        }
        assertTrue(split.isReconciled())
        assertEquals(1000.0, split.totalReconciled, 0.001)
    }

    // -------------------------------------------------------------
    // 2. REMAINDER AND ROUNDING RECONCILIATION
    // -------------------------------------------------------------

    @Test
    fun testOddDivisionWithPaisaRemainderReconciliation() {
        // ₹100 split among 3 diners:
        // 10000 paise / 3 = 3333 paise (₹33.33)
        // remainder = 1 paisa (₹0.01)
        // Diner 1 pays: ₹33.34
        // Diner 2 pays: ₹33.33
        // Diner 3 pays: ₹33.33
        // Sum = 33.34 + 33.33 + 33.33 = 100.00 exactly!
        val total = 100.0
        val split = BillSplittingRules.splitBill(total, partySize = 3)

        assertTrue(split.isValid)
        assertEquals(3, split.partySize)
        assertEquals(33.33, split.perPersonBaseAmount, 0.001)
        assertEquals(0.01, split.remainderAmount, 0.001)
        assertEquals(3, split.individualShares.size)
        assertEquals(33.34, split.individualShares[0], 0.001)
        assertEquals(33.33, split.individualShares[1], 0.001)
        assertEquals(33.33, split.individualShares[2], 0.001)
        assertTrue(split.isReconciled())
        assertEquals(100.0, split.totalReconciled, 0.001)
    }

    @Test
    fun testComplexRemainderSplitAmongSevenDiners() {
        // ₹1234.56 split among 7 diners
        // 123456 paise / 7 = 17636 paise (₹176.36)
        // remainder = 123456 % 7 = 4 paise (₹0.04)
        // First 4 diners pay ₹176.37, last 3 pay ₹176.36
        // Sum = (4 * 176.37) + (3 * 176.36) = 705.48 + 529.08 = 1234.56!
        val total = 1234.56
        val split = BillSplittingRules.splitBill(total, partySize = 7)

        assertTrue(split.isValid)
        assertEquals(7, split.partySize)
        assertEquals(176.36, split.perPersonBaseAmount, 0.001)
        assertEquals(0.04, split.remainderAmount, 0.001)
        assertEquals(7, split.individualShares.size)

        for (i in 0 until 4) {
            assertEquals(176.37, split.individualShares[i], 0.001)
        }
        for (i in 4 until 7) {
            assertEquals(176.36, split.individualShares[i], 0.001)
        }
        assertTrue(split.isReconciled())
        assertEquals(1234.56, split.totalReconciled, 0.001)
    }

    // -------------------------------------------------------------
    // 3. ZERO AND INVALID PARTY SIZE HANDLING
    // -------------------------------------------------------------

    @Test
    fun testZeroPartySizeHandledSafely() {
        val split = BillSplittingRules.splitBill(500.0, partySize = 0)
        assertFalse(split.isValid)
        assertEquals(0, split.partySize)
        assertEquals(0.0, split.perPersonBaseAmount, 0.001)
        assertEquals(0.0, split.remainderAmount, 0.001)
        assertTrue(split.individualShares.isEmpty())
    }

    @Test
    fun testNegativePartySizeHandledSafely() {
        val split = BillSplittingRules.splitBill(800.0, partySize = -3)
        assertFalse(split.isValid)
        assertEquals(-3, split.partySize)
        assertEquals(0.0, split.perPersonBaseAmount, 0.001)
        assertTrue(split.individualShares.isEmpty())
    }

    @Test
    fun testZeroTotalAmountProducesZeroShares() {
        val split = BillSplittingRules.splitBill(0.0, partySize = 4)
        assertTrue(split.isValid)
        assertEquals(4, split.partySize)
        assertEquals(0.0, split.perPersonBaseAmount, 0.001)
        assertEquals(0.0, split.remainderAmount, 0.001)
        assertEquals(4, split.individualShares.size)
        split.individualShares.forEach { share ->
            assertEquals(0.0, share, 0.001)
        }
        assertTrue(split.isReconciled())
        assertEquals(0.0, split.totalReconciled, 0.001)
    }

    @Test
    fun testNegativeOrNanTotalAmountHandledSafely() {
        val negativeSplit = BillSplittingRules.splitBill(-100.0, partySize = 2)
        assertEquals(0.0, negativeSplit.totalAmount, 0.001)
        assertEquals(0.0, negativeSplit.perPersonBaseAmount, 0.001)

        val nanSplit = BillSplittingRules.splitBill(Double.NaN, partySize = 2)
        assertEquals(0.0, nanSplit.totalAmount, 0.001)
        assertEquals(0.0, nanSplit.perPersonBaseAmount, 0.001)

        val infSplit = BillSplittingRules.splitBill(Double.POSITIVE_INFINITY, partySize = 2)
        assertEquals(0.0, infSplit.totalAmount, 0.001)
        assertEquals(0.0, infSplit.perPersonBaseAmount, 0.001)
    }

    @Test
    fun testPartySizeValidationAndClamping() {
        assertNull(BillSplittingRules.validatePartySize(null))
        assertNull(BillSplittingRules.validatePartySize(0))
        assertNull(BillSplittingRules.validatePartySize(-5))

        assertEquals(1, BillSplittingRules.validatePartySize(1))
        assertEquals(10, BillSplittingRules.validatePartySize(10))
        assertEquals(50, BillSplittingRules.validatePartySize(50))
        assertEquals(50, BillSplittingRules.validatePartySize(99)) // Clamped to MAX_PARTY_SIZE
    }

    // -------------------------------------------------------------
    // 4. INTEGRATION WITH AUTHORITATIVE BILLING SUMMARY
    // -------------------------------------------------------------

    @Test
    fun testBillSplitUsesAuthoritativeFinalBillTotalWithTipAndServiceCharge() {
        // Authoritative bill computed via TipAndServiceChargeRules
        val finalBill = TipAndServiceChargeRules.computeFinalBill(
            subtotal = 1200.0,
            tax = 60.0,
            isServiceChargeOptedOut = false, // 5% = 60.0
            tipAmount = 100.0                // Tip = 100.0
        )
        // Total = 1200 + 60 + 60 + 100 = 1420.0
        assertEquals(1420.0, finalBill.total, 0.001)

        // Split among party of 4
        val split4 = BillSplittingRules.splitBill(finalBill.total, partySize = 4)
        assertEquals(355.0, split4.perPersonBaseAmount, 0.001)
        assertEquals(0.0, split4.remainderAmount, 0.001)
        assertTrue(split4.isReconciled())

        // Split among party of 5 (1420 / 5 = 284 each)
        val split5 = BillSplittingRules.splitBill(finalBill.total, partySize = 5)
        assertEquals(284.0, split5.perPersonBaseAmount, 0.001)
        assertEquals(0.0, split5.remainderAmount, 0.001)
        assertTrue(split5.isReconciled())

        // Split among party of 3 (1420 / 3 = 473.33 with 1 paisa remainder)
        val split3 = BillSplittingRules.splitBill(finalBill.total, partySize = 3)
        assertEquals(473.33, split3.perPersonBaseAmount, 0.001)
        assertEquals(0.01, split3.remainderAmount, 0.001)
        assertEquals(473.34, split3.individualShares[0], 0.001)
        assertEquals(473.33, split3.individualShares[1], 0.001)
        assertEquals(473.33, split3.individualShares[2], 0.001)
        assertTrue(split3.isReconciled())
    }

    @Test
    fun testFormatPerPersonShareString() {
        val cleanSplit = BillSplittingRules.splitBill(600.0, partySize = 2)
        assertEquals("₹300.00 / person", BillSplittingRules.formatPerPersonShare(cleanSplit))

        val invalidSplit = BillSplittingRules.splitBill(600.0, partySize = 0)
        assertEquals("₹0.00 / person", BillSplittingRules.formatPerPersonShare(invalidSplit))
    }
}
