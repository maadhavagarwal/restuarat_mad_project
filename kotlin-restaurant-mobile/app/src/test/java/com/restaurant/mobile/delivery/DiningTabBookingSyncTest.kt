package com.restaurant.mobile.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Unit tests verifying Commit 4 real-time synchronization requirements for:
 * - TableBookings mapping, status lifecycle, and hash suppression
 * - DiningTab mapping, item serialization (JSON & List), billing calculation, and settlement safety
 * - Table availability and lifecycle state derivation
 * - Channel lifecycle and user-scoped cancellation on account switch / logout
 */
class DiningTabBookingSyncTest {

    private lateinit var subManager: FirestoreSubscriptionManager

    @Before
    fun setUp() {
        subManager = FirestoreSubscriptionManager()
    }

    // -------------------------------------------------------------
    // TABLE BOOKINGS MAPPING & LIFECYCLE
    // -------------------------------------------------------------

    @Test
    fun testTableBookingMappingWithFullDetails() {
        val payload = mapOf<String, Any?>(
            "id" to "TB-00123",
            "restaurantId" to "rest-1",
            "restaurantName" to "Bistro Lumière",
            "tableId" to "T-4",
            "tableNumber" to "T4",
            "userId" to "usr-aarav",
            "userName" to "Aarav Patel",
            "userPhone" to "+91 98765 43210",
            "partySize" to 4,
            "bookingDate" to "2026-10-27",
            "timeSlot" to "8:00 PM",
            "seatingArea" to "Patio / Balcony",
            "specialRequests" to "Window seat for anniversary celebration",
            "status" to TableBookingStatus.CONFIRMED,
            "createdAt" to "2026-10-26T12:00:00Z",
            "updatedAt" to "2026-10-26T12:05:00Z"
        )

        val booking = FirestoreModelMapper.mapToTableBooking("TB-00123", payload)
        assertNotNull(booking)
        booking?.let {
            assertEquals("TB-00123", it.id)
            assertEquals("rest-1", it.restaurantId)
            assertEquals("Bistro Lumière", it.restaurantName)
            assertEquals("T-4", it.tableId)
            assertEquals("T4", it.tableNumber)
            assertEquals("usr-aarav", it.userId)
            assertEquals("Aarav Patel", it.userName)
            assertEquals("+91 98765 43210", it.userPhone)
            assertEquals(4, it.partySize)
            assertEquals("2026-10-27", it.bookingDate)
            assertEquals("8:00 PM", it.timeSlot)
            assertEquals("Patio / Balcony", it.seatingArea)
            assertEquals("Window seat for anniversary celebration", it.specialRequests)
            assertEquals(TableBookingStatus.CONFIRMED, it.status)
            assertEquals("2026-10-26T12:00:00Z", it.createdAt)
            assertEquals("2026-10-26T12:05:00Z", it.updatedAt)
        }
    }

    @Test
    fun testTableBookingStatusValidation() {
        assertTrue(TableBookingStatus.ALL.contains(TableBookingStatus.CONFIRMED))
        assertTrue(TableBookingStatus.ALL.contains(TableBookingStatus.SEATED))
        assertTrue(TableBookingStatus.ALL.contains(TableBookingStatus.COMPLETED))
        assertTrue(TableBookingStatus.ALL.contains(TableBookingStatus.CANCELLED))
        assertFalse(TableBookingStatus.ALL.contains("UNKNOWN_STATUS"))
    }

    @Test
    fun testTableBookingModificationHashSuppression() {
        val channelId = "bookings:user:usr-aarav"
        val docId = "TB-00123"
        val payload = mapOf<String, Any?>(
            "id" to docId,
            "status" to "CONFIRMED",
            "partySize" to 4
        )

        // First event establishes hash baseline
        subManager.recordPayloadHash(channelId, docId, payload)

        // Duplicate snapshot with same content should be suppressed
        assertFalse(subManager.shouldProcessModification(channelId, docId, payload))

        // Mutation (e.g. customer cancels reservation) must pass through
        val updatedPayload = mapOf<String, Any?>(
            "id" to docId,
            "status" to "CANCELLED",
            "partySize" to 4
        )
        assertTrue(subManager.shouldProcessModification(channelId, docId, updatedPayload))
    }

