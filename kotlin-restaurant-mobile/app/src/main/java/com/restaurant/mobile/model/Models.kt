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
    val id: Long,
    val menuItemName: String,
    val quantity: Int,
    val priceAtTimeOfOrder: Double
)

data class KOT(
    val id: Long,
    val orderId: Long,
    var status: String, // "New", "Preparing", "Ready", "Served"
    val items: List<KOTItem>
)

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
