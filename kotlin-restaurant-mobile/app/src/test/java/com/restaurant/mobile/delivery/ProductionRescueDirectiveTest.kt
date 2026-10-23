package com.restaurant.mobile.delivery

import org.junit.Assert.*
import org.junit.Test

class ProductionRescueDirectiveTest {

    @Test
    fun testKotLifecycleRulesEnforceMonotonicProgression() {
        // Valid forward transitions
        assertTrue("New -> Preparing should be allowed", KotLifecycleRules.canTransition("New", "Preparing"))
        assertTrue("Preparing -> Ready should be allowed", KotLifecycleRules.canTransition("Preparing", "Ready"))
        assertTrue("Ready -> Served should be allowed", KotLifecycleRules.canTransition("Ready", "Served"))
        assertTrue("New -> Ready should be allowed", KotLifecycleRules.canTransition("New", "Ready"))

        // Invalid backward transitions (prevent stale state overwrite)
        assertFalse("Served -> Preparing must be blocked", KotLifecycleRules.canTransition("Served", "Preparing"))
        assertFalse("Ready -> New must be blocked", KotLifecycleRules.canTransition("Ready", "New"))
        assertFalse("Preparing -> New must be blocked", KotLifecycleRules.canTransition("Preparing", "New"))
        assertFalse("Served -> Ready must be blocked", KotLifecycleRules.canTransition("Served", "Ready"))
        assertFalse("Same state transition must return false", KotLifecycleRules.canTransition("Ready", "Ready"))
    }

    @Test
    fun testCurrencySafeBillSplittingZeroRemainder() {
        val totalPaise = 10001L // ₹100.01
        val numGuests = 3
        val shares = BillSplittingRules.splitBillEqually(totalPaise, numGuests)

        assertEquals("Must return 3 shares", 3, shares.size)
        val sumOfShares = shares.sumOf { it.amountPaise }
        assertEquals("Sum of shares must exactly match total paise without penny loss", totalPaise, sumOfShares)
        assertEquals(3334L, shares[0].amountPaise)
        assertEquals(3334L, shares[1].amountPaise)
        assertEquals(3333L, shares[2].amountPaise)
    }

    @Test
    fun testPartySizeFiltering() {
        val sampleRestaurants = listOf(
            Restaurant("r1", "Bistro 1", "North Indian", "", "", "Goregaon West, Mumbai", 19.1663, 72.8480, 25, 4000, maxPartySize = 8),
            Restaurant("r2", "Bistro 2", "Italian", "", "", "Bandra West, Mumbai", 19.0596, 72.8295, 30, 5000, maxPartySize = 12),
            Restaurant("r3", "Bistro 3", "South Indian", "", "", "Andheri West, Mumbai", 19.1136, 72.8697, 20, 3000, maxPartySize = 10)
        )

        // Filter for party size 10: r2 (12) and r3 (10) should match; r1 (8) must be excluded
        val filteredFor10 = sampleRestaurants.filter { it.maxPartySize >= 10 }
        assertEquals(2, filteredFor10.size)
        assertTrue(filteredFor10.any { it.id == "r2" })
        assertTrue(filteredFor10.any { it.id == "r3" })
        assertFalse(filteredFor10.any { it.id == "r1" })

        // Filter for party size 12: only r2 matches
        val filteredFor12 = sampleRestaurants.filter { it.maxPartySize >= 12 }
        assertEquals(1, filteredFor12.size)
        assertEquals("r2", filteredFor12.first().id)
    }

    @Test
    fun testUserRoleRbacHierarchy() {
        assertFalse(UserRole.CUSTOMER.isStaff)
        assertTrue(UserRole.WAITER.isStaff)
        assertTrue(UserRole.CHEF_KDS.isStaff)
        assertTrue(UserRole.ADMIN.isStaff)
        assertTrue(UserRole.MASTER_ADMIN.isStaff)

        assertFalse(UserRole.WAITER.isAdmin)
        assertFalse(UserRole.CHEF_KDS.isAdmin)
        assertTrue(UserRole.ADMIN.isAdmin)
        assertTrue(UserRole.MASTER_ADMIN.isAdmin)

        assertFalse(UserRole.ADMIN.isMasterAdmin)
        assertTrue(UserRole.MASTER_ADMIN.isMasterAdmin)
    }

    @Test
    fun testDineInPaymentRulesSettledValidation() {
        assertTrue(DineInPaymentRules.isSettledOrPaid("SETTLED"))
        assertTrue(DineInPaymentRules.isSettledOrPaid("PAID"))
        assertTrue(DineInPaymentRules.isSettledOrPaid("CLOSED"))
        assertFalse(DineInPaymentRules.isSettledOrPaid("OPEN"))
        assertFalse(DineInPaymentRules.isSettledOrPaid("ACTIVE"))
        assertFalse(DineInPaymentRules.isSettledOrPaid("PAYMENT_PENDING"))
    }
}
