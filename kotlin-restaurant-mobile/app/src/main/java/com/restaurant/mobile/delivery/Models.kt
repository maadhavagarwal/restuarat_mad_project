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

enum class UserRole(val label: String) {
    CUSTOMER("Customer"),
    WAITER("Waiter / POS"),
    CHEF_KDS("Kitchen Chef"),
    ADMIN("Admin");

    val isStaff: Boolean
        get() = this != CUSTOMER

    companion object {
        fun fromString(value: String?): UserRole =
            entries.find { it.name.equals(value?.trim(), ignoreCase = true) } ?: CUSTOMER
    }
}

data class Profile(
    val id: String,
    val name: String,
    val email: String,
    val phone: String = "",
    val reducedGlass: Boolean = false,
    val role: UserRole = UserRole.CUSTOMER,
)

data class Address(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val line: String,
    val city: String,
    val postcode: String,
    val phone: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class Line(val dishId: String, val quantity: Int, val addons: List<String> = emptyList())

data class Cart(
    val lines: List<Line> = emptyList(),
    val coupon: String = "",
    val restaurantId: String? = null,
)

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

data class Credentials(
    val email: String,
    val password: String,
    val name: String = "",
    val role: String = "CUSTOMER",
)

data class Checkout(
    val addressId: String,
    val paymentMethod: String,
    val idempotencyKey: String,
    val expectedTotal: Long,
)

data class OtpSendRequest(val target: String, val type: String = "phone")
data class OtpSendResponse(val success: Boolean, val message: String, val target: String, val previewOtp: String? = null)
data class OtpVerifyRequest(
    val target: String,
    val code: String,
    val name: String = "",
    val role: String = "CUSTOMER",
)

data class LocationPoint(val latitude: Double, val longitude: Double)
data class RiderTracking(val name: String, val phone: String, val vehicleNumber: String, val currentLocation: LocationPoint)
data class TrackingResponse(
    val orderId: String,
    val restaurantName: String = "",
    val status: String,
    val progress: Int = 0,
    val rider: RiderTracking? = null,
    val distanceRemainingKm: Double = 0.0,
    val etaMinutes: Int = 0,
    val isNearby: Boolean = false,
    val isDelivered: Boolean = false,
    val notificationAlert: String? = null,
    val updatedAt: String = ""
)

object TableBookingStatus {
    const val CONFIRMED = "CONFIRMED"
    const val SEATED = "SEATED"
    const val COMPLETED = "COMPLETED"
    const val CANCELLED = "CANCELLED"

    val ALL = listOf(CONFIRMED, SEATED, COMPLETED, CANCELLED)
}

data class TableBooking(
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val userName: String,
    val userPhone: String,
    val tableId: String,
    val tableNumber: String,
    val bookingDate: String,
    val timeSlot: String,
    val partySize: Int,
    val seatingArea: String = "Main Dining Room",
    val specialRequests: String = "",
    val status: String = TableBookingStatus.CONFIRMED,
    val createdAt: String = Instant.now().toString(),
    val restaurantId: String = "",
    val restaurantName: String = "",
) {
    companion object {
        fun generateId(): String = "TB-${UUID.randomUUID().toString().take(8).uppercase()}"
    }
}

data class RestaurantTable(
    val tableId: String,
    val tableNumber: String,
    val capacity: Int,
    val seatingArea: String,
)

object RestaurantTableDefaults {
    val DEFAULT_TABLES = listOf(
        RestaurantTable("T1", "T1", 2, "Main Dining Room"),
        RestaurantTable("T2", "T2", 2, "Main Dining Room"),
        RestaurantTable("T3", "T3", 4, "Main Dining Room"),
        RestaurantTable("T4", "T4", 4, "Main Dining Room"),
        RestaurantTable("T5", "T5", 6, "Main Dining Room"),
        RestaurantTable("T6", "T6", 8, "Main Dining Room"),
        RestaurantTable("T7", "T7", 2, "Outdoor Garden"),
        RestaurantTable("T8", "T8", 4, "Outdoor Garden"),
        RestaurantTable("T9", "T9", 4, "Private Booth"),
        RestaurantTable("T10", "T10", 6, "Private Booth"),
        RestaurantTable("T11", "T11", 2, "Rooftop Lounge"),
        RestaurantTable("T12", "T12", 4, "Rooftop Lounge"),
    )
}

sealed class TableAllocationResult {
    data class Success(val booking: TableBooking, val allocatedTable: RestaurantTable) : TableAllocationResult()
    data class Failure(val reason: String) : TableAllocationResult()
}

object TableAllocationEngine {
    /**
     * Determines table availability and selects the best-fit table matching the requested criteria.
     *
     * Rules:
     * 1. A table must have sufficient capacity for the party (capacity >= partySize).
     * 2. A table must satisfy the selected seating preference when compatible inventory exists.
     * 3. Existing CONFIRMED and SEATED bookings block overlapping reservations for the same table and time slot.
     * 4. CANCELLED and COMPLETED bookings do NOT block future availability.
     * 5. Does not allocate one table to two overlapping reservations.
     * 6. If no suitable table is available, returns actionable failure reason.
     * 7. Scopes availability checks to the target restaurant.
     */
    fun allocateTable(
        restaurantId: String,
        restaurantName: String,
        userId: String,
        userName: String,
        userPhone: String,
        bookingDate: String,
        timeSlot: String,
        partySize: Int,
        seatingArea: String,
        specialRequests: String = "",
        existingBookings: List<TableBooking>,
        inventory: List<RestaurantTable> = RestaurantTableDefaults.DEFAULT_TABLES
    ): TableAllocationResult {
        if (bookingDate.isBlank() || timeSlot.isBlank()) {
            return TableAllocationResult.Failure("Booking date and time slot must not be blank.")
        }
        if (partySize <= 0) {
            return TableAllocationResult.Failure("Party size must be at least 1 guest.")
        }
        if (partySize > 20) {
            return TableAllocationResult.Failure("Party size cannot exceed 20 guests.")
        }

        val blockingStatuses = setOf(TableBookingStatus.CONFIRMED, TableBookingStatus.SEATED)

        val bookedTableNumbers = existingBookings
            .filter { booking ->
                booking.bookingDate == bookingDate &&
                booking.timeSlot == timeSlot &&
                blockingStatuses.contains(booking.status) &&
                (booking.restaurantId.isBlank() || restaurantId.isBlank() || booking.restaurantId == restaurantId)
            }
            .map { it.tableNumber }
            .toSet()

        val availableTables = inventory.filter { !bookedTableNumbers.contains(it.tableNumber) }

        val areaMatching = availableTables.filter { it.seatingArea.equals(seatingArea, ignoreCase = true) }

        if (areaMatching.isEmpty()) {
            val totalInArea = inventory.filter { it.seatingArea.equals(seatingArea, ignoreCase = true) }
            val reason = if (totalInArea.isEmpty()) {
                "Seating area '$seatingArea' does not exist in this restaurant."
            } else if (totalInArea.none { it.capacity >= partySize }) {
                "No tables in '$seatingArea' have sufficient capacity for $partySize guests. Maximum capacity in this area is ${totalInArea.maxOfOrNull { it.capacity } ?: 0}."
            } else {
                "All tables in '$seatingArea' are fully booked for $timeSlot on $bookingDate. Please select another time slot or seating area."
            }
            return TableAllocationResult.Failure(reason)
        }

        val capableTables = areaMatching.filter { it.capacity >= partySize }

        if (capableTables.isEmpty()) {
            val maxCap = areaMatching.maxOfOrNull { it.capacity } ?: 0
            return TableAllocationResult.Failure(
                "No tables in '$seatingArea' can accommodate $partySize guests (maximum capacity is $maxCap). Please choose a smaller party size or another seating area."
            )
        }

        val chosenTable = capableTables.sortedWith(
            compareBy<RestaurantTable> { it.capacity }.thenBy { it.tableNumber }
        ).first()

        val assignedTableId = if (restaurantId.isNotBlank()) "${restaurantId}_${chosenTable.tableNumber}" else chosenTable.tableId

        val confirmedBooking = TableBooking(
            id = TableBooking.generateId(),
            userId = userId.ifBlank { "guest_user" },
            userName = userName.ifBlank { "Guest" },
            userPhone = userPhone,
            tableId = assignedTableId,
            tableNumber = chosenTable.tableNumber,
            bookingDate = bookingDate,
            timeSlot = timeSlot,
            partySize = partySize,
            seatingArea = chosenTable.seatingArea,
            specialRequests = specialRequests.trim(),
            status = TableBookingStatus.CONFIRMED,
            createdAt = Instant.now().toString(),
            restaurantId = restaurantId,
            restaurantName = restaurantName
        )

        return TableAllocationResult.Success(confirmedBooking, chosenTable)
    }
}
