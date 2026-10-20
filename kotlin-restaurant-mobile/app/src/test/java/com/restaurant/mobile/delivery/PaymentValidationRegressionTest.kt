package com.restaurant.mobile.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Focused regression tests for Phase 4 Commit 6:
 * - Payment success, verification, and table release.
 * - Payment failure and cancellation behavior (reverts to Active/Occupied).
 * - Authoritative bill total consistency across UI, DB, and payment payload.
 * - Duplicate settlement protection.
 * - Table lifecycle transitions and customer app compatibility.
 */
class PaymentValidationRegressionTest {

    // -------------------------------------------------------------
    // 1. PAYMENT INITIATION & AUTHORITATIVE BILL TOTAL
    // -------------------------------------------------------------

    @Test
    fun testPaymentInitiationWithAuthoritativeBillTotalAndTip() {
        val items = listOf(
            TableBillItem("dish-1", "Butter Chicken", 2, 35000L, 70000L), // ₹700
            TableBillItem("dish-2", "Garlic Naan", 4, 6000L, 24000L)      // ₹240
        )
        // Subtotal = ₹940.0, Tax (5%) = ₹47.0, Service Charge (5%) = ₹47.0, Tip = ₹100.0
        val activeTab = DiningTab(
            tableNumber = "T5",
            items = items,
            subtotal = 94000L,
            tax = 4700L,
            serviceCharge = 4700L,
            total = 103400L,
            status = DiningTabStatus.ACTIVE
        )

        val initiatedBill = DineInPaymentRules.validatePaymentInitiation(
            tab = activeTab,
            tip = 100.0,
            isServiceChargeOptedOut = false
        )

        assertNotNull(initiatedBill)
        assertEquals(940.0, initiatedBill!!.subtotal, 0.001)
        assertEquals(47.0, initiatedBill.tax, 0.001)
        assertEquals(47.0, initiatedBill.serviceCharge, 0.001)
        assertEquals(100.0, initiatedBill.tip, 0.001)
        // Total = 940 + 47 + 47 + 100 = 1134.0
        assertEquals(1134.0, initiatedBill.total, 0.001)
        assertEquals(113400L, initiatedBill.totalPaise)

        // Tab transitions to PAYMENT_PENDING
        val billedTab = activeTab.copy(
            status = DiningTabStatus.PAYMENT_PENDING,
            tip = initiatedBill.tipPaise,
            serviceCharge = initiatedBill.serviceChargePaise,
            total = initiatedBill.totalPaise,
            billedAt = Instant.now().toString()
        )

        val billedTableState = TableLifecycleRules.deriveTableState(
            tableNumber = "T5",
            diningTab = billedTab,
            bookingsForTable = emptyList()
        )
        assertEquals(TableLifecycleState.BILLED, billedTableState.lifecycleState)
    }

    @Test
    fun testEmptyOrZeroBillPaymentInitiationIsRejected() {
        val emptyTab = DiningTab(
            tableNumber = "T2",
            items = emptyList(),
            subtotal = 0L,
            tax = 0L,
            total = 0L,
            status = DiningTabStatus.ACTIVE
        )

        val result = DineInPaymentRules.validatePaymentInitiation(
            tab = emptyTab,
            tip = 0.0,
            isServiceChargeOptedOut = false
        )
        assertNull("Payment initiation on an empty tab with zero total must be rejected", result)
    }

    // -------------------------------------------------------------
    // 2. PAYMENT VERIFICATION & AUTHORIZATION REQUIREMENT
    // -------------------------------------------------------------

    @Test
    fun testVerifyPaymentWithoutAuthoritativeVerificationResultFails() {
        // Tab in PAYMENT_PENDING with NO transaction ID
        val pendingTab = DiningTab(
            tableNumber = "T3",
            subtotal = 50000L,
            tax = 2500L,
            serviceCharge = 2500L,
            total = 55000L,
            status = DiningTabStatus.PAYMENT_PENDING,
            paymentTransactionId = ""
        )

        // Attempting verification without an authoritative transaction ID must fail
        val verifiedTxn = DineInPaymentRules.validatePaymentVerification(
            tab = pendingTab,
            transactionId = ""
        )
        assertNull("Verification without transaction ID must be rejected", verifiedTxn)

        // Table must remain BILLED (Red), NOT released to AVAILABLE!
        val tableState = TableLifecycleRules.deriveTableState(
            tableNumber = "T3",
            diningTab = pendingTab,
            bookingsForTable = emptyList()
        )
        assertEquals(TableLifecycleState.BILLED, tableState.lifecycleState)
    }

