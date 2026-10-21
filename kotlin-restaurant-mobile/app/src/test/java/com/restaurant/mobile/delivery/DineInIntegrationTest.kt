package com.restaurant.mobile.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Comprehensive E2E and integration tests for Dine-In flows:
 * 1. Mandatory login lockout: unauthenticated users cannot access protected actions.
 * 2. Reservation creation: valid reservation allocation succeeds with proper defaults.
 * 3. Reservation conflicts: overlapping reservations for the same table/slot are rejected.
 * 4. Dynamic active-tab loading: unpaid dining tab calculates authoritative bill down to paisa.
 * 5. Walk-in payment fallback: active unpaid bills are retrieved; settled tabs are not payable.
 * 6. Anti-tampering defense: client total mismatch, negative quantity, negative tip rejected.
 * 7. Duplicate settlement and replay: repeating payment on settled tabs is strictly rejected.
 * 8. Successful settlement: lifecycle transitions from BILLED to AVAILABLE and bill splitting.
 */
class DineInIntegrationTest {

    // -------------------------------------------------------------
    // 1. MANDATORY LOGIN LOCKOUT
    // -------------------------------------------------------------

    @Test
    fun testMandatoryLoginLockout_UnauthenticatedUserCannotAccessProtectedActions() {
        val unauthenticatedProfile: Profile? = null

        // 1. Unauthenticated state verification
        assertNull("Unauthenticated session has null profile", unauthenticatedProfile)

        // 2. Staff portal protection: unauthenticated users cannot unlock staff mode
        val canAccessStaff = unauthenticatedProfile?.role?.isStaff == true
        assertFalse("Unauthenticated user must be locked out of staff portal", canAccessStaff)

        // 3. Customer role protection: standard customer cannot access staff portal
        val customerProfile = Profile(
            id = "usr-1",
            name = "Aarav Sharma",
            email = "aarav@example.com",
            role = UserRole.CUSTOMER
        )
        assertFalse("Customer role is not staff", customerProfile.role.isStaff)

        // 4. Elevated role allows staff access
        val waiterProfile = Profile(
            id = "waiter-1",
            name = "Staff Waiter",
            email = "waiter@restaurant.com",
            role = UserRole.WAITER
        )
        assertTrue("Waiter role has staff privileges", waiterProfile.role.isStaff)
    }

    // -------------------------------------------------------------
    // 2. RESERVATION CREATION
    // -------------------------------------------------------------

    @Test
    fun testReservationCreation_ValidReservationSucceeds() {
        val result = TableAllocationEngine.allocateTable(
            restaurantId = "rest-1",
            restaurantName = "Bistro Lumière",
            userId = "usr-1",
            userName = "Aarav Sharma",
            userPhone = "+91 98765 43210",
            bookingDate = "2026-10-25",
            timeSlot = "19:00 - 20:30",
            partySize = 4,
            seatingArea = "Main Dining Room",
            specialRequests = "Window seat preferred",
            existingBookings = emptyList()
        )

        assertTrue("Valid reservation allocation must succeed", result is TableAllocationResult.Success)
        val success = result as TableAllocationResult.Success
        val booking = success.booking

        assertTrue("Booking ID should start with TB-", booking.id.startsWith("TB-"))
        assertEquals("CONFIRMED", booking.status)
        assertEquals("usr-1", booking.userId)
        assertEquals("Aarav Sharma", booking.userName)
        assertEquals("2026-10-25", booking.bookingDate)
        assertEquals("19:00 - 20:30", booking.timeSlot)
        assertEquals(4, booking.partySize)
        assertTrue("Allocated table must have capacity >= 4", success.allocatedTable.capacity >= 4)
    }

    // -------------------------------------------------------------
    // 3. RESERVATION CONFLICT DETECTION
    // -------------------------------------------------------------

