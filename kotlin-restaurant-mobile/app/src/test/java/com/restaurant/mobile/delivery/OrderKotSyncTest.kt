package com.restaurant.mobile.delivery

import com.restaurant.mobile.model.KOT
import com.restaurant.mobile.model.KOTItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OrderKotSyncTest {

    private lateinit var subManager: FirestoreSubscriptionManager

    @Before
    fun setUp() {
        subManager = FirestoreSubscriptionManager()
    }

    // -------------------------------------------------------------
    // ORDER MAPPING & LIFECYCLE
    // -------------------------------------------------------------

    @Test
    fun testDeliveryOrderMappingWithFullDetails() {
        val payload = mapOf<String, Any?>(
            "id" to "ORD-9901",
            "userId" to "usr-aarav",
            "restaurantId" to "rest-1",
            "restaurantName" to "Bistro Lumière",
            "total" to 450,
            "deliveryAddress" to "1203 Tower B, Hiranandani Gardens, Powai",
            "paymentMethod" to "UPI",
            "paymentStatus" to "Paid via UPI",
            "status" to "Confirmed",
            "riderId" to "rider-07",
            "riderName" to "Vikram Singh",
            "riderPhone" to "+91 98765 00007",
            "riderVehicle" to "MH 02 CZ 4402 (Ather 450X)",
            "riderRating" to 4.95,
            "riderLatitude" to 19.1197,
            "riderLongitude" to 72.9051,
            "idempotencyKey" to "idem-order-9901"
        )

        val order = FirestoreModelMapper.mapToDeliveryOrder("ORD-9901", payload)
        assertNotNull(order)
        order?.let {
            assertEquals("ORD-9901", it.id)
            assertEquals("usr-aarav", it.userId)
            assertEquals("rest-1", it.restaurantId)
            assertEquals("Bistro Lumière", it.restaurantName)
            assertEquals("Confirmed", it.status)
            assertEquals("UPI", it.paymentMethod)
            assertEquals("Paid via UPI", it.paymentStatus)
            assertEquals("rider-07", it.riderId)
            assertEquals("Vikram Singh", it.riderName)
            assertEquals("idem-order-9901", it.idempotencyKey)
        }
    }

    @Test
    fun testDeliveryOrderStatusTransitions() {
        assertTrue(DeliveryOrderStatus.canTransition("Confirmed", "Preparing"))
        assertTrue(DeliveryOrderStatus.canTransition("Preparing", "Out for delivery"))
        assertTrue(DeliveryOrderStatus.canTransition("Out for delivery", "Delivered"))
        assertTrue(DeliveryOrderStatus.canTransition("Confirmed", "Cancelled"))
        assertFalse(DeliveryOrderStatus.canTransition("Delivered", "Preparing"))
        assertFalse(DeliveryOrderStatus.canTransition("Cancelled", "Confirmed"))
    }

    // -------------------------------------------------------------
    // KOT MAPPING & LIFECYCLE
    // -------------------------------------------------------------

    @Test
    fun testKotMappingFromJsonStringItems() {
        val payload = mapOf<String, Any?>(
            "id" to 101L,
            "orderId" to 5001L,
            "tableNumber" to "T4",
            "tableId" to "T4",
            "round" to 2,
            "roundName" to "Desserts Round",
            "status" to "New",
            "items" to """[{"id":1,"menuItemName":"Tiramisu","quantity":2,"priceAtTimeOfOrder":350.0}]""",
            "customerName" to "Ananya Sharma",
            "createdAt" to "2026-10-26T12:30:00Z"
        )

        val kot = FirestoreModelMapper.mapToKot("KOT-101", payload)
        assertNotNull(kot)
        kot?.let {
            assertEquals(101L, it.id)
            assertEquals(5001L, it.orderId)
            assertEquals("T4", it.tableNumber)
            assertEquals(2, it.round)
            assertEquals("Desserts Round", it.roundName)
            assertEquals("New", it.status)
            assertEquals("Ananya Sharma", it.customerName)
            assertEquals(1, it.items.size)
            assertEquals("Tiramisu", it.items.first().menuItemName)
            assertEquals(2, it.items.first().quantity)
        }
    }

    @Test
    fun testKotMappingFromListFormatItems() {
        val payload = mapOf<String, Any?>(
            "id" to 102L,
            "orderId" to 5002L,
            "tableNumber" to "DELIVERY-ORD-8821",
            "status" to "Preparing",
            "items" to listOf(
                mapOf("id" to 11L, "menuItemName" to "Paneer Tikka Roll", "quantity" to 1, "priceAtTimeOfOrder" to 220.0),
                mapOf("id" to 12L, "menuItemName" to "Mango Lassi", "quantity" to 2, "priceAtTimeOfOrder" to 90.0)
            )
        )

        val kot = FirestoreModelMapper.mapToKot("KOT-102", payload)
        assertNotNull(kot)
        kot?.let {
            assertEquals(102L, it.id)
            assertEquals("DELIVERY-ORD-8821", it.tableNumber)
            assertEquals("Preparing", it.status)
            assertEquals(2, it.items.size)
            assertEquals("Paneer Tikka Roll", it.items[0].menuItemName)
            assertEquals("Mango Lassi", it.items[1].menuItemName)
        }
    }

    @Test
    fun testKotMappingDocIdNumericExtraction() {
        val payload = mapOf<String, Any?>(
            "tableNumber" to "T1",
            "status" to "Ready"
        )

        val kot = FirestoreModelMapper.mapToKot("KOT-849", payload)
        assertNotNull(kot)
        assertEquals(849L, kot?.id)
        assertEquals("Ready", kot?.status)
    }

    @Test
    fun testKotLifecycleRules() {
        assertTrue(KotLifecycleRules.canTransition("New", "Preparing"))
        assertTrue(KotLifecycleRules.canTransition("Preparing", "Ready"))
        assertTrue(KotLifecycleRules.canTransition("Ready", "Served"))
        assertFalse(KotLifecycleRules.canTransition("Served", "Preparing"))
        assertFalse(KotLifecycleRules.canTransition("Ready", "New"))
    }

    // -------------------------------------------------------------
    // FEEDBACK LOOP & DEDUPLICATION PREVENTION
    // -------------------------------------------------------------

    @Test
    fun testOrderPayloadHashSuppressionPreventsEchoLoops() {
        val channelId = "orders:user:usr-10"
        val docId = "ORD-5501"
        val payload = mapOf<String, Any?>(
            "id" to docId,
            "userId" to "usr-10",
            "status" to "Confirmed",
            "total" to 650
        )

        subManager.recordPayloadHash(channelId, docId, payload)
        assertNotNull(subManager.getTrackedPayloadHash(channelId, docId))

        // Same snapshot arriving from cloud must be suppressed
        val shouldProcess = subManager.shouldProcessModification(channelId, docId, payload)
        assertFalse("Unchanged order snapshot must be suppressed to avoid feedback loops", shouldProcess)
    }

    @Test
    fun testOrderPayloadHashDetectsStatusTransition() {
        val channelId = "orders:user:usr-10"
        val docId = "ORD-5501"
        val initialPayload = mapOf<String, Any?>(
            "id" to docId,
            "userId" to "usr-10",
            "status" to "Confirmed",
            "total" to 650
        )
        subManager.recordPayloadHash(channelId, docId, initialPayload)

        // Status transitions to Out for delivery
        val updatedPayload = mapOf<String, Any?>(
            "id" to docId,
            "userId" to "usr-10",
            "status" to "Out for delivery",
            "total" to 650
        )
        val shouldProcess = subManager.shouldProcessModification(channelId, docId, updatedPayload)
        assertTrue("Order status change must be processed", shouldProcess)
    }

    @Test
    fun testKotPayloadHashDetectsPreparingToReadyTransition() {
        val channelId = "kot_tickets:rest-1"
        val docId = "KOT-301"
        val preparingPayload = mapOf<String, Any?>(
            "id" to 301L,
            "tableNumber" to "T3",
            "status" to "Preparing"
        )
        subManager.recordPayloadHash(channelId, docId, preparingPayload)

        // Chef marks KOT Ready
        val readyPayload = mapOf<String, Any?>(
            "id" to 301L,
            "tableNumber" to "T3",
            "status" to "Ready"
        )
        val shouldProcess = subManager.shouldProcessModification(channelId, docId, readyPayload)
        assertTrue("KOT transition to Ready must be processed to notify staff", shouldProcess)
    }

    @Test
    fun testKotRemovalCleansHash() {
        val channelId = "kot_tickets:rest-1"
        val docId = "KOT-99"
        subManager.recordPayloadHash(channelId, docId, mapOf("id" to 99L, "status" to "Served"))
        assertEquals(1, subManager.trackedHashCount)

        subManager.removePayloadHash(channelId, docId)
        assertNull(subManager.getTrackedPayloadHash(channelId, docId))
        assertEquals(0, subManager.trackedHashCount)
    }

    @Test
    fun testCancelUserScopedSubscriptionsClearsOrdersAndKots() {
        subManager.recordPayloadHash("catalog:restaurants", "rest-1", mapOf("name" to "R1"))
        subManager.recordPayloadHash("orders:user:usr-1", "ORD-1", mapOf("id" to "ORD-1"))
        subManager.recordPayloadHash("kot_tickets:rest-1", "KOT-1", mapOf("id" to 1L))

        subManager.cancelSubscription("orders:user:usr-1")
        subManager.cancelSubscription("kot_tickets:rest-1")

        assertNull(subManager.getTrackedPayloadHash("orders:user:usr-1", "ORD-1"))
        assertNull(subManager.getTrackedPayloadHash("kot_tickets:rest-1", "KOT-1"))
        assertNotNull(subManager.getTrackedPayloadHash("catalog:restaurants", "rest-1"))
    }
}