    @Test
    fun testVerifyPaymentSuccessWithAuthoritativeTransactionIdReleasesTable() {
        val booking = TableBooking(
            id = "B-101",
            tableNumber = "T4",
            partySize = 4,
            status = TableBookingStatus.SEATED
        )
        val pendingTab = DiningTab(
            id = "TAB-404",
            tableNumber = "T4",
            bookingId = "B-101",
            subtotal = 120000L,
            tax = 6000L,
            serviceCharge = 6000L,
            total = 132000L,
            status = DiningTabStatus.PAYMENT_PENDING,
            paymentTransactionId = "TXN-AUTH-998877"
        )

        val verifiedTxn = DineInPaymentRules.validatePaymentVerification(
            tab = pendingTab,
            transactionId = "TXN-AUTH-998877"
        )
        assertEquals("TXN-AUTH-998877", verifiedTxn)

        // After successful payment verification:
        // Tab is PAID, booking is COMPLETED
        val completedTab = pendingTab.copy(
            status = DiningTabStatus.PAID,
            paymentTransactionId = verifiedTxn!!
        )
        val completedBooking = booking.copy(status = TableBookingStatus.COMPLETED)

        // Table lifecycle state must now release to AVAILABLE (Green)
        val releasedState = TableLifecycleRules.deriveTableState(
            tableNumber = "T4",
            diningTab = completedTab,
            bookingsForTable = listOf(completedBooking)
        )
        assertEquals(TableLifecycleState.AVAILABLE, releasedState.lifecycleState)
    }

    // -------------------------------------------------------------
    // 3. PAYMENT FAILURE & CANCELLATION (REVERT TO OCCUPIED)
    // -------------------------------------------------------------

    @Test
    fun testPaymentFailureRevertsTabToActiveAndKeepsTableOccupied() {
        val pendingTab = DiningTab(
            tableNumber = "T6",
            subtotal = 80000L,
            tax = 4000L,
            serviceCharge = 4000L,
            total = 88000L,
            status = DiningTabStatus.PAYMENT_PENDING,
            billedAt = Instant.now().toString(),
            paymentTransactionId = "TXN-INCOMPLETE"
        )

        val revertedTab = DineInPaymentRules.validatePaymentFailureOrCancellation(
            tab = pendingTab,
            reason = "Card declined: Insufficient funds"
        )

        assertNotNull(revertedTab)
        assertEquals(DiningTabStatus.ACTIVE, revertedTab!!.status)
        assertEquals("", revertedTab.billedAt)
        assertEquals("", revertedTab.paymentTransactionId)

        // Verify table state: remains OCCUPIED (Amber), NOT released to AVAILABLE
        val tableState = TableLifecycleRules.deriveTableState(
            tableNumber = "T6",
            diningTab = revertedTab,
            bookingsForTable = emptyList()
        )
        assertEquals(TableLifecycleState.OCCUPIED, tableState.lifecycleState)
    }

    @Test
    fun testPaymentCancellationRevertsTabToActiveAndKeepsTableOccupied() {
        val pendingTab = DiningTab(
            tableNumber = "T8",
            subtotal = 45000L,
            tax = 2250L,
            total = 47250L,
            status = DiningTabStatus.PAYMENT_PENDING,
            billedAt = Instant.now().toString()
        )

        val revertedTab = DineInPaymentRules.validatePaymentFailureOrCancellation(
            tab = pendingTab,
            reason = "Customer cancelled payment checkout"
        )

        assertNotNull(revertedTab)
        assertEquals(DiningTabStatus.ACTIVE, revertedTab!!.status)

        // Bill is not settled and table is not released
        val tableState = TableLifecycleRules.deriveTableState(
            tableNumber = "T8",
            diningTab = revertedTab,
            bookingsForTable = emptyList()
        )
        assertEquals(TableLifecycleState.OCCUPIED, tableState.lifecycleState)
    }