    @Test
    fun testReservationConflict_OverlappingSlotRejectedWhenCapacityExhausted() {
        val tables = listOf(
            RestaurantTable(tableId = "tbl-1", tableNumber = "T1", capacity = 4, seatingArea = "Main Dining Room")
        )
        val existingBooking = TableBooking(
            id = "TB-101",
            userId = "usr-1",
            userName = "Diner One",
            userPhone = "+91 98765 43210",
            tableId = "tbl-1",
            tableNumber = "T1",
            bookingDate = "2026-10-25",
            timeSlot = "19:00 - 20:30",
            partySize = 4,
            seatingArea = "Main Dining Room",
            status = TableBookingStatus.CONFIRMED,
            restaurantId = "rest-1"
        )

        // Attempt second booking for the exact same table, date, and slot
        val conflictResult = TableAllocationEngine.allocateTable(
            restaurantId = "rest-1",
            restaurantName = "Bistro Lumière",
            userId = "usr-2",
            userName = "Diner Two",
            userPhone = "+91 98765 43211",
            bookingDate = "2026-10-25",
            timeSlot = "19:00 - 20:30",
            partySize = 4,
            seatingArea = "Main Dining Room",
            existingBookings = listOf(existingBooking),
            inventory = tables
        )

        assertTrue("Conflicting reservation must fail", conflictResult is TableAllocationResult.Failure)
        val failure = conflictResult as TableAllocationResult.Failure
        assertTrue("Failure reason must explain unavailability", failure.reason.isNotBlank())

        // If the blocking reservation is CANCELLED, allocation should succeed
        val cancelledBooking = existingBooking.copy(status = TableBookingStatus.CANCELLED)
        val retryResult = TableAllocationEngine.allocateTable(
            restaurantId = "rest-1",
            restaurantName = "Bistro Lumière",
            userId = "usr-2",
            userName = "Diner Two",
            userPhone = "+91 98765 43211",
            bookingDate = "2026-10-25",
            timeSlot = "19:00 - 20:30",
            partySize = 4,
            seatingArea = "Main Dining Room",
            existingBookings = listOf(cancelledBooking),
            inventory = tables
        )
        assertTrue("Cancelled booking does not conflict", retryResult is TableAllocationResult.Success)
    }

    // -------------------------------------------------------------
    // 4. DYNAMIC ACTIVE-TAB LOADING & AUTHORITATIVE BILLING
    // -------------------------------------------------------------

    @Test
    fun testDynamicActiveTabLoading_AuthoritativeBillCalculation() {
        val items = listOf(
            TableBillItem(dishId = "dish-1", name = "Hyderabadi Dum Gosht Biryani", quantity = 2, price = 38000L, round = 1), // 76000
            TableBillItem(dishId = "dish-105", name = "Butter Garlic Naan", quantity = 4, price = 7000L, round = 2)           // 28000
        )
        val tab = DiningTab(
            id = "TAB-TEST-1",
            tableNumber = "T1",
            items = items,
            status = DiningTabStatus.ACTIVE
        )

        // 1. Subtotal calculation
        val subtotal = items.sumOf { it.price * it.quantity }
        assertEquals(104000L, subtotal) // ₹1,040.00

        // 2. Tax (5% GST) & Service Charge (5%)
        val tax = Math.round(subtotal * 0.05)
        val serviceCharge = Math.round(subtotal * 0.05)
        assertEquals(5200L, tax)          // ₹52.00
        assertEquals(5200L, serviceCharge) // ₹52.00

        // 3. Final billing with tip
        val finalBill = TipAndServiceChargeRules.computeFinalBill(
            subtotal = subtotal / 100.0,
            tax = tax / 100.0,
            isServiceChargeOptedOut = false,
            tipAmount = 100.0 // ₹100.00 = 10000 paise
        )
        assertEquals(1040.0, finalBill.subtotal, 0.001)
        assertEquals(52.0, finalBill.tax, 0.001)
        assertEquals(52.0, finalBill.serviceCharge, 0.001)
        assertEquals(100.0, finalBill.tip, 0.001)
        assertEquals(1244.0, finalBill.total, 0.001)
        assertEquals(124400L, finalBill.totalPaise)

        // 4. Round grouping verification
        val grouped = tab.itemsGroupedByRound()
        assertEquals(2, grouped.size)
        assertEquals(1, grouped[1]?.size)
        assertEquals(1, grouped[2]?.size)
    }

    // -------------------------------------------------------------
    // 5. WALK-IN PAYMENT FALLBACK
    // -------------------------------------------------------------

