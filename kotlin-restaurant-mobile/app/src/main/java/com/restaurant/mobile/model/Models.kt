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
    val totalCustomers: Int = 0
)
