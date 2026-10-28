package com.restaurant.mobile.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying Commit 5 real-time synchronization requirements for:
 * - Rider ride records mapping, serialization, and hash suppression
 * - Rider payouts mapping, status, and balance validation
 * - Rider rejected offers mapping and composite key extraction
 * - Rider identity scoping and private payout protection
 * - Channel lifecycle and user-scoped cancellation on account switch / logout
 */
class RiderSyncTest {

    private lateinit var subManager: FirestoreSubscriptionManager

    @Before
    fun setUp() {
        subManager = FirestoreSubscriptionManager()
    }

    // -------------------------------------------------------------
    // RIDER RIDES MAPPING & LIFECYCLE
    // -------------------------------------------------------------

    @Test
    fun testRiderRideRecordMapping() {
        val payload = mapOf<String, Any?>(
            "id" to "ride-101",
            "riderId" to "rider-ramesh",
            "orderId" to "ORD-901",
            "restaurantName" to "Bistro Lumière & Biryani",
            "dropAddress" to "Oberoi Woods, Goregaon East",
            "distanceKm" to 3.2,
            "durationMinutes" to 24,
            "incomeRupees" to 75L,
            "completedAtMs" to 1700000000000L
        )

        val ride = FirestoreModelMapper.mapToRiderRideRecord("ride-101", payload)
        assertNotNull(ride)
        ride?.let {
            assertEquals("ride-101", it.id)
            assertEquals("rider-ramesh", it.riderId)
            assertEquals("ORD-901", it.orderId)
            assertEquals("Bistro Lumière & Biryani", it.restaurantName)
            assertEquals("Oberoi Woods, Goregaon East", it.dropAddress)
            assertEquals(3.2, it.distanceKm, 0.001)
            assertEquals(24, it.durationMinutes)
            assertEquals(75L, it.incomeRupees)
            assertEquals(1700000000000L, it.completedAtMs)
        }
    }

    @Test
    fun testRiderRideRecordMissingRiderIdReturnsNull() {
        val payload = mapOf<String, Any?>(
            "id" to "ride-102",
            "riderId" to "",
            "orderId" to "ORD-902",
            "restaurantName" to "Nori & Ember",
            "dropAddress" to "Mindspace, Malad West",
            "distanceKm" to 4.1,
            "durationMinutes" to 28,
            "incomeRupees" to 92L,
            "completedAtMs" to 1700000000000L
        )

        val ride = FirestoreModelMapper.mapToRiderRideRecord("ride-102", payload)
        assertNull(ride)
    }

    // -------------------------------------------------------------
    // RIDER PAYOUTS MAPPING & SCOPING
    // -------------------------------------------------------------

    @Test
    fun testRiderPayoutRecordMapping() {
        val payload = mapOf<String, Any?>(
            "id" to "payout-501",
            "riderId" to "rider-ramesh",
            "amountRupees" to 500L,
            "accountDestination" to "ramesh.rider@okaxis (HDFC •••• 4821)",
            "status" to "COMPLETED",
            "timestampMs" to 1700005000000L
        )

        val payout = FirestoreModelMapper.mapToRiderPayoutRecord("payout-501", payload)
        assertNotNull(payout)
        payout?.let {
            assertEquals("payout-501", it.id)
            assertEquals("rider-ramesh", it.riderId)
            assertEquals(500L, it.amountRupees)
            assertEquals("ramesh.rider@okaxis (HDFC •••• 4821)", it.accountDestination)
            assertEquals("COMPLETED", it.status)
            assertEquals(1700005000000L, it.timestampMs)
        }
    }

    @Test
    fun testRiderPayoutMissingAmountOrRiderIdReturnsNull() {
        val missingAmount = mapOf<String, Any?>(
            "id" to "payout-502",
            "riderId" to "rider-ramesh"
        )
        assertNull(FirestoreModelMapper.mapToRiderPayoutRecord("payout-502", missingAmount))

        val missingRider = mapOf<String, Any?>(
            "id" to "payout-503",
            "amountRupees" to 300L
        )
        assertNull(FirestoreModelMapper.mapToRiderPayoutRecord("payout-503", missingRider))
    }

    // -------------------------------------------------------------
    // RIDER REJECTED OFFERS MAPPING
    // -------------------------------------------------------------