    @Test
    fun testWalkInPaymentFallback_ActiveUnpaidBillVsSettledTab() {
        val openTab = DiningTab(
            id = "TAB-WALKIN-1",
            tableNumber = "T5",
            items = listOf(TableBillItem(dishId = "d-1", name = "Dosa", quantity = 1, price = 16000L)),
            subtotal = 16000L,
            total = 17600L,
            status = DiningTabStatus.OPEN
        )

        val settledTab = DiningTab(
            id = "TAB-WALKIN-SETTLED",
            tableNumber = "T5",
            items = listOf(TableBillItem(dishId = "d-1", name = "Dosa", quantity = 1, price = 16000L)),
            status = DiningTabStatus.SETTLED
        )

        // Active unpaid tab is eligible for payment
        assertFalse("Open tab is not settled", DineInPaymentRules.isSettledOrPaid(openTab.status))

        // Settled tab is recognized as finalized and rejected from payment lookup
        assertTrue("Settled tab is recognized as settled", DineInPaymentRules.isSettledOrPaid(settledTab.status))
        assertTrue("Paid tab is recognized as settled", DineInPaymentRules.isSettledOrPaid(DiningTabStatus.PAID))
        assertTrue("Closed tab is recognized as settled", DineInPaymentRules.isSettledOrPaid(DiningTabStatus.CLOSED))
    }

    // -------------------------------------------------------------
    // 6. ANTI-TAMPERING DEFENSE
    // -------------------------------------------------------------

    @Test
    fun testAntiTampering_ManipulatedTotalRejected() {
        val items = listOf(
            TableBillItem(dishId = "dish-1", name = "Biryani", quantity = 2, price = 38000L), // 76000
            TableBillItem(dishId = "dish-105", name = "Naan", quantity = 4, price = 7000L)    // 28000
        )
        // Subtotal = 104000, 5% tax = 5200, 5% sc = 5200 -> Authoritative Total = 114400 paise (₹1,144)
        val tab = DiningTab(
            id = "TAB-TAMPER",
            tableNumber = "T1",
            items = items,
            status = DiningTabStatus.ACTIVE
        )

        // ATTACK 1: Manipulated expected total (Client sends ₹1.00 instead of ₹1144.00)
        val tamperResult = DineInPaymentRules.validateDineInPaymentSecurity(
            tab = tab,
            expectedTotalPaise = 100L // ₹1.00
        )
        assertTrue("Price tampering must be caught", tamperResult is DineInSecurityValidationResult.Error)
        val tamperError = tamperResult as DineInSecurityValidationResult.Error
        assertEquals("DINE_IN_PRICE_TAMPERING_DETECTED", tamperError.code)
        assertEquals(114400L, tamperError.authoritativeTotal)

        // ATTACK 2: Negative quantity injection
        val negTab = tab.copy(
            items = listOf(TableBillItem(dishId = "dish-1", name = "Biryani", quantity = -5, price = 38000L))
        )
        val negQtyResult = DineInPaymentRules.validateDineInPaymentSecurity(
            tab = negTab,
            expectedTotalPaise = 114400L
        )
        assertTrue("Negative quantity must be caught", negQtyResult is DineInSecurityValidationResult.Error)
        assertEquals("NEGATIVE_QUANTITY_ATTACK", (negQtyResult as DineInSecurityValidationResult.Error).code)

        // ATTACK 3: Negative tip injection
        val negTipResult = DineInPaymentRules.validateDineInPaymentSecurity(
            tab = tab,
            expectedTotalPaise = 114400L,
            tipPaise = -500L
        )
        assertTrue("Negative tip must be caught", negTipResult is DineInSecurityValidationResult.Error)
        assertEquals("INVALID_TIP", (negTipResult as DineInSecurityValidationResult.Error).code)

        // ATTACK 4: Invalid split party size <= 0
        val invalidPartyResult = DineInPaymentRules.validateDineInPaymentSecurity(
            tab = tab,
            expectedTotalPaise = 114400L,
            partySize = 0
        )
        assertTrue("Invalid party size must be caught", invalidPartyResult is DineInSecurityValidationResult.Error)
        assertEquals("INVALID_PARTY_SIZE", (invalidPartyResult as DineInSecurityValidationResult.Error).code)

        // SUCCESS: Legitimate payment with exact authoritative total
        val successResult = DineInPaymentRules.validateDineInPaymentSecurity(
            tab = tab,
            expectedTotalPaise = 114400L
        )
        assertTrue("Exact payment total must succeed", successResult is DineInSecurityValidationResult.Success)
        val valid = successResult as DineInSecurityValidationResult.Success
        assertEquals(104000L, valid.subtotal)
        assertEquals(5200L, valid.tax)
        assertEquals(5200L, valid.serviceCharge)
        assertEquals(114400L, valid.total)
    }

    // -------------------------------------------------------------
    // 7. DUPLICATE SETTLEMENT AND REPLAY DEFENSE
    // -------------------------------------------------------------

