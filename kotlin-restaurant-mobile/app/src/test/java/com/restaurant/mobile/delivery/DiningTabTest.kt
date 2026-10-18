package com.restaurant.mobile.delivery

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DiningTabTest {

    private val gson = Gson()

    @Test
    fun testDatabaseConstants() {
        assertEquals("dining_tabs", RestroSqlDatabase.TABLE_DINING_TABS)
        assertEquals("dining_tabs", RestroSqlDatabase.TABLE_TABS)
        assertEquals(6, RestroSqlDatabase.DATABASE_VERSION)
    }

    @Test
    fun testTableBillItemCreationAndDefaults() {
        val item = TableBillItem(
            dishId = "d-paneer-tikka",
            name = "Paneer Tikka",
            quantity = 2,
            price = 28000L,
            total = 56000L
        )

        assertEquals("d-paneer-tikka", item.dishId)
        assertEquals("Paneer Tikka", item.name)
        assertEquals(2, item.quantity)
        assertEquals(28000L, item.price)
        assertEquals(56000L, item.total)
        assertNotNull(item.orderedAt)
        assertTrue(item.orderedAt.isNotBlank())
    }

    @Test
    fun testDiningTabDefaults() {
        val tab = DiningTab(
            tableNumber = "T4"
        )

        assertNotNull(tab.id)
        assertTrue(tab.id.isNotBlank())
        assertEquals("T4", tab.tableNumber)
        assertNull(tab.bookingId)
        assertNull(tab.userId)
        assertEquals("Guest Diner", tab.userName)
        assertTrue(tab.items.isEmpty())
        assertEquals(0L, tab.subtotal)
        assertEquals(0L, tab.tax)
        assertEquals(0L, tab.serviceCharge)
        assertEquals(0L, tab.total)
        assertEquals("OPEN", tab.status)
        assertNull(tab.paymentMethod)
        assertNotNull(tab.updatedAt)
    }

    @Test
    fun testDiningTabWithFullDetailsAndBillingCalculations() {
        val item1 = TableBillItem(
            dishId = "dish-1",
            name = "Butter Chicken",
            quantity = 2,
            price = 35000L,
            total = 70000L
        )
        val item2 = TableBillItem(
            dishId = "dish-2",
            name = "Garlic Naan",
            quantity = 4,
            price = 6000L,
            total = 24000L
        )
        val items = listOf(item1, item2)
        val subtotal = items.sumOf { it.total } // 94000L
        val gst = (subtotal * 5) / 100          // 4700L (5% GST)
        val serviceCharge = (subtotal * 5) / 100 // 4700L (5% service charge)
        val total = subtotal + gst + serviceCharge // 103400L

        val tab = DiningTab(
            id = "TAB-TEST-001",
            tableNumber = "T12",
            bookingId = "TB-BOOKING-99",
            userId = "usr-vip-42",
            userName = "Rohit Verma",
            items = items,
            subtotal = subtotal,
            tax = gst,
            serviceCharge = serviceCharge,
            total = total,
            status = "OPEN",
            paymentMethod = null,
            updatedAt = Instant.now().toString()
        )

        assertEquals("TAB-TEST-001", tab.id)
        assertEquals("T12", tab.tableNumber)
        assertEquals("TB-BOOKING-99", tab.bookingId)
        assertEquals("usr-vip-42", tab.userId)
        assertEquals("Rohit Verma", tab.userName)
        assertEquals(2, tab.items.size)
        assertEquals(94000L, tab.subtotal)
        assertEquals(4700L, tab.tax)
        assertEquals(4700L, tab.serviceCharge)
        assertEquals(103400L, tab.total)
        assertEquals("OPEN", tab.status)
        assertNull(tab.paymentMethod)
    }

    @Test
    fun testDiningTabStatusLifecycleTransitions() {
        val baseTab = DiningTab(
            tableNumber = "T1",
            subtotal = 50000L,
            tax = 2500L,
            total = 52500L,
            status = "OPEN"
        )
        assertEquals("OPEN", baseTab.status)

        val pendingTab = baseTab.copy(status = "PAYMENT_PENDING")
        assertEquals("PAYMENT_PENDING", pendingTab.status)

        val settledTab = pendingTab.copy(
            status = "SETTLED",
            paymentMethod = "UPI: rohit@oksbi"
        )
        assertEquals("SETTLED", settledTab.status)
        assertEquals("UPI: rohit@oksbi", settledTab.paymentMethod)
    }

    @Test
    fun testTableBillItemsJsonSerializationRoundtrip() {
        val items = listOf(
            TableBillItem("dish-101", "Dal Makhani", 1, 22000L, 22000L),
            TableBillItem("dish-102", "Jeera Rice", 2, 12000L, 24000L)
        )

        val json = gson.toJson(items)
        assertNotNull(json)
        assertTrue(json.contains("Dal Makhani"))
        assertTrue(json.contains("Jeera Rice"))

        val listType = object : TypeToken<List<TableBillItem>>() {}.type
        val deserialized: List<TableBillItem> = gson.fromJson(json, listType)

        assertEquals(2, deserialized.size)
        assertEquals("dish-101", deserialized[0].dishId)
        assertEquals("Dal Makhani", deserialized[0].name)
        assertEquals(1, deserialized[0].quantity)
        assertEquals(22000L, deserialized[0].price)
        assertEquals(22000L, deserialized[0].total)
        assertEquals("dish-102", deserialized[1].dishId)
        assertEquals(24000L, deserialized[1].total)
    }

    @Test
    fun testDineInPaymentResultDataClass() {
        val tab = DiningTab(
            id = "TAB-PAID-01",
            tableNumber = "T3",
            subtotal = 50000L,
            tax = 2500L,
            total = 52500L,
            status = "OPEN"
        )
        val result = DineInPaymentResult(
            tab = tab,
            paymentMethod = "Netbanking (HDFC Bank)",
            transactionId = "TXN-8849ABCD"
        )

        assertEquals("TAB-PAID-01", result.tab.id)
        assertEquals("T3", result.tab.tableNumber)
        assertEquals(52500L, result.tab.total)
        assertEquals("Netbanking (HDFC Bank)", result.paymentMethod)
        assertEquals("TXN-8849ABCD", result.transactionId)
        assertNotNull(result.timestamp)
        assertTrue(result.timestamp.isNotBlank())
    }

    @Test
    fun testDineInPaymentSupportedMethodsFormatting() {
        val upiMethod = "UPI (Google Pay)"
        val cardMethod = "Card (Mastercard)"
        val netbankingMethod = "Netbanking (State Bank of India)"

        assertTrue(upiMethod.startsWith("UPI"))
        assertTrue(cardMethod.startsWith("Card"))
        assertTrue(netbankingMethod.startsWith("Netbanking"))

        assertTrue(upiMethod.contains("Google Pay"))
        assertTrue(cardMethod.contains("Mastercard"))
        assertTrue(netbankingMethod.contains("State Bank of India"))
    }

    @Test
    fun testNativeNotificationExactMessageFormat() {
        val totalFormatted = "₹367.50"
        val tableNumber = "T4"
        val message = DeliveryNotificationHelper.formatDineInSettlementMessage(totalFormatted, tableNumber)

        val expected = "Bill Settled! Payment of ₹367.50 for Table T4 verified. Thank you for dining with us!"
        assertEquals(expected, message)
        assertEquals(1004, DeliveryNotificationHelper.NOTIFICATION_ID_DINE_IN_SETTLED)
    }

    @Test
    fun testNativeNotificationWithMoneyUtility() {
        val amountPaise = 125000L // ₹1,250.00
        val formattedMoney = money(amountPaise)
        val tableNumber = "T10"
        val message = DeliveryNotificationHelper.formatDineInSettlementMessage(formattedMoney, tableNumber)

        assertTrue(message.startsWith("Bill Settled! Payment of "))
        assertTrue(message.contains("for Table T10 verified."))
        assertTrue(message.endsWith("Thank you for dining with us!"))
        assertTrue(message.contains(formattedMoney))
    }

    @Test
    fun testDigitalGstReceiptRequiredFields() {
        val items = listOf(
            TableBillItem("d1", "Mutton Dum Biryani", 2, 42000L, 84000L),
            TableBillItem("d2", "Garlic Naan", 3, 6000L, 18000L)
        )
        val subtotal = 102000L
        val gst5Percent = (subtotal * 5) / 100 // 5100L
        val serviceCharge5Percent = (subtotal * 5) / 100 // 5100L
        val grandTotal = subtotal + gst5Percent + serviceCharge5Percent // 112200L

        val tab = DiningTab(
            id = "TAB-RECEIPT-999",
            tableNumber = "T5",
            items = items,
            subtotal = subtotal,
            tax = gst5Percent,
            serviceCharge = serviceCharge5Percent,
            total = grandTotal,
            status = "SETTLED",
            paymentMethod = "UPI (Google Pay)"
        )

        val receipt = DineInPaymentResult(
            tab = tab,
            paymentMethod = "UPI (Google Pay)",
            transactionId = "TXN-GST-987654"
        )

        // 1. Table number
        assertEquals("T5", receipt.tab.tableNumber)
        // 2. Itemized dishes and quantities
        assertEquals(2, receipt.tab.items.size)
        assertEquals("Mutton Dum Biryani", receipt.tab.items[0].name)
        assertEquals(2, receipt.tab.items[0].quantity)
        assertEquals("Garlic Naan", receipt.tab.items[1].name)
        assertEquals(3, receipt.tab.items[1].quantity)
        // 3. Subtotal
        assertEquals(102000L, receipt.tab.subtotal)
        // 4. 5% GST
        assertEquals(5100L, receipt.tab.tax)
        assertEquals((receipt.tab.subtotal * 5) / 100, receipt.tab.tax)
        // 5. Applicable service charge
        assertEquals(5100L, receipt.tab.serviceCharge)
        // 6. Total paid
        assertEquals(112200L, receipt.tab.total)
        assertEquals(receipt.tab.subtotal + receipt.tab.tax + receipt.tab.serviceCharge, receipt.tab.total)
        // 7. Payment method
        assertEquals("UPI (Google Pay)", receipt.paymentMethod)
        // 8. Transaction ID
        assertEquals("TXN-GST-987654", receipt.transactionId)
        assertTrue(receipt.transactionId.isNotBlank())
    }

    @Test
    fun testCompletedBookingFreesFloorTableInAllocationEngine() {
        // Table T7 (Outdoor Garden, capacity 2) and T8 (Outdoor Garden, capacity 4)
        val initialBookings = listOf(
            TableBooking(
                id = "TB-T7-SEATED",
                userId = "u1",
                userName = "Diner 1",
                userPhone = "+91 99999 11111",
                tableId = "rest-1_T7",
                tableNumber = "T7",
                bookingDate = "2026-10-25",
                timeSlot = "8:00 PM",
                partySize = 2,
                seatingArea = "Outdoor Garden",
                status = TableBookingStatus.SEATED,
                restaurantId = "rest-1"
            ),
            TableBooking(
                id = "TB-T8-CONFIRMED",
                userId = "u2",
                userName = "Diner 2",
                userPhone = "+91 99999 22222",
                tableId = "rest-1_T8",
                tableNumber = "T8",
                bookingDate = "2026-10-25",
                timeSlot = "8:00 PM",
                partySize = 4,
                seatingArea = "Outdoor Garden",
                status = TableBookingStatus.CONFIRMED,
                restaurantId = "rest-1"
            )
        )

        // While both tables are active (SEATED/CONFIRMED), no outdoor table is available
        val allocationBeforeSettlement = TableAllocationEngine.allocateTable(
            restaurantId = "rest-1",
            restaurantName = "Bistro Lumière",
            userId = "u3",
            userName = "Diner 3",
            userPhone = "+91 99999 33333",
            bookingDate = "2026-10-25",
            timeSlot = "8:00 PM",
            partySize = 2,
            seatingArea = "Outdoor Garden",
            existingBookings = initialBookings
        )
        assertTrue(allocationBeforeSettlement is TableAllocationResult.Failure)

        // After dine-in settlement, the booking status transitions to COMPLETED
        val bookingsAfterSettlement = initialBookings.map {
            if (it.tableNumber == "T7") it.copy(status = TableBookingStatus.COMPLETED) else it
        }

        // The floor table T7 now becomes available and can be allocated
        val allocationAfterSettlement = TableAllocationEngine.allocateTable(
            restaurantId = "rest-1",
            restaurantName = "Bistro Lumière",
            userId = "u3",
            userName = "Diner 3",
            userPhone = "+91 99999 33333",
            bookingDate = "2026-10-25",
            timeSlot = "8:00 PM",
            partySize = 2,
            seatingArea = "Outdoor Garden",
            existingBookings = bookingsAfterSettlement
        )
        assertTrue(allocationAfterSettlement is TableAllocationResult.Success)
        val success = allocationAfterSettlement as TableAllocationResult.Success
        assertEquals("T7", success.allocatedTable.tableNumber)
        assertEquals("Outdoor Garden", success.allocatedTable.seatingArea)
    }

    @Test
    fun testFloorPlanDefaultTablesCoverT1ToT12() {
        val tables = RestaurantTableDefaults.DEFAULT_TABLES
        assertEquals(12, tables.size)

        val tableNumbers = tables.map { it.tableNumber }
        for (i in 1..12) {
            assertTrue("Floor plan inventory must contain T$i", tableNumbers.contains("T$i"))
        }
    }
}
