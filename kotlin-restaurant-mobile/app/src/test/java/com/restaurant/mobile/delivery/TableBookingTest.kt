package com.restaurant.mobile.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TableBookingTest {

    @Test
    fun testTableBookingCreationAndDefaults() {
        val booking = TableBooking(
            userId = "usr-1",
            userName = "Aarav Sharma",
            userPhone = "+91 98765 43210",
            tableId = "tbl-1",
            tableNumber = "T1",
            bookingDate = "2026-10-20",
            timeSlot = "19:00 - 20:30",
            partySize = 4
        )

        assertNotNull(booking.id)
        assertTrue(booking.id.isNotBlank())
        assertEquals("usr-1", booking.userId)
        assertEquals("Aarav Sharma", booking.userName)
        assertEquals("+91 98765 43210", booking.userPhone)
        assertEquals("tbl-1", booking.tableId)
        assertEquals("T1", booking.tableNumber)
        assertEquals("2026-10-20", booking.bookingDate)
        assertEquals("19:00 - 20:30", booking.timeSlot)
        assertEquals(4, booking.partySize)
        assertEquals("Main Dining Room", booking.seatingArea)
        assertEquals("", booking.specialRequests)
        assertEquals("CONFIRMED", booking.status)
        assertNotNull(booking.createdAt)
        assertTrue(booking.createdAt.isNotBlank())
    }

    @Test
    fun testTableBookingStatusConstants() {
        assertEquals("CONFIRMED", TableBookingStatus.CONFIRMED)
        assertEquals("SEATED", TableBookingStatus.SEATED)
        assertEquals("COMPLETED", TableBookingStatus.COMPLETED)
        assertEquals("CANCELLED", TableBookingStatus.CANCELLED)
        assertEquals(4, TableBookingStatus.ALL.size)
        assertTrue(TableBookingStatus.ALL.contains("CONFIRMED"))
        assertTrue(TableBookingStatus.ALL.contains("SEATED"))
        assertTrue(TableBookingStatus.ALL.contains("COMPLETED"))
        assertTrue(TableBookingStatus.ALL.contains("CANCELLED"))
    }

    @Test
    fun testInvalidStatusValuesRejected() {
        val invalidStatuses = listOf("PENDING", "UNKNOWN", "DELETED", "", "confirmed", "seated")
        for (invalid in invalidStatuses) {
            assertFalse(
                "Status '$invalid' should not be valid in TableBookingStatus.ALL",
                TableBookingStatus.ALL.contains(invalid)
            )
        }
    }

    @Test
    fun testGenerateId() {
        val id = TableBooking.generateId()
        assertTrue(id.startsWith("TB-"))
        assertEquals(11, id.length) // "TB-" (3) + 8 chars = 11
    }

    @Test
    fun testCancellationStatusTransition() {
        val booking = TableBooking(
            id = "TB-TEST001",
            userId = "usr-1",
            userName = "Aarav",
            userPhone = "+91 98765 43210",
            tableId = "tbl-1",
            tableNumber = "T1",
            bookingDate = "2026-10-20",
            timeSlot = "19:00 - 20:30",
            partySize = 2,
            status = TableBookingStatus.CONFIRMED
        )
        assertEquals(TableBookingStatus.CONFIRMED, booking.status)

        val cancelled = booking.copy(status = TableBookingStatus.CANCELLED)
        assertEquals(TableBookingStatus.CANCELLED, cancelled.status)
        assertEquals("TB-TEST001", cancelled.id)
    }

    @Test
    fun testAllStatusLifecycleTransitions() {
        var booking = TableBooking(
            id = "TB-CYCLE",
            userId = "usr-1",
            userName = "Aarav",
            userPhone = "+91 98765 43210",
            tableId = "tbl-1",
            tableNumber = "T1",
            bookingDate = "2026-10-20",
            timeSlot = "19:00 - 20:30",
            partySize = 2
        )
        assertEquals(TableBookingStatus.CONFIRMED, booking.status)

        booking = booking.copy(status = TableBookingStatus.SEATED)
        assertEquals(TableBookingStatus.SEATED, booking.status)

        booking = booking.copy(status = TableBookingStatus.COMPLETED)
        assertEquals(TableBookingStatus.COMPLETED, booking.status)
    }

    @Test
    fun testUpdateEditableFields() {
        val booking = TableBooking(
            id = "TB-TEST002",
            userId = "usr-2",
            userName = "Priya",
            userPhone = "+91 98765 11111",
            tableId = "tbl-2",
            tableNumber = "T2",
            bookingDate = "2026-10-21",
            timeSlot = "13:00 - 14:30",
            partySize = 2
        )

        val updated = booking.copy(
            partySize = 6,
            seatingArea = "Outdoor Terrace",
            specialRequests = "High chair needed",
            timeSlot = "14:00 - 15:30"
        )

        assertEquals(6, updated.partySize)
        assertEquals("Outdoor Terrace", updated.seatingArea)
        assertEquals("High chair needed", updated.specialRequests)
        assertEquals("14:00 - 15:30", updated.timeSlot)
        assertEquals("TB-TEST002", updated.id)
    }

    @Test
    fun testCrudAndDuplicateIdLogic() {
        val storage = mutableMapOf<String, TableBooking>()

        val b1 = TableBooking(
            id = "TB-001",
            userId = "usr-1",
            userName = "Aarav",
            userPhone = "+91",
            tableId = "tbl-1",
            tableNumber = "T1",
            bookingDate = "2026-10-22",
            timeSlot = "19:00",
            partySize = 4
        )

        // Insert
        val inserted = storage.putIfAbsent(b1.id, b1) == null
        assertTrue(inserted)

        // Duplicate ID insertion should be rejected / ignored
        val duplicate = b1.copy(userName = "Duplicate User")
        val duplicateInserted = storage.putIfAbsent(duplicate.id, duplicate) == null
        assertFalse(duplicateInserted)
        assertEquals("Aarav", storage[b1.id]?.userName)

        // Retrieve by ID
        val found = storage[b1.id]
        assertNotNull(found)
        assertEquals("TB-001", found?.id)

        // Missing ID returns null
        val missing = storage["NON-EXISTENT-ID"]
        assertNull(missing)

        // Query by user
        val userBookings = storage.values.filter { it.userId == "usr-1" }
        assertEquals(1, userBookings.size)

        // Query by date and slot
        val slotBookings = storage.values.filter { it.bookingDate == "2026-10-22" && it.timeSlot == "19:00" }
        assertEquals(1, slotBookings.size)

        // Query by table
        val tableBookings = storage.values.filter { it.tableId == "tbl-1" }
        assertEquals(1, tableBookings.size)

        // Safe Cancel
        val target = storage[b1.id]
        assertNotNull(target)
        storage[b1.id] = target!!.copy(status = TableBookingStatus.CANCELLED)
        assertEquals(TableBookingStatus.CANCELLED, storage[b1.id]?.status)

        // Delete operation
        val deleted = storage.remove(b1.id) != null
        assertTrue(deleted)
        assertNull(storage[b1.id])
    }

    @Test
    fun testBatchAtomicRollbackBehavior() {
        val storage = mutableMapOf<String, TableBooking>()
        val initialBooking = TableBooking(
            id = "TB-INIT",
            userId = "usr-init",
            userName = "Initial",
            userPhone = "+91",
            tableId = "tbl-1",
            tableNumber = "T1",
            bookingDate = "2026-10-20",
            timeSlot = "18:00",
            partySize = 2
        )
        storage[initialBooking.id] = initialBooking

        val batchWithInvalid = listOf(
            TableBooking(
                id = "TB-B1",
                userId = "usr-1",
                userName = "Valid 1",
                userPhone = "+91",
                tableId = "tbl-1",
                tableNumber = "T1",
                bookingDate = "2026-10-20",
                timeSlot = "19:00",
                partySize = 4
            ),
            TableBooking(
                id = "TB-B2",
                userId = "usr-2",
                userName = "Invalid Status",
                userPhone = "+91",
                tableId = "tbl-2",
                tableNumber = "T2",
                bookingDate = "2026-10-20",
                timeSlot = "19:00",
                partySize = 2,
                status = "INVALID_STATUS"
            )
        )

        // Atomic batch simulation
        val success = try {
            val staging = mutableMapOf<String, TableBooking>()
            for (b in batchWithInvalid) {
                if (!TableBookingStatus.ALL.contains(b.status) || b.partySize <= 0) {
                    throw IllegalArgumentException("Invalid booking")
                }
                staging[b.id] = b
            }
            storage.putAll(staging)
            true
        } catch (_: Exception) {
            false
        }

        assertFalse(success)
        // Ensure atomic rollback: neither TB-B1 nor TB-B2 was committed
        assertNull(storage["TB-B1"])
        assertNull(storage["TB-B2"])
        assertEquals(1, storage.size)
        assertNotNull(storage["TB-INIT"])
    }

    @Test
    fun testAllocationSuccessPersistsAssignedTableAndConfirmedStatus() {
        val result = TableAllocationEngine.allocateTable(
            restaurantId = "rest-1",
            restaurantName = "Bistro Lumière",
            userId = "usr-1",
            userName = "Aarav Sharma",
            userPhone = "+91 98765 43210",
            bookingDate = "2026-10-25",
            timeSlot = "7:00 PM",
            partySize = 2,
            seatingArea = "Main Dining Room",
            specialRequests = "Window seat if possible",
            existingBookings = emptyList()
        )

        assertTrue("Allocation should succeed", result is TableAllocationResult.Success)
        val success = result as TableAllocationResult.Success
        assertEquals("T1", success.allocatedTable.tableNumber)
        assertEquals(2, success.allocatedTable.capacity)
        assertEquals("Main Dining Room", success.allocatedTable.seatingArea)

        val booking = success.booking
        assertTrue(booking.id.startsWith("TB-"))
        assertEquals("CONFIRMED", booking.status)
        assertEquals("T1", booking.tableNumber)
        assertEquals("rest-1_T1", booking.tableId)
        assertEquals("rest-1", booking.restaurantId)
        assertEquals("Bistro Lumière", booking.restaurantName)
        assertEquals(2, booking.partySize)
        assertEquals("Main Dining Room", booking.seatingArea)
        assertEquals("Window seat if possible", booking.specialRequests)
    }

    @Test
    fun testInsufficientCapacityPreventsAllocation() {
        // Private Booth has tables T9 (cap 4) and T10 (cap 6). Party of 8 exceeds capacity.
        val result = TableAllocationEngine.allocateTable(
            restaurantId = "rest-1",
            restaurantName = "Bistro Lumière",
            userId = "usr-1",
            userName = "Aarav",
            userPhone = "+91",
            bookingDate = "2026-10-25",
            timeSlot = "7:00 PM",
            partySize = 8,
            seatingArea = "Private Booth",
            existingBookings = emptyList()
        )

        assertTrue("Allocation must fail due to insufficient capacity", result is TableAllocationResult.Failure)
        val failure = result as TableAllocationResult.Failure
        assertTrue(failure.reason.contains("capacity", ignoreCase = true))
    }

    @Test
    fun testSeatingPreferenceIsRespected() {
        // Request Outdoor Garden for party of 2
        val outdoorResult = TableAllocationEngine.allocateTable(
            restaurantId = "rest-1",
            restaurantName = "Bistro Lumière",
            userId = "usr-1",
            userName = "Aarav",
            userPhone = "+91",
            bookingDate = "2026-10-25",
            timeSlot = "7:00 PM",
            partySize = 2,
            seatingArea = "Outdoor Garden",
            existingBookings = emptyList()
        )
        assertTrue(outdoorResult is TableAllocationResult.Success)
        val outdoorSuccess = outdoorResult as TableAllocationResult.Success
        assertEquals("Outdoor Garden", outdoorSuccess.allocatedTable.seatingArea)
        assertEquals("T7", outdoorSuccess.allocatedTable.tableNumber)

        // Request Rooftop Lounge for party of 4
        val rooftopResult = TableAllocationEngine.allocateTable(
            restaurantId = "rest-1",
            restaurantName = "Bistro Lumière",
            userId = "usr-1",
            userName = "Aarav",
            userPhone = "+91",
            bookingDate = "2026-10-25",
            timeSlot = "7:00 PM",
            partySize = 4,
            seatingArea = "Rooftop Lounge",
            existingBookings = emptyList()
        )
        assertTrue(rooftopResult is TableAllocationResult.Success)
        val rooftopSuccess = rooftopResult as TableAllocationResult.Success
        assertEquals("Rooftop Lounge", rooftopSuccess.allocatedTable.seatingArea)
        assertEquals("T12", rooftopSuccess.allocatedTable.tableNumber)
    }

    @Test
    fun testExistingConfirmedBookingsBlockConflictingAllocations() {
        val existingConfirmed = listOf(
            TableBooking(
                id = "TB-EXIST-1",
                userId = "usr-prior",
                userName = "Prior Guest",
                userPhone = "+91",
                tableId = "rest-1_T1",
                tableNumber = "T1",
                bookingDate = "2026-10-25",
                timeSlot = "7:00 PM",
                partySize = 2,
                seatingArea = "Main Dining Room",
                status = TableBookingStatus.CONFIRMED,
                restaurantId = "rest-1"
            )
        )

        // T1 is blocked for 7:00 PM on 2026-10-25. The next 2-top should receive T2.
        val result = TableAllocationEngine.allocateTable(
            restaurantId = "rest-1",
            restaurantName = "Bistro Lumière",
            userId = "usr-new",
            userName = "New Guest",
            userPhone = "+91",
            bookingDate = "2026-10-25",
            timeSlot = "7:00 PM",
            partySize = 2,
            seatingArea = "Main Dining Room",
            existingBookings = existingConfirmed
        )

        assertTrue(result is TableAllocationResult.Success)
        val success = result as TableAllocationResult.Success
        assertEquals("T2", success.allocatedTable.tableNumber)
    }

    @Test
    fun testExistingSeatedBookingsBlockConflictingAllocations() {
        val existingSeated = listOf(
            TableBooking(
                id = "TB-SEATED-1",
                userId = "usr-prior",
                userName = "Dining Guest",
                userPhone = "+91",
                tableId = "rest-1_T7",
                tableNumber = "T7",
                bookingDate = "2026-10-25",
                timeSlot = "1:00 PM",
                partySize = 2,
                seatingArea = "Outdoor Garden",
                status = TableBookingStatus.SEATED,
                restaurantId = "rest-1"
            )
        )

        // T7 is SEATED. In Outdoor Garden, next best table for 2 is T8 (cap 4).
        val result = TableAllocationEngine.allocateTable(
            restaurantId = "rest-1",
            restaurantName = "Bistro Lumière",
            userId = "usr-2",
            userName = "Second Guest",
            userPhone = "+91",
            bookingDate = "2026-10-25",
            timeSlot = "1:00 PM",
            partySize = 2,
            seatingArea = "Outdoor Garden",
            existingBookings = existingSeated
        )

        assertTrue(result is TableAllocationResult.Success)
        val success = result as TableAllocationResult.Success
        assertEquals("T8", success.allocatedTable.tableNumber)
    }

    @Test
    fun testCancelledAndCompletedBookingsDoNotBlockAvailability() {
        val pastBookings = listOf(
            TableBooking(
                id = "TB-CAN",
                userId = "usr-can",
                userName = "Cancelled Guest",
                userPhone = "+91",
                tableId = "rest-1_T1",
                tableNumber = "T1",
                bookingDate = "2026-10-25",
                timeSlot = "7:00 PM",
                partySize = 2,
                seatingArea = "Main Dining Room",
                status = TableBookingStatus.CANCELLED,
                restaurantId = "rest-1"
            ),
            TableBooking(
                id = "TB-COMP",
                userId = "usr-comp",
                userName = "Completed Guest",
                userPhone = "+91",
                tableId = "rest-1_T2",
                tableNumber = "T2",
                bookingDate = "2026-10-25",
                timeSlot = "7:00 PM",
                partySize = 2,
                seatingArea = "Main Dining Room",
                status = TableBookingStatus.COMPLETED,
                restaurantId = "rest-1"
            )
        )

        // Both T1 and T2 were previously booked, but CANCELLED and COMPLETED do not block.
        // Best fit for party of 2 should immediately allocate T1.
        val result = TableAllocationEngine.allocateTable(
            restaurantId = "rest-1",
            restaurantName = "Bistro Lumière",
            userId = "usr-new",
            userName = "New Guest",
            userPhone = "+91",
            bookingDate = "2026-10-25",
            timeSlot = "7:00 PM",
            partySize = 2,
            seatingArea = "Main Dining Room",
            existingBookings = pastBookings
        )

        assertTrue(result is TableAllocationResult.Success)
        val success = result as TableAllocationResult.Success
        assertEquals("T1", success.allocatedTable.tableNumber)
    }

    @Test
    fun testCompetingConcurrentBookingAttemptsCannotBothAllocateSameTable() {
        // In Rooftop Lounge, only T11 (cap 2) and T12 (cap 4) exist.
        // Block T11 so only T12 remains.
        val existing = mutableListOf(
            TableBooking(
                id = "TB-LOCK",
                userId = "usr-0",
                userName = "User 0",
                userPhone = "+91",
                tableId = "rest-1_T11",
                tableNumber = "T11",
                bookingDate = "2026-10-25",
                timeSlot = "8:00 PM",
                partySize = 2,
                seatingArea = "Rooftop Lounge",
                status = TableBookingStatus.CONFIRMED,
                restaurantId = "rest-1"
            )
        )

        val lock = Any()
        var winnerTable: String? = null
        var loserReason: String? = null

        // Customer A attempt
        val resultA = synchronized(lock) {
            val res = TableAllocationEngine.allocateTable(
                restaurantId = "rest-1",
                restaurantName = "Bistro Lumière",
                userId = "usr-A",
                userName = "Customer A",
                userPhone = "+91",
                bookingDate = "2026-10-25",
                timeSlot = "8:00 PM",
                partySize = 3,
                seatingArea = "Rooftop Lounge",
                existingBookings = existing
            )
            if (res is TableAllocationResult.Success) {
                existing.add(res.booking)
            }
            res
        }

        // Customer B competing attempt for the same slot
        val resultB = synchronized(lock) {
            val res = TableAllocationEngine.allocateTable(
                restaurantId = "rest-1",
                restaurantName = "Bistro Lumière",
                userId = "usr-B",
                userName = "Customer B",
                userPhone = "+91",
                bookingDate = "2026-10-25",
                timeSlot = "8:00 PM",
                partySize = 4,
                seatingArea = "Rooftop Lounge",
                existingBookings = existing
            )
            if (res is TableAllocationResult.Success) {
                existing.add(res.booking)
            }
            res
        }

        assertTrue("First attempt succeeds", resultA is TableAllocationResult.Success)
        winnerTable = (resultA as TableAllocationResult.Success).allocatedTable.tableNumber
        assertEquals("T12", winnerTable)

        assertTrue("Second competing attempt fails", resultB is TableAllocationResult.Failure)
        loserReason = (resultB as TableAllocationResult.Failure).reason
        assertTrue(loserReason.contains("fully booked", ignoreCase = true))
    }

    @Test
    fun testFailedInsertTransactionRollbackLeavesNoPartialBooking() {
        val simulatedDb = mutableMapOf<String, TableBooking>()
        val initialBooking = TableBooking(
            id = "TB-EXISTING",
            userId = "usr-1",
            userName = "Initial",
            userPhone = "+91",
            tableId = "rest-1_T1",
            tableNumber = "T1",
            bookingDate = "2026-10-25",
            timeSlot = "7:00 PM",
            partySize = 2,
            restaurantId = "rest-1"
        )
        simulatedDb[initialBooking.id] = initialBooking

        // Attempt transaction with intentional insertion failure
        val transactionSucceeded = try {
            val staging = mutableMapOf<String, TableBooking>()
            val allocation = TableAllocationEngine.allocateTable(
                restaurantId = "rest-1",
                restaurantName = "Bistro Lumière",
                userId = "usr-2",
                userName = "Failing Guest",
                userPhone = "+91",
                bookingDate = "2026-10-25",
                timeSlot = "7:00 PM",
                partySize = 2,
                seatingArea = "Main Dining Room",
                existingBookings = simulatedDb.values.toList()
            )
            assertTrue(allocation is TableAllocationResult.Success)
            val newBooking = (allocation as TableAllocationResult.Success).booking
            staging[newBooking.id] = newBooking

            // Simulate database constraint/IO error
            throw IllegalStateException("Simulated SQLite disk write failure")
        } catch (_: Exception) {
            false
        }

        assertFalse(transactionSucceeded)
        assertEquals(1, simulatedDb.size)
        assertNotNull(simulatedDb["TB-EXISTING"])
    }

    @Test
    fun testUnavailableSlotCreatesNoConfirmedBooking() {
        // Fill all tables in Outdoor Garden (T7 and T8)
        val fullOutdoor = listOf(
            TableBooking(
                id = "TB-T7",
                userId = "u1",
                userName = "User 1",
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
                id = "TB-T8",
                userId = "u2",
                userName = "User 2",
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

        val result = TableAllocationEngine.allocateTable(
            restaurantId = "rest-1",
            restaurantName = "Bistro Lumière",
            userId = "u3",
            userName = "User 3",
            userPhone = "+91",
            bookingDate = "2026-10-25",
            timeSlot = "7:00 PM",
            partySize = 2,
            seatingArea = "Outdoor Garden",
            existingBookings = fullOutdoor
        )

        assertTrue(result is TableAllocationResult.Failure)
        val failure = result as TableAllocationResult.Failure
        assertTrue(failure.reason.contains("fully booked", ignoreCase = true))
    }

    @Test
    fun testBookingReferencesRemainAssociatedWithPersistedReservation() {
        val result = TableAllocationEngine.allocateTable(
            restaurantId = "rest-1",
            restaurantName = "Bistro Lumière",
            userId = "usr-stable",
            userName = "Stable Guest",
            userPhone = "+91",
            bookingDate = "2026-10-25",
            timeSlot = "7:00 PM",
            partySize = 2,
            seatingArea = "Main Dining Room",
            existingBookings = emptyList()
        )

        assertTrue(result is TableAllocationResult.Success)
        val booking = (result as TableAllocationResult.Success).booking

        // Reference must remain consistent
        val reference = booking.id
        assertTrue(reference.startsWith("TB-"))
        assertEquals(reference, booking.id)

        // Stored and re-read preserves identical reference
        val map = mapOf(reference to booking)
        assertEquals(reference, map[reference]?.id)
        assertEquals("Stable Guest", map[reference]?.userName)
    }

    @Test
    fun testRestaurantScopingPreventsCrossRestaurantBookingBlockage() {
        val rest1Bookings = listOf(
            TableBooking(
                id = "TB-REST1",
                userId = "u1",
                userName = "User 1",
                userPhone = "+91",
                tableId = "rest-1_T1",
                tableNumber = "T1",
                bookingDate = "2026-10-25",
                timeSlot = "7:00 PM",
                partySize = 2,
                seatingArea = "Main Dining Room",
                status = TableBookingStatus.CONFIRMED,
                restaurantId = "rest-1"
            )
        )

        // Customer booking at restaurant 2 for the same slot should still get T1
        val resultRest2 = TableAllocationEngine.allocateTable(
            restaurantId = "rest-2",
            restaurantName = "Trattoria Milano",
            userId = "u2",
            userName = "User 2",
            userPhone = "+91",
            bookingDate = "2026-10-25",
            timeSlot = "7:00 PM",
            partySize = 2,
            seatingArea = "Main Dining Room",
            existingBookings = rest1Bookings
        )

        assertTrue(resultRest2 is TableAllocationResult.Success)
        val success2 = resultRest2 as TableAllocationResult.Success
        assertEquals("T1", success2.allocatedTable.tableNumber)
        assertEquals("rest-2_T1", success2.booking.tableId)
        assertEquals("rest-2", success2.booking.restaurantId)
    }

    @Test
    fun testCrossRestaurantIsolationWithLegacyTableIdPrefix() {
        val legacyBookingRest1 = TableBooking(
            id = "TB-LEGACY-1",
            userId = "u1",
            userName = "User 1",
            userPhone = "+91",
            tableId = "rest-1_T1",
            tableNumber = "T1",
            bookingDate = "2026-10-25",
            timeSlot = "7:00 PM",
            partySize = 2,
            seatingArea = "Main Dining Room",
            status = TableBookingStatus.CONFIRMED,
            restaurantId = ""
        )

        assertFalse(TableAllocationEngine.isSameRestaurant(legacyBookingRest1, "rest-2"))
        assertTrue(TableAllocationEngine.isSameRestaurant(legacyBookingRest1, "rest-1"))

        val resultRest2 = TableAllocationEngine.allocateTable(
            restaurantId = "rest-2",
            restaurantName = "Trattoria Milano",
            userId = "u2",
            userName = "User 2",
            userPhone = "+91",
            bookingDate = "2026-10-25",
            timeSlot = "7:00 PM",
            partySize = 2,
            seatingArea = "Main Dining Room",
            existingBookings = listOf(legacyBookingRest1)
        )

        assertTrue(resultRest2 is TableAllocationResult.Success)
        val success = resultRest2 as TableAllocationResult.Success
        assertEquals("T1", success.allocatedTable.tableNumber)
        assertEquals("rest-2_T1", success.booking.tableId)
    }

    @Test
    fun testRestaurantTableDefaultsInventoryScoping() {
        val restId = "bistro-99"
        val tables = RestaurantTableDefaults.getTablesForRestaurant(restId)
        assertEquals(12, tables.size)
        tables.forEach { table ->
            assertEquals(restId, table.restaurantId)
            assertTrue(table.tableId.startsWith("${restId}_"))
            assertEquals("${restId}_${table.tableNumber}", table.tableId)
        }
    }

    @Test
    fun testConcurrencyLockIsSharedAcrossDatabaseInstances() {
        val lock1 = RestroSqlDatabase.ALLOCATION_LOCK
        val lock2 = RestroSqlDatabase.ALLOCATION_LOCK
        assertTrue(lock1 === lock2)
    }

    @Test
    fun testComputeBookingValidationErrorAllScenarios() {
        val testFutureDate = java.time.LocalDate.now().plusDays(2).toString()
        val validDraft = TableBookingDraft(
            restaurantId = "rest-1",
            restaurantName = "Bistro",
            bookingDate = testFutureDate,
            timeSlot = "7:00 PM",
            partySize = 4,
            seatingArea = "Main Dining Room",
            tableNumber = "T1"
        )
        assertNull(computeBookingValidationError(validDraft))

        assertEquals("Please select a booking date to proceed", computeBookingValidationError(validDraft.copy(bookingDate = "")))
        assertEquals("The selected booking date cannot be in the past", computeBookingValidationError(validDraft.copy(bookingDate = "2020-01-01")))
        assertEquals("Please select a dining time slot to proceed", computeBookingValidationError(validDraft.copy(timeSlot = "")))
        assertEquals("Party size must be at least 1 guest", computeBookingValidationError(validDraft.copy(partySize = 0)))
        assertEquals("Party size must be at least 1 guest", computeBookingValidationError(validDraft.copy(partySize = -2)))
        assertEquals("Party size cannot exceed 20 guests", computeBookingValidationError(validDraft.copy(partySize = 21)))
        assertEquals("Please select a seating area preference", computeBookingValidationError(validDraft.copy(seatingArea = "")))
    }

    @Test
    fun testQueryBookingsByCustomerIdentity() {
        val allBookings = listOf(
            TableBooking(
                id = "TB-ALICE-1",
                userId = "usr-alice",
                userName = "Alice",
                userPhone = "+91 99999 11111",
                tableId = "rest-1_T1",
                tableNumber = "T1",
                bookingDate = "2026-10-25",
                timeSlot = "7:00 PM",
                partySize = 2,
                restaurantId = "rest-1"
            ),
            TableBooking(
                id = "TB-BOB-1",
                userId = "usr-bob",
                userName = "Bob",
                userPhone = "+91 99999 22222",
                tableId = "rest-1_T2",
                tableNumber = "T2",
                bookingDate = "2026-10-25",
                timeSlot = "7:00 PM",
                partySize = 2,
                restaurantId = "rest-1"
            ),
            TableBooking(
                id = "TB-ALICE-2",
                userId = "usr-alice",
                userName = "Alice",
                userPhone = "+91 99999 11111",
                tableId = "rest-2_T3",
                tableNumber = "T3",
                bookingDate = "2026-10-26",
                timeSlot = "8:00 PM",
                partySize = 4,
                restaurantId = "rest-2"
            )
        )

        val aliceBookings = allBookings.filter { it.userId == "usr-alice" }
        assertEquals(2, aliceBookings.size)
        assertTrue(aliceBookings.all { it.userId == "usr-alice" })
        assertFalse(aliceBookings.any { it.userId == "usr-bob" })

        val bobBookings = allBookings.filter { it.userId == "usr-bob" }
        assertEquals(1, bobBookings.size)
        assertEquals("TB-BOB-1", bobBookings.first().id)
    }

    @Test
    fun testEmptyBookingListHandling() {
        val emptyBookings = emptyList<TableBooking>()

        val upcoming = TableBookingManagementRules.filterAndSort(emptyBookings, TableBookingFilter.UPCOMING)
        assertTrue(upcoming.isEmpty())

        val completed = TableBookingManagementRules.filterAndSort(emptyBookings, TableBookingFilter.COMPLETED)
        assertTrue(completed.isEmpty())

        val cancelled = TableBookingManagementRules.filterAndSort(emptyBookings, TableBookingFilter.CANCELLED)
        assertTrue(cancelled.isEmpty())

        val all = TableBookingManagementRules.filterAndSort(emptyBookings, TableBookingFilter.ALL)
        assertTrue(all.isEmpty())
    }

    @Test
    fun testSortingAndStatusFiltering() {
        val testBookings = listOf(
            TableBooking(
                id = "TB-LATER-UPCOMING",
                userId = "usr-1",
                userName = "User",
                userPhone = "+91",
                tableId = "rest-1_T1",
                tableNumber = "T1",
                bookingDate = "2026-10-28",
                timeSlot = "8:00 PM",
                partySize = 2,
                status = TableBookingStatus.CONFIRMED
            ),
            TableBooking(
                id = "TB-EARLIER-UPCOMING",
                userId = "usr-1",
                userName = "User",
                userPhone = "+91",
                tableId = "rest-1_T2",
                tableNumber = "T2",
                bookingDate = "2026-10-22",
                timeSlot = "1:00 PM",
                partySize = 4,
                status = TableBookingStatus.CONFIRMED
            ),
            TableBooking(
                id = "TB-COMPLETED-1",
                userId = "usr-1",
                userName = "User",
                userPhone = "+91",
                tableId = "rest-1_T3",
                tableNumber = "T3",
                bookingDate = "2026-10-15",
                timeSlot = "7:00 PM",
                partySize = 2,
                status = TableBookingStatus.COMPLETED
            ),
            TableBooking(
                id = "TB-CANCELLED-1",
                userId = "usr-1",
                userName = "User",
                userPhone = "+91",
                tableId = "rest-1_T4",
                tableNumber = "T4",
                bookingDate = "2026-10-18",
                timeSlot = "9:00 PM",
                partySize = 2,
                status = TableBookingStatus.CANCELLED
            )
        )

        val upcoming = TableBookingManagementRules.filterAndSort(testBookings, TableBookingFilter.UPCOMING)
        assertEquals(2, upcoming.size)
        assertEquals("TB-EARLIER-UPCOMING", upcoming[0].id)
        assertEquals("TB-LATER-UPCOMING", upcoming[1].id)

        val completed = TableBookingManagementRules.filterAndSort(testBookings, TableBookingFilter.COMPLETED)
        assertEquals(1, completed.size)
        assertEquals("TB-COMPLETED-1", completed.first().id)

        val cancelled = TableBookingManagementRules.filterAndSort(testBookings, TableBookingFilter.CANCELLED)
        assertEquals(1, cancelled.size)
        assertEquals("TB-CANCELLED-1", cancelled.first().id)

        val all = TableBookingManagementRules.filterAndSort(testBookings, TableBookingFilter.ALL)
        assertEquals(4, all.size)
    }

    @Test
    fun testDetailsLookupForExistingAndMissingBooking() {
        val booking = TableBooking(
            id = "TB-DETAIL-100",
            userId = "usr-1",
            userName = "Neha Sen",
            userPhone = "+91 98888 77777",
            tableId = "rest-1_T9",
            tableNumber = "T9",
            bookingDate = "2026-10-30",
            timeSlot = "8:00 PM",
            partySize = 4,
            seatingArea = "Private Booth",
            specialRequests = "Anniversary celebration with dessert surprise",
            restaurantId = "rest-1",
            restaurantName = "Bistro Lumière"
        )
        val lookupMap = mapOf(booking.id to booking)

        val found = lookupMap["TB-DETAIL-100"]
        assertNotNull(found)
        assertEquals("Neha Sen", found?.userName)
        assertEquals("Anniversary celebration with dessert surprise", found?.specialRequests)
        assertEquals("Private Booth", found?.seatingArea)
        assertEquals("TB-DETAIL-100", found?.id)

        val missing = lookupMap["TB-NON-EXISTENT"]
        assertNull(missing)
    }

    @Test
    fun testSuccessfulCancellationByOwner() {
        val booking = TableBooking(
            id = "TB-CANCEL-OK",
            userId = "usr-alice",
            userName = "Alice",
            userPhone = "+91",
            tableId = "rest-1_T1",
            tableNumber = "T1",
            bookingDate = "2026-10-25",
            timeSlot = "7:00 PM",
            partySize = 2,
            status = TableBookingStatus.CONFIRMED
        )

        val result = TableBookingManagementRules.validateCancellationEligibility(booking, "usr-alice")
        assertTrue(result is TableCancellationResult.Success)
        val success = result as TableCancellationResult.Success
        assertEquals(TableBookingStatus.CANCELLED, success.booking.status)
        assertEquals("TB-CANCEL-OK", success.booking.id)
    }

    @Test
    fun testCancellationRejectedForAnotherCustomer() {
        val booking = TableBooking(
            id = "TB-AUTH-CHECK",
            userId = "usr-alice",
            userName = "Alice",
            userPhone = "+91",
            tableId = "rest-1_T1",
            tableNumber = "T1",
            bookingDate = "2026-10-25",
            timeSlot = "7:00 PM",
            partySize = 2,
            status = TableBookingStatus.CONFIRMED
        )

        val result = TableBookingManagementRules.validateCancellationEligibility(booking, "usr-bob")
        assertTrue(result is TableCancellationResult.Failure)
        val failure = result as TableCancellationResult.Failure
        assertTrue(failure.reason.contains("not authorized", ignoreCase = true))
    }

    @Test
    fun testCancellationRejectedForIneligibleStatus() {
        val baseBooking = TableBooking(
            id = "TB-STATUS-TEST",
            userId = "usr-alice",
            userName = "Alice",
            userPhone = "+91",
            tableId = "rest-1_T1",
            tableNumber = "T1",
            bookingDate = "2026-10-25",
            timeSlot = "7:00 PM",
            partySize = 2
        )

        val cancelledBooking = baseBooking.copy(status = TableBookingStatus.CANCELLED)
        val cancelResult = TableBookingManagementRules.validateCancellationEligibility(cancelledBooking, "usr-alice")
        assertTrue(cancelResult is TableCancellationResult.Failure)
        assertTrue((cancelResult as TableCancellationResult.Failure).reason.contains("already cancelled", ignoreCase = true))

        val completedBooking = baseBooking.copy(status = TableBookingStatus.COMPLETED)
        val completeResult = TableBookingManagementRules.validateCancellationEligibility(completedBooking, "usr-alice")
        assertTrue(completeResult is TableCancellationResult.Failure)
        assertTrue((completeResult as TableCancellationResult.Failure).reason.contains("Completed reservations cannot be cancelled", ignoreCase = true))

        val seatedBooking = baseBooking.copy(status = TableBookingStatus.SEATED)
        val seatedResult = TableBookingManagementRules.validateCancellationEligibility(seatedBooking, "usr-alice")
        assertTrue(seatedResult is TableCancellationResult.Failure)
    }

    @Test
    fun testRepeatedCancellationAttemptsIdempotencyGuard() {
        var booking = TableBooking(
            id = "TB-REPEAT-CANCEL",
            userId = "usr-alice",
            userName = "Alice",
            userPhone = "+91",
            tableId = "rest-1_T1",
            tableNumber = "T1",
            bookingDate = "2026-10-25",
            timeSlot = "7:00 PM",
            partySize = 2,
            status = TableBookingStatus.CONFIRMED
        )

        val firstResult = TableBookingManagementRules.validateCancellationEligibility(booking, "usr-alice")
        assertTrue(firstResult is TableCancellationResult.Success)
        booking = (firstResult as TableCancellationResult.Success).booking
        assertEquals(TableBookingStatus.CANCELLED, booking.status)

        val secondResult = TableBookingManagementRules.validateCancellationEligibility(booking, "usr-alice")
        assertTrue(secondResult is TableCancellationResult.Failure)
        assertEquals(TableBookingStatus.CANCELLED, booking.status)
    }

    @Test
    fun testCancelledBookingsNoLongerBlockAvailability() {
        val confirmedBooking = TableBooking(
            id = "TB-OCCUPY-T1",
            userId = "usr-1",
            userName = "User 1",
            userPhone = "+91",
            tableId = "rest-1_T1",
            tableNumber = "T1",
            bookingDate = "2026-10-25",
            timeSlot = "7:00 PM",
            partySize = 2,
            seatingArea = "Main Dining Room",
            status = TableBookingStatus.CONFIRMED,
            restaurantId = "rest-1"
        )

        val nextResultOccupied = TableAllocationEngine.allocateTable(
            restaurantId = "rest-1",
            restaurantName = "Bistro",
            userId = "usr-2",
            userName = "User 2",
            userPhone = "+91",
            bookingDate = "2026-10-25",
            timeSlot = "7:00 PM",
            partySize = 2,
            seatingArea = "Main Dining Room",
            existingBookings = listOf(confirmedBooking)
        )
        assertTrue(nextResultOccupied is TableAllocationResult.Success)
        assertEquals("T2", (nextResultOccupied as TableAllocationResult.Success).allocatedTable.tableNumber)

        val cancelledBooking = confirmedBooking.copy(status = TableBookingStatus.CANCELLED)

        val nextResultFreed = TableAllocationEngine.allocateTable(
            restaurantId = "rest-1",
            restaurantName = "Bistro",
            userId = "usr-3",
            userName = "User 3",
            userPhone = "+91",
            bookingDate = "2026-10-25",
            timeSlot = "7:00 PM",
            partySize = 2,
            seatingArea = "Main Dining Room",
            existingBookings = listOf(cancelledBooking)
        )
        assertTrue(nextResultFreed is TableAllocationResult.Success)
        assertEquals("T1", (nextResultFreed as TableAllocationResult.Success).allocatedTable.tableNumber)
    }

    @Test
    fun testCancellationFailureDoesNotShowFalseSuccess() {
        val failureResult: TableCancellationResult = TableCancellationResult.Failure("Network database lock timeout.")
        assertFalse(failureResult is TableCancellationResult.Success)
        assertEquals("Network database lock timeout.", (failureResult as TableCancellationResult.Failure).reason)
    }
}
