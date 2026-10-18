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
}
