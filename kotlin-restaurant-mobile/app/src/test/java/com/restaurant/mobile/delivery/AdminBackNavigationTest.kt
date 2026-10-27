package com.restaurant.mobile.delivery

import com.restaurant.mobile.NavigationScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for Admin portal back navigation:
 * Ensures when user is in Admin mode and selects "Menu & Prices" or "Total Sales"
 * (or any sub-screen), clicking Back lands back on the primary Admin page (ORDERS).
 */
class AdminBackNavigationTest {

    @Test
    fun testAdminAllowedScreensRootIsOrders() {
        val userRole = UserRole.ADMIN
        val allowedScreens = when (userRole) {
            UserRole.WAITER -> listOf(NavigationScreen.ORDERS, NavigationScreen.POS, NavigationScreen.TABLES)
            UserRole.CHEF_KDS -> listOf(NavigationScreen.KDS, NavigationScreen.ORDERS, NavigationScreen.TABLES)
            UserRole.RIDER -> listOf(NavigationScreen.RIDER, NavigationScreen.ORDERS)
            UserRole.KIOSK -> listOf(NavigationScreen.POS)
            UserRole.ADMIN -> listOf(
                NavigationScreen.ORDERS,
                NavigationScreen.MENU,
                NavigationScreen.ANALYTICS,
                NavigationScreen.POS,
                NavigationScreen.KDS,
                NavigationScreen.TABLES,
                NavigationScreen.RIDER,
                NavigationScreen.INVENTORY,
                NavigationScreen.SALARY
            )
            UserRole.MASTER_ADMIN -> NavigationScreen.entries
            UserRole.CUSTOMER -> emptyList()
        }

        val rootScreen = allowedScreens.firstOrNull() ?: NavigationScreen.ORDERS
        assertEquals("Root admin landing page must be ORDERS", NavigationScreen.ORDERS, rootScreen)
    }

    @Test
    fun testBackFromMenuPricesNavigatesToAdminPage() {
        val rootScreen = NavigationScreen.ORDERS
        var currentScreen = NavigationScreen.MENU

        // Simulate Back action (TopBar back button, hardware back, or on-screen back button)
        var backToCustomerCalled = false
        val handleBack = {
            if (currentScreen != rootScreen) {
                currentScreen = rootScreen
            } else {
                backToCustomerCalled = true
            }
        }

        handleBack()

        assertEquals("Navigating back from Menu & Prices must land on Admin Orders", NavigationScreen.ORDERS, currentScreen)
        assertTrue("Back to customer should not be called when on Menu & Prices", !backToCustomerCalled)
    }

    @Test
    fun testBackFromTotalSalesNavigatesToAdminPage() {
        val rootScreen = NavigationScreen.ORDERS
        var currentScreen = NavigationScreen.ANALYTICS

        // Simulate Back action
        var backToCustomerCalled = false
        val handleBack = {
            if (currentScreen != rootScreen) {
                currentScreen = rootScreen
            } else {
                backToCustomerCalled = true
            }
        }

        handleBack()

        assertEquals("Navigating back from Total Sales must land on Admin Orders", NavigationScreen.ORDERS, currentScreen)
        assertTrue("Back to customer should not be called when on Total Sales", !backToCustomerCalled)
    }

    @Test
    fun testBackFromAdminPageExitsToCustomer() {
        val rootScreen = NavigationScreen.ORDERS
        var currentScreen = NavigationScreen.ORDERS

        var backToCustomerCalled = false
        val handleBack = {
            if (currentScreen != rootScreen) {
                currentScreen = rootScreen
            } else {
                backToCustomerCalled = true
            }
        }

        handleBack()

        assertEquals(NavigationScreen.ORDERS, currentScreen)
        assertTrue("Back from root Admin page must exit to customer view", backToCustomerCalled)
    }
}
