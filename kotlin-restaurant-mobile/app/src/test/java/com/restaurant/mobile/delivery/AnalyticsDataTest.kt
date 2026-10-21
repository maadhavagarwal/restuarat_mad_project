package com.restaurant.mobile.delivery

import com.restaurant.mobile.model.AnalyticsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Unit tests for Phase 5 Analytics Data Layer:
 * - Settled dine-in revenue
 * - Eligible completed/confirmed delivery revenue (excluding cancelled/failed orders)
 * - Average dine-in bill value
 * - Average dining duration with real timestamps and defensible fallback
 * - Table turnover rate
 * - Total diners served with guest counts (party_size) and anti-duplicate counting
 * - Table occupancy metrics based on floor-plan table inventory
 * - Top 5 dishes across dine-in and delivery with dish-level channel quantities
 * - Monetary unit consistency (paise Long)
 */
class AnalyticsDataTest {

    private fun createAddress(id: String = "addr-1"): Address {
        return Address(
            id = id,
            label = "Home",
            line = "123 Main Street",
            city = "Bengaluru",
            postcode = "560001",
            phone = "+91 98765 43210"
        )
    }

    private fun createOrder(
        id: String,
        status: String,
        totalPaise: Long,
        lines: List<QuoteLine> = emptyList()
    ): DeliveryOrder {
        return DeliveryOrder(
            id = id,
            restaurantId = "rest-1",
            restaurantName = "Bistro Lumière",
            address = createAddress(),
            quote = Quote(
                restaurantId = "rest-1",
                lines = lines,
                subtotal = totalPaise,
                tax = 0L,
                delivery = 0L,
                discount = 0L,
                total = totalPaise
            ),
            paymentMethod = "UPI",
            status = status
        )
    }

    // -------------------------------------------------------------
    // 1. EMPTY DATA HANDLING
    // -------------------------------------------------------------

    @Test
    fun testEmptyDataCalculationsReturnSafeDefaults() {
        val emptyTabs = emptyList<DiningTab>()
        val emptyOrders = emptyList<DeliveryOrder>()
        val emptyBookings = emptyList<TableBooking>()

        val settledDineInRev = AnalyticsEngine.calculateSettledDineInRevenue(emptyTabs)
        assertEquals(0L, settledDineInRev)

        val deliveryRev = AnalyticsEngine.calculateEligibleDeliveryRevenue(emptyOrders)
        assertEquals(0L, deliveryRev)

        val avgBill = AnalyticsEngine.calculateAverageDineInBillValue(emptyTabs)
        assertEquals(0L, avgBill)

        val avgDuration = AnalyticsEngine.calculateAverageDiningDurationMinutes(emptyTabs)
        assertEquals(0L, avgDuration)

        val turnoverRate = AnalyticsEngine.calculateTableTurnoverRate(0, 12)
        assertEquals(0.0, turnoverRate, 0.0001)

        val turnoverZeroTables = AnalyticsEngine.calculateTableTurnoverRate(0, 0)
        assertEquals(0.0, turnoverZeroTables, 0.0001)

        val dinersServed = AnalyticsEngine.calculateTotalDinersServed(emptyTabs, emptyBookings)
        assertEquals(0, dinersServed)

        val occupancy = AnalyticsEngine.calculateTableOccupancy(RestaurantTableDefaults.DEFAULT_TABLES) { false }
        assertEquals(0, occupancy.occupiedTables)
        assertEquals(12, occupancy.totalTables)
        assertEquals(0.0, occupancy.occupancyRate, 0.0001)

        val topDishes = AnalyticsEngine.aggregateTopDishes(emptyTabs, emptyOrders)
        assertTrue("Top dishes must be empty on empty data", topDishes.isEmpty())
    }

    // -------------------------------------------------------------
    // 2. SETTLED VS UNSETTLED DINE-IN REVENUE
    // -------------------------------------------------------------

