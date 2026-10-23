package com.restaurant.mobile.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TipAndServiceChargeTest {

    // -------------------------------------------------------------
    // 1. TIP OPTIONS & PRESET AMOUNTS
    // -------------------------------------------------------------

    @Test
    fun testTipOptionEnumLabelsAndValues() {
        val options = TipAndServiceChargeRules.TipOption.values()
        assertEquals(5, options.size)

        assertEquals("No Tip", TipAndServiceChargeRules.TipOption.NO_TIP.label)
        assertEquals(0.0, TipAndServiceChargeRules.TipOption.NO_TIP.amount)

        assertEquals("₹50", TipAndServiceChargeRules.TipOption.TIP_50.label)
        assertEquals(50.0, TipAndServiceChargeRules.TipOption.TIP_50.amount)

        assertEquals("₹100", TipAndServiceChargeRules.TipOption.TIP_100.label)
        assertEquals(100.0, TipAndServiceChargeRules.TipOption.TIP_100.amount)

        assertEquals("₹200", TipAndServiceChargeRules.TipOption.TIP_200.label)
        assertEquals(200.0, TipAndServiceChargeRules.TipOption.TIP_200.amount)

        assertEquals("Custom", TipAndServiceChargeRules.TipOption.CUSTOM.label)
        assertNull(TipAndServiceChargeRules.TipOption.CUSTOM.amount)
    }

    @Test
    fun testResolvePresetTipAmounts() {
        assertEquals(0.0, TipAndServiceChargeRules.resolveTipAmount(TipAndServiceChargeRules.TipOption.NO_TIP), 0.001)
        assertEquals(50.0, TipAndServiceChargeRules.resolveTipAmount(TipAndServiceChargeRules.TipOption.TIP_50), 0.001)
        assertEquals(100.0, TipAndServiceChargeRules.resolveTipAmount(TipAndServiceChargeRules.TipOption.TIP_100), 0.001)
        assertEquals(200.0, TipAndServiceChargeRules.resolveTipAmount(TipAndServiceChargeRules.TipOption.TIP_200), 0.001)
    }

    // -------------------------------------------------------------
    // 2. CUSTOM TIP VALIDATION & RESOLUTION
    // -------------------------------------------------------------

    @Test
    fun testValidateCustomTipValidInputs() {
        assertEquals(150.0, TipAndServiceChargeRules.validateCustomTip("150") ?: 0.0, 0.001)
        assertEquals(75.50, TipAndServiceChargeRules.validateCustomTip("75.50") ?: 0.0, 0.001)
        assertEquals(0.0, TipAndServiceChargeRules.validateCustomTip("0") ?: -1.0, 0.001)
        assertEquals(500.0, TipAndServiceChargeRules.validateCustomTip(" 500  ") ?: 0.0, 0.001)
    }

    @Test
    fun testValidateCustomTipRejectsNegativeAndInvalidValues() {
        // Negative values must be rejected
        assertNull(TipAndServiceChargeRules.validateCustomTip("-50"))
        assertNull(TipAndServiceChargeRules.validateCustomTip("-0.01"))
        assertNull(TipAndServiceChargeRules.validateCustomTip("-1000"))

        // Blank and null inputs
        assertNull(TipAndServiceChargeRules.validateCustomTip(""))
        assertNull(TipAndServiceChargeRules.validateCustomTip("   "))
        assertNull(TipAndServiceChargeRules.validateCustomTip(null))

        // Non-numeric garbage
        assertNull(TipAndServiceChargeRules.validateCustomTip("abc"))
        assertNull(TipAndServiceChargeRules.validateCustomTip("₹50"))
        assertNull(TipAndServiceChargeRules.validateCustomTip("50rs"))
        assertNull(TipAndServiceChargeRules.validateCustomTip("12.34.56"))
        assertNull(TipAndServiceChargeRules.validateCustomTip("NaN"))
        assertNull(TipAndServiceChargeRules.validateCustomTip("Infinity"))
        assertNull(TipAndServiceChargeRules.validateCustomTip("-Infinity"))
    }

    @Test
    fun testResolveCustomTipAmountWithValidAndInvalidInputs() {
        // Valid custom tip
        assertEquals(120.0, TipAndServiceChargeRules.resolveTipAmount(TipAndServiceChargeRules.TipOption.CUSTOM, "120"), 0.001)
        assertEquals(45.5, TipAndServiceChargeRules.resolveTipAmount(TipAndServiceChargeRules.TipOption.CUSTOM, "45.50"), 0.001)

        // Invalid custom tip safely resolves to 0.0
        assertEquals(0.0, TipAndServiceChargeRules.resolveTipAmount(TipAndServiceChargeRules.TipOption.CUSTOM, "-50"), 0.001)
        assertEquals(0.0, TipAndServiceChargeRules.resolveTipAmount(TipAndServiceChargeRules.TipOption.CUSTOM, "invalid"), 0.001)
        assertEquals(0.0, TipAndServiceChargeRules.resolveTipAmount(TipAndServiceChargeRules.TipOption.CUSTOM, ""), 0.001)
    }

    // -------------------------------------------------------------
    // 3. SERVICE-CHARGE CALCULATION & OPT-OUT
    // -------------------------------------------------------------

    @Test
    fun testServiceChargeCalculationDefaultFivePercent() {
        // 5% of 1000 = 50
        assertEquals(50.0, TipAndServiceChargeRules.computeServiceCharge(1000.0, isOptedOut = false), 0.001)

        // 5% of 500 = 25
        assertEquals(25.0, TipAndServiceChargeRules.computeServiceCharge(500.0, isOptedOut = false), 0.001)

        // 5% of 250 = 12.5
        assertEquals(12.5, TipAndServiceChargeRules.computeServiceCharge(250.0, isOptedOut = false), 0.001)

        // 0 subtotal
        assertEquals(0.0, TipAndServiceChargeRules.computeServiceCharge(0.0, isOptedOut = false), 0.001)
    }

    @Test
    fun testServiceChargeOptOutSetsChargeToZero() {
        // Opted out must strictly return 0.0 regardless of subtotal
        assertEquals(0.0, TipAndServiceChargeRules.computeServiceCharge(1000.0, isOptedOut = true), 0.001)
        assertEquals(0.0, TipAndServiceChargeRules.computeServiceCharge(500.0, isOptedOut = true), 0.001)
        assertEquals(0.0, TipAndServiceChargeRules.computeServiceCharge(2500.0, isOptedOut = true), 0.001)
    }

    // -------------------------------------------------------------
    // 4. IMMEDIATE BILL BREAKDOWN & TOTAL CALCULATION
    // -------------------------------------------------------------

    @Test
    fun testComputeFinalBillWithPresetTipAndServiceCharge() {
        val subtotal = 1000.0
        val tax = 50.0 // 5% GST
        val tip = 100.0 // ₹100 tip
        val isOptedOut = false

        val bill = TipAndServiceChargeRules.computeFinalBill(
            subtotal = subtotal,
            tax = tax,
            isServiceChargeOptedOut = isOptedOut,
            tipAmount = tip
        )

        assertEquals(1000.0, bill.subtotal, 0.001)
        assertEquals(50.0, bill.tax, 0.001)
        assertEquals(50.0, bill.serviceCharge, 0.001) // 5% of 1000
        assertEquals(100.0, bill.tip, 0.001)
        assertFalse(bill.isServiceChargeOptedOut)
        // Total = 1000 + 50 + 50 + 100 = 1200
        assertEquals(1200.0, bill.total, 0.001)
    }

    @Test
    fun testComputeFinalBillWithServiceChargeOptedOut() {
        val subtotal = 1000.0
        val tax = 50.0
        val tip = 100.0
        val isOptedOut = true

        val bill = TipAndServiceChargeRules.computeFinalBill(
            subtotal = subtotal,
            tax = tax,
            isServiceChargeOptedOut = isOptedOut,
            tipAmount = tip
        )

        assertEquals(1000.0, bill.subtotal, 0.001)
        assertEquals(50.0, bill.tax, 0.001)
        assertEquals(0.0, bill.serviceCharge, 0.001) // Opted out -> 0
        assertEquals(100.0, bill.tip, 0.001)
        assertTrue(bill.isServiceChargeOptedOut)
        // Total = 1000 + 50 + 0 + 100 = 1150
        assertEquals(1150.0, bill.total, 0.001)
    }

    @Test
    fun testComputeFinalBillWithCustomTipAndNoServiceCharge() {
        val subtotal = 800.0
        val tax = 40.0
        val customTip = 75.0
        val isOptedOut = true

        val bill = TipAndServiceChargeRules.computeFinalBill(
            subtotal = subtotal,
            tax = tax,
            isServiceChargeOptedOut = isOptedOut,
            tipAmount = customTip
        )

        assertEquals(800.0, bill.subtotal, 0.001)
        assertEquals(40.0, bill.tax, 0.001)
        assertEquals(0.0, bill.serviceCharge, 0.001)
        assertEquals(75.0, bill.tip, 0.001)
        // Total = 800 + 40 + 0 + 75 = 915
        assertEquals(915.0, bill.total, 0.001)
    }

    @Test
    fun testComputeFinalBillWithNoTipAndNoServiceCharge() {
        val subtotal = 600.0
        val tax = 30.0
        val tip = 0.0
        val isOptedOut = true

        val bill = TipAndServiceChargeRules.computeFinalBill(
            subtotal = subtotal,
            tax = tax,
            isServiceChargeOptedOut = isOptedOut,
            tipAmount = tip
        )

        assertEquals(600.0, bill.subtotal, 0.001)
        assertEquals(30.0, bill.tax, 0.001)
        assertEquals(0.0, bill.serviceCharge, 0.001)
        assertEquals(0.0, bill.tip, 0.001)
        assertEquals(630.0, bill.total, 0.001)
    }

    // -------------------------------------------------------------
    // 5. DATA MODEL CONSISTENCY & DINING TAB BILLING
    // -------------------------------------------------------------

    @Test
    fun testDiningTabCalculateBillingWithTipAndOptOut() {
        val items = listOf(
            TableBillItem(menuItemId = 1L, name = "Burger", price = 250.0, quantity = 2, round = 1), // 500 subtotal, 25 tax
            TableBillItem(menuItemId = 2L, name = "Fries", price = 150.0, quantity = 2, round = 1)   // 300 subtotal, 15 tax
        )
        // Subtotal = 800, Tax = 40

        // Case A: Default 5% Service Charge (40.0) + ₹50 Tip
        val summaryA = DiningTab.calculateBilling(
            items = items,
            existingServiceCharge = 40.0,
            isServiceChargeOptedOut = false,
            tip = 50.0
        )
        assertEquals(800.0, summaryA.subtotal, 0.001)
        assertEquals(40.0, summaryA.tax, 0.001)
        assertEquals(40.0, summaryA.serviceCharge, 0.001) // 5% of 800
        assertEquals(50.0, summaryA.tip, 0.001)
        assertFalse(summaryA.isServiceChargeOptedOut)
        assertEquals(930.0, summaryA.total, 0.001) // 800 + 40 + 40 + 50

        // Case B: Opted out Service Charge + ₹200 Tip
        val summaryB = DiningTab.calculateBilling(
            items = items,
            isServiceChargeOptedOut = true,
            tip = 200.0
        )
        assertEquals(800.0, summaryB.subtotal, 0.001)
        assertEquals(40.0, summaryB.tax, 0.001)
        assertEquals(0.0, summaryB.serviceCharge, 0.001)
        assertEquals(200.0, summaryB.tip, 0.001)
        assertTrue(summaryB.isServiceChargeOptedOut)
        assertEquals(1040.0, summaryB.total, 0.001) // 800 + 40 + 0 + 200

        // Consistency: TipAndServiceChargeRules matches DiningTab.calculateBilling
        val ruleSummary = TipAndServiceChargeRules.computeFinalBill(
            subtotal = summaryB.subtotal,
            tax = summaryB.tax,
            isServiceChargeOptedOut = true,
            tipAmount = 200.0
        )
        assertEquals(summaryB.total, ruleSummary.total, 0.001)
        assertEquals(summaryB.serviceCharge, ruleSummary.serviceCharge, 0.001)
        assertEquals(summaryB.tip, ruleSummary.tip, 0.001)
    }

    @Test
    fun testAppendOrOpenDiningTabPreservesTipAndOptOut() {
        val initialItems = listOf(TableBillItem(menuItemId = 1L, name = "Pizza", price = 500.0, quantity = 1, round = 1))
        val tab = WaiterTablePairingRules.appendOrOpenDiningTab(
            existingTab = null,
            tableNumber = "T4",
            tableId = "T4",
            newItems = initialItems
        )
        // Fresh tab before payment customization has service charge = 0.0, tip = 0.0
        assertEquals(500.0, tab.subtotalRupees, 0.001)
        assertEquals(25.0, tab.taxRupees, 0.001)
        assertEquals(0.0, tab.serviceChargeRupees, 0.001)
        assertEquals(0.0, tab.tipRupees, 0.001)
        assertFalse(tab.isServiceChargeOptedOut)
        assertEquals(525.0, tab.totalRupees, 0.001)

        // Case 1: User customizes tip to ₹100 with service charge opted out
        val optedOutTab = tab.copy(
            isServiceChargeOptedOut = true,
            serviceCharge = 0L,
            tip = CurrencyConversionRules.rupeesToPaise(100.0),
            total = CurrencyConversionRules.rupeesToPaise(500.0 + 25.0 + 0.0 + 100.0)
        )

        // Subsequent order in round 2 should preserve the opt-out and tip settings
        val round2Items = listOf(TableBillItem(menuItemId = 2L, name = "Coke", price = 50.0, quantity = 2, round = 2))
        val appendedTab = WaiterTablePairingRules.appendOrOpenDiningTab(
            existingTab = optedOutTab,
            tableNumber = "T4",
            tableId = "T4",
            newItems = round2Items
        )

        // Total subtotal = 500 + 100 = 600, Tax = 25 + 5 = 30
        assertEquals(600.0, appendedTab.subtotalRupees, 0.001)
        assertEquals(30.0, appendedTab.taxRupees, 0.001)
        assertEquals(0.0, appendedTab.serviceChargeRupees, 0.001) // Preserved opt-out
        assertEquals(100.0, appendedTab.tipRupees, 0.001) // Preserved tip
        assertTrue(appendedTab.isServiceChargeOptedOut)
        assertEquals(730.0, appendedTab.totalRupees, 0.001) // 600 + 30 + 0 + 100

        // Case 2: User sets 5% service charge (30.0) and ₹50 tip
        val billedTab = tab.copy(
            isServiceChargeOptedOut = false,
            serviceCharge = CurrencyConversionRules.rupeesToPaise(25.0),
            tip = CurrencyConversionRules.rupeesToPaise(50.0),
            total = CurrencyConversionRules.rupeesToPaise(500.0 + 25.0 + 25.0 + 50.0)
        )
        val appendedBilledTab = WaiterTablePairingRules.appendOrOpenDiningTab(
            existingTab = billedTab,
            tableNumber = "T4",
            tableId = "T4",
            newItems = round2Items
        )
        assertEquals(600.0, appendedBilledTab.subtotalRupees, 0.001)
        assertEquals(30.0, appendedBilledTab.taxRupees, 0.001)
        assertEquals(25.0, appendedBilledTab.serviceChargeRupees, 0.001) // Preserved existing service charge
        assertEquals(50.0, appendedBilledTab.tipRupees, 0.001) // Preserved tip
        assertFalse(appendedBilledTab.isServiceChargeOptedOut)
        assertEquals(705.0, appendedBilledTab.totalRupees, 0.001) // 600 + 30 + 25 + 50
    }

    @Test
    fun testDatabaseVersionBumpForTipsAndServiceCharge() {
        // Must be version 9 with tip and is_service_charge_opted_out schema columns
        assertTrue(RestroSqlDatabase.DATABASE_VERSION >= 9)
    }
}
