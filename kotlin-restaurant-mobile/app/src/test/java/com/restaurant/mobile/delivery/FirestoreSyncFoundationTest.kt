package com.restaurant.mobile.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FirestoreSyncFoundationTest {

    private lateinit var subManager: FirestoreSubscriptionManager

    @Before
    fun setUp() {
        subManager = FirestoreSubscriptionManager()
    }

    @Test
    fun testInitialSubscriptionState() {
        assertEquals(0, subManager.activeSubscriptionCount)
        assertFalse(subManager.isSubscribed("orders:usr-1"))
        assertFalse(subManager.isSubscribed("catalog:restaurants"))
    }

    @Test
    fun testCancelSubscriptionCleansState() {
        subManager.cancelSubscription("non_existent_channel")
        assertEquals(0, subManager.activeSubscriptionCount)
    }

    @Test
    fun testCancelAllClearsEverything() {
        subManager.cancelAll()
        assertEquals(0, subManager.activeSubscriptionCount)
    }

    @Test
    fun testCancelUserScopedSubscriptionsLeavesGlobalUntouched() {
        subManager.cancelUserScopedSubscriptions()
        assertEquals(0, subManager.activeSubscriptionCount)
    }

    @Test
    fun testModelMapperDeliveryOrderValidData() {
        val payload = mapOf<String, Any?>(
            "id" to "ord-test-101",
            "userId" to "usr-aarav",
            "restaurantId" to "rest-1",
            "restaurantName" to "Bistro Lumière",
            "status" to "Confirmed",
            "paymentMethod" to "UPI",
            "paymentStatus" to "Paid (provider verified)",
            "total" to 450L
        )

        val order = FirestoreModelMapper.mapToDeliveryOrder("ord-test-101", payload)
        assertNotNull(order)
        assertEquals("ord-test-101", order!!.id)
        assertEquals("usr-aarav", order.userId)
        assertEquals("rest-1", order.restaurantId)
        assertEquals("Bistro Lumière", order.restaurantName)
        assertEquals("Confirmed", order.status)
        assertEquals("UPI", order.paymentMethod)
        assertEquals("Paid (provider verified)", order.paymentStatus)
        assertEquals(45000L, order.quote.total)
    }

    @Test
    fun testModelMapperDeliveryOrderMalformedFallback() {
        val brokenPayload = mapOf<String, Any?>(
            "id" to "ord-broken",
            "quote" to "{invalid json",
            "address" to "{broken address json",
            "total" to 300L
        )

        val order = FirestoreModelMapper.mapToDeliveryOrder("ord-broken", brokenPayload)
        assertNotNull(order)
        assertEquals("ord-broken", order!!.id)
        assertNotNull(order.quote)
        assertEquals(30000L, order.quote.total)
        assertNotNull(order.address)
        assertEquals("Home", order.address.label)
    }

    @Test
    fun testModelMapperTableBooking() {
        val payload = mapOf<String, Any?>(
            "id" to "TB-12345",
            "userId" to "usr-1",
            "userName" to "Aarav Sharma",
            "userPhone" to "+91 98765 43210",
            "tableNumber" to "T4",
            "partySize" to 4L,
            "bookingDate" to "2026-10-28",
            "timeSlot" to "8:00 PM",
            "seatingArea" to "Rooftop Terrace",
            "status" to TableBookingStatus.CONFIRMED
        )

        val booking = FirestoreModelMapper.mapToTableBooking("TB-12345", payload)
        assertNotNull(booking)
        assertEquals("TB-12345", booking!!.id)
        assertEquals("usr-1", booking.userId)
        assertEquals("Aarav Sharma", booking.userName)
        assertEquals("T4", booking.tableNumber)
        assertEquals(4, booking.partySize)
        assertEquals("2026-10-28", booking.bookingDate)
        assertEquals("8:00 PM", booking.timeSlot)
        assertEquals("Rooftop Terrace", booking.seatingArea)
        assertEquals(TableBookingStatus.CONFIRMED, booking.status)
    }

    @Test
    fun testModelMapperRestaurant() {
        val payload = mapOf<String, Any?>(
            "id" to "rest-1",
            "name" to "Bistro Lumière",
            "cuisine" to "French & Contemporary",
            "open" to true,
            "rating" to 4.9,
            "deliveryMinutes" to 30L
        )

        val restaurant = FirestoreModelMapper.mapToRestaurant("rest-1", payload)
        assertNotNull(restaurant)
        assertEquals("rest-1", restaurant!!.id)
        assertEquals("Bistro Lumière", restaurant.name)
        assertEquals("French & Contemporary", restaurant.cuisine)
        assertTrue(restaurant.open)
        assertEquals(4.9, restaurant.rating, 0.01)
        assertEquals(30, restaurant.deliveryMinutes)
    }

    @Test
    fun testModelMapperRestaurantRequiresName() {
        val invalidPayload = mapOf<String, Any?>(
            "id" to "rest-missing-name"
        )
        val restaurant = FirestoreModelMapper.mapToRestaurant("rest-missing-name", invalidPayload)
        assertNull(restaurant)
    }

    @Test
    fun testModelMapperDish() {
        val payload = mapOf<String, Any?>(
            "id" to "dish-101",
            "restaurantId" to "rest-1",
            "name" to "Truffle Fettuccine",
            "category" to "Mains",
            "price" to 42000L,
            "vegetarian" to true,
            "available" to true
        )

        val dish = FirestoreModelMapper.mapToDish("dish-101", payload)
        assertNotNull(dish)
        assertEquals("dish-101", dish!!.id)
        assertEquals("Truffle Fettuccine", dish.name)
        assertEquals("Mains", dish.category)
        assertEquals(42000L, dish.price)
        assertTrue(dish.vegetarian)
        assertTrue(dish.available)
    }

    @Test
    fun testModelMapperAddress() {
        val payload = mapOf<String, Any?>(
            "id" to "addr-1",
            "label" to "Office",
            "line" to "Level 5, Mindspace IT Park",
            "city" to "Mumbai",
            "postcode" to "400064",
            "phone" to "+91 98112 23344",
            "latitude" to 19.1720,
            "longitude" to 72.8360
        )

        val address = FirestoreModelMapper.mapToAddress("addr-1", payload)
        assertNotNull(address)
        assertEquals("addr-1", address!!.id)
        assertEquals("Office", address.label)
        assertEquals("Level 5, Mindspace IT Park", address.line)
        assertEquals("Mumbai", address.city)
        assertEquals("400064", address.postcode)
        assertEquals(19.1720, address.latitude!!, 0.0001)
        assertEquals(72.8360, address.longitude!!, 0.0001)
    }
}
