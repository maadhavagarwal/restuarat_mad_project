package com.restaurant.mobile.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeliveryNotificationTest {

    private lateinit var helper: DeliveryNotificationHelper

    @Before
    fun setUp() {
        helper = DeliveryNotificationHelper()
    }

    // -------------------------------------------------------------
    // 1. RESERVATION CONFIRMED NOTIFICATION
    // -------------------------------------------------------------

    @Test
    fun testShowReservationConfirmed_ContentAndDestination() {
        helper.showReservationConfirmed(tableNumber = "T4", slot = "7:30 PM")

        val record = helper.lastDispatchedRecord
        assertNotNull("Reservation confirmed record should be captured", record)
        assertEquals(DineInNotificationType.RESERVATION_CONFIRMED, record!!.type)
        assertEquals("T4", record.tableNumber)
        assertEquals("7:30 PM", record.slot)
        assertTrue("Title should contain confirmation indicator", record.title.contains("Reservation Confirmed"))
        assertTrue("Message should contain table number T4", record.message.contains("T4"))
        assertTrue("Message should contain slot 7:30 PM", record.message.contains("7:30 PM"))
        assertTrue("BigText should contain table and slot", record.bigText.contains("T4") && record.bigText.contains("7:30 PM"))
        assertEquals("Destination must be MyBookings", "MyBookings", record.destination)
        assertEquals(DeliveryNotificationHelper.NOTIFICATION_ID_RESERVATION_CONFIRMED, record.notificationId)
    }

    // -------------------------------------------------------------
    // 2. RESERVATION REMINDER NOTIFICATION
    // -------------------------------------------------------------

    @Test
    fun testShowReservationReminder_PositiveMinutes() {
        helper.showReservationReminder(tableNumber = "T2", minutesRemaining = 15)

        val record = helper.lastDispatchedRecord
        assertNotNull("Reminder record should be captured", record)
        assertEquals(DineInNotificationType.RESERVATION_REMINDER, record!!.type)
        assertEquals("T2", record.tableNumber)
        assertEquals(15, record.minutesRemaining)
        assertTrue("Message should mention 15 minutes", record.message.contains("15 minutes"))
        assertTrue("Message should contain table T2", record.message.contains("T2"))
        assertEquals("Destination must be MyBookings", "MyBookings", record.destination)
        assertEquals(DeliveryNotificationHelper.NOTIFICATION_ID_RESERVATION_REMINDER, record.notificationId)
    }

    @Test
    fun testShowReservationReminder_ZeroMinutesHandledSafely() {
        helper.showReservationReminder(tableNumber = "T5", minutesRemaining = 0)

        val record = helper.lastDispatchedRecord
        assertNotNull("Zero minutes reminder should be captured safely", record)
        assertEquals(DineInNotificationType.RESERVATION_REMINDER, record!!.type)
        assertEquals("T5", record.tableNumber)
        assertEquals(0, record.minutesRemaining)
        assertTrue("Message should indicate starting now", record.message.contains("starting now"))
        assertEquals("MyBookings", record.destination)
    }

    @Test
    fun testShowReservationReminder_NegativeMinutesSafelyIgnored() {
        helper.showReservationReminder(tableNumber = "T2", minutesRemaining = -10)

        assertNull("Negative minutes must be ignored without dispatching", helper.lastDispatchedRecord)
        assertEquals("No records should be dispatched for negative minutes", 0, helper.dispatchedRecords.size)
    }

    // -------------------------------------------------------------
    // 3. TABLE BILL READY NOTIFICATION
    // -------------------------------------------------------------

    @Test
    fun testShowTableBillReady_ContentAndFormattedAmount() {
        // ₹1,144.00 = 114400 paise
        helper.showTableBillReady(tableNumber = "T1", amount = 114400L)

        val record = helper.lastDispatchedRecord
        assertNotNull("Table bill ready record should be captured", record)
        assertEquals(DineInNotificationType.TABLE_BILL_READY, record!!.type)
        assertEquals("T1", record.tableNumber)
        assertEquals(114400L, record.amountPaise)
        assertTrue("Formatted amount should contain 1,144", record.formattedAmount.contains("1,144"))
        assertTrue("Message should contain formatted amount", record.message.contains("1,144"))
        assertTrue("Message should contain table T1", record.message.contains("T1"))
        assertEquals("Destination must be Home for live active tab", "Home", record.destination)
        assertEquals(DeliveryNotificationHelper.NOTIFICATION_ID_TABLE_BILL_READY, record.notificationId)
    }

    // -------------------------------------------------------------
    // 4. BILL SETTLED RECEIPT NOTIFICATION
    // -------------------------------------------------------------

    @Test
    fun testShowBillSettledReceipt_ContentAndFormatting() {
        // ₹550.00 = 55000 paise
        helper.showBillSettledReceipt(tableNumber = "T3", amount = 55000L, invoiceId = "TXN-DIN-7890")

        val record = helper.lastDispatchedRecord
        assertNotNull("Bill settled receipt record should be captured", record)
        assertEquals(DineInNotificationType.BILL_SETTLED_RECEIPT, record!!.type)
        assertEquals("T3", record.tableNumber)
        assertEquals(55000L, record.amountPaise)
        assertEquals("TXN-DIN-7890", record.invoiceId)
        assertTrue("Formatted amount should contain 550", record.formattedAmount.contains("550"))
        assertTrue("Message should contain invoiceId", record.message.contains("TXN-DIN-7890"))
        assertTrue("Message should contain table T3", record.message.contains("T3"))
        assertEquals("Destination must be Orders for receipt details", "Orders", record.destination)
        assertEquals(DeliveryNotificationHelper.NOTIFICATION_ID_BILL_SETTLED_RECEIPT, record.notificationId)
    }

    @Test
    fun testBillSettledReceipt_DeduplicationOnDuplicateInvoiceId() {
        helper.showBillSettledReceipt(tableNumber = "T3", amount = 55000L, invoiceId = "TXN-DIN-DUP-1")
        assertEquals(1, helper.dispatchedRecords.size)

        // Repeat with identical invoice ID
        helper.showBillSettledReceipt(tableNumber = "T3", amount = 55000L, invoiceId = "TXN-DIN-DUP-1")
        assertEquals("Duplicate invoice settlement should be ignored", 1, helper.dispatchedRecords.size)

        // Distinct invoice ID should dispatch
        helper.showBillSettledReceipt(tableNumber = "T3", amount = 55000L, invoiceId = "TXN-DIN-DISTINCT-2")
        assertEquals(2, helper.dispatchedRecords.size)
    }

    // -------------------------------------------------------------
    // 5. EXISTING DELIVERY NOTIFICATIONS & CONSTANTS
    // -------------------------------------------------------------

    @Test
    fun testExistingChannelsAndConstants() {
        assertEquals("food_delivery_channel", DeliveryNotificationHelper.CHANNEL_ID)
        assertEquals("Live Order & Delivery Updates", DeliveryNotificationHelper.CHANNEL_NAME)
        assertEquals("dine_in_notifications_channel", DeliveryNotificationHelper.DINE_IN_CHANNEL_ID)
        assertEquals("Table Reservations & Dining Updates", DeliveryNotificationHelper.DINE_IN_CHANNEL_NAME)
        assertEquals(1001, DeliveryNotificationHelper.NOTIFICATION_ID_PLACED)
        assertEquals(1002, DeliveryNotificationHelper.NOTIFICATION_ID_PROXIMITY)
        assertEquals(1003, DeliveryNotificationHelper.NOTIFICATION_ID_DELIVERED)
        assertEquals(1004, DeliveryNotificationHelper.NOTIFICATION_ID_DINE_IN_SETTLED)
        assertEquals(1004, DeliveryNotificationHelper.NOTIFICATION_ID_BILL_SETTLED_RECEIPT)
    }

    @Test
    fun testFormatDineInSettlementMessage() {
        val msg = DeliveryNotificationHelper.formatDineInSettlementMessage("₹1,200.00", "T6")
        assertTrue(msg.contains("₹1,200.00"))
        assertTrue(msg.contains("T6"))
        assertTrue(msg.contains("Bill Settled!"))
    }
}