    @Test
    fun testSettledVsUnsettledDiningTabsRevenue() {
        val tabs = listOf(
            DiningTab(
                id = "TAB-OPEN-1",
                tableNumber = "T1",
                status = "OPEN",
                total = 45000L
            ),
            DiningTab(
                id = "TAB-PENDING-1",
                tableNumber = "T2",
                status = "PAYMENT_PENDING",
                total = 32000L
            ),
            DiningTab(
                id = "TAB-SETTLED-1",
                tableNumber = "T3",
                status = "SETTLED",
                total = 55000L
            ),
            DiningTab(
                id = "TAB-SETTLED-2",
                tableNumber = "T4",
                status = "SETTLED",
                total = 25000L
            )
        )

        // Only SETTLED tabs contribute
        val settledRevenue = AnalyticsEngine.calculateSettledDineInRevenue(tabs)
        assertEquals(80000L, settledRevenue) // 55000L + 25000L

        // Average bill calculation strictly over settled tabs (80000 / 2 = 40000)
        val avgBill = AnalyticsEngine.calculateAverageDineInBillValue(tabs)
        assertEquals(40000L, avgBill)
    }

    // -------------------------------------------------------------
    // 3. EXCLUDED DELIVERY STATUSES REVENUE
    // -------------------------------------------------------------

    @Test
    fun testEligibleDeliveryRevenueExcludesCancelledAndFailedOrders() {
        val orders = listOf(
            createOrder("ORD-1", "Confirmed", 30000L),
            createOrder("ORD-2", "Preparing", 40000L),
            createOrder("ORD-3", "Out for delivery", 25000L),
            createOrder("ORD-4", "Delivered", 50000L),
            createOrder("ORD-5", "Cancelled", 35000L),  // Strictly excluded
            createOrder("ORD-6", "Failed", 60000L),     // Strictly excluded
            createOrder("ORD-7", "Unknown", 10000L)     // Strictly excluded
        )

        val eligibleRevenue = AnalyticsEngine.calculateEligibleDeliveryRevenue(orders)
        // 30000 + 40000 + 25000 + 50000 = 145000L
        assertEquals(145000L, eligibleRevenue)
    }

    // -------------------------------------------------------------
    // 4. AVERAGE DINING DURATION & FALLBACK HANDLING
    // -------------------------------------------------------------

    @Test
    fun testAverageDiningDurationWithRealTimestampsAndDefensibleFallback() {
        val baseTime = Instant.parse("2026-10-21T18:00:00Z")

        val item1 = TableBillItem(
            dishId = "d1",
            name = "Paneer Tikka",
            quantity = 1,
            price = 28000L,
            total = 28000L,
            orderedAt = baseTime.toString()
        )
        // Tab 1: duration = 45 minutes
        val tab1 = DiningTab(
            id = "TAB-1",
            tableNumber = "T1",
            status = "SETTLED",
            items = listOf(item1),
            total = 28000L,
            updatedAt = baseTime.plusSeconds(45 * 60).toString()
        )

        val item2 = TableBillItem(
            dishId = "d2",
            name = "Butter Chicken",
            quantity = 1,
            price = 35000L,
            total = 35000L,
            orderedAt = baseTime.toString()
        )
        // Tab 2: duration = 75 minutes
        val tab2 = DiningTab(
            id = "TAB-2",
            tableNumber = "T2",
            status = "SETTLED",
            items = listOf(item2),
            total = 35000L,
            updatedAt = baseTime.plusSeconds(75 * 60).toString()
        )

        // Tab 3: Empty items, missing timestamps (defensible fallback: skipped from avg duration)
        val tab3 = DiningTab(
            id = "TAB-3",
            tableNumber = "T3",
            status = "SETTLED",
            items = emptyList(),
            total = 10000L,
            updatedAt = baseTime.toString()
        )

        // Tab 4: OPEN tab (unsettled, must not contribute)
        val tab4 = DiningTab(
            id = "TAB-4",
            tableNumber = "T4",
            status = "OPEN",
            items = listOf(item1),
            total = 28000L,
            updatedAt = baseTime.plusSeconds(120 * 60).toString()
        )

        val avgDuration = AnalyticsEngine.calculateAverageDiningDurationMinutes(listOf(tab1, tab2, tab3, tab4))
        // Expected average of tab1 (45m) and tab2 (75m): (45 + 75) / 2 = 60 minutes
        assertEquals(60L, avgDuration)
    }