    // -------------------------------------------------------------
    // 4. DUPLICATE SETTLEMENT PROTECTION
    // -------------------------------------------------------------

    @Test
    fun testDuplicateSettlementRejectedForPaidOrSettledTabs() {
        val paidTab = DiningTab(
            tableNumber = "T9",
            subtotal = 60000L,
            tax = 3000L,
            total = 63000L,
            status = DiningTabStatus.PAID,
            paymentTransactionId = "TXN-PAID-123"
        )
        val settledTab = DiningTab(
            tableNumber = "T9",
            subtotal = 60000L,
            tax = 3000L,
            total = 63000L,
            status = DiningTabStatus.SETTLED,
            paymentTransactionId = "TXN-SETTLED-456"
        )
        val closedTab = DiningTab(
            tableNumber = "T9",
            status = DiningTabStatus.CLOSED
        )

        // Verification on already settled/paid tabs must be strictly rejected
        assertNull(DineInPaymentRules.validatePaymentVerification(paidTab, "TXN-NEW"))
        assertNull(DineInPaymentRules.validatePaymentVerification(settledTab, "TXN-NEW"))
        assertNull(DineInPaymentRules.validatePaymentVerification(closedTab, "TXN-NEW"))

        // Re-initiating payment on already settled tabs must be strictly rejected
        assertNull(DineInPaymentRules.validatePaymentInitiation(paidTab, 50.0))
        assertNull(DineInPaymentRules.validatePaymentInitiation(settledTab, 50.0))
        assertNull(DineInPaymentRules.validatePaymentInitiation(closedTab, 50.0))

        // Reverting already settled tabs must be strictly rejected
        assertNull(DineInPaymentRules.validatePaymentFailureOrCancellation(paidTab))
        assertNull(DineInPaymentRules.validatePaymentFailureOrCancellation(settledTab))
        assertNull(DineInPaymentRules.validatePaymentFailureOrCancellation(closedTab))

        // isSettledOrPaid check
        assertTrue(DineInPaymentRules.isSettledOrPaid("PAID"))
        assertTrue(DineInPaymentRules.isSettledOrPaid("SETTLED"))
        assertTrue(DineInPaymentRules.isSettledOrPaid("CLOSED"))
        assertFalse(DineInPaymentRules.isSettledOrPaid("ACTIVE"))
        assertFalse(DineInPaymentRules.isSettledOrPaid("OPEN"))
        assertFalse(DineInPaymentRules.isSettledOrPaid("PAYMENT_PENDING"))
    }

    // -------------------------------------------------------------
    // 5. TOTAL CONSISTENCY & RECONCILIATION
    // -------------------------------------------------------------

    @Test
    fun testAuthoritativeTotalConsistencyBetweenUIModelAndPaymentPayload() {
        val subtotal = 1500.0 // ₹1500.00
        val tax = 75.0        // 5% GST = ₹75.00
        val tip = 150.0       // ₹150.00
        val isOptedOut = false // Service charge = 5% = ₹75.00

        // Authoritative bill calculation (as shown in UI)
        val finalBill = TipAndServiceChargeRules.computeFinalBill(
            subtotal = subtotal,
            tax = tax,
            isServiceChargeOptedOut = isOptedOut,
            tipAmount = tip
        )

        // Total = 1500 + 75 + 75 + 150 = 1800.0 (180000 paise)
        assertEquals(1800.0, finalBill.total, 0.001)
        assertEquals(180000L, finalBill.totalPaise)

        // DiningTab model representation in SQLite
        val tab = DiningTab(
            tableNumber = "T1",
            subtotal = CurrencyConversionRules.rupeesToPaise(subtotal),
            tax = CurrencyConversionRules.rupeesToPaise(tax),
            serviceCharge = finalBill.serviceChargePaise,
            tip = finalBill.tipPaise,
            isServiceChargeOptedOut = isOptedOut,
            total = finalBill.totalPaise,
            status = DiningTabStatus.PAYMENT_PENDING
        )

        assertEquals(1800.0, tab.totalRupees, 0.001)
        assertEquals(180000L, tab.total)

        // Payment Payload check: must be strictly identical down to the paisa
        assertTrue(
            DineInPaymentRules.isPaymentPayloadTotalConsistent(
                authoritativeTotalPaise = finalBill.totalPaise,
                payloadTotalPaise = tab.total
            )
        )
        assertFalse(
            DineInPaymentRules.isPaymentPayloadTotalConsistent(
                authoritativeTotalPaise = finalBill.totalPaise,
                payloadTotalPaise = 179900L // 1 rupee less
            )
        )
    }