    // -------------------------------------------------------------
    // DINING TABS MAPPING & BILLING
    // -------------------------------------------------------------

    @Test
    fun testDiningTabMappingWithFullDetails() {
        val itemsJson = """
            [
                {
                    "dishId": "dish-101",
                    "name": "Dum Biryani",
                    "quantity": 2,
                    "price": 38000,
                    "total": 76000,
                    "taxRate": 5.0,
                    "specialInstructions": "Extra raita",
                    "round": 1,
                    "roundName": "Mains"
                }
            ]
        """.trimIndent()

        val payload = mapOf<String, Any?>(
            "id" to "TAB-4001",
            "tableId" to "T5",
            "tableNumber" to "T5",
            "customerId" to "usr-priya",
            "customerName" to "Priya Rao",
            "customerPhone" to "+91 99887 76655",
            "bookingId" to "TB-0088",
            "restaurantId" to "rest-1",
            "itemsJson" to itemsJson,
            "subtotal" to 76000L,
            "tax" to 3800L,
            "serviceCharge" to 3800L,
            "tip" to 2000L,
            "isServiceChargeOptedOut" to false,
            "total" to 85600L,
            "status" to DiningTabStatus.OPEN,
            "paymentMethod" to "Card",
            "paymentTransactionId" to "TXN-998877",
            "guestCount" to 3,
            "seatedAt" to "2026-10-26T12:00:00Z",
            "billedAt" to "2026-10-26T13:00:00Z",
            "createdAt" to "2026-10-26T12:00:00Z",
            "updatedAt" to "2026-10-26T13:00:00Z"
        )

        val tab = FirestoreModelMapper.mapToDiningTab("TAB-4001", payload)
        assertNotNull(tab)
        tab?.let {
            assertEquals("TAB-4001", it.id)
            assertEquals("T5", it.tableId)
            assertEquals("T5", it.tableNumber)
            assertEquals("usr-priya", it.customerId)
            assertEquals("Priya Rao", it.customerName)
            assertEquals("+91 99887 76655", it.customerPhone)
            assertEquals("TB-0088", it.bookingId)
            assertEquals("rest-1", it.restaurantId)
            assertEquals(1, it.items.size)
            assertEquals("Dum Biryani", it.items[0].name)
            assertEquals(2, it.items[0].quantity)
            assertEquals(76000L, it.subtotal)
            assertEquals(3800L, it.tax)
            assertEquals(3800L, it.serviceCharge)
            assertEquals(2000L, it.tip)
            assertFalse(it.isServiceChargeOptedOut)
            assertEquals(85600L, it.total)
            assertEquals(DiningTabStatus.OPEN, it.status)
            assertEquals("Card", it.paymentMethod)
            assertEquals("TXN-998877", it.paymentTransactionId)
            assertEquals(3, it.guestCount)
        }
    }

    @Test
    fun testDiningTabMappingFromListMapItems() {
        val payload = mapOf<String, Any?>(
            "id" to "TAB-4002",
            "tableNumber" to "T2",
            "items" to listOf(
                mapOf(
                    "dishId" to "dish-201",
                    "name" to "Paneer Tikka",
                    "quantity" to 1,
                    "price" to 28000L,
                    "total" to 28000L,
                    "round" to 1
                ),
                mapOf(
                    "dishId" to "dish-202",
                    "name" to "Gulab Jamun",
                    "quantity" to 2,
                    "price" to 12000L,
                    "total" to 24000L,
                    "round" to 2
                )
            ),
            "subtotal" to 52000L,
            "total" to 54600L,
            "status" to DiningTabStatus.ACTIVE
        )

        val tab = FirestoreModelMapper.mapToDiningTab("TAB-4002", payload)
        assertNotNull(tab)
        tab?.let {
            assertEquals("TAB-4002", it.id)
            assertEquals("T2", it.tableNumber)
            assertEquals(2, it.items.size)
            assertEquals("Paneer Tikka", it.items[0].name)
            assertEquals("Gulab Jamun", it.items[1].name)
            assertEquals(52000L, it.subtotal)
            assertEquals(DiningTabStatus.ACTIVE, it.status)
        }
    }