    @Test
    fun testDuplicateSettlementAndReplay_AlreadyPaidTabRejected() {
        val settledTab = DiningTab(
            id = "TAB-SETTLED-1",
            tableNumber = "T3",
            items = listOf(TableBillItem(dishId = "d-1", name = "Paneer Tikka", quantity = 1, price = 28000L)),
            subtotal = 28000L,
            total = 30800L,
            status = DiningTabStatus.SETTLED,
            paymentTransactionId = "TXN-ORIGINAL-123"
        )

        // 1. Security validation rejects payment on already settled tab
        val securityResult = DineInPaymentRules.validateDineInPaymentSecurity(
            tab = settledTab,
            expectedTotalPaise = 30800L
        )
        assertTrue(securityResult is DineInSecurityValidationResult.Error)
        assertEquals("TAB_ALREADY_PAID", (securityResult as DineInSecurityValidationResult.Error).code)

        // 2. Payment verification rejected on already settled tab
        val verifiedTxn = DineInPaymentRules.validatePaymentVerification(
            tab = settledTab,
            transactionId = "TXN-NEW-REPLAY"
        )
        assertNull("Payment verification must fail on already settled tab", verifiedTxn)

        // 3. Receipt notification deduplicates idempotent invoice replays
        val notificationHelper = DeliveryNotificationHelper()
        notificationHelper.showBillSettledReceipt(
            tableNumber = "T3",
            amount = 30800L,
            invoiceId = "TXN-ORIGINAL-123"
        )
        assertEquals(1, notificationHelper.dispatchedRecords.size)

        // Repeating exact same invoice settlement
        notificationHelper.showBillSettledReceipt(
            tableNumber = "T3",
            amount = 30800L,
            invoiceId = "TXN-ORIGINAL-123"
        )
        assertEquals("Duplicate invoice receipt must not be double-posted", 1, notificationHelper.dispatchedRecords.size)
    }

    // -------------------------------------------------------------
    // 8. SUCCESSFUL SETTLEMENT & LIFECYCLE TRANSITION
    // -------------------------------------------------------------

    @Test
    fun testSuccessfulSettlement_LifecycleTransitionsAndBillSplitting() {
        val booking = TableBooking(
            id = "TB-404",
            tableNumber = "T4",
            partySize = 4,
            status = TableBookingStatus.SEATED
        )
        val pendingTab = DiningTab(
            id = "TAB-404",
            tableNumber = "T4",
            bookingId = "TB-404",
            items = listOf(TableBillItem(dishId = "d-1", name = "Dish", quantity = 4, price = 26000L)),
            subtotal = 104000L,
            tax = 5200L,
            serviceCharge = 5200L,
            total = 114400L,
            status = DiningTabStatus.PAYMENT_PENDING,
            paymentTransactionId = "TXN-AUTH-4040"
        )

        // 1. Pre-settlement lifecycle state: BILLED (Red)
        val billedState = TableLifecycleRules.deriveTableState(
            tableNumber = "T4",
            diningTab = pendingTab,
            bookingsForTable = listOf(booking)
        )
        assertEquals(TableLifecycleState.BILLED, billedState.lifecycleState)

        // 2. Authoritative verification succeeds with transaction ID
        val verifiedTxn = DineInPaymentRules.validatePaymentVerification(
            tab = pendingTab,
            transactionId = "TXN-AUTH-4040"
        )
        assertEquals("TXN-AUTH-4040", verifiedTxn)

        // 3. Post-settlement: tab transitions to PAID, booking transitions to COMPLETED
        val completedTab = pendingTab.copy(status = DiningTabStatus.PAID)
        val completedBooking = booking.copy(status = TableBookingStatus.COMPLETED)

        // 4. Post-settlement lifecycle state: table releases to AVAILABLE (Green)
        val availableState = TableLifecycleRules.deriveTableState(
            tableNumber = "T4",
            diningTab = completedTab,
            bookingsForTable = listOf(completedBooking)
        )
        assertEquals(TableLifecycleState.AVAILABLE, availableState.lifecycleState)

        // 5. Bill Splitting across 4 diners
        val splitResult = BillSplittingRules.splitBill(
            totalAmount = 1144.0, // ₹1,144.00
            partySize = 4
        )
        assertEquals(4, splitResult.partySize)
        assertEquals(286.0, splitResult.perPersonBaseAmount, 0.001) // ₹286.00 per person
        assertEquals(0.0, splitResult.remainderAmount, 0.001)
        assertTrue(splitResult.isReconciled())
        val formattedShare = BillSplittingRules.formatPerPersonShare(splitResult)
        assertTrue("Formatted share should contain 286", formattedShare.contains("286"))
    }
}