    // -------------------------------------------------------------
    // 5. TABLE TURNOVER RATE
    // -------------------------------------------------------------

    @Test
    fun testTableTurnoverRateCalculations() {
        val totalTables = 12

        // 6 settled tabs over 12 tables = 0.5 turns per table
        val rateHalf = AnalyticsEngine.calculateTableTurnoverRate(6, totalTables)
        assertEquals(0.5, rateHalf, 0.0001)

        // 24 settled tabs over 12 tables = 2.0 turns per table
        val rateDouble = AnalyticsEngine.calculateTableTurnoverRate(24, totalTables)
        assertEquals(2.0, rateDouble, 0.0001)

        // 0 settled tabs = 0.0
        val rateZero = AnalyticsEngine.calculateTableTurnoverRate(0, totalTables)
        assertEquals(0.0, rateZero, 0.0001)
    }

    // -------------------------------------------------------------
    // 6. TOTAL DINERS SERVED & ANTI-DUPLICATE COUNTING
    // -------------------------------------------------------------

    @Test
    fun testTotalDinersServedGuestCountsAndAntiDuplicateCounting() {
        val booking1 = TableBooking(
            id = "TB-101",
            userId = "usr-1",
            userName = "Alice",
            userPhone = "+91 98765 00001",
            tableId = "T1",
            tableNumber = "T1",
            bookingDate = "2026-10-21",
            timeSlot = "19:00 - 20:30",
            partySize = 4,
            status = TableBookingStatus.COMPLETED
        )

        val booking2 = TableBooking(
            id = "TB-102",
            userId = "usr-2",
            userName = "Bob",
            userPhone = "+91 98765 00002",
            tableId = "T2",
            tableNumber = "T2",
            bookingDate = "2026-10-21",
            timeSlot = "19:00 - 20:30",
            partySize = 6,
            status = TableBookingStatus.COMPLETED
        )

        val bookingCancelled = TableBooking(
            id = "TB-103",
            userId = "usr-3",
            userName = "Charlie",
            userPhone = "+91 98765 00003",
            tableId = "T3",
            tableNumber = "T3",
            bookingDate = "2026-10-21",
            timeSlot = "19:00 - 20:30",
            partySize = 2,
            status = TableBookingStatus.CANCELLED
        )

        val bookingConfirmed = TableBooking(
            id = "TB-104",
            userId = "usr-4",
            userName = "David",
            userPhone = "+91 98765 00004",
            tableId = "T4",
            tableNumber = "T4",
            bookingDate = "2026-10-21",
            timeSlot = "19:00 - 20:30",
            partySize = 5,
            status = TableBookingStatus.CONFIRMED
        )

        val tabWithBooking1 = DiningTab(
            id = "TAB-1",
            tableNumber = "T1",
            bookingId = "TB-101",
            status = "SETTLED",
            total = 80000L
        )

        val walkInTab = DiningTab(
            id = "TAB-2",
            tableNumber = "T5",
            bookingId = null,
            status = "SETTLED",
            total = 30000L
        )

        val openTab = DiningTab(
            id = "TAB-3",
            tableNumber = "T6",
            bookingId = null,
            status = "OPEN",
            total = 20000L
        )

        val tabs = listOf(tabWithBooking1, walkInTab, openTab)
        val bookings = listOf(booking1, booking2, bookingCancelled, bookingConfirmed)

        val totalDiners = AnalyticsEngine.calculateTotalDinersServed(tabs, bookings)

        // Expected count:
        // - tabWithBooking1: uses booking1.partySize = 4 (counted once)
        // - walkInTab: walk-in without booking = 1
        // - booking2: COMPLETED booking not linked to tab = 6
        // - bookingCancelled: 0 (excluded)
        // - bookingConfirmed: 0 (unseated, excluded)
        // - openTab: 0 (unsettled, excluded)
        // Total = 4 + 1 + 6 = 11
        assertEquals(11, totalDiners)
    }

