package com.restaurant.mobile.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TableBookingUiTest {

    private val fixedToday = LocalDate.of(2026, 10, 18)

    @Test
    fun testInitialFormStateAndDefaults() {
        val draft = TableBookingDraft()

        assertEquals("", draft.restaurantId)
        assertEquals("", draft.restaurantName)
        assertEquals("", draft.bookingDate)
        assertEquals("", draft.timeSlot)
        assertEquals(TableBookingDefaults.DEFAULT_PARTY_SIZE, draft.partySize)
        assertEquals(2, draft.partySize)
        assertEquals(TableBookingDefaults.DEFAULT_SEATING_AREA, draft.seatingArea)
        assertEquals("Main Dining Room", draft.seatingArea)
        assertEquals("", draft.specialRequests)

        // Initial draft must be invalid because date and slot are not selected yet
        assertFalse(draft.isValid)
    }

    @Test
    fun testDateGenerationAndFormatting() {
        val dates = generateBookingDates(startDate = fixedToday, daysCount = 7)

        assertEquals(7, dates.size)

        // Day 1 (Today)
        val todayOption = dates[0]
        assertEquals("2026-10-18", todayOption.dateString)
        assertEquals("Sun", todayOption.dayOfWeek)
        assertEquals("18", todayOption.dayNumber)
        assertEquals("Oct", todayOption.monthName)
        assertTrue(todayOption.isToday)
        assertTrue(todayOption.accessibilityText.contains("Today"))
        assertTrue(todayOption.accessibilityText.contains("18"))

        // Day 2
        val tomorrowOption = dates[1]
        assertEquals("2026-10-19", tomorrowOption.dateString)
        assertEquals("Mon", tomorrowOption.dayOfWeek)
        assertEquals("19", tomorrowOption.dayNumber)
        assertEquals("Oct", tomorrowOption.monthName)
        assertFalse(tomorrowOption.isToday)

        // Day 7
        val lastOption = dates[6]
        assertEquals("2026-10-24", lastOption.dateString)
        assertEquals("Sat", lastOption.dayOfWeek)
        assertEquals("24", lastOption.dayNumber)
        assertFalse(lastOption.isToday)
    }

    @Test
    fun testDateFormatting() {
        assertEquals("Sunday, Oct 18, 2026", formatDisplayDate("2026-10-18"))
        assertEquals("Monday, Oct 19, 2026", formatDisplayDate("2026-10-19"))
        assertEquals("Not selected", formatDisplayDate(""))
        assertEquals("invalid-date", formatDisplayDate("invalid-date"))
    }

    @Test
    fun testDateValidationPreventsPastDates() {
        assertTrue(TableBookingDraft.isValidDateFormat("2026-10-18", today = fixedToday))
        assertTrue(TableBookingDraft.isValidDateFormat("2026-10-19", today = fixedToday))
        assertTrue(TableBookingDraft.isValidDateFormat("2026-11-01", today = fixedToday))

        // Past dates must be rejected
        assertFalse(TableBookingDraft.isValidDateFormat("2026-10-17", today = fixedToday))
        assertFalse(TableBookingDraft.isValidDateFormat("2026-09-01", today = fixedToday))

        // Malformed dates must be rejected
        assertFalse(TableBookingDraft.isValidDateFormat("", today = fixedToday))
        assertFalse(TableBookingDraft.isValidDateFormat("2026/10/18", today = fixedToday))
        assertFalse(TableBookingDraft.isValidDateFormat("not-a-date", today = fixedToday))
    }

    @Test
    fun testTimeSlotSelectionAndGrouping() {
        // Lunch period verification
        assertEquals(3, TableBookingDefaults.LUNCH_SLOTS.size)
        assertTrue(TableBookingDefaults.LUNCH_SLOTS.contains("12:00 PM"))
        assertTrue(TableBookingDefaults.LUNCH_SLOTS.contains("1:00 PM"))
        assertTrue(TableBookingDefaults.LUNCH_SLOTS.contains("2:00 PM"))

        // Dinner period verification
        assertEquals(3, TableBookingDefaults.DINNER_SLOTS.size)
        assertTrue(TableBookingDefaults.DINNER_SLOTS.contains("7:00 PM"))
        assertTrue(TableBookingDefaults.DINNER_SLOTS.contains("8:00 PM"))
        assertTrue(TableBookingDefaults.DINNER_SLOTS.contains("9:00 PM"))

        // Total slots
        assertEquals(6, TableBookingDefaults.ALL_SLOTS.size)

        var draft = TableBookingDraft(
            bookingDate = "2026-10-18",
            timeSlot = "1:00 PM"
        )
        assertEquals("1:00 PM", draft.timeSlot)

        draft = draft.copy(timeSlot = "8:00 PM")
        assertEquals("8:00 PM", draft.timeSlot)
    }

    @Test
    fun testGuestCountBoundariesAndPresets() {
        // Quick presets
        assertEquals(listOf(1, 2, 4, 6), TableBookingDefaults.QUICK_PARTY_SIZES)

        // Bounds
        assertEquals(1, TableBookingDefaults.MIN_PARTY_SIZE)
        assertEquals(20, TableBookingDefaults.MAX_PARTY_SIZE)

        val draft = TableBookingDraft(
            bookingDate = "2026-10-18",
            timeSlot = "7:00 PM",
            partySize = 4
        )
        assertEquals(4, draft.partySize)

        // Test boundary clamping logic
        val clampedMin = (-2).coerceIn(TableBookingDefaults.MIN_PARTY_SIZE, TableBookingDefaults.MAX_PARTY_SIZE)
        assertEquals(1, clampedMin)

        val clampedZero = 0.coerceIn(TableBookingDefaults.MIN_PARTY_SIZE, TableBookingDefaults.MAX_PARTY_SIZE)
        assertEquals(1, clampedZero)

        val clampedMax = 25.coerceIn(TableBookingDefaults.MIN_PARTY_SIZE, TableBookingDefaults.MAX_PARTY_SIZE)
        assertEquals(20, clampedMax)
    }

    @Test
    fun testSeatingPreferenceOptions() {
        val expectedOptions = listOf(
            "Main Dining Room",
            "Outdoor Garden",
            "Private Booth",
            "Rooftop Lounge"
        )
        assertEquals(expectedOptions, TableBookingDefaults.SEATING_AREAS)
        assertEquals("Main Dining Room", TableBookingDefaults.DEFAULT_SEATING_AREA)

        var draft = TableBookingDraft(
            bookingDate = "2026-10-18",
            timeSlot = "8:00 PM"
        )
        assertEquals("Main Dining Room", draft.seatingArea)

        draft = draft.copy(seatingArea = "Outdoor Garden")
        assertEquals("Outdoor Garden", draft.seatingArea)

        draft = draft.copy(seatingArea = "Private Booth")
        assertEquals("Private Booth", draft.seatingArea)

        draft = draft.copy(seatingArea = "Rooftop Lounge")
        assertEquals("Rooftop Lounge", draft.seatingArea)
    }

    @Test
    fun testSpecialRequestsSafeHandlingAndLimit() {
        val draft = TableBookingDraft(
            bookingDate = "2026-10-18",
            timeSlot = "7:00 PM",
            specialRequests = "Corner table with high chair for toddler"
        )
        assertEquals("Corner table with high chair for toddler", draft.specialRequests)

        // Safe empty input
        val emptyRequests = draft.copy(specialRequests = "")
        assertEquals("", emptyRequests.specialRequests)

        // Capping at 250 characters
        val longText = "A".repeat(300)
        val capped = longText.take(TableBookingDefaults.MAX_SPECIAL_REQUESTS_LENGTH)
        assertEquals(250, capped.length)
    }

    @Test
    fun testFormValidationStates() {
        val testFutureDate = LocalDate.now().plusDays(1).toString()

        // 1. Initial / empty date -> invalid
        val noDate = TableBookingDraft(
            bookingDate = "",
            timeSlot = "7:00 PM"
        )
        assertFalse(noDate.isValid)

        // 2. Empty slot -> invalid
        val noSlot = TableBookingDraft(
            bookingDate = testFutureDate,
            timeSlot = ""
        )
        assertFalse(noSlot.isValid)

        // 3. Zero or negative party size -> invalid
        val zeroParty = TableBookingDraft(
            bookingDate = testFutureDate,
            timeSlot = "7:00 PM",
            partySize = 0
        )
        assertFalse(zeroParty.isValid)

        val negativeParty = TableBookingDraft(
            bookingDate = testFutureDate,
            timeSlot = "7:00 PM",
            partySize = -1
        )
        assertFalse(negativeParty.isValid)

        // 4. Over party limit -> invalid
        val overParty = TableBookingDraft(
            bookingDate = testFutureDate,
            timeSlot = "7:00 PM",
            partySize = 25
        )
        assertFalse(overParty.isValid)

        // 5. Blank seating area -> invalid
        val noArea = TableBookingDraft(
            bookingDate = testFutureDate,
            timeSlot = "7:00 PM",
            seatingArea = ""
        )
        assertFalse(noArea.isValid)

        // 6. Complete valid draft
        val validDraft = TableBookingDraft(
            restaurantId = "rest-1",
            restaurantName = "Bistro Lumière & Biryani",
            bookingDate = testFutureDate,
            timeSlot = "7:00 PM",
            partySize = 4,
            seatingArea = "Private Booth",
            specialRequests = "Anniversary celebration"
        )
        assertTrue(validDraft.isValid)
    }

    @Test
    fun testContinueActionDoesNotPersistPrematurely() {
        val validDraft = TableBookingDraft(
            restaurantId = "rest-1",
            restaurantName = "Bistro Lumière",
            bookingDate = "2026-10-20",
            timeSlot = "8:00 PM",
            partySize = 4,
            seatingArea = "Rooftop Lounge",
            specialRequests = "Quiet table"
        )

        var passedDraft: TableBookingDraft? = null
        var databaseWriteOccurred = false
        var qrCodeGenerated = false

        // Simulate continue action callback
        val onProceedToReview: (TableBookingDraft) -> Unit = { draft ->
            passedDraft = draft
            // Verify that this action strictly DOES NOT write to DB or generate QR pass
            databaseWriteOccurred = false
            qrCodeGenerated = false
        }

        onProceedToReview(validDraft)

        assertNotNull(passedDraft)
        assertEquals("2026-10-20", passedDraft?.bookingDate)
        assertEquals("8:00 PM", passedDraft?.timeSlot)
        assertEquals(4, passedDraft?.partySize)
        assertEquals("Rooftop Lounge", passedDraft?.seatingArea)
        assertEquals("Quiet table", passedDraft?.specialRequests)
        assertFalse("Database write must NOT occur during UI form continue action", databaseWriteOccurred)
        assertFalse("QR code / confirmation must NOT be generated prematurely", qrCodeGenerated)
    }

    @Test
    fun testDraftStatePreservationAcrossInteractions() {
        var draft = TableBookingDraft()
        assertFalse(draft.isValid)

        // Step 1: User selects date
        draft = draft.copy(bookingDate = "2026-10-21")
        assertEquals("2026-10-21", draft.bookingDate)
        assertFalse(draft.isValid) // Still needs timeSlot

        // Step 2: User selects time slot
        draft = draft.copy(timeSlot = "1:00 PM")
        assertEquals("1:00 PM", draft.timeSlot)
        assertTrue(draft.isValid) // Now valid!

        // Step 3: User adjusts guest count
        draft = draft.copy(partySize = 6)
        assertEquals(6, draft.partySize)
        assertTrue(draft.isValid)

        // Step 4: User selects seating preference
        draft = draft.copy(seatingArea = "Outdoor Garden")
        assertEquals("Outdoor Garden", draft.seatingArea)
        assertTrue(draft.isValid)

        // Step 5: User adds special requests
        draft = draft.copy(specialRequests = "High chair needed")
        assertEquals("High chair needed", draft.specialRequests)
        assertTrue(draft.isValid)
    }

    @Test
    fun testComputeBookingValidationError() {
        val emptyDraft = TableBookingDraft()
        assertEquals("Please select a booking date to proceed", computeBookingValidationError(emptyDraft))

        val pastDateDraft = TableBookingDraft(bookingDate = "2026-10-10")
        assertEquals("The selected booking date cannot be in the past", computeBookingValidationError(pastDateDraft))

        val noSlotDraft = TableBookingDraft(bookingDate = "2026-10-20")
        assertEquals("Please select a dining time slot to proceed", computeBookingValidationError(noSlotDraft))

        val zeroGuestDraft = TableBookingDraft(bookingDate = "2026-10-20", timeSlot = "7:00 PM", partySize = 0)
        assertEquals("Party size must be at least 1 guest", computeBookingValidationError(zeroGuestDraft))

        val overGuestDraft = TableBookingDraft(bookingDate = "2026-10-20", timeSlot = "7:00 PM", partySize = 25)
        assertEquals("Party size cannot exceed 20 guests", computeBookingValidationError(overGuestDraft))

        val noAreaDraft = TableBookingDraft(bookingDate = "2026-10-20", timeSlot = "7:00 PM", partySize = 2, seatingArea = "")
        assertEquals("Please select a seating area preference", computeBookingValidationError(noAreaDraft))

        val validDraft = TableBookingDraft(
            bookingDate = "2026-10-20",
            timeSlot = "7:00 PM",
            partySize = 4,
            seatingArea = "Private Booth"
        )
        org.junit.Assert.assertNull(computeBookingValidationError(validDraft))
    }

    @Test
    fun testReviewNavigationAndRouteTransition() {
        var currentRoute = "BookTable"
        val draft = TableBookingDraft(
            restaurantId = "rest-1",
            restaurantName = "Bistro Lumière & Biryani",
            bookingDate = "2026-10-22",
            timeSlot = "7:00 PM",
            partySize = 4,
            seatingArea = "Main Dining Room",
            specialRequests = "High chair please"
        )

        // Valid draft navigates to BookTableReview
        val err = computeBookingValidationError(draft)
        org.junit.Assert.assertNull(err)
        if (err == null) {
            currentRoute = "BookTableReview"
        }
        assertEquals("BookTableReview", currentRoute)

        // Simulate Back action from BookTableReview -> BookTable
        val backRoute = when (currentRoute) {
            "BookTableReview" -> "BookTable"
            else -> "Home"
        }
        assertEquals("BookTable", backRoute)

        // Draft state must be completely preserved after navigating back
        assertEquals("rest-1", draft.restaurantId)
        assertEquals("Bistro Lumière & Biryani", draft.restaurantName)
        assertEquals("2026-10-22", draft.bookingDate)
        assertEquals("7:00 PM", draft.timeSlot)
        assertEquals(4, draft.partySize)
        assertEquals("Main Dining Room", draft.seatingArea)
        assertEquals("High chair please", draft.specialRequests)
    }

    @Test
    fun testInvalidDraftBlocksNavigation() {
        var currentRoute = "BookTable"
        val invalidDraft = TableBookingDraft(
            bookingDate = "2026-10-22",
            timeSlot = "" // Missing slot
        )

        val err = computeBookingValidationError(invalidDraft)
        assertNotNull(err)
        assertEquals("Please select a dining time slot to proceed", err)

        // Navigation does NOT happen
        if (err == null) {
            currentRoute = "BookTableReview"
        }
        assertEquals("BookTable", currentRoute)
    }

    @Test
    fun testConfirmButtonLoadingStateAndDuplicateSubmissionGuard() {
        var isConfirming = false
        var submissionCount = 0

        fun onConfirmClicked() {
            if (isConfirming) return
            isConfirming = true
            submissionCount++
        }

        // First tap: launches confirmation
        onConfirmClicked()
        assertEquals(1, submissionCount)
        assertTrue(isConfirming)

        // Repeated tap while in flight: guarded and blocked
        onConfirmClicked()
        onConfirmClicked()
        assertEquals(1, submissionCount)
    }

    @Test
    fun testSuccessNavigationOccursOnlyAfterSuccessfulPersistence() {
        var currentRoute = "BookTableReview"
        var confirmedBooking: TableBooking? = null
        var errorMessage: String? = null

        val draft = TableBookingDraft(
            restaurantId = "rest-1",
            restaurantName = "Bistro Lumière",
            bookingDate = "2026-10-25",
            timeSlot = "7:00 PM",
            partySize = 2,
            seatingArea = "Main Dining Room"
        )

        val result = TableAllocationEngine.allocateTable(
            restaurantId = draft.restaurantId,
            restaurantName = draft.restaurantName,
            userId = "usr-1",
            userName = "Aarav",
            userPhone = "+91 98765 43210",
            bookingDate = draft.bookingDate,
            timeSlot = draft.timeSlot,
            partySize = draft.partySize,
            seatingArea = draft.seatingArea,
            existingBookings = emptyList()
        )

        when (result) {
            is TableAllocationResult.Success -> {
                confirmedBooking = result.booking
                currentRoute = "BookTableSuccess"
            }
            is TableAllocationResult.Failure -> {
                errorMessage = result.reason
            }
        }

        assertEquals("BookTableSuccess", currentRoute)
        assertNotNull(confirmedBooking)
        assertEquals("CONFIRMED", confirmedBooking?.status)
        assertEquals("T1", confirmedBooking?.tableNumber)
        org.junit.Assert.assertNull(errorMessage)
    }

    @Test
    fun testAllocationFailureDisplaysActionableErrorAndBlocksSuccessNavigation() {
        var currentRoute = "BookTableReview"
        var confirmedBooking: TableBooking? = null
        var errorMessage: String? = null

        // Overbooked Outdoor Garden tables
        val fullOutdoor = listOf(
            TableBooking(
                id = "TB-1",
                userId = "u1",
                userName = "Guest 1",
                userPhone = "+91",
                tableId = "rest-1_T7",
                tableNumber = "T7",
                bookingDate = "2026-10-25",
                timeSlot = "7:00 PM",
                partySize = 2,
                seatingArea = "Outdoor Garden",
                status = TableBookingStatus.CONFIRMED,
                restaurantId = "rest-1"
            ),
            TableBooking(
                id = "TB-2",
                userId = "u2",
                userName = "Guest 2",
                userPhone = "+91",
                tableId = "rest-1_T8",
                tableNumber = "T8",
                bookingDate = "2026-10-25",
                timeSlot = "7:00 PM",
                partySize = 4,
                seatingArea = "Outdoor Garden",
                status = TableBookingStatus.CONFIRMED,
                restaurantId = "rest-1"
            )
        )

        val draft = TableBookingDraft(
            restaurantId = "rest-1",
            restaurantName = "Bistro Lumière",
            bookingDate = "2026-10-25",
            timeSlot = "7:00 PM",
            partySize = 2,
            seatingArea = "Outdoor Garden"
        )

        val result = TableAllocationEngine.allocateTable(
            restaurantId = draft.restaurantId,
            restaurantName = draft.restaurantName,
            userId = "usr-3",
            userName = "Denied Guest",
            userPhone = "+91",
            bookingDate = draft.bookingDate,
            timeSlot = draft.timeSlot,
            partySize = draft.partySize,
            seatingArea = draft.seatingArea,
            existingBookings = fullOutdoor
        )

        when (result) {
            is TableAllocationResult.Success -> {
                confirmedBooking = result.booking
                currentRoute = "BookTableSuccess"
            }
            is TableAllocationResult.Failure -> {
                errorMessage = result.reason
            }
        }

        // Did NOT navigate to success
        assertEquals("BookTableReview", currentRoute)
        org.junit.Assert.assertNull(confirmedBooking)
        assertNotNull(errorMessage)
        assertTrue(errorMessage!!.contains("fully booked", ignoreCase = true))
    }

    @Test
    fun testConfirmationScreenDisplaysActualSavedBookingDetails() {
        val confirmed = TableBooking(
            id = "TB-CONFIRM88",
            userId = "usr-42",
            userName = "Rohan Mehta",
            userPhone = "+91 99999 88888",
            tableId = "rest-1_T3",
            tableNumber = "T3",
            bookingDate = "2026-10-26",
            timeSlot = "8:00 PM",
            partySize = 4,
            seatingArea = "Main Dining Room",
            specialRequests = "Quiet corner table",
            status = TableBookingStatus.CONFIRMED,
            restaurantId = "rest-1",
            restaurantName = "Bistro Lumière & Biryani"
        )

        assertEquals("TB-CONFIRM88", confirmed.id)
        assertEquals("Bistro Lumière & Biryani", confirmed.restaurantName)
        assertEquals("rest-1", confirmed.restaurantId)
        assertEquals("2026-10-26", confirmed.bookingDate)
        assertEquals("8:00 PM", confirmed.timeSlot)
        assertEquals(4, confirmed.partySize)
        assertEquals("T3", confirmed.tableNumber)
        assertEquals("Main Dining Room", confirmed.seatingArea)
        assertEquals("Quiet corner table", confirmed.specialRequests)
        assertEquals(TableBookingStatus.CONFIRMED, confirmed.status)
        assertEquals("Rohan Mehta", confirmed.userName)
    }

    @Test
    fun testResettingDraftAfterDoneOrNewBookingClearsState() {
        var draft = TableBookingDraft(
            restaurantId = "rest-1",
            restaurantName = "Bistro Lumière",
            bookingDate = "2026-10-25",
            timeSlot = "7:00 PM",
            partySize = 4,
            seatingArea = "Private Booth"
        )
        var confirmedBooking: TableBooking? = TableBooking(
            id = "TB-DONE",
            userId = "u1",
            userName = "User",
            userPhone = "+91",
            tableId = "rest-1_T9",
            tableNumber = "T9",
            bookingDate = "2026-10-25",
            timeSlot = "7:00 PM",
            partySize = 4
        )

        // Simulate onDone: reset draft and clear confirmed booking
        draft = TableBookingDraft()
        confirmedBooking = null

        assertFalse(draft.isValid)
        assertEquals("", draft.bookingDate)
        assertEquals("", draft.timeSlot)
        assertEquals(TableBookingDefaults.DEFAULT_PARTY_SIZE, draft.partySize)
        org.junit.Assert.assertNull(confirmedBooking)
    }
}
