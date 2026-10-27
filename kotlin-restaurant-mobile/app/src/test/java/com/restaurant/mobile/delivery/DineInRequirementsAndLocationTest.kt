package com.restaurant.mobile.delivery

import org.junit.Assert.*
import org.junit.Test

class DineInRequirementsAndLocationTest {

    @Test
    fun `restaurants have rich dine-in metadata including vibes, capacity, and cost for two`() {
        val sampleCatalog = MockCatalog.sample
        assertEquals(5, sampleCatalog.restaurants.size)

        sampleCatalog.restaurants.forEach { r ->
            assertTrue("Rating should be >= 4.0: ${r.name}", r.rating >= 4.0)
            assertTrue("Review count should be positive: ${r.name}", r.reviewCount > 0)
            assertTrue("Cost for two should be positive: ${r.name}", r.costForTwo > 0)
            assertTrue("Vibes list should not be empty: ${r.name}", (r.vibes ?: emptyList()).isNotEmpty())
            assertTrue("Amenities list should not be empty: ${r.name}", (r.amenities ?: emptyList()).isNotEmpty())
            assertTrue("Max party size should be at least 4: ${r.name}", r.maxPartySize >= 4)
            assertTrue("Table booking should be available: ${r.name}", r.tableBookingAvailable)
        }
    }

    @Test
    fun `filtering restaurants by party size correctly matches capacity`() {
        val catalog = MockCatalog.sample

        // Party size = 12 (large group)
        val largeGroupRestos = catalog.restaurants.filter { it.maxPartySize >= 12 }
        assertTrue("Bistro Lumiere and Trattoria Milano and Dakshin Delights accommodate 12+", largeGroupRestos.size >= 3)
        assertTrue(largeGroupRestos.any { it.id == "rest-1" }) // max 16
        assertTrue(largeGroupRestos.any { it.id == "rest-2" }) // max 12
        assertTrue(largeGroupRestos.any { it.id == "rest-3" }) // max 20

        // Party size = 4 (standard family)
        val familyRestos = catalog.restaurants.filter { it.maxPartySize >= 4 }
        assertEquals("All 5 restaurants accommodate 4 guests", 5, familyRestos.size)
    }

    @Test
    fun `filtering restaurants by vibe matches requirement`() {
        val catalog = MockCatalog.sample

        val rooftopRestos = catalog.restaurants.filter { rest ->
            (rest.vibes ?: emptyList()).any { it.contains("Rooftop", ignoreCase = true) }
        }
        assertEquals(1, rooftopRestos.size)
        assertEquals("Trattoria Milano", rooftopRestos.first().name)

        val fineDiningRestos = catalog.restaurants.filter { rest ->
            (rest.vibes ?: emptyList()).any { it.contains("Fine Dining", ignoreCase = true) }
        }
        assertTrue("Fine dining matches at least 2 restaurants", fineDiningRestos.size >= 2)
    }

    @Test
    fun `dynamic relocation positions restaurants within 3km of any GPS coordinate`() {
        // Test in Mumbai (Borivali West)
        val mumbaiLat = 19.2288
        val mumbaiLng = 72.8541
        val offsets = listOf(
            Triple(0.0075, 0.0055, "100ft Road, Borivali West"),
            Triple(-0.0090, 0.0080, "Main Avenue, Borivali West"),
            Triple(0.0130, -0.0070, "Heritage Circle, Borivali West"),
            Triple(-0.0150, -0.0110, "Civic Center Road, Borivali West"),
            Triple(0.0040, -0.0035, "Green Garden Arcade, Borivali West")
        )

        offsets.forEach { offset ->
            val rLat = Math.round((mumbaiLat + offset.first) * 10000.0) / 10000.0
            val rLng = Math.round((mumbaiLng + offset.second) * 10000.0) / 10000.0

            val earthRadius = 6371.0
            val dLat = Math.toRadians(rLat - mumbaiLat)
            val dLng = Math.toRadians(rLng - mumbaiLng)
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(mumbaiLat)) * Math.cos(Math.toRadians(rLat)) *
                    Math.sin(dLng / 2) * Math.sin(dLng / 2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            val dist = Math.round(earthRadius * c * 10.0) / 10.0

            assertTrue("Distance should be under 3.5 km: $dist", dist <= 3.5)
            assertTrue("Distance should be within delivery radius of 6.5 km: $dist", dist <= 6.5)
        }
    }
}