    // -------------------------------------------------------------
    // 6. CUSTOMER FLOW & COMPATIBILITY CHECKS
    // -------------------------------------------------------------

    @Test
    fun testCustomerOpenTabCompatibilityWithTableLifecycle() {
        // Customer creates tab with status = "OPEN" (Neil's Phase 3 customer app flow)
        val customerOpenTab = DiningTab(
            tableNumber = "T3",
            customerId = "user-123",
            customerName = "Rohit Verma",
            subtotal = 40000L,
            tax = 2000L,
            total = 42000L,
            status = DiningTabStatus.OPEN
        )

        // deriveTableState must recognize OPEN tabs as OCCUPIED (Amber), not Available!
        val state = TableLifecycleRules.deriveTableState(
            tableNumber = "T3",
            diningTab = customerOpenTab,
            bookingsForTable = emptyList()
        )
        assertEquals(TableLifecycleState.OCCUPIED, state.lifecycleState)

        // When customer settles tab (status becomes SETTLED), table is released to AVAILABLE
        val settledTab = customerOpenTab.copy(
            status = DiningTabStatus.SETTLED,
            paymentMethod = "UPI: rohit@oksbi"
        )
        val settledState = TableLifecycleRules.deriveTableState(
            tableNumber = "T3",
            diningTab = settledTab,
            bookingsForTable = emptyList()
        )
        assertEquals(TableLifecycleState.AVAILABLE, settledState.lifecycleState)
    }

    @Test
    fun testTableLifecycleStateTransitionsFullFlow() {
        val tableNum = "T10"

        // 1. Initially unoccupied: AVAILABLE
        val s1 = TableLifecycleRules.deriveTableState(tableNum, null, emptyList())
        assertEquals(TableLifecycleState.AVAILABLE, s1.lifecycleState)

        // 2. Seated / Tab Opened: OCCUPIED (Amber)
        val openTab = DiningTab(tableNumber = tableNum, status = DiningTabStatus.ACTIVE, subtotal = 50000L, total = 52500L)
        val s2 = TableLifecycleRules.deriveTableState(tableNum, openTab, emptyList())
        assertEquals(TableLifecycleState.OCCUPIED, s2.lifecycleState)

        // 3. Bill Requested: BILLED (Red)
        val billedTab = openTab.copy(status = DiningTabStatus.PAYMENT_PENDING)
        val s3 = TableLifecycleRules.deriveTableState(tableNum, billedTab, emptyList())
        assertEquals(TableLifecycleState.BILLED, s3.lifecycleState)

        // 4. Payment Failed: Reverts to OCCUPIED (Amber)
        val failedTab = DineInPaymentRules.validatePaymentFailureOrCancellation(billedTab, "Card declined")!!
        val s4 = TableLifecycleRules.deriveTableState(tableNum, failedTab, emptyList())
        assertEquals(TableLifecycleState.OCCUPIED, s4.lifecycleState)

        // 5. Bill Re-initiated: BILLED (Red)
        val reBilledTab = failedTab.copy(status = DiningTabStatus.PAYMENT_PENDING, paymentTransactionId = "TXN-SUCCESS-777")
        val s5 = TableLifecycleRules.deriveTableState(tableNum, reBilledTab, emptyList())
        assertEquals(TableLifecycleState.BILLED, s5.lifecycleState)

        // 6. Payment Verified: Settled & Released to AVAILABLE (Green)
        val verifiedTxn = DineInPaymentRules.validatePaymentVerification(reBilledTab, "TXN-SUCCESS-777")!!
        val paidTab = reBilledTab.copy(status = DiningTabStatus.PAID, paymentTransactionId = verifiedTxn)
        val s6 = TableLifecycleRules.deriveTableState(tableNum, paidTab, emptyList())
        assertEquals(TableLifecycleState.AVAILABLE, s6.lifecycleState)
    }
}