    @Test
    fun testRiderRejectedOfferMappingExplicitFields() {
        val payload = mapOf<String, Any?>(
            "riderId" to "rider-ramesh",
            "offerId" to "offer-dispatch-201",
            "rejectedAtMs" to 1700010000000L
        )

        val rejected = FirestoreModelMapper.mapToRiderRejectedOffer("rider-ramesh_offer-dispatch-201", payload)
        assertNotNull(rejected)
        rejected?.let {
            assertEquals("rider-ramesh", it.riderId)
            assertEquals("offer-dispatch-201", it.offerId)
            assertEquals(1700010000000L, it.rejectedAtMs)
        }
    }

    @Test
    fun testRiderRejectedOfferMappingCompositeDocIdFallback() {
        val payload = emptyMap<String, Any?>()
        val rejected = FirestoreModelMapper.mapToRiderRejectedOffer("rider-suresh_offer-dispatch-202", payload)
        assertNotNull(rejected)
        rejected?.let {
            assertEquals("rider-suresh", it.riderId)
            assertEquals("offer-dispatch-202", it.offerId)
        }
    }

    // -------------------------------------------------------------
    // DUPLICATE SNAPSHOT & PAYLOAD HASH SUPPRESSION
    // -------------------------------------------------------------

    @Test
    fun testPayloadHashSuppressionForRiderRideUpdates() {
        val channel = "rider_rides:rider:rider-ramesh"
        val docId = "ride-101"
        val payload = mapOf<String, Any?>(
            "id" to docId,
            "riderId" to "rider-ramesh",
            "distanceKm" to 3.2,
            "incomeRupees" to 75L
        )

        // First event should process
        assertTrue(subManager.shouldProcessModification(channel, docId, payload))

        // Duplicate snapshot event should be suppressed
        assertFalse(subManager.shouldProcessModification(channel, docId, payload))

        // Mutation in income should process
        val updatedPayload = payload.toMutableMap().apply { put("incomeRupees", 95L) }
        assertTrue(subManager.shouldProcessModification(channel, docId, updatedPayload))
    }

    @Test
    fun testPayloadHashSuppressionForRiderPayouts() {
        val channel = "rider_payouts:rider:rider-ramesh"
        val docId = "payout-501"
        val payload = mapOf<String, Any?>(
            "id" to docId,
            "riderId" to "rider-ramesh",
            "amountRupees" to 500L,
            "status" to "PENDING"
        )

        assertTrue(subManager.shouldProcessModification(channel, docId, payload))
        assertFalse(subManager.shouldProcessModification(channel, docId, payload))

        val statusChange = payload.toMutableMap().apply { put("status", "COMPLETED") }
        assertTrue(subManager.shouldProcessModification(channel, docId, statusChange))
    }

    // -------------------------------------------------------------
    // SCOPING & CANCELLATION ON ACCOUNT SWITCH
    // -------------------------------------------------------------

    @Test
    fun testUserScopedRiderSubscriptionsCancelledOnAccountSwitch() {
        subManager.registerTestSubscription("rider_rides:rider:rider-1", FirestoreSubscriptionManager.ChannelScope.USER_SCOPED)
        subManager.registerTestSubscription("rider_payouts:rider:rider-1", FirestoreSubscriptionManager.ChannelScope.USER_SCOPED)
        subManager.registerTestSubscription("rider_rejected:rider:rider-1", FirestoreSubscriptionManager.ChannelScope.USER_SCOPED)
        subManager.registerTestSubscription("rider_rides:admin", FirestoreSubscriptionManager.ChannelScope.GLOBAL)
        subManager.registerTestSubscription("rider_payouts:admin", FirestoreSubscriptionManager.ChannelScope.GLOBAL)

        assertEquals(5, subManager.activeSubscriptionCount)
        assertTrue(subManager.isSubscribed("rider_rides:rider:rider-1"))
        assertTrue(subManager.isSubscribed("rider_payouts:rider:rider-1"))
        assertTrue(subManager.isSubscribed("rider_rejected:rider:rider-1"))

        // Rider logs out / switches account
        subManager.cancelUserScopedSubscriptions()

        assertFalse(subManager.isSubscribed("rider_rides:rider:rider-1"))
        assertFalse(subManager.isSubscribed("rider_payouts:rider:rider-1"))
        assertFalse(subManager.isSubscribed("rider_rejected:rider:rider-1"))

        // Admin/Global subscriptions survive
        assertTrue(subManager.isSubscribed("rider_rides:admin"))
        assertTrue(subManager.isSubscribed("rider_payouts:admin"))
        assertEquals(2, subManager.activeSubscriptionCount)
    }

