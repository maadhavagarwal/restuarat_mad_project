package com.restaurant.mobile.firebase

import com.restaurant.mobile.delivery.AddOn
import com.restaurant.mobile.delivery.Dish
import com.restaurant.mobile.delivery.Restaurant
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying serialization and data consistency for FirebaseMenuRepository.
 */
class FirebaseMenuRepositoryTest {

    @Test
    fun testDishToMapSerialization() {
        val dish = Dish(
            id = "dish-101",
            restaurantId = "rest-1",
            category = "Biryani",
            name = "Dum Biryani",
            description = "Fragrant basmati rice with aromatic spices",
            price = 32000L,
            image = "https://example.com/biryani.jpg",
            vegetarian = false,
            available = true,
            addons = listOf(
                AddOn(id = "addon-1", name = "Extra Raita", price = 4000L),
                AddOn(id = "addon-2", name = "Mirchi ka Salan", price = 5000L)
            )
        )

        val map = FirebaseMenuRepository.dishToMap(dish)

        assertEquals("dish-101", map["id"])
        assertEquals("rest-1", map["restaurantId"])
        assertEquals("Biryani", map["category"])
        assertEquals("Dum Biryani", map["name"])
        assertEquals("Fragrant basmati rice with aromatic spices", map["description"])
        assertEquals(32000L, map["price"])
        assertEquals("https://example.com/biryani.jpg", map["image"])
        assertEquals(false, map["vegetarian"])
        assertEquals(true, map["available"])

        @Suppress("UNCHECKED_CAST")
        val addons = map["addons"] as List<Map<String, Any>>
        assertEquals(2, addons.size)
        assertEquals("addon-1", addons[0]["id"])
        assertEquals("Extra Raita", addons[0]["name"])
        assertEquals(4000L, addons[0]["price"])
        assertTrue(map.containsKey("updatedAt"))
    }

    @Test
    fun testDishWithoutAddonsSerialization() {
        val dish = Dish(
            id = "dish-102",
            restaurantId = "rest-1",
            category = "Beverages",
            name = "Masala Chai",
            description = "Spiced hot tea",
            price = 5000L,
            image = "https://example.com/tea.jpg",
            vegetarian = true,
            available = false
        )

        val map = FirebaseMenuRepository.dishToMap(dish)

        assertEquals("dish-102", map["id"])
        assertEquals(5000L, map["price"])
        assertEquals(false, map["available"])
        assertEquals(true, map["vegetarian"])

        @Suppress("UNCHECKED_CAST")
        val addons = map["addons"] as List<Map<String, Any>>
        assertTrue(addons.isEmpty())
    }

    @Test
    fun testRestaurantDataIntegrity() {
        val restaurant = Restaurant(
            id = "rest-test",
            name = "Test Kitchen",
            cuisine = "North Indian",
            description = "Authentic curries and rotis",
            image = "https://example.com/kitchen.jpg",
            address = "100ft Road",
            latitude = 12.9716,
            longitude = 77.5946,
            deliveryMinutes = 25,
            deliveryFee = 3500L,
            open = true
        )

        assertEquals("rest-test", restaurant.id)
        assertEquals("Test Kitchen", restaurant.name)
        assertEquals(25, restaurant.deliveryMinutes)
        assertEquals(3500L, restaurant.deliveryFee)
        assertTrue(restaurant.open)
    }
}