    @Test
    fun testSettlementStatusTransitions() {
        // Active non-terminal states
        assertFalse(DineInPaymentRules.isSettledOrPaid(DiningTabStatus.OPEN))
        assertFalse(DineInPaymentRules.isSettledOrPaid(DiningTabStatus.ACTIVE))
        assertFalse(DineInPaymentRules.isSettledOrPaid(DiningTabStatus.PAYMENT_PENDING))

        // Settled / terminal states
        assertTrue(DineInPaymentRules.isSettledOrPaid(DiningTabStatus.SETTLED))
        assertTrue(DineInPaymentRules.isSettledOrPaid(DiningTabStatus.PAID))
        assertTrue(DineInPaymentRules.isSettledOrPaid(DiningTabStatus.CLOSED))
    }

    @Test
    fun testDiningTabBillingCalculationOptOutServiceCharge() {
        val items = listOf(
            TableBillItem(dishId = "d1", name = "Biryani", quantity = 1, price = 40000L, total = 40000L, taxRate = 5.0),
            TableBillItem(dishId = "d2", name = "Raita", quantity = 1, price = 10000L, total = 10000L, taxRate = 5.0)
        )
        // Subtotal = 500.0, Tax (5%) = 25.0
        val withServiceCharge = DiningTab.calculateBilling(
            items = items,
            existingServiceCharge = 25.0,
            isServiceChargeOptedOut = false,
            tip = 50.0
        )
        assertEquals(500.0, withServiceCharge.subtotal, 0.01)
        assertEquals(25.0, withServiceCharge.tax, 0.01)
        assertEquals(25.0, withServiceCharge.serviceCharge, 0.01)
        assertEquals(50.0, withServiceCharge.tip, 0.01)
        assertEquals(600.0, withServiceCharge.total, 0.01)

        val optedOut = DiningTab.calculateBilling(
            items = items,
            existingServiceCharge = 25.0,
            isServiceChargeOptedOut = true,
            tip = 0.0
        )
        assertEquals(500.0, optedOut.subtotal, 0.01)
        assertEquals(25.0, optedOut.tax, 0.01)
        assertEquals(0.0, optedOut.serviceCharge, 0.01)
        assertEquals(525.0, optedOut.total, 0.01)
    }

    @Test
    fun testDiningTabModificationHashSuppression() {
        val channelId = "dining_tab:table:T3"
        val docId = "TAB-900"
        val payload = mapOf<String, Any?>(
            "id" to docId,
            "tableNumber" to "T3",
            "total" to 45000L,
            "status" to "OPEN"
        )

        subManager.recordPayloadHash(channelId, docId, payload)

        // Duplicate event suppressed
        assertFalse(subManager.shouldProcessModification(channelId, docId, payload))

        // Waiter adds an item -> total updates -> must process
        val updatedPayload = mapOf<String, Any?>(
            "id" to docId,
            "tableNumber" to "T3",
            "total" to 65000L,
            "status" to "OPEN"
        )
        assertTrue(subManager.shouldProcessModification(channelId, docId, updatedPayload))
    }

    // -------------------------------------------------------------
    // SUBSCRIPTION SCOPING & LIFECYCLE
    // -------------------------------------------------------------

    @Test
    fun testUserScopedSubscriptionsCancelledOnLogout() {
        // Register user and table scoped subscription channels
        subManager.registerTestSubscription("catalog:global", FirestoreSubscriptionManager.ChannelScope.GLOBAL)
        subManager.registerTestSubscription("bookings:user:usr-aarav", FirestoreSubscriptionManager.ChannelScope.USER_SCOPED)
        subManager.registerTestSubscription("dining_tab:table:T4", FirestoreSubscriptionManager.ChannelScope.USER_SCOPED)

        assertEquals(3, subManager.activeSubscriptionCount)
        assertTrue(subManager.isSubscribed("catalog:global"))
        assertTrue(subManager.isSubscribed("bookings:user:usr-aarav"))
        assertTrue(subManager.isSubscribed("dining_tab:table:T4"))

        // User logs out -> user-scoped listeners cancelled, global catalog preserved
        subManager.cancelUserScopedSubscriptions()

        assertEquals(1, subManager.activeSubscriptionCount)
        assertTrue(subManager.isSubscribed("catalog:global"))
        assertFalse(subManager.isSubscribed("bookings:user:usr-aarav"))
        assertFalse(subManager.isSubscribed("dining_tab:table:T4"))
    }
}
