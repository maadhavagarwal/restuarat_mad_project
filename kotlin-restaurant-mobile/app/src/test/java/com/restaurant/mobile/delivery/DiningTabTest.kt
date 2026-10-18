package com.restaurant.mobile.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DiningTabTest {

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
}
