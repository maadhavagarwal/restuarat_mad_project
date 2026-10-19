package com.restaurant.mobile.delivery

import com.restaurant.mobile.model.CartItem
import com.restaurant.mobile.model.Category
import com.restaurant.mobile.model.KOT
import com.restaurant.mobile.model.KOTItem
import com.restaurant.mobile.model.MenuItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class KotKitchenSyncTest {

    @Test
    fun testKotDisplayTitleFormatting() {
        val kot1 = KOT(
            id = 12L,
            orderId = 101L,
            status = "Ready",
            items = listOf(KOTItem(id = 1L, menuItemName = "Paneer Tikka", quantity = 2)),
            tableNumber = "T3",
            tableId = "T3",
            round = 1,
            roundName = "Starters",
            createdAt = Instant.now().minus(8, ChronoUnit.MINUTES).toString()
        )

        assertEquals("KOT-12", kot1.ticketNumber)
        assertEquals("Table T3 — Round 1 (Starters) — READY", kot1.displayTitle)

        val kot2 = KOT(
            id = 13L,
            orderId = 101L,
            status = "Preparing",
            items = listOf(KOTItem(id = 2L, menuItemName = "Dal Makhani", quantity = 1)),
            tableNumber = "T3",
            tableId = "T3",
            round = 2,
            roundName = "Mains",
            createdAt = Instant.now().minus(2, ChronoUnit.MINUTES).toString()
        )

        assertEquals("KOT-13", kot2.ticketNumber)
        assertEquals("Table T3 — Round 2 (Mains) — PREPARING", kot2.displayTitle)

        // Without round name
        val kotWithoutRoundName = KOT(
            id = 14L,
            status = "New",
            tableNumber = "T1",
            round = 3,
            roundName = ""
        )
        assertEquals("Table T1 — Round 3 — NEW", kotWithoutRoundName.displayTitle)

        // Unassigned table fallback
        val kotUnassigned = KOT(
            id = 15L,
            status = "Ready",
            tableNumber = "",
            round = 1,
            roundName = "Desserts"
        )
        assertEquals("Table Unassigned — Round 1 (Desserts) — READY", kotUnassigned.displayTitle)
    }

    @Test
    fun testKotTicketGroupingByTableAndRound() {
        val tickets = listOf(
            KOT(id = 101L, tableNumber = "T2", round = 2, roundName = "Mains", status = "New"),
            KOT(id = 102L, tableNumber = "T3", round = 2, roundName = "Mains", status = "Preparing"),
            KOT(id = 103L, tableNumber = "T1", round = 1, roundName = "Starters", status = "Ready"),
            KOT(id = 104L, tableNumber = "T3", round = 1, roundName = "Starters", status = "Ready"),
            KOT(id = 105L, tableNumber = "T2", round = 1, roundName = "Starters", status = "Served")
        )

        // Group by table, then sort tickets in each table by round ascending
        val groupedByTable = tickets.groupBy { it.tableNumber.ifBlank { "Unassigned" } }
            .toSortedMap()
            .mapValues { entry -> entry.value.sortedBy { it.round } }

        assertEquals(3, groupedByTable.size)
        assertTrue(groupedByTable.containsKey("T1"))
        assertTrue(groupedByTable.containsKey("T2"))
        assertTrue(groupedByTable.containsKey("T3"))

        val t3Tickets = groupedByTable["T3"]
        assertNotNull(t3Tickets)
        assertEquals(2, t3Tickets?.size)
        assertEquals(1, t3Tickets?.get(0)?.round)
        assertEquals("Starters", t3Tickets?.get(0)?.roundName)
        assertEquals(2, t3Tickets?.get(1)?.round)
        assertEquals("Mains", t3Tickets?.get(1)?.roundName)
    }

    @Test
    fun testElapsedOrderTimeFormatting() {
        val now = Instant.parse("2026-10-19T14:30:00Z")

        // 5 seconds ago -> Ordered just now
        val fiveSecsAgo = now.minus(5, ChronoUnit.SECONDS).toString()
        assertEquals("Ordered just now", KotTimerRules.formatElapsedOrderTime(fiveSecsAgo, now))

        // 35 seconds ago -> Ordered 35s ago
        val thirtyFiveSecsAgo = now.minus(35, ChronoUnit.SECONDS).toString()
        assertEquals("Ordered 35s ago", KotTimerRules.formatElapsedOrderTime(thirtyFiveSecsAgo, now))

        // 8 minutes ago -> Ordered 8m ago
        val eightMinsAgo = now.minus(8, ChronoUnit.MINUTES).toString()
        assertEquals("Ordered 8m ago", KotTimerRules.formatElapsedOrderTime(eightMinsAgo, now))

        // Exactly 1 hour ago -> Ordered 1h ago
        val oneHourAgo = now.minus(60, ChronoUnit.MINUTES).toString()
        assertEquals("Ordered 1h ago", KotTimerRules.formatElapsedOrderTime(oneHourAgo, now))

        // 1 hour 15 minutes ago -> Ordered 1h 15m ago
        val hourAndFifteen = now.minus(75, ChronoUnit.MINUTES).toString()
        assertEquals("Ordered 1h 15m ago", KotTimerRules.formatElapsedOrderTime(hourAndFifteen, now))

        // 2 days ago -> Ordered 2d ago
        val twoDaysAgo = now.minus(48, ChronoUnit.HOURS).toString()
        assertEquals("Ordered 2d ago", KotTimerRules.formatElapsedOrderTime(twoDaysAgo, now))

        // Future timestamp (clock skew tolerance) -> Ordered just now
        val future = now.plus(10, ChronoUnit.SECONDS).toString()
        assertEquals("Ordered just now", KotTimerRules.formatElapsedOrderTime(future, now))

        // Missing / empty / null timestamps -> Ordered just now
        assertEquals("Ordered just now", KotTimerRules.formatElapsedOrderTime(null, now))
        assertEquals("Ordered just now", KotTimerRules.formatElapsedOrderTime("", now))
        assertEquals("Ordered just now", KotTimerRules.formatElapsedOrderTime("   ", now))

        // Malformed / unparseable timestamp -> Ordered recently (graceful degradation)
        assertEquals("Ordered recently", KotTimerRules.formatElapsedOrderTime("invalid-timestamp", now))

        // Epoch millis format support
        val epochMsString = (now.toEpochMilli() - (8 * 60 * 1000)).toString()
        assertEquals("Ordered 8m ago", KotTimerRules.formatElapsedOrderTime(epochMsString, now))
    }

    @Test
    fun testReadyNotificationFormatting() {
        val msg1 = KotTimerRules.formatReadyNotificationMessage(12L, "T3")
        assertEquals("Order #KOT-12 for Table T3 is Ready for Pickup!", msg1)

        val msg2 = KotTimerRules.formatReadyNotificationMessage("12", "T3")
        assertEquals("Order #KOT-12 for Table T3 is Ready for Pickup!", msg2)

        val msg3 = KotTimerRules.formatReadyNotificationMessage("KOT-12", "T3")
        assertEquals("Order #KOT-12 for Table T3 is Ready for Pickup!", msg3)

        val msgUnassigned = KotTimerRules.formatReadyNotificationMessage(99L, "")
        assertEquals("Order #KOT-99 for Table Unassigned is Ready for Pickup!", msgUnassigned)
    }

    @Test
    fun testDuplicateReadyNotificationPrevention() {
        val notifiedKotIds = mutableSetOf<String>()
        val notificationsFired = mutableListOf<String>()

        fun onKotStatusChanged(kotId: String, newStatus: String, tableNumber: String) {
            if (newStatus.equals("Ready", ignoreCase = true)) {
                if (!notifiedKotIds.contains(kotId)) {
                    notifiedKotIds.add(kotId)
                    val message = KotTimerRules.formatReadyNotificationMessage(kotId, tableNumber)
                    notificationsFired.add(message)
                }
            }
        }

        // First transition to Ready -> fires notification
        onKotStatusChanged("KOT-12", "Ready", "T3")
        assertEquals(1, notificationsFired.size)
        assertEquals("Order #KOT-12 for Table T3 is Ready for Pickup!", notificationsFired[0])

        // Subsequent call with same Ready status (e.g., UI recomposition or sync poll) -> does NOT fire again
        onKotStatusChanged("KOT-12", "Ready", "T3")
        assertEquals(1, notificationsFired.size)

        // Another ticket marks Ready -> fires for that new ticket
        onKotStatusChanged("KOT-14", "Ready", "T1")
        assertEquals(2, notificationsFired.size)
        assertEquals("Order #KOT-14 for Table T1 is Ready for Pickup!", notificationsFired[1])

        // Non-ready status transitions do not fire ready notification
        onKotStatusChanged("KOT-15", "Preparing", "T2")
        assertEquals(2, notificationsFired.size)
    }

    @Test
    fun testCustomerLiveBillAggregationAcrossMultipleRounds() {
        // Round 1 items (Starters)
        val round1Items = listOf(
            TableBillItem(
                id = "item-r1-1",
                menuItemId = 1L,
                name = "Paneer Tikka",
                price = 250.0,
                quantity = 2,
                taxRate = 5.0,
                round = 1,
                roundName = "Starters"
            ),
            TableBillItem(
                id = "item-r1-2",
                menuItemId = 2L,
                name = "Crispy Corn",
                price = 180.0,
                quantity = 1,
                taxRate = 5.0,
                round = 1,
                roundName = "Starters"
            )
        )

        // Place Round 1
        var diningTab = WaiterTablePairingRules.appendOrOpenDiningTab(
            existingTab = null,
            tableNumber = "T3",
            tableId = "T3",
            newItems = round1Items
        )

        assertEquals(2, diningTab.items.size)
        // Subtotal = (250 * 2) + (180 * 1) = 500 + 180 = 680
        assertEquals(680.0, diningTab.subtotalRupees, 0.01)
        // Tax = 5% of 680 = 34
        assertEquals(34.0, diningTab.taxRupees, 0.01)
        assertEquals(714.0, diningTab.totalRupees, 0.01)

        // Verify round 1 grouping
        val groupedRound1 = diningTab.itemsGroupedByRound()
        assertEquals(1, groupedRound1.size)
        assertEquals(2, groupedRound1[1]?.size)

        // Round 2 items (Mains)
        val round2Items = listOf(
            TableBillItem(
                id = "item-r2-1",
                menuItemId = 3L,
                name = "Dal Makhani",
                price = 320.0,
                quantity = 1,
                taxRate = 5.0,
                round = 2,
                roundName = "Mains"
            ),
            TableBillItem(
                id = "item-r2-2",
                menuItemId = 4L,
                name = "Butter Naan",
                price = 50.0,
                quantity = 4,
                taxRate = 5.0,
                round = 2,
                roundName = "Mains"
            )
        )

        // Place Round 2: Appends to existing active tab
        diningTab = WaiterTablePairingRules.appendOrOpenDiningTab(
            existingTab = diningTab,
            tableNumber = "T3",
            tableId = "T3",
            newItems = round2Items
        )

        // Now tab contains all 4 items across both rounds
        assertEquals(4, diningTab.items.size)

        // Subtotal = 680 (Round 1) + 320 + (50 * 4 = 200) = 680 + 520 = 1200
        assertEquals(1200.0, diningTab.subtotalRupees, 0.01)
        // Tax = 5% of 1200 = 60
        assertEquals(60.0, diningTab.taxRupees, 0.01)
        // Total = 1260
        assertEquals(1260.0, diningTab.totalRupees, 0.01)

        // Verify items grouped by round in the customer's live bill
        val groupedBothRounds = diningTab.itemsGroupedByRound()
        assertEquals(2, groupedBothRounds.size)

        val r1Group = groupedBothRounds[1]
        assertNotNull(r1Group)
        assertEquals(2, r1Group?.size)
        assertEquals("Paneer Tikka", r1Group?.get(0)?.name)
        assertEquals("Crispy Corn", r1Group?.get(1)?.name)

        val r2Group = groupedBothRounds[2]
        assertNotNull(r2Group)
        assertEquals(2, r2Group?.size)
        assertEquals("Dal Makhani", r2Group?.get(0)?.name)
        assertEquals("Butter Naan", r2Group?.get(1)?.name)

        // Confirm previously ordered items are intact
        assertTrue(diningTab.items.any { it.name == "Paneer Tikka" && it.round == 1 })
        assertTrue(diningTab.items.any { it.name == "Crispy Corn" && it.round == 1 })
        assertTrue(diningTab.items.any { it.name == "Dal Makhani" && it.round == 2 })
        assertTrue(diningTab.items.any { it.name == "Butter Naan" && it.round == 2 })
    }

    @Test
    fun testKotStatusUpdatesDoNotMutateDiningTabItems() {
        // Prepare active tab with items
        val initialItems = listOf(
            TableBillItem(
                id = "item-1",
                menuItemId = 10L,
                name = "Masala Dosa",
                price = 150.0,
                quantity = 2,
                taxRate = 5.0,
                round = 1,
                roundName = "Starters"
            )
        )
        val tab = WaiterTablePairingRules.appendOrOpenDiningTab(
            existingTab = null,
            tableNumber = "T4",
            tableId = "T4",
            newItems = initialItems
        )

        val initialItemCount = tab.items.size
        val initialSubtotal = tab.subtotal
        val initialTotal = tab.total

        // Simulate KOT status changes: NEW -> PREPARING -> READY -> SERVED
        val kot = KOT(
            id = 201L,
            status = "New",
            tableNumber = "T4",
            round = 1,
            roundName = "Starters"
        )

        val statuses = listOf("Preparing", "Ready", "Served")
        for (st in statuses) {
            kot.status = st
            // The customer's dining tab items and totals must remain identical
            assertEquals(initialItemCount, tab.items.size)
            assertEquals(initialSubtotal, tab.subtotal)
            assertEquals(initialTotal, tab.total)
        }
    }

    @Test
    fun testDetectRoundNameFromCategories() {
        val categories = listOf(
            Category(id = 1L, name = "Starters & Appetizers"),
            Category(id = 2L, name = "Main Course"),
            Category(id = 3L, name = "Desserts & Sweets"),
            Category(id = 4L, name = "Cold Beverages")
        )

        // Starters cart
        val starterCart = listOf(
            CartItem(menuItem = MenuItem(id = 1, categoryId = 1, name = "Veg Spring Roll", price = 150.0), quantity = 2)
        )
        assertEquals("Starters", KotRoundRules.detectRoundName(starterCart, categories))

        // Mains cart
        val mainsCart = listOf(
            CartItem(menuItem = MenuItem(id = 2, categoryId = 2, name = "Paneer Butter Masala", price = 300.0), quantity = 1)
        )
        assertEquals("Mains", KotRoundRules.detectRoundName(mainsCart, categories))

        // Desserts cart
        val dessertCart = listOf(
            CartItem(menuItem = MenuItem(id = 3, categoryId = 3, name = "Gulab Jamun", price = 100.0), quantity = 3)
        )
        assertEquals("Desserts", KotRoundRules.detectRoundName(dessertCart, categories))

        // Beverages cart
        val beverageCart = listOf(
            CartItem(menuItem = MenuItem(id = 4, categoryId = 4, name = "Mango Lassi", price = 120.0), quantity = 2)
        )
        assertEquals("Beverages", KotRoundRules.detectRoundName(beverageCart, categories))

        // Empty cart fallback
        assertEquals("Starters", KotRoundRules.detectRoundName(emptyList(), categories))
    }
}
