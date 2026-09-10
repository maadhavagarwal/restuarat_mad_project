package com.restaurant.restaurant_os.model

import java.time.Instant

data class Customer(
    var id: Long? = null,
    val phone: String,
    val name: String,
    var loyaltyPoints: Int = 0,
    val qrCode: String = "CUST-$phone",
    var syncStatus: String = "synced",
    var updatedAt: String = Instant.now().toString()
)

data class Category(
    var id: Long? = null,
    val name: String,
    var syncStatus: String = "synced",
    var updatedAt: String = Instant.now().toString()
)

data class MenuItem(
    var id: Long? = null,
    val categoryId: Long,
    val name: String,
    val price: Double,
    val taxRate: Double = 5.0,
    var isAvailable: Boolean = true,
    var syncStatus: String = "synced",
    var updatedAt: String = Instant.now().toString()
)

data class DiningTable(
    var id: Long? = null,
    val tableNumber: String,
    val seatingCapacity: Int,
    var status: String = "Available", // "Available", "Occupied", "Reserved"
    var syncStatus: String = "synced",
    var updatedAt: String = Instant.now().toString()
)

data class Order(
    var id: Long? = null,
    val tableId: Long? = null,
    val customerId: Long? = null,
    val orderType: String = "Dine-in", // "Dine-in", "Takeaway", "Delivery"
    var totalAmount: Double = 0.0,
    var status: String = "Active", // "Active", "Paid", "Cancelled"
    var paymentMethod: String? = null, // "Cash", "Card", "UPI"
    val createdAt: String = Instant.now().toString(),
    var syncStatus: String = "synced",
    var updatedAt: String = Instant.now().toString()
)

data class OrderItem(
    var id: Long? = null,
    val orderId: Long,
    val menuItemId: Long,
    val quantity: Int,
    val priceAtTimeOfOrder: Double,
    var syncStatus: String = "synced",
    var updatedAt: String = Instant.now().toString()
)

data class KOT(
    var id: Long? = null,
    val orderId: Long,
    var status: String = "New", // "New", "Preparing", "Ready", "Served"
    var printedStatus: Boolean = false,
    var syncStatus: String = "synced",
    var updatedAt: String = Instant.now().toString()
)

// Request / Response DTOs

data class CartItemRequest(
    val menuItemId: Long,
    val quantity: Int
)

data class CheckoutRequest(
    val orderType: String = "Dine-in",
    val tableId: Long? = null,
    val customerId: Long? = null,
    val paymentMethod: String = "Cash",
    val items: List<CartItemRequest>
)

data class KOTItemResponse(
    val id: Long?,
    val orderId: Long,
    val menuItemId: Long,
    val menuItemName: String,
    val quantity: Int,
    val priceAtTimeOfOrder: Double
)

data class KOTResponse(
    val id: Long?,
    val orderId: Long,
    val status: String,
    val printedStatus: Boolean,
    val updatedAt: String,
    val items: List<KOTItemResponse>
)

data class StatusUpdateRequest(
    val status: String
)

data class CustomerCreateRequest(
    val name: String,
    val phone: String
)
