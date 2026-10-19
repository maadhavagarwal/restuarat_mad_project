package com.restaurant.mobile.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class WaiterPosTablePairingTest {

    private val today = LocalDate.now().toString()

    @Test
    fun testAddingItemsWithoutSelectingTableIsRejected() {
        val selectedTableNumber: String? = null
        val itemsToAdd = listOf(
            TableBillItem(
                menuItemId = 1L,
                name = "Paneer Tikka",
                price = 250.0,
                quantity = 1
            )
        )

        try {
            WaiterTablePairingRules.appendOrOpenDiningTab(
                existingTab = null,
                tableNumber = selectedTableNumber ?: "",
                tableId = "",
                newItems = itemsToAdd
            )
            fail("Expected IllegalArgumentException when table number is blank/unselected")
        } catch (e: IllegalArgumentException) {
            assertEquals("Table number must not be blank", e.message)
        }
    }

    @Test
    fun testSelectingTableDisplaysActualAssociatedCustomer() {
        val seatedBooking = TableBooking(
            id = "TB-TEST-001",
            userId = "usr-aarav",
            userName = "Aarav Sharma",
            userPhone = "+91 98765 43210",
            tableId = "T3",
            tableNumber = "T3",
            bookingDate = today,
            timeSlot = "1:00 PM",
            partySize = 4,
            status = TableBookingStatus.SEATED
        )

        val resolved = WaiterTablePairingRules.resolveActiveBookingForTable(
            tableNumber = "T3",
            bookings = listOf(seatedBooking),
            serviceDate = today
        )

        assertNotNull(resolved)
        assertEquals("usr-aarav", resolved?.userId)
        assertEquals("Aarav Sharma", resolved?.userName)
        assertEquals(TableBookingStatus.SEATED, resolved?.status)

        val headerText = WaiterTablePairingRules.formatServingHeader("T3", resolved)
        assertEquals("Serving Table: T3 (Aarav Sharma — Seated)", headerText)
    }

    @Test
    fun testSubmittingOrderAppendsItemsToExistingActiveDiningTab() {
        val initialItems = listOf(
            TableBillItem(
                id = "item-1",
                menuItemId = 1L,
                name = "Paneer Tikka",
                price = 250.0,
                quantity = 2,
                taxRate = 5.0
            )
        )
        val initialTab = WaiterTablePairingRules.appendOrOpenDiningTab(
            existingTab = null,
            tableNumber = "T3",
            tableId = "T3",
            newItems = initialItems,
            associatedBooking = TableBooking(
                id = "TB-001",
                userId = "usr-1",
                userName = "Rohan Verma",
                userPhone = "+91 99999 00000",
                tableId = "T3",
                tableNumber = "T3",
                bookingDate = today,
                timeSlot = "7:00 PM",
                partySize = 2,
                status = TableBookingStatus.SEATED
            )
        )

        assertEquals(1, initialTab.items.size)
        assertEquals(500.0, initialTab.subtotalRupees, 0.01)
        assertEquals(525.0, initialTab.totalRupees, 0.01)

        val roundTwoItems = listOf(
            TableBillItem(
                id = "item-2",
                menuItemId = 2L,
                name = "Chicken Biryani",
                price = 350.0,
                quantity = 1,
                taxRate = 5.0
            )
        )

        val updatedTab = WaiterTablePairingRules.appendOrOpenDiningTab(
            existingTab = initialTab,
            tableNumber = "T3",
            tableId = "T3",
            newItems = roundTwoItems
        )

        assertEquals(2, updatedTab.items.size)
        assertEquals(initialTab.id, updatedTab.id)
        assertEquals("Rohan Verma", updatedTab.customerName)
        assertEquals("TB-001", updatedTab.bookingId)
        assertEquals(850.0, updatedTab.subtotalRupees, 0.01)
        assertEquals(42.5, updatedTab.taxRupees, 0.01)
        assertEquals(892.5, updatedTab.totalRupees, 0.01)
    }

    @Test
    fun testExistingItemsArePreservedOnTabUpdate() {
        val existingItem = TableBillItem(
            id = "preserved-item-1",
            menuItemId = 5L,
            name = "Fresh Mint Mojito",
            price = 120.0,
            quantity = 3,
            taxRate = 5.0
        )
        val existingTab = DiningTab(
            id = "TAB-TEST-99",
            tableId = "T2",
            tableNumber = "T2",
            customerId = "usr-test",
            customerName = "Priya Nair",
            bookingId = "TB-888",
            status = DiningTabStatus.ACTIVE,
            items = listOf(existingItem),
            subtotal = 360.0,
            tax = 18.0,
            serviceCharge = 0.0,
            total = 378.0
        )

        val newItem = TableBillItem(
            id = "new-item-2",
            menuItemId = 3L,
            name = "Garlic Naan",
            price = 60.0,
            quantity = 2,
            taxRate = 5.0
        )

        val updatedTab = WaiterTablePairingRules.appendOrOpenDiningTab(
            existingTab = existingTab,
            tableNumber = "T2",
            tableId = "T2",
            newItems = listOf(newItem)
        )

        assertEquals(2, updatedTab.items.size)
        assertEquals("preserved-item-1", updatedTab.items[0].id)
        assertEquals("Fresh Mint Mojito", updatedTab.items[0].name)
        assertEquals(3, updatedTab.items[0].quantity)
        assertEquals("new-item-2", updatedTab.items[1].id)
        assertEquals("Garlic Naan", updatedTab.items[1].name)
        assertEquals(2, updatedTab.items[1].quantity)
        assertEquals(480.0, updatedTab.subtotalRupees, 0.01)
        assertEquals(24.0, updatedTab.taxRupees, 0.01)
        assertEquals(504.0, updatedTab.totalRupees, 0.01)
    }

    @Test
    fun testCreatingNewDiningTabRetainsCorrectCustomerAndReservationIdentifiers() {
        val booking = TableBooking(
            id = "TB-RESERV-44",
            userId = "usr-meera",
            userName = "Meera Sen",
            userPhone = "+91 91234 56789",
            tableId = "T4",
            tableNumber = "T4",
            bookingDate = today,
            timeSlot = "8:00 PM",
            partySize = 4,
            status = TableBookingStatus.CONFIRMED,
            restaurantId = "rest-1"
        )

        val items = listOf(
            TableBillItem(
                menuItemId = 4L,
                name = "Gulab Jamun",
                price = 90.0,
                quantity = 2,
                taxRate = 5.0
            )
        )

        val newTab = WaiterTablePairingRules.appendOrOpenDiningTab(
            existingTab = null,
            tableNumber = "T4",
            tableId = "T4",
            newItems = items,
            associatedBooking = booking,
            restaurantId = "rest-1"
        )

        assertNotNull(newTab.id)
        assertTrue(newTab.id.startsWith("TAB-"))
        assertEquals("T4", newTab.tableNumber)
        assertEquals("usr-meera", newTab.customerId)
        assertEquals("Meera Sen", newTab.customerName)
        assertEquals("+91 91234 56789", newTab.customerPhone)
        assertEquals("TB-RESERV-44", newTab.bookingId)
        assertEquals("rest-1", newTab.restaurantId)
        assertEquals(DiningTabStatus.ACTIVE, newTab.status)
        assertEquals(180.0, newTab.subtotalRupees, 0.01)
        assertEquals(9.0, newTab.taxRupees, 0.01)
        assertEquals(189.0, newTab.totalRupees, 0.01)
    }

    @Test
    fun testWalkInOrderDoesNotReceiveUnrelatedCustomerBooking() {
        val unrelatedBookingForOtherTable = TableBooking(
            id = "TB-OTHER",
            userId = "usr-other",
            userName = "Someone Else",
            userPhone = "+91 00000 00000",
            tableId = "T5",
            tableNumber = "T5",
            bookingDate = today,
            timeSlot = "1:00 PM",
            partySize = 2,
            status = TableBookingStatus.SEATED
        )

        val resolved = WaiterTablePairingRules.resolveActiveBookingForTable(
            tableNumber = "T1",
            bookings = listOf(unrelatedBookingForOtherTable),
            serviceDate = today
        )
        assertNull(resolved)

        val headerText = WaiterTablePairingRules.formatServingHeader("T1", resolved)
        assertEquals("Serving Table: T1 (Walk-in)", headerText)

        val walkInItems = listOf(
            TableBillItem(
                menuItemId = 1L,
                name = "Paneer Tikka",
                price = 250.0,
                quantity = 1
            )
        )

        val walkInTab = WaiterTablePairingRules.appendOrOpenDiningTab(
            existingTab = null,
            tableNumber = "T1",
            tableId = "T1",
            newItems = walkInItems,
            associatedBooking = null
        )

        assertNull(walkInTab.customerId)
        assertNull(walkInTab.customerName)
        assertNull(walkInTab.bookingId)
        assertEquals("T1", walkInTab.tableNumber)
        assertEquals(250.0, walkInTab.subtotalRupees, 0.01)
        assertEquals(262.5, walkInTab.totalRupees, 0.01)
    }

    @Test
    fun testInvalidOrAmbiguousReservationRelationshipsHandledSafely() {
        val cancelledBooking = TableBooking(
            id = "TB-CANCELLED",
            userId = "usr-cancel",
            userName = "Cancelled Customer",
            userPhone = "+91 11111 22222",
            tableId = "T2",
            tableNumber = "T2",
            bookingDate = today,
            timeSlot = "1:00 PM",
            partySize = 2,
            status = TableBookingStatus.CANCELLED
        )
        val completedBooking = TableBooking(
            id = "TB-COMPLETED",
            userId = "usr-completed",
            userName = "Completed Customer",
            userPhone = "+91 33333 44444",
            tableId = "T2",
            tableNumber = "T2",
            bookingDate = today,
            timeSlot = "12:00 PM",
            partySize = 2,
            status = TableBookingStatus.COMPLETED
        )

        val resolvedInactive = WaiterTablePairingRules.resolveActiveBookingForTable(
            tableNumber = "T2",
            bookings = listOf(cancelledBooking, completedBooking),
            serviceDate = today
        )
        assertNull("Cancelled or completed bookings must never be associated as active", resolvedInactive)

        // Multiple confirmed reservations for the same table on the same date with different slots
        val lunchBooking = TableBooking(
            id = "TB-LUNCH",
            userId = "usr-lunch",
            userName = "Lunch Guest",
            userPhone = "+91 55555 66666",
            tableId = "T2",
            tableNumber = "T2",
            bookingDate = today,
            timeSlot = "1:00 PM",
            partySize = 2,
            status = TableBookingStatus.CONFIRMED
        )
        val dinnerBooking = TableBooking(
            id = "TB-DINNER",
            userId = "usr-dinner",
            userName = "Dinner Guest",
            userPhone = "+91 77777 88888",
            tableId = "T2",
            tableNumber = "T2",
            bookingDate = today,
            timeSlot = "8:00 PM",
            partySize = 2,
            status = TableBookingStatus.CONFIRMED
        )

        // Without unambiguous slot: returns null rather than arbitrarily guessing
        val ambiguousResolved = WaiterTablePairingRules.resolveActiveBookingForTable(
            tableNumber = "T2",
            bookings = listOf(lunchBooking, dinnerBooking),
            serviceDate = today,
            currentTimeSlot = null
        )
        assertNull("Ambiguous multi-slot confirmed bookings must safely resolve to null (walk-in)", ambiguousResolved)

        // With unambiguous slot match:
        val matchedLunch = WaiterTablePairingRules.resolveActiveBookingForTable(
            tableNumber = "T2",
            bookings = listOf(lunchBooking, dinnerBooking),
            serviceDate = today,
            currentTimeSlot = "1:00 PM"
        )
        assertNotNull(matchedLunch)
        assertEquals("usr-lunch", matchedLunch?.userId)

        val matchedDinner = WaiterTablePairingRules.resolveActiveBookingForTable(
            tableNumber = "T2",
            bookings = listOf(lunchBooking, dinnerBooking),
            serviceDate = today,
            currentTimeSlot = "8:00 PM"
        )
        assertNotNull(matchedDinner)
        assertEquals("usr-dinner", matchedDinner?.userId)
    }

    @Test
    fun testSubtotalTaxAndTotalCalculationsRemainCorrect() {
        val items = listOf(
            TableBillItem(
                menuItemId = 1L,
                name = "Item A",
                price = 200.0,
                quantity = 2,
                taxRate = 5.0
            ),
            TableBillItem(
                menuItemId = 2L,
                name = "Item B",
                price = 150.0,
                quantity = 3,
                taxRate = 12.0
            )
        )

        // Item A: 200 * 2 = 400, tax 5% = 20
        // Item B: 150 * 3 = 450, tax 12% = 54
        // Subtotal = 850, Tax = 74, ServiceCharge = 30 -> Total = 954
        val billing = DiningTab.calculateBilling(items, existingServiceCharge = 30.0)

        assertEquals(850.0, billing.subtotal, 0.001)
        assertEquals(74.0, billing.tax, 0.001)
        assertEquals(30.0, billing.serviceCharge, 0.001)
        assertEquals(954.0, billing.total, 0.001)
    }

    @Test
    fun testFailedDatabaseOperationsDoNotLeavePartiallyUpdatedTabs() {
        val initialItem = TableBillItem(
            id = "valid-1",
            menuItemId = 1L,
            name = "Safe Starter",
            price = 100.0,
            quantity = 1,
            taxRate = 5.0
        )
        val baselineTab = WaiterTablePairingRules.appendOrOpenDiningTab(
            existingTab = null,
            tableNumber = "T1",
            tableId = "T1",
            newItems = listOf(initialItem)
        )

        var simulatedTabState = baselineTab

        try {
            // Attempting to append invalid item with quantity 0
            val corruptItem = TableBillItem(
                id = "bad-1",
                menuItemId = 99L,
                name = "Invalid Item",
                price = 100.0,
                quantity = 0
            )
            simulatedTabState = WaiterTablePairingRules.appendOrOpenDiningTab(
                existingTab = baselineTab,
                tableNumber = "T1",
                tableId = "T1",
                newItems = listOf(corruptItem)
            )
            fail("Expected IllegalArgumentException for zero quantity item")
        } catch (_: IllegalArgumentException) {
            // Transaction aborted, state unchanged
        }

        // Verify baseline tab was not mutated or corrupted
        assertEquals(1, simulatedTabState.items.size)
        assertEquals("Safe Starter", simulatedTabState.items[0].name)
        assertEquals(100.0, simulatedTabState.subtotalRupees, 0.01)
        assertEquals(105.0, simulatedTabState.totalRupees, 0.01)
    }

    @Test
    fun testExistingReservationBillingAndOrderWorkflowsContinueWorking() {
        val tab = DiningTab(
            tableId = "T1",
            tableNumber = "T1"
        )
        assertEquals(DiningTabStatus.ACTIVE, tab.status)
        assertTrue(DiningTabStatus.ALL.contains(tab.status))
        assertTrue(DiningTabStatus.ALL.contains("PAID"))
        assertTrue(DiningTabStatus.ALL.contains("CLOSED"))

        val item = TableBillItem(
            menuItemId = 10L,
            name = "Test Item",
            price = 50.0,
            quantity = 4
        )
        assertEquals(200.0, item.itemTotal, 0.01)
    }
}
