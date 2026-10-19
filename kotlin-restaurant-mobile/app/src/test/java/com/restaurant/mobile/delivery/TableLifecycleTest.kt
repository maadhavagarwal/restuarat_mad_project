package com.restaurant.mobile.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class TableLifecycleTest {

    // -------------------------------------------------------------
    // 1. ALL FOUR TABLE STATES & LIFECYCLE TRANSITIONS
    // -------------------------------------------------------------

    @Test
    fun testTableLifecycleStateEnumPropertiesAndColorHexes() {
        assertEquals("Available", TableLifecycleState.AVAILABLE.label)
        assertEquals(0xFF10B981, TableLifecycleState.AVAILABLE.colorHex) // Green

        assertEquals("Reserved", TableLifecycleState.RESERVED.label)
        assertEquals(0xFF8B5CF6, TableLifecycleState.RESERVED.colorHex) // Purple

        assertEquals("Occupied", TableLifecycleState.OCCUPIED.label)
        assertEquals(0xFFF59E0B, TableLifecycleState.OCCUPIED.colorHex) // Amber

        assertEquals("Billed / Payment Pending", TableLifecycleState.BILLED.label)
        assertEquals(0xFFEF4444, TableLifecycleState.BILLED.colorHex) // Red
    }

    @Test
    fun testAvailableStateWhenTableHasNoActiveReservationOrSession() {
        val state = TableLifecycleRules.deriveTableState(
            tableNumber = "T1",
            diningTab = null,
            bookingsForTable = emptyList()
        )

        assertEquals(TableLifecycleState.AVAILABLE, state.lifecycleState)
        assertNull(state.activeTab)
        assertNull(state.associatedBooking)
        assertNull(state.elapsedDiningTime)
    }

    @Test
    fun testReservedStateWithinThirtyMinutesOfConfirmedBooking() {
        val today = LocalDate.now().toString()
        val booking = TableBooking(
            id = "B-001",
            tableNumber = "T2",
            bookingDate = today,
            timeSlot = "7:00 PM",
            status = TableBookingStatus.CONFIRMED,
            userName = "Maadhav",
            partySize = 4
        )

        val targetDate = LocalDate.parse(today)
        val testNow = LocalDateTime.of(targetDate, LocalTime.of(18, 35)) // 6:35 PM -> 25 min before 7:00 PM

        val state = TableLifecycleRules.deriveTableState(
            tableNumber = "T2",
            diningTab = null,
            bookingsForTable = listOf(booking),
            now = testNow
        )

        assertEquals(TableLifecycleState.RESERVED, state.lifecycleState)
        assertEquals(4, state.guestCount)
        assertEquals("7:00 PM · Maadhav", state.reservationInfo)
        assertEquals("B-001", state.associatedBooking?.id)
    }

    @Test
    fun testTransitionFromReservedToOccupiedWhenGuestsSeated() {
        val today = LocalDate.now().toString()
        val booking = TableBooking(
            id = "B-002",
            tableNumber = "T3",
            bookingDate = today,
            timeSlot = "8:00 PM",
            status = TableBookingStatus.CONFIRMED,
            userName = "Sarah",
            partySize = 3
        )

        val targetDate = LocalDate.parse(today)
        val reservationTime = LocalDateTime.of(targetDate, LocalTime.of(19, 45)) // 15 mins before

        // Step 1: Before seating -> RESERVED (Purple)
        val reservedState = TableLifecycleRules.deriveTableState(
            tableNumber = "T3",
            diningTab = null,
            bookingsForTable = listOf(booking),
            now = reservationTime
        )
        assertEquals(TableLifecycleState.RESERVED, reservedState.lifecycleState)

        // Step 2: Waiter seats guests -> Booking marked SEATED, DiningTab opened
        val seatedBooking = booking.copy(status = TableBookingStatus.SEATED)
        val activeTab = DiningTab(
            id = "TAB-001",
            tableId = "T3",
            tableNumber = "T3",
            bookingId = "B-002",
            customerName = "Sarah",
            status = DiningTabStatus.ACTIVE,
            guestCount = 3,
            seatedAt = Instant.now().minus(10, ChronoUnit.MINUTES).toString()
        )

        val occupiedState = TableLifecycleRules.deriveTableState(
            tableNumber = "T3",
            diningTab = activeTab,
            bookingsForTable = listOf(seatedBooking),
            now = reservationTime.plusMinutes(10)
        )

        assertEquals(TableLifecycleState.OCCUPIED, occupiedState.lifecycleState)
        assertEquals(3, occupiedState.guestCount)
        assertNotNull(occupiedState.elapsedDiningTime)
        assertTrue(occupiedState.elapsedDiningTime!!.contains("Dining"))
    }

    @Test
    fun testWalkInTransitionDirectlyToOccupied() {
        val activeTab = DiningTab(
            id = "TAB-WALKIN",
            tableId = "T4",
            tableNumber = "T4",
            status = DiningTabStatus.ACTIVE,
            guestCount = 2,
            seatedAt = Instant.now().toString()
        )

        val state = TableLifecycleRules.deriveTableState(
            tableNumber = "T4",
            diningTab = activeTab,
            bookingsForTable = emptyList()
        )

        assertEquals(TableLifecycleState.OCCUPIED, state.lifecycleState)
        assertEquals(2, state.guestCount)
        assertEquals("Dining just now", state.elapsedDiningTime)
    }

    @Test
    fun testTransitionFromOccupiedToBilledWhenPaymentInitiated() {
        val activeTab = DiningTab(
            id = "TAB-005",
            tableId = "T5",
            tableNumber = "T5",
            status = DiningTabStatus.ACTIVE,
            guestCount = 5,
            seatedAt = Instant.now().minus(45, ChronoUnit.MINUTES).toString(),
            total = CurrencyConversionRules.rupeesToPaise(1500.0)
        )

        // Initial state -> Occupied
        val occupiedState = TableLifecycleRules.deriveTableState(
            tableNumber = "T5",
            diningTab = activeTab,
            bookingsForTable = emptyList()
        )
        assertEquals(TableLifecycleState.OCCUPIED, occupiedState.lifecycleState)

        // Customer or waiter initiates bill -> status transitions to PAYMENT_PENDING
        val billedTab = activeTab.copy(
            status = DiningTabStatus.PAYMENT_PENDING,
            billedAt = Instant.now().toString(),
            paymentMethod = "Card"
        )

        val billedState = TableLifecycleRules.deriveTableState(
            tableNumber = "T5",
            diningTab = billedTab,
            bookingsForTable = emptyList()
        )

        assertEquals(TableLifecycleState.BILLED, billedState.lifecycleState)
        assertEquals(5, billedState.guestCount)
        assertEquals(1500.0, billedState.activeTab?.totalRupees ?: 0.0, 0.01)
    }

    @Test
    fun testPaymentFailureOrCancellationRevertsToOccupiedAndNeverFreesTable() {
        val billedTab = DiningTab(
            id = "TAB-006",
            tableId = "T6",
            tableNumber = "T6",
            status = DiningTabStatus.PAYMENT_PENDING,
            guestCount = 2,
            seatedAt = Instant.now().minus(30, ChronoUnit.MINUTES).toString(),
            billedAt = Instant.now().toString(),
            paymentMethod = "UPI",
            total = CurrencyConversionRules.rupeesToPaise(850.0)
        )

        val billedState = TableLifecycleRules.deriveTableState(
            tableNumber = "T6",
            diningTab = billedTab,
            bookingsForTable = emptyList()
        )
        assertEquals(TableLifecycleState.BILLED, billedState.lifecycleState)

        // Payment failed or waiter reverted -> status goes back to ACTIVE, NOT FREED!
        val revertedTab = billedTab.copy(
            status = DiningTabStatus.ACTIVE,
            billedAt = "",
            updatedAt = Instant.now().toString()
        )

        val revertedState = TableLifecycleRules.deriveTableState(
            tableNumber = "T6",
            diningTab = revertedTab,
            bookingsForTable = emptyList()
        )

        assertEquals(TableLifecycleState.OCCUPIED, revertedState.lifecycleState)
        assertEquals(2, revertedState.guestCount)
        assertFalse(revertedState.lifecycleState == TableLifecycleState.AVAILABLE)
    }

    @Test
    fun testTableBecomesAvailableOnlyAfterSuccessfulPaymentVerification() {
        val booking = TableBooking(
            id = "B-007",
            tableNumber = "T7",
            status = TableBookingStatus.SEATED,
            partySize = 4
        )
        val billedTab = DiningTab(
            id = "TAB-007",
            tableId = "T7",
            tableNumber = "T7",
            bookingId = "B-007",
            status = DiningTabStatus.PAYMENT_PENDING,
            guestCount = 4,
            total = CurrencyConversionRules.rupeesToPaise(2100.0)
        )

        val inFlightState = TableLifecycleRules.deriveTableState(
            tableNumber = "T7",
            diningTab = billedTab,
            bookingsForTable = listOf(booking)
        )
        assertEquals(TableLifecycleState.BILLED, inFlightState.lifecycleState)

        // After successful payment verification:
        // 1. Tab is marked PAID (so activeDiningTab query returns null)
        // 2. Booking is marked COMPLETED
        val completedBooking = booking.copy(status = TableBookingStatus.COMPLETED)
        val settledState = TableLifecycleRules.deriveTableState(
            tableNumber = "T7",
            diningTab = null, // PAID tabs are not returned by getActiveDiningTabForTable
            bookingsForTable = listOf(completedBooking)
        )

        // Table is now Available again!
        assertEquals(TableLifecycleState.AVAILABLE, settledState.lifecycleState)
    }

    // -------------------------------------------------------------
    // 2. RESERVATION 30-MINUTE WINDOW CALCULATIONS
    // -------------------------------------------------------------

    @Test
    fun testReservationLeadTimeExactBoundaryChecks() {
        val date = "2026-10-25"
        val timeSlot = "7:00 PM"
        val bookingDate = LocalDate.parse(date)

        // Exactly 30 minutes before 7:00 PM -> 6:30 PM: Should be RESERVED
        val exactly30MinBefore = LocalDateTime.of(bookingDate, LocalTime.of(18, 30))
        assertTrue(TableLifecycleRules.isWithinReservationWindow(date, timeSlot, exactly30MinBefore))

        // 31 minutes before 7:00 PM -> 6:29 PM: Should NOT be reserved yet
        val thirtyOneMinBefore = LocalDateTime.of(bookingDate, LocalTime.of(18, 29))
        assertFalse(TableLifecycleRules.isWithinReservationWindow(date, timeSlot, thirtyOneMinBefore))

        // 15 minutes before 7:00 PM -> 6:45 PM: Should be RESERVED
        val fifteenMinBefore = LocalDateTime.of(bookingDate, LocalTime.of(18, 45))
        assertTrue(TableLifecycleRules.isWithinReservationWindow(date, timeSlot, fifteenMinBefore))

        // Right at start time -> 7:00 PM: Should be RESERVED
        val rightAtStartTime = LocalDateTime.of(bookingDate, LocalTime.of(19, 0))
        assertTrue(TableLifecycleRules.isWithinReservationWindow(date, timeSlot, rightAtStartTime))

        // 60 minutes into dining window -> 8:00 PM: Still within 90 min window
        val sixtyMinIn = LocalDateTime.of(bookingDate, LocalTime.of(20, 0))
        assertTrue(TableLifecycleRules.isWithinReservationWindow(date, timeSlot, sixtyMinIn))

        // After 90 minutes -> 8:31 PM: Outside reservation window
        val pastWindowEnd = LocalDateTime.of(bookingDate, LocalTime.of(20, 31))
        assertFalse(TableLifecycleRules.isWithinReservationWindow(date, timeSlot, pastWindowEnd))
    }

    @Test
    fun testReservationDifferentDatesNeverTriggerPrematureReservedStatus() {
        val today = LocalDate.of(2026, 10, 25)
        val now = LocalDateTime.of(today, LocalTime.of(18, 45))

        // Booking tomorrow at 7:00 PM
        assertFalse(TableLifecycleRules.isWithinReservationWindow("2026-10-26", "7:00 PM", now))

        // Booking next week
        assertFalse(TableLifecycleRules.isWithinReservationWindow("2026-11-01", "7:00 PM", now))

        // Booking yesterday at 7:00 PM
        assertFalse(TableLifecycleRules.isWithinReservationWindow("2026-10-24", "7:00 PM", now))
    }

    @Test
    fun testReservationTimeSlotParsingVariants() {
        val date = "2026-10-25"
        val expected7Pm = LocalDateTime.of(2026, 10, 25, 19, 0)
        val expected12Pm = LocalDateTime.of(2026, 10, 25, 12, 0)

        assertEquals(expected7Pm, TableLifecycleRules.parseBookingDateTime(date, "7:00 PM"))
        assertEquals(expected7Pm, TableLifecycleRules.parseBookingDateTime(date, "07:00 PM"))
        assertEquals(expected7Pm, TableLifecycleRules.parseBookingDateTime(date, "7:00PM"))
        assertEquals(expected7Pm, TableLifecycleRules.parseBookingDateTime(date, "19:00"))
        assertEquals(expected7Pm, TableLifecycleRules.parseBookingDateTime(date, "19:00:00"))

        assertEquals(expected12Pm, TableLifecycleRules.parseBookingDateTime(date, "12:00 PM"))
        assertEquals(expected12Pm, TableLifecycleRules.parseBookingDateTime(date, "12:00PM"))
        assertEquals(expected12Pm, TableLifecycleRules.parseBookingDateTime(date, "12:00"))

        // Malformed inputs return null safely without throwing
        assertNull(TableLifecycleRules.parseBookingDateTime(null, "7:00 PM"))
        assertNull(TableLifecycleRules.parseBookingDateTime(date, null))
        assertNull(TableLifecycleRules.parseBookingDateTime("", ""))
        assertNull(TableLifecycleRules.parseBookingDateTime("invalid-date", "7:00 PM"))
        assertNull(TableLifecycleRules.parseBookingDateTime(date, "not-a-time"))
    }

    @Test
    fun testCancelledOrCompletedBookingsDoNotMarkTableReserved() {
        val today = LocalDate.now().toString()
        val targetDate = LocalDate.parse(today)
        val now = LocalDateTime.of(targetDate, LocalTime.of(18, 45))

        val cancelledBooking = TableBooking(
            id = "B-CANCEL",
            tableNumber = "T8",
            bookingDate = today,
            timeSlot = "7:00 PM",
            status = TableBookingStatus.CANCELLED
        )
        val completedBooking = TableBooking(
            id = "B-DONE",
            tableNumber = "T8",
            bookingDate = today,
            timeSlot = "7:00 PM",
            status = TableBookingStatus.COMPLETED
        )

        val state = TableLifecycleRules.deriveTableState(
            tableNumber = "T8",
            diningTab = null,
            bookingsForTable = listOf(cancelledBooking, completedBooking),
            now = now
        )

        assertEquals(TableLifecycleState.AVAILABLE, state.lifecycleState)
    }

    // -------------------------------------------------------------
    // 3. GUEST COUNT & ELAPSED DINING TIMER
    // -------------------------------------------------------------

    @Test
    fun testElapsedDiningTimeFormattingRules() {
        val baseNow = Instant.parse("2026-10-25T20:00:00Z")

        // 30 seconds ago -> "Dining just now"
        val thirtySecAgo = baseNow.minusSeconds(30).toString()
        assertEquals("Dining just now", TableLifecycleRules.formatElapsedDiningTime(thirtySecAgo, baseNow))

        // 5 minutes ago -> "Dining 5m"
        val fiveMinAgo = baseNow.minus(5, ChronoUnit.MINUTES).toString()
        assertEquals("Dining 5m", TableLifecycleRules.formatElapsedDiningTime(fiveMinAgo, baseNow))

        // 45 minutes ago -> "Dining 45m"
        val fortyFiveMinAgo = baseNow.minus(45, ChronoUnit.MINUTES).toString()
        assertEquals("Dining 45m", TableLifecycleRules.formatElapsedDiningTime(fortyFiveMinAgo, baseNow))

        // 60 minutes ago -> "Dining 1h"
        val oneHourAgo = baseNow.minus(60, ChronoUnit.MINUTES).toString()
        assertEquals("Dining 1h", TableLifecycleRules.formatElapsedDiningTime(oneHourAgo, baseNow))

        // 75 minutes ago -> "Dining 1h 15m"
        val oneHourFifteenAgo = baseNow.minus(75, ChronoUnit.MINUTES).toString()
        assertEquals("Dining 1h 15m", TableLifecycleRules.formatElapsedDiningTime(oneHourFifteenAgo, baseNow))

        // 120 minutes ago -> "Dining 2h"
        val twoHoursAgo = baseNow.minus(120, ChronoUnit.MINUTES).toString()
        assertEquals("Dining 2h", TableLifecycleRules.formatElapsedDiningTime(twoHoursAgo, baseNow))

        // Missing or null -> "Dining just now"
        assertEquals("Dining just now", TableLifecycleRules.formatElapsedDiningTime(null, baseNow))
        assertEquals("Dining just now", TableLifecycleRules.formatElapsedDiningTime("", baseNow))

        // Epoch millis support
        val epochMsAgo = (baseNow.toEpochMilli() - (10 * 60 * 1000)).toString()
        assertEquals("Dining 10m", TableLifecycleRules.formatElapsedDiningTime(epochMsAgo, baseNow))

        // Malformed timestamp fallback
        assertEquals("Dining recently", TableLifecycleRules.formatElapsedDiningTime("corrupt-time", baseNow))
    }

    @Test
    fun testGuestCountResolutionFromDiningTabAndBooking() {
        // Tab with explicit guest count
        val tabWithGuests = DiningTab(
            tableId = "T9",
            tableNumber = "T9",
            guestCount = 6,
            status = DiningTabStatus.ACTIVE
        )
        val state1 = TableLifecycleRules.deriveTableState("T9", tabWithGuests, emptyList())
        assertEquals(6, state1.guestCount)

        // Tab without explicit guest count falls back to associated booking
        val booking = TableBooking(
            id = "B-009",
            tableNumber = "T9",
            partySize = 4,
            status = TableBookingStatus.SEATED
        )
        val tabWithoutGuests = DiningTab(
            tableId = "T9",
            tableNumber = "T9",
            bookingId = "B-009",
            guestCount = 0,
            status = DiningTabStatus.ACTIVE
        )
        val state2 = TableLifecycleRules.deriveTableState("T9", tabWithoutGuests, listOf(booking))
        assertEquals(4, state2.guestCount)

        // Seated booking without tab yet opened
        val state3 = TableLifecycleRules.deriveTableState("T9", null, listOf(booking))
        assertEquals(4, state3.guestCount)
        assertEquals(TableLifecycleState.OCCUPIED, state3.lifecycleState)
    }

    // -------------------------------------------------------------
    // 4. DINING TAB STATUSES & LIFECYCLE CONSTANTS
    // -------------------------------------------------------------

    @Test
    fun testDiningTabStatusConstantsIncludePaymentPending() {
        assertEquals("ACTIVE", DiningTabStatus.ACTIVE)
        assertEquals("PAYMENT_PENDING", DiningTabStatus.PAYMENT_PENDING)
        assertEquals("PAID", DiningTabStatus.PAID)
        assertEquals("CLOSED", DiningTabStatus.CLOSED)

        assertTrue(DiningTabStatus.ALL.contains(DiningTabStatus.ACTIVE))
        assertTrue(DiningTabStatus.ALL.contains(DiningTabStatus.PAYMENT_PENDING))
        assertTrue(DiningTabStatus.ALL.contains(DiningTabStatus.PAID))
        assertTrue(DiningTabStatus.ALL.contains(DiningTabStatus.CLOSED))
    }

    @Test
    fun testAppendOrOpenDiningTabInitializesGuestCountAndSeatedAt() {
        val booking = TableBooking(
            id = "B-APPEND",
            tableNumber = "T10",
            partySize = 4,
            userName = "Alex"
        )
        val items = listOf(
            TableBillItem(
                menuItemId = 1L,
                name = "Spring Rolls",
                price = 120.0,
                quantity = 2
            )
        )

        val newTab = WaiterTablePairingRules.appendOrOpenDiningTab(
            existingTab = null,
            tableNumber = "T10",
            tableId = "T10",
            newItems = items,
            associatedBooking = booking
        )

        assertEquals("T10", newTab.tableNumber)
        assertEquals(DiningTabStatus.ACTIVE, newTab.status)
        assertEquals(4, newTab.guestCount)
        assertTrue(newTab.seatedAt.isNotBlank())
        assertEquals(240.0, newTab.subtotalRupees, 0.01)

        // Appending subsequent round preserves guestCount and seatedAt
        val itemsRound2 = listOf(
            TableBillItem(
                menuItemId = 2L,
                name = "Fried Rice",
                price = 200.0,
                quantity = 1,
                round = 2
            )
        )
        val updatedTab = WaiterTablePairingRules.appendOrOpenDiningTab(
            existingTab = newTab,
            tableNumber = "T10",
            tableId = "T10",
            newItems = itemsRound2,
            associatedBooking = booking
        )

        assertEquals(4, updatedTab.guestCount)
        assertEquals(newTab.seatedAt, updatedTab.seatedAt)
        assertEquals(2, updatedTab.items.size)
        assertEquals(440.0, updatedTab.subtotalRupees, 0.01)
    }

    // -------------------------------------------------------------
    // 5. SQLITE UPGRADE VERSION & TABLE DEFINITIONS
    // -------------------------------------------------------------

    @Test
    fun testSqliteDatabaseVersionAndTableConstants() {
        assertEquals(8, RestroSqlDatabase.DATABASE_VERSION)
        assertEquals("restro_delivery.db", RestroSqlDatabase.DATABASE_NAME)
        assertEquals("dining_tabs", RestroSqlDatabase.TABLE_DINING_TABS)
        assertEquals("table_bookings", RestroSqlDatabase.TABLE_BOOKINGS)
        assertEquals("kot_tickets", RestroSqlDatabase.TABLE_KOT_TICKETS)
    }
}
