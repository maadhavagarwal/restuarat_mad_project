package com.restaurant.mobile.delivery

import java.time.Instant
import java.util.UUID

data class AddOn(val id: String, val name: String, val price: Long)

data class Dish(
    val id: String,
    val restaurantId: String,
    val category: String,
    val name: String,
    val description: String,
    val price: Long,
    val image: String,
    val vegetarian: Boolean,
    val available: Boolean = true,
    val addons: List<AddOn> = emptyList(),
)

data class Restaurant(
    val id: String,
    val name: String,
    val cuisine: String,
    val description: String,
    val image: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val deliveryMinutes: Int,
    val deliveryFee: Long,
    val open: Boolean = true,
)

data class Offer(
    val code: String,
    val description: String,
    val minimum: Long,
    val discount: Long,
    val expiresAt: String,
)

data class Catalog(
    val restaurants: List<Restaurant> = emptyList(),
    val dishes: List<Dish> = emptyList(),
    val offers: List<Offer> = emptyList(),
)

data class Profile(
    val id: String,
    val name: String,
    val email: String,
    val phone: String = "",
    val reducedGlass: Boolean = false,
)

data class Address(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val line: String,
    val city: String,
    val postcode: String,
    val phone: String,
)

data class Line(val dishId: String, val quantity: Int, val addons: List<String> = emptyList())

data class Cart(val lines: List<Line> = emptyList(), val coupon: String = "")

data class QuoteLine(
    val dishId: String,
    val name: String,
    val quantity: Int,
    val addons: List<String>,
    val unitPrice: Long,
    val total: Long,
)

data class Quote(
    val restaurantId: String,
    val lines: List<QuoteLine>,
    val subtotal: Long,
    val tax: Long,
    val delivery: Long,
    val discount: Long,
    val total: Long,
)

data class Event(val status: String, val at: String = Instant.now().toString())

data class DeliveryOrder(
    val id: String,
    val restaurantId: String,
    val restaurantName: String,
    val address: Address,
    val quote: Quote,
    val paymentMethod: String,
    val paymentStatus: String = "Due on delivery",
    val status: String = "Confirmed",
    val events: List<Event> = listOf(Event("Confirmed")),
)

data class Review(
    val orderId: String,
    val restaurantId: String,
    val author: String,
    val rating: Int,
    val text: String,
    val at: String = Instant.now().toString(),
)

data class Credentials(val email: String, val password: String, val name: String = "")

data class Checkout(
    val addressId: String,
    val paymentMethod: String,
    val idempotencyKey: String,
    val expectedTotal: Long,
)