    // -------------------------------------------------------------
    // 7. TABLE OCCUPANCY METRICS
    // -------------------------------------------------------------

    @Test
    fun testTableOccupancyMetrics() {
        val tables = RestaurantTableDefaults.DEFAULT_TABLES // 12 tables: T1..T12
        val occupiedTables = setOf("T1", "T4", "T9")

        val occupancy = AnalyticsEngine.calculateTableOccupancy(tables) { tableNumber ->
            occupiedTables.contains(tableNumber)
        }

        assertEquals(3, occupancy.occupiedTables)
        assertEquals(12, occupancy.totalTables)
        assertEquals(0.25, occupancy.occupancyRate, 0.0001) // 3 / 12 = 0.25
    }

    // -------------------------------------------------------------
    // 8. TOP FIVE DISHES ACROSS CHANNELS
    // -------------------------------------------------------------

    @Test
    fun testTopFiveDishesAcrossDineInAndDeliveryWithRankingAndFiltering() {
        val dineInTab1 = DiningTab(
            id = "TAB-1",
            tableNumber = "T1",
            status = "SETTLED",
            items = listOf(
                TableBillItem(dishId = "d-paneer", name = "Paneer Tikka", quantity = 3, price = 28000L, total = 84000L),
                TableBillItem(dishId = "d-butter-chicken", name = "Butter Chicken", quantity = 2, price = 35000L, total = 70000L),
                TableBillItem(dishId = "d-garlic-naan", name = "Garlic Naan", quantity = 6, price = 6000L, total = 36000L),
                TableBillItem(dishId = "d-biryani", name = "Biryani", quantity = 4, price = 30000L, total = 120000L),
                TableBillItem(dishId = "d-gulab-jamun", name = "Gulab Jamun", quantity = 1, price = 12000L, total = 12000L)
            )
        )

        // OPEN tab dishes must be excluded
        val dineInOpenTab = DiningTab(
            id = "TAB-OPEN",
            tableNumber = "T2",
            status = "OPEN",
            items = listOf(
                TableBillItem(dishId = "d-biryani", name = "Biryani", quantity = 100, price = 30000L, total = 3000000L)
            )
        )

        val deliveryOrder1 = createOrder(
            id = "ORD-1",
            status = "Delivered",
            totalPaise = 250000L,
            lines = listOf(
                QuoteLine(dishId = "d-paneer", name = "Paneer Tikka", quantity = 4, addons = emptyList(), unitPrice = 28000L, total = 112000L),
                QuoteLine(dishId = "d-butter-chicken", name = "Butter Chicken", quantity = 5, addons = emptyList(), unitPrice = 35000L, total = 175000L),
                QuoteLine(dishId = "d-garlic-naan", name = "Garlic Naan", quantity = 3, addons = emptyList(), unitPrice = 6000L, total = 18000L),
                QuoteLine(dishId = "d-lassi", name = "Lassi", quantity = 8, addons = emptyList(), unitPrice = 10000L, total = 80000L)
            )
        )

        // Cancelled order dishes must be excluded
        val deliveryCancelled = createOrder(
            id = "ORD-CANCELLED",
            status = "Cancelled",
            totalPaise = 500000L,
            lines = listOf(
                QuoteLine(dishId = "d-garlic-naan", name = "Garlic Naan", quantity = 200, addons = emptyList(), unitPrice = 6000L, total = 1200000L)
            )
        )

        val topDishes = AnalyticsEngine.aggregateTopDishes(
            settledTabs = listOf(dineInTab1, dineInOpenTab),
            eligibleOrders = listOf(deliveryOrder1, deliveryCancelled),
            limit = 5
        )

        // Expected quantities:
        // 1. Garlic Naan: dine-in 6 + delivery 3 = 9
        // 2. Lassi: dine-in 0 + delivery 8 = 8
        // 3. Butter Chicken: dine-in 2 + delivery 5 = 7
        // 4. Paneer Tikka: dine-in 3 + delivery 4 = 7
        // 5. Biryani: dine-in 4 + delivery 0 = 4 (OPEN tab's 100 excluded)
        // (Gulab Jamun: dine-in 1, truncated because limit = 5)
        assertEquals(5, topDishes.size)

        assertEquals("d-garlic-naan", topDishes[0].dishId)
        assertEquals(9, topDishes[0].totalQuantity)
        assertEquals(6, topDishes[0].dineInQuantity)
        assertEquals(3, topDishes[0].deliveryQuantity)

        assertEquals("d-lassi", topDishes[1].dishId)
        assertEquals(8, topDishes[1].totalQuantity)
        assertEquals(0, topDishes[1].dineInQuantity)
        assertEquals(8, topDishes[1].deliveryQuantity)

        val thirdDish = topDishes[2]
        val fourthDish = topDishes[3]
        assertTrue(thirdDish.totalQuantity == 7 && fourthDish.totalQuantity == 7)

        assertEquals("d-biryani", topDishes[4].dishId)
        assertEquals(4, topDishes[4].totalQuantity)
    }

