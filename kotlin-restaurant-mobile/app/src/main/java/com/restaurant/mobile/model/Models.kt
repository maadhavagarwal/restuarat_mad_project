package com.restaurant.mobile.model

data class Category(
    val id: Long,
    val name: String
)

data class MenuItem(
    val id: Long,
    val categoryId: Long,
    val name: String,
    val price: Double,
    val taxRate: Double = 5.0,
    val isAvailable: Boolean = true
)

data class CartItem(
    val menuItem: MenuItem,
    var quantity: Int
)

data class DiningTable(
    val id: Long,
    val tableNumber: String,
    val seatingCapacity: Int,
    var status: String // "Available", "Occupied", "Reserved"
)

data class KOTItem(
    val id: Long = 0L,
    val menuItemName: String,
    val quantity: Int,
    val priceAtTimeOfOrder: Double = 0.0
)

data class KOT(
    val id: Long,
    val orderId: Long = 0L,
    var status: String = "New", // "New", "Preparing", "Ready", "Served"
    val items: List<KOTItem> = emptyList(),
    val tableNumber: String = "",
    val tableId: String = "",
    val round: Int = 1,
    val roundName: String = "",
    val createdAt: String = "",
    val customerName: String? = null
) {
    val ticketNumber: String
        get() = "KOT-$id"

    val displayTitle: String
        get() = buildString {
            append("Table ${tableNumber.ifBlank { "Unassigned" }}")
            append(" — Round $round")
            if (roundName.isNotBlank()) {
                append(" ($roundName)")
            }
            append(" — ${status.uppercase()}")
        }
}

data class Customer(
    val id: Long,
    val name: String,
    val phone: String,
    val loyaltyPoints: Int,
    val qrCode: String
)

data class AnalyticsData(
    val totalRevenue: Double = 0.0,
    val totalOrders: Int = 0,
    val activeKOTs: Int = 0,
    val availableTables: Int = 0,
    val occupiedTables: Int = 0,
    val totalCustomers: Int = 0,
    val dineInRevenue: Double = 0.0,
    val deliveryRevenue: Double = 0.0,
    val averageDineInBill: Double = 0.0,
    val occupancyRate: Double = 0.0,
    val avgDiningDurationMinutes: Long = 0L,
    val tableTurnoverRate: Double = 0.0,
    val totalDinersServed: Int = 0,
    val topDishes: List<com.restaurant.mobile.delivery.TopDishStat> = emptyList(),
    val summary: com.restaurant.mobile.delivery.AnalyticsSummary? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    companion object {
        /**
         * Maps an [AnalyticsSummary] (persistence layer, monetary values in paise)
         * to an [AnalyticsData] (UI layer, monetary values in rupees).
         *
         * Converts paise to rupees exactly once using [CurrencyConversionRules.paiseToRupees].
         * Non-monetary fields are mapped directly.
         *
         * @param summary The persistence-layer analytics summary.
         * @param fallback Optional existing [AnalyticsData] whose remote-only metrics
         *   (totalOrders, activeKOTs, totalCustomers) are preserved when their values
         *   are non-default, since these metrics come from the remote API and are not
         *   available from local SQLite aggregation.
         */
        fun fromSummary(
            summary: com.restaurant.mobile.delivery.AnalyticsSummary,
            fallback: AnalyticsData? = null
        ): AnalyticsData {
            val dineInRupees = com.restaurant.mobile.delivery.CurrencyConversionRules.paiseToRupees(summary.settledDineInRevenue)
            val deliveryRupees = com.restaurant.mobile.delivery.CurrencyConversionRules.paiseToRupees(summary.eligibleDeliveryRevenue)
            val totalRupees = com.restaurant.mobile.delivery.CurrencyConversionRules.paiseToRupees(summary.totalRevenue)
            val avgBillRupees = com.restaurant.mobile.delivery.CurrencyConversionRules.paiseToRupees(summary.averageDineInBillValue)

            return AnalyticsData(
                totalRevenue = totalRupees,
                // Remote-only metrics: preserve fallback values when available
                totalOrders = fallback?.totalOrders ?: 0,
                activeKOTs = fallback?.activeKOTs ?: 0,
                totalCustomers = fallback?.totalCustomers ?: 0,
                // Local analytics metrics
                dineInRevenue = dineInRupees,
                deliveryRevenue = deliveryRupees,
                averageDineInBill = avgBillRupees,
                occupancyRate = summary.tableOccupancy.occupancyRate,
                availableTables = summary.tableOccupancy.totalTables - summary.tableOccupancy.occupiedTables,
                occupiedTables = summary.tableOccupancy.occupiedTables,
                avgDiningDurationMinutes = summary.averageDiningDurationMinutes,
                tableTurnoverRate = summary.tableTurnoverRate,
                totalDinersServed = summary.totalDinersServed,
                topDishes = summary.topDishes,
                summary = summary,
                isLoading = false,
                errorMessage = null
            )
        }
    }
}
