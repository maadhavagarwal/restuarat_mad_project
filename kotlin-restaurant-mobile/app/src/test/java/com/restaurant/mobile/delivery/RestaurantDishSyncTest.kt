package com.restaurant.mobile.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RestaurantDishSyncTest {

    private lateinit var subManager: FirestoreSubscriptionManager

    @Before
    fun setUp() {
        subManager = FirestoreSubscriptionManager()
    }

    // -------------------------------------------------------------
    // RESTAURANT MAPPING & OPERATIONAL SETTINGS
    // -------------------------------------------------------------

    @Test
    fun testRestaurantMappingWithAllOperatingSettings() {
        val payload = mapOf<String, Any?>(
            "id" to "rest-bistro",
            "name" to "Bistro Lumière",
            "cuisine" to "French & Mediterranean",
            "description" to "Artisanal bakery and bistro",
            "image" to "https://cloud.storage/bistro.jpg",
            "address" to "Bandra West, Mumbai",
            "latitude" to 19.0596,
            "longitude" to 72.8295,
            "deliveryMinutes" to 30,
            "deliveryFee" to 4900L,
            "open" to true,
            "rating" to 4.9,
            "reviewCount" to 1540,
            "costForTwo" to 1200,
            "tableBookingAvailable" to true,
            "maxPartySize" to 10,
            "prepMinutes" to 24,
            "acceptsDiningPayment" to true,
            "logoUrl" to "https://cloud.storage/bistro_logo.png",
            "fssaiNumber" to "11523006000999",
            "gstNumber" to "27AABCU9603R1ZZ",
            "vibes" to listOf("Romantic", "Cozy", "Fine Dining"),
            "amenities" to listOf("Valet Parking", "Full Bar", "Outdoor Seating")
        )

        val rest = FirestoreModelMapper.mapToRestaurant("rest-bistro", payload)
        assertNotNull(rest)
        rest?.let {
            assertEquals("rest-bistro", it.id)
            assertEquals("Bistro Lumière", it.name)
            assertEquals("French & Mediterranean", it.cuisine)
            assertTrue(it.open)
            assertEquals(24, it.prepMinutes)
            assertTrue(it.acceptsDiningPayment)
            assertEquals("https://cloud.storage/bistro_logo.png", it.logoUrl)
            assertEquals(1540, it.reviewCount)
            assertEquals(1200, it.costForTwo)
            assertEquals(10, it.maxPartySize)
            assertEquals("11523006000999", it.fssaiNumber)
            assertEquals("27AABCU9603R1ZZ", it.gstNumber)
            assertEquals(3, it.vibes.size)
            assertEquals(3, it.amenities.size)
        }
    }

    @Test
    fun testRestaurantOperatingSettingsChanges() {
        val closedForDeliveryPayload = mapOf<String, Any?>(
            "id" to "rest-bistro",
            "name" to "Bistro Lumière",
            "open" to false,
            "prepMinutes" to 25,
            "acceptsDiningPayment" to false
        )

        val rest = FirestoreModelMapper.mapToRestaurant("rest-bistro", closedForDeliveryPayload)
        assertNotNull(rest)
        rest?.let {
            assertFalse(it.open)
            assertEquals(25, it.prepMinutes)
            assertFalse(it.acceptsDiningPayment)
        }
    }

    @Test
    fun testRestaurantSettingsDocMapping() {
        val settingsPayload = mapOf<String, Any?>(
            "restaurantId" to "rest-1",
            "open" to true,
            "prepMinutes" to 23,
            "acceptsDiningPayment" to false,
            "updatedAt" to "2026-10-26T12:00:00Z"
        )

        val settings = FirestoreModelMapper.mapToRestaurantSettings("rest-1", settingsPayload)
        assertNotNull(settings)
        settings?.let {
            assertEquals("rest-1", it.restaurantId)
            assertTrue(it.open)
            assertEquals(23, it.prepMinutes)
            assertFalse(it.acceptsDiningPayment)
            assertEquals("2026-10-26T12:00:00Z", it.updatedAt)
        }
    }

    @Test
    fun testRestaurantSettingsPrepMinutesClamping() {
        val outOfBoundsPayload = mapOf<String, Any?>(
            "restaurantId" to "rest-1",
            "open" to true,
            "prepMinutes" to 99, // Should clamp to 25
            "acceptsDiningPayment" to true
        )

        val settings = FirestoreModelMapper.mapToRestaurantSettings("rest-1", outOfBoundsPayload)
        assertNotNull(settings)
        assertEquals(25, settings?.prepMinutes)
    }

    // -------------------------------------------------------------
    // DISH MAPPING, PRICES & AVAILABILITY
    // -------------------------------------------------------------

    @Test
    fun testDishMappingWithPriceChangeAndAvailability() {
        val dishPayload = mapOf<String, Any?>(
            "id" to "dish-truffle-pasta",
            "restaurantId" to "rest-bistro",
            "category" to "Mains",
            "name" to "Black Truffle Tagliatelle",
            "description" to "Handmade tagliatelle with shaved Umbrian black truffle",
            "price" to 38900L,
            "image" to "https://cloud.storage/pasta.jpg",
            "vegetarian" to true,
            "available" to true
        )

        val dish = FirestoreModelMapper.mapToDish("dish-truffle-pasta", dishPayload)
        assertNotNull(dish)
        dish?.let {
            assertEquals("dish-truffle-pasta", it.id)
            assertEquals("rest-bistro", it.restaurantId)
            assertEquals("Black Truffle Tagliatelle", it.name)
            assertEquals(38900L, it.price)
            assertTrue(it.available)
            assertTrue(it.vegetarian)
        }
    }

    @Test
    fun testDishMappingUnavailableStatus() {
        val outOfStockPayload = mapOf<String, Any?>(
            "id" to "dish-truffle-pasta",
            "restaurantId" to "rest-bistro",
            "name" to "Black Truffle Tagliatelle",
            "price" to 42000L,
            "available" to false
        )

        val dish = FirestoreModelMapper.mapToDish("dish-truffle-pasta", outOfStockPayload)
        assertNotNull(dish)
        dish?.let {
            assertEquals(42000L, it.price)
            assertFalse(it.available)
        }
    }

    @Test
    fun testDishMappingAddonsJsonString() {
        val payload = mapOf<String, Any?>(
            "id" to "dish-croissant",
            "name" to "Butter Croissant",
            "price" to 14900L,
            "addons" to """[{"id":"extra-butter","name":"French Butter","price":3000}]"""
        )

        val dish = FirestoreModelMapper.mapToDish("dish-croissant", payload)
        assertNotNull(dish)
        assertEquals(1, dish?.addons?.size)
        assertEquals("French Butter", dish?.addons?.first()?.name)
        assertEquals(3000L, dish?.addons?.first()?.price)
    }

    @Test
    fun testDishMappingAddonsListFormat() {
        val payload = mapOf<String, Any?>(
            "id" to "dish-croissant",
            "name" to "Butter Croissant",
            "price" to 14900L,
            "addons" to listOf(
                mapOf("id" to "addon-jam", "name" to "Berry Jam", "price" to 2500L)
            )
        )

        val dish = FirestoreModelMapper.mapToDish("dish-croissant", payload)
        assertNotNull(dish)
        assertEquals(1, dish?.addons?.size)
        assertEquals("Berry Jam", dish?.addons?.first()?.name)
        assertEquals(2500L, dish?.addons?.first()?.price)
    }

    @Test
    fun testDishMappingMissingNameReturnsNull() {
        val invalidPayload = mapOf<String, Any?>(
            "id" to "dish-invalid",
            "price" to 10000L
        )

        val dish = FirestoreModelMapper.mapToDish("dish-invalid", invalidPayload)
        assertNull(dish)
    }

    // -------------------------------------------------------------
    // SYNCHRONIZATION LOOP PREVENTION & PAYLOAD HASH SUPPRESSION
    // -------------------------------------------------------------

    @Test
    fun testPayloadHashSuppressionPreventsEchoLoops() {
        val channelId = "catalog:dishes"
        val docId = "dish-101"
        val payload = mapOf<String, Any?>(
            "id" to docId,
            "name" to "Classic Margherita Pizza",
            "price" to 28900L,
            "available" to true
        )

        // Initial recording (e.g. from local save or remote add)
        subManager.recordPayloadHash(channelId, docId, payload)
        assertNotNull(subManager.getTrackedPayloadHash(channelId, docId))

        // When a snapshot listener receives the exact same payload (write echo):
        val shouldProcessDuplicate = subManager.shouldProcessModification(channelId, docId, payload)
        assertFalse("Duplicate snapshot with unchanged payload hash must be suppressed to prevent echo loops", shouldProcessDuplicate)
    }

    @Test
    fun testPayloadHashDetectsPriceUpdate() {
        val channelId = "catalog:dishes"
        val docId = "dish-101"
        val initialPayload = mapOf<String, Any?>(
            "id" to docId,
            "name" to "Classic Margherita Pizza",
            "price" to 28900L,
            "available" to true
        )
        subManager.recordPayloadHash(channelId, docId, initialPayload)

        // Price changes from 28900 to 31900
        val updatedPricePayload = mapOf<String, Any?>(
            "id" to docId,
            "name" to "Classic Margherita Pizza",
            "price" to 31900L,
            "available" to true
        )
        val shouldProcess = subManager.shouldProcessModification(channelId, docId, updatedPricePayload)
        assertTrue("Snapshot with changed price must be processed", shouldProcess)
    }

    @Test
    fun testPayloadHashDetectsAvailabilityToggle() {
        val channelId = "catalog:dishes"
        val docId = "dish-101"
        val initialPayload = mapOf<String, Any?>(
            "id" to docId,
            "name" to "Classic Margherita Pizza",
            "price" to 28900L,
            "available" to true
        )
        subManager.recordPayloadHash(channelId, docId, initialPayload)

        // Availability toggled to false (item marked 86'd / sold out)
        val toggledPayload = mapOf<String, Any?>(
            "id" to docId,
            "name" to "Classic Margherita Pizza",
            "price" to 28900L,
            "available" to false
        )
        val shouldProcess = subManager.shouldProcessModification(channelId, docId, toggledPayload)
        assertTrue("Snapshot with toggled availability must be processed", shouldProcess)
    }

    @Test
    fun testPayloadHashDetectsRestaurantOpenForDeliveryToggle() {
        val channelId = "catalog:restaurant_settings"
        val docId = "rest-1"
        val initialSettings = mapOf<String, Any?>(
            "restaurantId" to docId,
            "open" to true,
            "prepMinutes" to 22,
            "acceptsDiningPayment" to true
        )
        subManager.recordPayloadHash(channelId, docId, initialSettings)

        // Restaurant closed for delivery by admin
        val closedSettings = mapOf<String, Any?>(
            "restaurantId" to docId,
            "open" to false,
            "prepMinutes" to 22,
            "acceptsDiningPayment" to true
        )
        val shouldProcess = subManager.shouldProcessModification(channelId, docId, closedSettings)
        assertTrue("Open for Delivery toggle must be processed", shouldProcess)
    }

    @Test
    fun testPayloadHashDetectsAcceptsDiningPaymentToggle() {
        val channelId = "catalog:restaurant_settings"
        val docId = "rest-1"
        val initialSettings = mapOf<String, Any?>(
            "restaurantId" to docId,
            "open" to true,
            "prepMinutes" to 22,
            "acceptsDiningPayment" to true
        )
        subManager.recordPayloadHash(channelId, docId, initialSettings)

        // Dining pay mode toggled to counter settlement only
        val counterOnlySettings = mapOf<String, Any?>(
            "restaurantId" to docId,
            "open" to true,
            "prepMinutes" to 22,
            "acceptsDiningPayment" to false
        )
        val shouldProcess = subManager.shouldProcessModification(channelId, docId, counterOnlySettings)
        assertTrue("Accepts Dining Payment toggle must be processed", shouldProcess)
    }

    @Test
    fun testDocumentRemovalCleansTrackingHash() {
        val channelId = "catalog:dishes"
        val docId = "dish-deleted"
        val payload = mapOf<String, Any?>("id" to docId, "name" to "Temporary Special", "price" to 19900L)
        subManager.recordPayloadHash(channelId, docId, payload)
        assertEquals(1, subManager.trackedHashCount)

        subManager.removePayloadHash(channelId, docId)
        assertNull(subManager.getTrackedPayloadHash(channelId, docId))
        assertEquals(0, subManager.trackedHashCount)
    }

    @Test
    fun testCancelSubscriptionCleansAllChannelHashes() {
        val channelId = "catalog:restaurants"
        subManager.recordPayloadHash(channelId, "rest-1", mapOf("name" to "R1"))
        subManager.recordPayloadHash(channelId, "rest-2", mapOf("name" to "R2"))
        subManager.recordPayloadHash("catalog:dishes", "dish-1", mapOf("name" to "D1"))
        assertEquals(3, subManager.trackedHashCount)

        subManager.cancelSubscription(channelId)
        assertNull(subManager.getTrackedPayloadHash(channelId, "rest-1"))
        assertNull(subManager.getTrackedPayloadHash(channelId, "rest-2"))
        assertNotNull(subManager.getTrackedPayloadHash("catalog:dishes", "dish-1"))
        assertEquals(1, subManager.trackedHashCount)
    }
}