    // -------------------------------------------------------------
    // 9. MONETARY UNIT CONSISTENCY (PAISE)
    // -------------------------------------------------------------

    @Test
    fun testCurrencyPreservationInPaiseWithoutPrecisionLoss() {
        val largeTab1 = DiningTab(
            id = "TAB-L1",
            tableNumber = "T1",
            status = "SETTLED",
            total = 1500000L // ₹15,000.00
        )
        val largeTab2 = DiningTab(
            id = "TAB-L2",
            tableNumber = "T2",
            status = "SETTLED",
            total = 2500000L // ₹25,000.00
        )

        val settledRevenue = AnalyticsEngine.calculateSettledDineInRevenue(listOf(largeTab1, largeTab2))
        assertEquals(4000000L, settledRevenue) // ₹40,000.00

        val avgBill = AnalyticsEngine.calculateAverageDineInBillValue(listOf(largeTab1, largeTab2))
        assertEquals(2000000L, avgBill) // ₹20,000.00
    }

    // -------------------------------------------------------------
    // 10. ANALYTICS SUMMARY TO UI MODEL MAPPING
    // -------------------------------------------------------------

    @Test
    fun testFromSummaryConvertsPaiseToRupeesExactlyOnce() {
        val summary = AnalyticsSummary(
            settledDineInRevenue = 1500000L,   // $15,000.00
            eligibleDeliveryRevenue = 2500000L, // $25,000.00
            totalRevenue = 4000000L,            // $40,000.00
            averageDineInBillValue = 750000L,   // $7,500.00
            averageDiningDurationMinutes = 45L,
            tableTurnoverRate = 1.5,
            totalDinersServed = 28,
            tableOccupancy = TableOccupancyMetrics(occupiedTables = 4, totalTables = 12, occupancyRate = 0.3333),
            topDishes = listOf(
                TopDishStat(dishId = "d1", name = "Paneer Tikka", totalQuantity = 10, dineInQuantity = 6, deliveryQuantity = 4)
            )
        )

        val result = AnalyticsData.fromSummary(summary)

        // Monetary fields converted from paise to rupees
        assertEquals(15000.0, result.dineInRevenue, 0.001)
        assertEquals(25000.0, result.deliveryRevenue, 0.001)
        assertEquals(40000.0, result.totalRevenue, 0.001)
        assertEquals(7500.0, result.averageDineInBill, 0.001)

        // Non-monetary fields mapped directly
        assertEquals(45L, result.avgDiningDurationMinutes)
        assertEquals(1.5, result.tableTurnoverRate, 0.0001)
        assertEquals(28, result.totalDinersServed)
        assertEquals(0.3333, result.occupancyRate, 0.0001)
        assertEquals(4, result.occupiedTables)
        assertEquals(8, result.availableTables) // 12 - 4
        assertEquals(1, result.topDishes.size)
        assertEquals("Paneer Tikka", result.topDishes[0].name)

        // Remote-only metrics default to 0 with no fallback
        assertEquals(0, result.totalOrders)
        assertEquals(0, result.activeKOTs)
        assertEquals(0, result.totalCustomers)

        // Summary attached
        assertEquals(summary, result.summary)
        assertEquals(false, result.isLoading)
        assertNull(result.errorMessage)
    }