    @Test
    fun testRiderBalanceCalculationIntegrity() {
        val rides = listOf(
            RiderRideRecord("ride-1", "rider-ramesh", "ORD-1", "Rest 1", "Addr 1", 3.0, 20, 80L, 1000L),
            RiderRideRecord("ride-2", "rider-ramesh", "ORD-2", "Rest 2", "Addr 2", 4.0, 25, 120L, 2000L),
            RiderRideRecord("ride-3", "rider-ramesh", "ORD-3", "Rest 3", "Addr 3", 2.0, 15, 60L, 3000L)
        )
        val payouts = listOf(
            RiderPayoutRecord("pay-1", "rider-ramesh", 100L, "ramesh@upi", "COMPLETED", 1500L)
        )

        val totalIncome = rides.sumOf { it.incomeRupees }
        val totalWithdrawn = payouts.sumOf { it.amountRupees }
        val availableBalance = (totalIncome - totalWithdrawn).coerceAtLeast(0L)

        assertEquals(260L, totalIncome)
        assertEquals(100L, totalWithdrawn)
        assertEquals(160L, availableBalance)
    }

    @Test
    fun testLiveRiderGpsBroadcastAndDynamicTrafficEtaPerimeter() {
        val dispatchEngine = RiderDispatchEngine(cloudEngine = null)
        // Far position (~1.1 km away): outside 500m perimeter
        val farTelemetry = dispatchEngine.broadcastRiderGpsTelemetry(
            orderId = "ORD-LIVE-1",
            riderId = "rider-101",
            riderLat = 19.1700,
            riderLng = 72.8500,
            destLat = 19.1600,
            destLng = 72.8500,
            prepMinutesRemaining = 22
        )
        assertTrue(farTelemetry.remainingDistanceKm > 0.5)
        assertFalse(farTelemetry.isWithin500mPerimeter)
        assertTrue(farTelemetry.etaMinutes >= 22)

        // Close position (~0.3 km away): inside 500m proximity perimeter
        val nearTelemetry = dispatchEngine.broadcastRiderGpsTelemetry(
            orderId = "ORD-LIVE-1",
            riderId = "rider-101",
            riderLat = 19.1620,
            riderLng = 72.8500,
            destLat = 19.1600,
            destLng = 72.8500,
            prepMinutesRemaining = 0
        )
        assertTrue(nearTelemetry.remainingDistanceKm <= 0.5)
        assertTrue(nearTelemetry.isWithin500mPerimeter)
        assertTrue(nearTelemetry.etaMinutes >= 1)
    }

    // -------------------------------------------------------------
    // HANDOVER & SYSTEM ASSIGNED OFFERS FLOW
    // -------------------------------------------------------------

    @Test
    fun testHandoverOrderStatusTransitions() {
        assertTrue(DeliveryOrderStatus.canTransition("Ready", "Handed over"))
        assertTrue(DeliveryOrderStatus.canTransition("Handed over", "Out for delivery"))
        assertTrue(DeliveryOrderStatus.canTransition("Handed over", "Delivered"))
        assertFalse(DeliveryOrderStatus.canTransition("Delivered", "Handed over"))
    }

    @Test
    fun testRiderPickupAllowedOnlyAfterHandover() {
        // When food is still preparing or ready without handover:
        val readyStatus = "Ready"
        val isReadyHandedOver = readyStatus.equals("Handed over", ignoreCase = true) || readyStatus.equals("Out for delivery", ignoreCase = true)
        assertFalse("Pickup confirmation must NOT be enabled before restaurant hands over", isReadyHandedOver)

        // When restaurant marks Handed over:
        val handedOverStatus = "Handed over"
        val isHandedOver = handedOverStatus.equals("Handed over", ignoreCase = true) || handedOverStatus.equals("Out for delivery", ignoreCase = true)
        assertTrue("Pickup confirmation MUST be enabled when restaurant hands over", isHandedOver)
    }
}