    @Test
    fun testFromSummaryEmptyDefaultsReturnSafeZeros() {
        val emptySummary = AnalyticsSummary()
        val result = AnalyticsData.fromSummary(emptySummary)

        assertEquals(0.0, result.totalRevenue, 0.001)
        assertEquals(0.0, result.dineInRevenue, 0.001)
        assertEquals(0.0, result.deliveryRevenue, 0.001)
        assertEquals(0.0, result.averageDineInBill, 0.001)
        assertEquals(0L, result.avgDiningDurationMinutes)
        assertEquals(0.0, result.tableTurnoverRate, 0.0001)
        assertEquals(0, result.totalDinersServed)
        assertEquals(0.0, result.occupancyRate, 0.0001)
        assertEquals(0, result.occupiedTables)
        assertEquals(0, result.availableTables)
        assertTrue(result.topDishes.isEmpty())
    }

    @Test
    fun testFromSummaryPreservesRemoteOnlyFallbackMetrics() {
        val summary = AnalyticsSummary(
            settledDineInRevenue = 100000L, // 1000 rupees
            eligibleDeliveryRevenue = 200000L,
            totalRevenue = 300000L
        )

        val remoteFallback = AnalyticsData(
            totalOrders = 42,
            activeKOTs = 5,
            totalCustomers = 150
        )

        val result = AnalyticsData.fromSummary(summary, fallback = remoteFallback)

        // Local metrics from summary
        assertEquals(1000.0, result.dineInRevenue, 0.001)
        assertEquals(2000.0, result.deliveryRevenue, 0.001)
        assertEquals(3000.0, result.totalRevenue, 0.001)

        // Remote-only metrics preserved from fallback
        assertEquals(42, result.totalOrders)
        assertEquals(5, result.activeKOTs)
        assertEquals(150, result.totalCustomers)
    }

    @Test
    fun testFromSummaryNullFallbackDefaultsRemoteMetricsToZero() {
        val summary = AnalyticsSummary(settledDineInRevenue = 50000L)
        val result = AnalyticsData.fromSummary(summary, fallback = null)

        assertEquals(0, result.totalOrders)
        assertEquals(0, result.activeKOTs)
        assertEquals(0, result.totalCustomers)
    }

    @Test
    fun testCurrencyConversionRulesRoundTrip() {
        // paiseToRupees
        assertEquals(0.0, CurrencyConversionRules.paiseToRupees(0L), 0.0001)
        assertEquals(150.0, CurrencyConversionRules.paiseToRupees(15000L), 0.0001)
        assertEquals(0.01, CurrencyConversionRules.paiseToRupees(1L), 0.0001)
        assertEquals(99999.99, CurrencyConversionRules.paiseToRupees(9999999L), 0.0001)

        // rupeesToPaise
        assertEquals(0L, CurrencyConversionRules.rupeesToPaise(0.0))
        assertEquals(15000L, CurrencyConversionRules.rupeesToPaise(150.0))
        assertEquals(1L, CurrencyConversionRules.rupeesToPaise(0.01))
        assertEquals(9999999L, CurrencyConversionRules.rupeesToPaise(99999.99))
    }
}