package com.restaurant.mobile.delivery

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

data class AddOn(val id: String, val name: String, val price: Long)

data class DishTranslation(
    val name: String,
    val description: String = ""
)

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
    val translations: Map<String, DishTranslation> = emptyMap()
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
    val rating: Double = 4.8,
    val reviewCount: Int = 1240,
    val costForTwo: Int = 850,
    val vibes: List<String>? = listOf("Fine Dining", "Romantic", "Family Friendly"),
    val amenities: List<String>? = listOf("AC Dining", "Valet Parking", "Full Bar"),
    val tableBookingAvailable: Boolean = true,
    val maxPartySize: Int = 12,
    val prepMinutes: Int = 22,
    val acceptsDiningPayment: Boolean = true,
    val logoUrl: String? = "",
    val fssaiNumber: String? = "11523006000482",
    val gstNumber: String? = "27AABCU9603R1ZM",
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
    RIDER("Delivery Rider"),
    ADMIN("Admin"),
    MASTER_ADMIN("Master Admin"),
    KIOSK("Self-Service Kiosk");

    val isStaff: Boolean
        get() = this != CUSTOMER

    val isAdmin: Boolean
        get() = this == ADMIN || this == MASTER_ADMIN

    val isMasterAdmin: Boolean
        get() = this == MASTER_ADMIN

    companion object {
        fun fromString(value: String?): UserRole =
            entries.find { it.name.equals(value?.trim(), ignoreCase = true) } ?: CUSTOMER
    }
}

data class RiderRideRecord(
    val id: String,
    val riderId: String,
    val orderId: String,
    val restaurantName: String,
    val dropAddress: String,
    val distanceKm: Double,
    val durationMinutes: Int,
    val incomeRupees: Long,
    val completedAtMs: Long = System.currentTimeMillis()
)

data class RiderPayoutRecord(
    val id: String,
    val riderId: String,
    val amountRupees: Long,
    val accountDestination: String,
    val status: String = "PENDING",
    val timestampMs: Long = System.currentTimeMillis(),
    val payoutMethod: String = "UPI",
    val referenceId: String = "",
    val utr: String = "",
    val failureReason: String = "",
    val idempotencyKey: String = "",
    val receiptNumber: String = ""
)

data class PayoutValidationResult(val isValid: Boolean, val errorMessage: String? = null)

data class RiderPayoutReceipt(
    val receiptNumber: String,
    val payoutId: String,
    val riderId: String,
    val riderName: String = "Rider",
    val amountRupees: Long,
    val currency: String = "INR",
    val payoutMethod: String = "UPI",
    val maskedDestination: String = "",
    val destinationMasked: String = maskedDestination,
    val status: String = "PROCESSED",
    val referenceId: String = "",
    val utr: String = "",
    val failureReason: String = "",
    val createdAt: String = "",
    val processedAt: String = "",
    val timestampMs: Long = System.currentTimeMillis(),
    val receiptText: String = "",
    val issuer: String = "Restro Up Delivery Partner Payouts",
    val legalNote: String = "System-generated electronic payout receipt powered by RazorpayX."
)

object RiderPayoutRules {
    private val UPI_VPA_REGEX = Regex("^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}$")

    fun validateVpa(vpa: String): PayoutValidationResult {
        val trimmed = vpa.trim()
        if (trimmed.isBlank()) {
            return PayoutValidationResult(false, "UPI ID cannot be blank.")
        }
        if (!trimmed.contains("@")) {
            return PayoutValidationResult(false, "UPI ID must contain '@' handle (e.g. name@okaxis).")
        }
        if (!UPI_VPA_REGEX.matches(trimmed)) {
            return PayoutValidationResult(false, "Invalid UPI VPA format. Must be like rider@bank.")
        }
        return PayoutValidationResult(true)
    }

    fun validatePayoutAmount(amount: Long, availableBalance: Long = 100_000L): PayoutValidationResult {
        if (amount <= 0L) {
            return PayoutValidationResult(false, "Payout amount must be positive.")
        }
        if (amount < 10L) {
            return PayoutValidationResult(false, "Minimum payout amount is ₹10.")
        }
        if (amount > availableBalance) {
            return PayoutValidationResult(false, "Payout amount exceeds available balance of ₹$availableBalance.")
        }
        if (amount > 100_000L) {
            return PayoutValidationResult(false, "Single payout cannot exceed ₹100,000.")
        }
        return PayoutValidationResult(true)
    }

    fun maskDestination(dest: String): String {
        val trimmed = dest.trim()
        if (trimmed.isEmpty()) return "••••"
        if (trimmed.contains("@")) {
            val parts = trimmed.split("@")
            val user = parts[0]
            val domain = parts.getOrNull(1).orEmpty()
            return if (user.length <= 2) {
                "${user.firstOrNull() ?: '*'}***@$domain"
            } else {
                "${user.first()}***${user.last()}@$domain"
            }
        }
        val digits = trimmed.filter { it.isDigit() }
        return if (digits.length >= 4) {
            "•••• •••• ${digits.takeLast(4)}"
        } else {
            "•••• $trimmed"
        }
    }

    fun generateReceiptText(receipt: RiderPayoutReceipt): String {
        return buildString {
            appendLine("==========================================")
            appendLine("           RESTRO UP PAYOUT RECEIPT       ")
            appendLine("==========================================")
            appendLine("Receipt No  : ${receipt.receiptNumber}")
            appendLine("Payout ID   : ${receipt.payoutId}")
            appendLine("Rider Name  : ${receipt.riderName} (${receipt.riderId})")
            appendLine("Amount      : ₹${receipt.amountRupees} ${receipt.currency}")
            appendLine("Method      : ${receipt.payoutMethod}")
            val dest = if (receipt.maskedDestination.isNotBlank()) receipt.maskedDestination else receipt.destinationMasked
            appendLine("Destination : $dest")
            appendLine("Status      : ${receipt.status.uppercase()}")
            if (receipt.referenceId.isNotBlank()) {
                appendLine("Reference ID: ${receipt.referenceId}")
            }
            if (receipt.utr.isNotBlank()) {
                appendLine("Bank UTR    : ${receipt.utr}")
            }
            if (receipt.createdAt.isNotBlank()) {
                appendLine("Date        : ${receipt.createdAt}")
            }
            appendLine("------------------------------------------")
            appendLine(receipt.legalNote)
            appendLine("==========================================")
        }
    }

    fun generateReceiptText(payout: RiderPayoutRecord, riderName: String = "Rider"): String {
        val receipt = RiderPayoutReceipt(
            receiptNumber = payout.receiptNumber.ifBlank { "REC-${payout.id}" },
            payoutId = payout.id,
            riderId = payout.riderId,
            riderName = riderName,
            amountRupees = payout.amountRupees,
            payoutMethod = payout.payoutMethod,
            maskedDestination = payout.accountDestination,
            destinationMasked = payout.accountDestination,
            status = payout.status,
            referenceId = payout.referenceId,
            utr = payout.utr,
            timestampMs = payout.timestampMs
        )
        return generateReceiptText(receipt)
    }
}

data class RiderRideOffer(
    val id: String,
    val orderId: String,
    val restaurantId: String,
    val restaurantName: String,
    val pickupAddress: String,
    val dropAddress: String,
    val distanceKm: Double,
    val estimatedMinutes: Int,
    val riderIncomeRupees: Long,
    val status: String = "AVAILABLE" // AVAILABLE, ACCEPTED, DELIVERED
)

data class RiderRejectedOffer(
    val riderId: String,
    val offerId: String,
    val rejectedAtMs: Long = System.currentTimeMillis()
)

data class PartnerVerificationRecord(
    val userId: String,
    val role: String,
    val vehicleNumber: String = "",
    val drivingLicenseUri: String = "",
    val fssaiNumber: String = "",
    val fssaiCertUri: String = "",
    val gstNumber: String = "",
    val gstCertUri: String = "",
    val restaurantName: String = "",
    val restaurantLogoUri: String = "",
    val verificationStatus: String = "VERIFIED_BY_GEMINI_OCR",
    val extractedSummary: String = "",
    val verifiedAt: String = Instant.now().toString()
)

data class StaffPayrollRecord(
    val id: String,
    val staffId: String,
    val staffName: String,
    val staffRole: String, // "Waiter", "Chef", "Delivery", "Cashier", "Manager"
    val monthYear: String, // "October 2026"
    val baseSalary: Long, // in INR (e.g. 25000)
    val bonusTips: Long = 0L,
    val deductions: Long = 0L,
    val netSalary: Long = baseSalary + bonusTips - deductions,
    val status: String = "PENDING", // "PAID", "PENDING"
    val paidAt: String? = null,
    val paymentMethod: String = "UPI / Bank Transfer",
    val transactionRef: String = ""
)

data class Profile(
    val id: String,
    val name: String,
    val email: String,
    val phone: String = "",
    val reducedGlass: Boolean = false,
    val isDarkMode: Boolean = true,
    val role: UserRole = UserRole.CUSTOMER,
    val preferredLanguage: String = "en",
    val hasCompletedTour: Boolean = false
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

data class Rider(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val vehicleNumber: String = "",
    val vehicleModel: String = "Hero Splendor Electric",
    val rating: Double = 4.8,
    val totalDeliveries: Int = 300,
    val latitude: Double = 19.1672,
    val longitude: Double = 72.8518,
    val isAvailable: Boolean = true,
    val activeOrderId: String? = null,
    val batteryPercent: Int = 90,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class RiderAssignmentResult(
    val rider: Rider,
    val distanceKm: Double,
    val etaMinutes: Int,
    val candidateCount: Int,
    val dispatchLog: String
)

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
    /** Owner of the order (authenticated user id). Empty only for legacy rows created before v10.1. */
    val userId: String = "",
    val idempotencyKey: String? = null,
    val riderId: String = "",
    val riderName: String = "",
    val riderPhone: String = "",
    val riderVehicle: String = "",
    val riderRating: Double = 4.8,
    val riderLatitude: Double = 0.0,
    val riderLongitude: Double = 0.0,
)

/** Payment status values. Only a provider-verified payment may ever be [PAID_VERIFIED]. */
object PaymentStatus {
    const val PENDING_COD = "Pending - pay on delivery"
    const val TEST_SIMULATED = "TEST ONLY - simulated, no money collected"
    const val PAID_VERIFIED = "Paid (provider verified)"
    const val RECORDED_BY_STAFF = "Paid - recorded by staff"

    fun isCashOnDelivery(method: String): Boolean {
        val m = method.trim().lowercase()
        return m.contains("cash") || m.contains("on delivery") || m.contains("cod")
    }
}

/** Customer delivery order lifecycle. Transitions not listed here are rejected. */
object DeliveryOrderStatus {
    const val PLACED = "Placed"
    const val CONFIRMED = "Confirmed"
    const val PREPARING = "Preparing"
    const val READY = "Ready"
    const val ACCEPTED = "Accepted"
    const val OUT_FOR_DELIVERY = "Out for delivery"
    const val DELIVERED = "Delivered"
    const val CANCELLED = "Cancelled"

    private val allowed = mapOf(
        PLACED to setOf(CONFIRMED, PREPARING, CANCELLED),
        CONFIRMED to setOf(PREPARING, READY, ACCEPTED, CANCELLED),
        PREPARING to setOf(READY, ACCEPTED, OUT_FOR_DELIVERY, CANCELLED),
        READY to setOf(ACCEPTED, OUT_FOR_DELIVERY),
        ACCEPTED to setOf(OUT_FOR_DELIVERY, DELIVERED),
        OUT_FOR_DELIVERY to setOf(DELIVERED),
        DELIVERED to emptySet(),
        CANCELLED to emptySet(),
    )

    fun canTransition(from: String, to: String): Boolean {
        if (from.equals(to, ignoreCase = true)) return true
        val matchedFrom = allowed.keys.find { it.equals(from, ignoreCase = true) } ?: from
        val matchedTo = allowed.keys.find { it.equals(to, ignoreCase = true) } ?: to
        return allowed[matchedFrom]?.any { it.equals(matchedTo, ignoreCase = true) } == true
    }

    /** A customer may cancel only before the kitchen starts preparing. */
    fun customerCanCancel(status: String): Boolean =
        status.equals(CONFIRMED, ignoreCase = true) || status.equals(PLACED, ignoreCase = true)
}

data class Review(
    val orderId: String,
    val restaurantId: String,
    val author: String,
    val rating: Int,
    val text: String,
    val at: String = Instant.now().toString(),
    val dishId: String = "",
    val dishName: String = "",
    val verifiedPurchase: Boolean = true,
)

data class Credentials(
    val email: String,
    val password: String,
    val name: String = "",
    val role: String = "CUSTOMER",
    val phone: String = "",
    val vehicleNumber: String = "",
    val drivingLicenseUri: String = "",
    val fssaiNumber: String = "",
    val fssaiCertUri: String = "",
    val gstNumber: String = "",
    val gstCertUri: String = "",
    val restaurantName: String = "",
    val restaurantLogoUri: String = "",
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
    val phone: String = "",
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
    val id: String = generateId(),
    val userId: String = "user-1",
    val userName: String = "Guest",
    val userPhone: String = "9876543210",
    val tableId: String = "",
    val tableNumber: String = "",
    val bookingDate: String = "",
    val timeSlot: String = "",
    val partySize: Int = 2,
    val seatingArea: String = "Main Dining Room",
    val specialRequests: String = "",
    val status: String = TableBookingStatus.CONFIRMED,
    val createdAt: String = Instant.now().toString(),
    val restaurantId: String = "",
    val restaurantName: String = "",
    val updatedAt: String = ""
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
    val restaurantId: String = "",
)

object RestaurantTableDefaults {
    val DEFAULT_TABLES = listOf(
        RestaurantTable("T1", "T1", 2, "Main Dining Room"),
        RestaurantTable("T2", "T2", 2, "Main Dining Room"),
        RestaurantTable("T3", "T3", 4, "Main Dining Room"),
        RestaurantTable("T4", "T4", 4, "Main Dining Room"),
        RestaurantTable("T5", "T5", 6, "Main Dining Room"),
        RestaurantTable("T6", "T6", 10, "Main Dining Room"),
        RestaurantTable("T7", "T7", 2, "Outdoor Garden"),
        RestaurantTable("T8", "T8", 4, "Outdoor Garden"),
        RestaurantTable("T9", "T9", 4, "Private Booth"),
        RestaurantTable("T10", "T10", 6, "Private Booth"),
        RestaurantTable("T11", "T11", 2, "Rooftop Lounge"),
        RestaurantTable("T12", "T12", 12, "Rooftop Lounge"),
    )

    fun getTablesForRestaurant(restaurantId: String): List<RestaurantTable> {
        val baseTables = when (restaurantId) {
            "rest-1" -> DEFAULT_TABLES
            "rest-2" -> listOf(
                RestaurantTable("T1", "T1", 2, "Main Dining Room"),
                RestaurantTable("T2", "T2", 2, "Main Dining Room"),
                RestaurantTable("T3", "T3", 4, "Main Dining Room"),
                RestaurantTable("T4", "T4", 4, "Main Dining Room"),
                RestaurantTable("T5", "T5", 12, "Main Dining Room"),
                RestaurantTable("T6", "T6", 2, "Outdoor Garden"),
                RestaurantTable("T7", "T7", 4, "Outdoor Garden"),
                RestaurantTable("T8", "T8", 4, "Private Booth")
            )
            "rest-3" -> listOf(
                RestaurantTable("T1", "T1", 2, "Main Dining Room"),
                RestaurantTable("T2", "T2", 2, "Main Dining Room"),
                RestaurantTable("T3", "T3", 4, "Main Dining Room"),
                RestaurantTable("T4", "T4", 4, "Main Dining Room"),
                RestaurantTable("T5", "T5", 4, "Main Dining Room"),
                RestaurantTable("T6", "T6", 6, "Main Dining Room"),
                RestaurantTable("T7", "T7", 6, "Main Dining Room"),
                RestaurantTable("T8", "T8", 20, "Main Dining Room"),
                RestaurantTable("T9", "T9", 2, "Outdoor Garden"),
                RestaurantTable("T10", "T10", 4, "Outdoor Garden"),
                RestaurantTable("T11", "T11", 4, "Outdoor Garden"),
                RestaurantTable("T12", "T12", 4, "Private Booth"),
                RestaurantTable("T13", "T13", 6, "Private Booth"),
                RestaurantTable("T14", "T14", 8, "Rooftop Lounge")
            )
            "rest-4" -> listOf(
                RestaurantTable("T1", "T1", 2, "Main Dining Room"),
                RestaurantTable("T2", "T2", 2, "Main Dining Room"),
                RestaurantTable("T3", "T3", 4, "Main Dining Room"),
                RestaurantTable("T4", "T4", 10, "Main Dining Room"),
                RestaurantTable("T5", "T5", 2, "Private Booth"),
                RestaurantTable("T6", "T6", 4, "Private Booth")
            )
            "rest-5" -> listOf(
                RestaurantTable("T1", "T1", 2, "Main Dining Room"),
                RestaurantTable("T2", "T2", 4, "Main Dining Room"),
                RestaurantTable("T3", "T3", 4, "Main Dining Room"),
                RestaurantTable("T4", "T4", 2, "Outdoor Garden"),
                RestaurantTable("T5", "T5", 4, "Outdoor Garden"),
                RestaurantTable("T6", "T6", 4, "Rooftop Lounge"),
                RestaurantTable("T7", "T7", 6, "Rooftop Lounge")
            )
            else -> DEFAULT_TABLES
        }
        return baseTables.map { table ->
            table.copy(
                tableId = if (restaurantId.isNotBlank()) "${restaurantId}_${table.tableNumber}" else table.tableId,
                restaurantId = restaurantId
            )
        }
    }
}

sealed class TableAllocationResult {
    data class Success(val booking: TableBooking, val allocatedTable: RestaurantTable) : TableAllocationResult()
    data class Failure(val reason: String) : TableAllocationResult()
}

object TableAllocationEngine {
    /**
     * Determines whether a booking belongs to the target restaurant.
     * Prevents cross-restaurant availability collisions while maintaining backward compatibility.
     */
    fun isSameRestaurant(booking: TableBooking, targetRestaurantId: String): Boolean {
        return when {
            targetRestaurantId.isBlank() -> true
            booking.restaurantId.isNotBlank() -> booking.restaurantId == targetRestaurantId
            booking.tableId.startsWith("${targetRestaurantId}_") -> true
            booking.tableId.contains("_") -> false
            else -> false
        }
    }

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

        val effectiveInventory = if (inventory === RestaurantTableDefaults.DEFAULT_TABLES && restaurantId.isNotBlank()) {
            RestaurantTableDefaults.getTablesForRestaurant(restaurantId)
        } else {
            inventory
        }

        val blockingStatuses = setOf(TableBookingStatus.CONFIRMED, TableBookingStatus.SEATED)

        val bookedTableNumbers = existingBookings
            .filter { booking ->
                booking.bookingDate == bookingDate &&
                booking.timeSlot == timeSlot &&
                blockingStatuses.contains(booking.status) &&
                isSameRestaurant(booking, restaurantId)
            }
            .map { it.tableNumber }
            .toSet()

        val availableTables = effectiveInventory.filter { !bookedTableNumbers.contains(it.tableNumber) }

        val areaMatching = availableTables.filter { it.seatingArea.equals(seatingArea, ignoreCase = true) }

        if (areaMatching.isEmpty()) {
            val totalInArea = effectiveInventory.filter { it.seatingArea.equals(seatingArea, ignoreCase = true) }
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

        val assignedTableId = if (chosenTable.restaurantId.isNotBlank()) {
            chosenTable.tableId
        } else if (restaurantId.isNotBlank()) {
            "${restaurantId}_${chosenTable.tableNumber}"
        } else {
            chosenTable.tableId
        }

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

object TableBookingFilter {
    const val UPCOMING = "Upcoming"
    const val COMPLETED = "Completed"
    const val CANCELLED = "Cancelled"
    const val ALL = "All"
    val ALL_FILTERS = listOf(UPCOMING, COMPLETED, CANCELLED, ALL)
}

sealed class TableCancellationResult {
    data class Success(val booking: TableBooking) : TableCancellationResult()
    data class Failure(val reason: String) : TableCancellationResult()
}

val TableBooking.isEligibleForCancellation: Boolean
    get() = status == TableBookingStatus.CONFIRMED

object TableBookingManagementRules {
    private val DEFAULT_SLOT_ORDER = listOf(
        "12:00 PM", "1:00 PM", "2:00 PM",
        "7:00 PM", "8:00 PM", "9:00 PM"
    )

    fun filterAndSort(
        bookings: List<TableBooking>,
        filter: String,
        slotOrder: List<String> = DEFAULT_SLOT_ORDER
    ): List<TableBooking> {
        return when (filter) {
            TableBookingFilter.UPCOMING -> bookings
                .filter { it.status == TableBookingStatus.CONFIRMED || it.status == TableBookingStatus.SEATED }
                .sortedWith(
                    compareBy<TableBooking> { it.bookingDate }
                        .thenBy { booking ->
                            val idx = slotOrder.indexOf(booking.timeSlot)
                            if (idx >= 0) idx else 99
                        }
                        .thenBy { it.timeSlot }
                        .thenBy { it.tableNumber }
                )
            TableBookingFilter.COMPLETED -> bookings
                .filter { it.status == TableBookingStatus.COMPLETED }
                .sortedWith(
                    compareByDescending<TableBooking> { it.bookingDate }
                        .thenByDescending { it.timeSlot }
                )
            TableBookingFilter.CANCELLED -> bookings
                .filter { it.status == TableBookingStatus.CANCELLED }
                .sortedWith(
                    compareByDescending<TableBooking> { it.bookingDate }
                        .thenByDescending { it.timeSlot }
                )
            else -> bookings.sortedWith(
                compareByDescending<TableBooking> { it.bookingDate }
                    .thenByDescending { it.timeSlot }
            )
        }
    }

    fun validateCancellationEligibility(booking: TableBooking?, requestingUserId: String): TableCancellationResult {
        if (booking == null) {
            return TableCancellationResult.Failure("Reservation not found.")
        }
        if (requestingUserId.isBlank()) {
            return TableCancellationResult.Failure("Customer identification is required.")
        }
        if (booking.userId != requestingUserId) {
            return TableCancellationResult.Failure("You are not authorized to cancel this reservation.")
        }
        if (booking.status == TableBookingStatus.CANCELLED) {
            return TableCancellationResult.Failure("This reservation is already cancelled.")
        }
        if (booking.status == TableBookingStatus.COMPLETED) {
            return TableCancellationResult.Failure("Completed reservations cannot be cancelled.")
        }
        if (booking.status != TableBookingStatus.CONFIRMED) {
            return TableCancellationResult.Failure("Only confirmed reservations can be cancelled (current status: ${booking.status}).")
        }
        return TableCancellationResult.Success(booking.copy(status = TableBookingStatus.CANCELLED))
    }
}

object CurrencyConversionRules {
    /**
     * Converts paise (Long) to rupees (Double).
     * 1 INR = 100 paise.
     */
    fun paiseToRupees(paise: Long): Double = paise / 100.0

    /**
     * Converts rupees (Double) to paise (Long) with standard half-up rounding.
     * Prevents floating-point truncation (e.g. 525.0 * 100 = 52500L).
     */
    fun rupeesToPaise(rupees: Double): Long = Math.round(rupees * 100.0)
}

data class TableBillItem(
    val dishId: String = "",
    val name: String,
    val quantity: Int,
    val price: Long = 0L,
    val total: Long = price * quantity,
    val orderedAt: String = Instant.now().toString(),
    val id: String = UUID.randomUUID().toString(),
    val menuItemId: Long = 0L,
    val taxRate: Double = 5.0,
    val specialInstructions: String = "",
    val addedAt: String = orderedAt,
    val round: Int = 1,
    val roundName: String = ""
) {
    val priceDouble: Double get() = CurrencyConversionRules.paiseToRupees(price)
    val itemTotal: Double get() = CurrencyConversionRules.paiseToRupees(total)

    /**
     * Secondary constructor for Waiter POS and callers providing prices in rupees (Double).
     */
    constructor(
        id: String = UUID.randomUUID().toString(),
        menuItemId: Long = 0L,
        name: String,
        price: Double,
        quantity: Int,
        taxRate: Double = 5.0,
        specialInstructions: String = "",
        addedAt: String = Instant.now().toString(),
        round: Int = 1,
        roundName: String = "",
        orderedAt: String = addedAt
    ) : this(
        dishId = if (menuItemId > 0L) "dish-$menuItemId" else id,
        name = name,
        quantity = quantity,
        price = CurrencyConversionRules.rupeesToPaise(price),
        total = CurrencyConversionRules.rupeesToPaise(price * quantity),
        orderedAt = orderedAt,
        id = id,
        menuItemId = menuItemId,
        taxRate = taxRate,
        specialInstructions = specialInstructions,
        addedAt = addedAt,
        round = round,
        roundName = roundName
    )
}

object DiningTabStatus {
    const val OPEN = "OPEN"
    const val ACTIVE = "ACTIVE"
    const val PAYMENT_PENDING = "PAYMENT_PENDING"
    const val PAID = "PAID"
    const val SETTLED = "SETTLED"
    const val CLOSED = "CLOSED"
    val ALL = listOf(OPEN, ACTIVE, PAYMENT_PENDING, PAID, SETTLED, CLOSED)
}

enum class TableLifecycleState(val label: String, val colorHex: Long) {
    AVAILABLE("Available", 0xFF10B981),                // Green
    RESERVED("Reserved", 0xFF8B5CF6),                  // Purple
    OCCUPIED("Occupied", 0xFFF59E0B),                  // Amber
    BILLED("Billed / Payment Pending", 0xFFEF4444)     // Red
}

data class DerivedTableState(
    val tableNumber: String,
    val lifecycleState: TableLifecycleState,
    val guestCount: Int? = null,
    val elapsedDiningTime: String? = null,
    val reservationInfo: String? = null,
    val activeTab: DiningTab? = null,
    val associatedBooking: TableBooking? = null
)

data class BillingSummary(
    val subtotal: Double,
    val tax: Double,
    val serviceCharge: Double,
    val tip: Double = 0.0,
    val total: Double,
    val isServiceChargeOptedOut: Boolean = false
) {
    val subtotalPaise: Long get() = CurrencyConversionRules.rupeesToPaise(subtotal)
    val taxPaise: Long get() = CurrencyConversionRules.rupeesToPaise(tax)
    val serviceChargePaise: Long get() = CurrencyConversionRules.rupeesToPaise(serviceCharge)
    val tipPaise: Long get() = CurrencyConversionRules.rupeesToPaise(tip)
    val totalPaise: Long get() = CurrencyConversionRules.rupeesToPaise(total)
}

data class DiningTab(
    val id: String = generateId(),
    val tableId: String = "",
    val tableNumber: String,
    val customerId: String? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val bookingId: String? = null,
    val userId: String? = customerId,
    val userName: String = customerName ?: "Guest Diner",
    val items: List<TableBillItem> = emptyList(),
    val subtotal: Long = 0L,
    val tax: Long = 0L,          // 5% GST in paise
    val serviceCharge: Long = 0L, // Optional 5% service charge in paise
    val tip: Long = 0L,
    val isServiceChargeOptedOut: Boolean = false,
    val total: Long = 0L,
    val status: String = if (tableId.isNotBlank()) DiningTabStatus.ACTIVE else DiningTabStatus.OPEN,
    val paymentMethod: String? = null,
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val restaurantId: String = "",
    val guestCount: Int = 0,
    val seatedAt: String = "",
    val billedAt: String = "",
    val paymentTransactionId: String = ""
) {
    val subtotalRupees: Double get() = CurrencyConversionRules.paiseToRupees(subtotal)
    val taxRupees: Double get() = CurrencyConversionRules.paiseToRupees(tax)
    val serviceChargeRupees: Double get() = CurrencyConversionRules.paiseToRupees(serviceCharge)
    val tipRupees: Double get() = CurrencyConversionRules.paiseToRupees(tip)
    val totalRupees: Double get() = CurrencyConversionRules.paiseToRupees(total)

    fun itemsGroupedByRound(): Map<Int, List<TableBillItem>> {
        return items.groupBy { it.round }
    }

    /**
     * Secondary constructor for POS and callers providing subtotal, tax, serviceCharge, and total in rupees (Double).
     */
    constructor(
        id: String = generateId(),
        tableId: String = "",
        tableNumber: String,
        customerId: String? = null,
        customerName: String? = null,
        customerPhone: String? = null,
        bookingId: String? = null,
        status: String = if (tableId.isNotBlank()) DiningTabStatus.ACTIVE else DiningTabStatus.OPEN,
        items: List<TableBillItem> = emptyList(),
        subtotal: Double = 0.0,
        tax: Double = 0.0,
        serviceCharge: Double = 0.0,
        tip: Double = 0.0,
        isServiceChargeOptedOut: Boolean = false,
        total: Double = 0.0,
        createdAt: String = Instant.now().toString(),
        updatedAt: String = Instant.now().toString(),
        restaurantId: String = "",
        guestCount: Int = 0,
        seatedAt: String = "",
        billedAt: String = "",
        paymentMethod: String? = null,
        paymentTransactionId: String = ""
    ) : this(
        id = id,
        tableId = tableId,
        tableNumber = tableNumber,
        customerId = customerId,
        customerName = customerName,
        customerPhone = customerPhone,
        bookingId = bookingId,
        userId = customerId,
        userName = customerName ?: "Guest Diner",
        items = items,
        subtotal = CurrencyConversionRules.rupeesToPaise(subtotal),
        tax = CurrencyConversionRules.rupeesToPaise(tax),
        serviceCharge = CurrencyConversionRules.rupeesToPaise(serviceCharge),
        tip = CurrencyConversionRules.rupeesToPaise(tip),
        isServiceChargeOptedOut = isServiceChargeOptedOut,
        total = CurrencyConversionRules.rupeesToPaise(total),
        status = status,
        paymentMethod = paymentMethod,
        createdAt = createdAt,
        updatedAt = updatedAt,
        restaurantId = restaurantId,
        guestCount = guestCount,
        seatedAt = seatedAt,
        billedAt = billedAt,
        paymentTransactionId = paymentTransactionId
    )

    companion object {
        fun generateId(): String = "TAB-${UUID.randomUUID().toString().take(8).uppercase()}"

        fun calculateBilling(
            items: List<TableBillItem>,
            existingServiceCharge: Double = 0.0,
            isServiceChargeOptedOut: Boolean = false,
            tip: Double = 0.0
        ): BillingSummary {
            val subtotal = items.sumOf { it.itemTotal }
            val tax = items.sumOf { (it.itemTotal) * (it.taxRate / 100.0) }
            val serviceCharge = if (isServiceChargeOptedOut) 0.0 else existingServiceCharge
            val validatedTip = if (tip > 0.0) tip else 0.0
            val total = subtotal + tax + serviceCharge + validatedTip
            return BillingSummary(
                subtotal = subtotal,
                tax = tax,
                serviceCharge = serviceCharge,
                tip = validatedTip,
                total = total,
                isServiceChargeOptedOut = isServiceChargeOptedOut
            )
        }
    }
}

data class DineInPaymentResult(
    val tab: DiningTab,
    val paymentMethod: String,
    val transactionId: String,
    val timestamp: String = Instant.now().toString()
)

data class DineInPaymentBilling(
    val subtotal: Long = 0L,
    val tax: Long = 0L,
    val serviceCharge: Long = 0L,
    val tip: Long = 0L,
    val total: Long = 0L,
    val isServiceChargeOptedOut: Boolean = false
) {
    val totalRupees: Double get() = CurrencyConversionRules.paiseToRupees(total)
}

data class DineInPaymentRequest(
    val tabId: String? = null,
    val tableNumber: String? = null,
    val paymentMethod: String = "Card",
    val idempotencyKey: String = "",
    val expectedTotal: Long = 0L,
    val tip: Long = 0L,
    val isServiceChargeOptedOut: Boolean = false,
    val splitPartySize: Int = 1
)

data class DineInPaymentResponse(
    val success: Boolean = true,
    val status: String = "SETTLED",
    val tabId: String = "",
    val tableNumber: String = "",
    val transactionId: String = "",
    val invoiceNumber: String = "",
    val paymentMethod: String = "Card",
    val billing: DineInPaymentBilling = DineInPaymentBilling(),
    val settledAt: String = Instant.now().toString()
)

sealed class DineInSecurityValidationResult {
    data class Success(
        val subtotal: Long,
        val tax: Long,
        val serviceCharge: Long,
        val tip: Long,
        val total: Long
    ) : DineInSecurityValidationResult()

    data class Error(
        val code: String,
        val message: String,
        val authoritativeTotal: Long? = null
    ) : DineInSecurityValidationResult()
}

object TipAndServiceChargeRules {
    const val SERVICE_CHARGE_PERCENT = 5.0
    val PRESET_TIPS = listOf(0.0, 50.0, 100.0, 200.0)

    enum class TipOption(val label: String, val amount: Double?) {
        NO_TIP("No Tip", 0.0),
        TIP_50("₹50", 50.0),
        TIP_100("₹100", 100.0),
        TIP_200("₹200", 200.0),
        CUSTOM("Custom", null)
    }

    /**
     * Validates user-entered custom tip amount.
     * Must be a non-negative finite number.
     * Returns null for empty, blank, non-numeric, NaN, infinite, or negative values.
     */
    fun validateCustomTip(input: String?): Double? {
        if (input.isNullOrBlank()) return null
        val cleaned = input.trim()
        val parsed = cleaned.toDoubleOrNull() ?: return null
        if (parsed.isNaN() || parsed.isInfinite() || parsed < 0.0) return null
        return parsed
    }

    /**
     * Resolves effective tip amount based on selected option and custom input.
     */
    fun resolveTipAmount(option: TipOption, customInput: String = ""): Double {
        return when (option) {
            TipOption.NO_TIP -> 0.0
            TipOption.TIP_50 -> 50.0
            TipOption.TIP_100 -> 100.0
            TipOption.TIP_200 -> 200.0
            TipOption.CUSTOM -> validateCustomTip(customInput) ?: 0.0
        }
    }

    /**
     * Computes the 5% service charge, or 0.0 if opted out.
     */
    fun computeServiceCharge(subtotal: Double, isOptedOut: Boolean): Double {
        if (isOptedOut || subtotal <= 0.0) return 0.0
        return subtotal * (SERVICE_CHARGE_PERCENT / 100.0)
    }

    /**
     * Computes final billing summary immediately reflecting tip and service-charge customization.
     */
    fun computeFinalBill(
        subtotal: Double,
        tax: Double,
        isServiceChargeOptedOut: Boolean,
        tipAmount: Double
    ): BillingSummary {
        val sc = computeServiceCharge(subtotal, isServiceChargeOptedOut)
        val validTip = if (tipAmount > 0.0) tipAmount else 0.0
        val total = subtotal + tax + sc + validTip
        return BillingSummary(
            subtotal = subtotal,
            tax = tax,
            serviceCharge = sc,
            tip = validTip,
            total = total,
            isServiceChargeOptedOut = isServiceChargeOptedOut
        )
    }
}

data class BillSplitResult(
    val partySize: Int,
    val totalAmount: Double,
    val perPersonBaseAmount: Double,
    val remainderAmount: Double = 0.0,
    val individualShares: List<Double> = emptyList(),
    val isValid: Boolean = true
) {
    val totalReconciled: Double
        get() = individualShares.sum()

    fun isReconciled(): Boolean {
        if (!isValid || individualShares.isEmpty()) return true
        val sumPaise = individualShares.sumOf { CurrencyConversionRules.rupeesToPaise(it) }
        val expectedPaise = CurrencyConversionRules.rupeesToPaise(totalAmount)
        return sumPaise == expectedPaise
    }
}

data class BillShare(
    val guestIndex: Int = 0,
    val amountPaise: Long = 0L
)

object BillSplittingRules {
    const val MIN_PARTY_SIZE = 1
    const val MAX_PARTY_SIZE = 50

    /**
     * Splits a bill in paise equally with exact remainder reconciliation (no penny loss).
     */
    fun splitBillEqually(totalPaise: Long, numGuests: Int): List<BillShare> {
        if (numGuests <= 0) return emptyList()
        val clampedGuests = numGuests.coerceIn(MIN_PARTY_SIZE, MAX_PARTY_SIZE)
        val safeTotal = if (totalPaise < 0L) 0L else totalPaise
        val basePaise = safeTotal / clampedGuests
        val remainder = (safeTotal % clampedGuests).toInt()

        return List(clampedGuests) { index ->
            val share = if (index < remainder) basePaise + 1L else basePaise
            BillShare(guestIndex = index + 1, amountPaise = share)
        }
    }

    /**
     * Validates party size.
     * Returns null for non-positive or null values. Clamps within valid bounds.
     */
    fun validatePartySize(partySize: Int?): Int? {
        if (partySize == null || partySize <= 0) return null
        return partySize.coerceIn(MIN_PARTY_SIZE, MAX_PARTY_SIZE)
    }

    /**
     * Splits total bill among partySize people.
     * Safely handles:
     * - partySize <= 0: marks invalid, returns 0.0 per person, empty shares.
     * - totalAmount <= 0.0: returns 0.0 per person.
     * - Remainder amounts distributed 1 paisa (₹0.01) each to the first remainder diners,
     *   ensuring the sum of individual shares exactly reconciles with the authoritative total down to the paisa.
     */
    fun splitBill(totalAmount: Double, partySize: Int): BillSplitResult {
        if (partySize <= 0) {
            val safeTotal = if (totalAmount.isNaN() || totalAmount.isInfinite() || totalAmount < 0.0) 0.0 else totalAmount
            return BillSplitResult(
                partySize = partySize,
                totalAmount = safeTotal,
                perPersonBaseAmount = 0.0,
                remainderAmount = 0.0,
                individualShares = emptyList(),
                isValid = false
            )
        }

        val clampedSize = partySize.coerceIn(MIN_PARTY_SIZE, MAX_PARTY_SIZE)
        val validTotal = if (totalAmount.isNaN() || totalAmount.isInfinite() || totalAmount < 0.0) 0.0 else totalAmount

        val totalPaise = CurrencyConversionRules.rupeesToPaise(validTotal)
        val basePaise = totalPaise / clampedSize
        val remainderPaise = totalPaise % clampedSize

        val shares = List(clampedSize) { index ->
            val sharePaise = if (index < remainderPaise) basePaise + 1L else basePaise
            CurrencyConversionRules.paiseToRupees(sharePaise)
        }

        val baseAmountRupees = CurrencyConversionRules.paiseToRupees(basePaise)
        val remainderRupees = CurrencyConversionRules.paiseToRupees(remainderPaise)

        return BillSplitResult(
            partySize = clampedSize,
            totalAmount = validTotal,
            perPersonBaseAmount = baseAmountRupees,
            remainderAmount = remainderRupees,
            individualShares = shares,
            isValid = true
        )
    }

    /**
     * Computes equal split base share directly.
     */
    fun calculateEqualSplit(totalAmount: Double, partySize: Int): Double {
        return splitBill(totalAmount, partySize).perPersonBaseAmount
    }

    /**
     * Formats party size and per-person share string for display.
     */
    fun formatPerPersonShare(result: BillSplitResult): String {
        if (!result.isValid || result.partySize <= 0) return "₹0.00 / person"
        return String.format(java.util.Locale.ENGLISH, "₹%.2f / person", result.perPersonBaseAmount)
    }
}

object DineInPaymentRules {
    /**
     * Checks whether a dining tab status represents an already finalized/settled tab.
     */
    fun isSettledOrPaid(status: String?): Boolean {
        if (status.isNullOrBlank()) return false
        val s = status.trim().uppercase()
        return s == DiningTabStatus.PAID || s == DiningTabStatus.SETTLED || s == DiningTabStatus.CLOSED
    }

    /**
     * Validates payment initiation.
     * Rejects:
     * - Already finalized/settled tabs (duplicate settlement protection).
     * - Tabs with zero or negative subtotal and zero tip.
     * Returns the authoritative BillingSummary on success, or null on rejection.
     */
    fun validatePaymentInitiation(
        tab: DiningTab,
        tip: Double = 0.0,
        isServiceChargeOptedOut: Boolean = false
    ): BillingSummary? {
        if (isSettledOrPaid(tab.status)) return null

        val effectiveSubtotal = if (tab.subtotalRupees > 0.0) tab.subtotalRupees else tab.items.sumOf { it.itemTotal }
        val effectiveTax = if (tab.taxRupees > 0.0) tab.taxRupees else tab.items.sumOf { it.itemTotal * (it.taxRate / 100.0) }

        if (effectiveSubtotal <= 0.0 && tip <= 0.0) return null

        return TipAndServiceChargeRules.computeFinalBill(
            subtotal = effectiveSubtotal,
            tax = effectiveTax,
            isServiceChargeOptedOut = isServiceChargeOptedOut,
            tipAmount = tip
        )
    }

    /**
     * Validates whether a payment can be verified and settled.
     * Requires:
     * 1. Tab must NOT be already finalized/settled (rejects duplicate settlement).
     * 2. Tab status must be in PAYMENT_PENDING.
     * 3. An authoritative, non-blank transaction ID must exist (passed in or previously recorded on tab).
     * 4. Authoritative total must be positive (> 0).
     *
     * Returns the validated authoritative transaction ID on success, or null on failure.
     */
    fun validatePaymentVerification(
        tab: DiningTab,
        transactionId: String = ""
    ): String? {
        if (isSettledOrPaid(tab.status)) return null
        if (tab.status != DiningTabStatus.PAYMENT_PENDING) return null
        if (tab.total <= 0L && tab.subtotal <= 0L) return null

        val resolvedTxn = transactionId.trim().ifBlank { tab.paymentTransactionId.trim() }
        if (resolvedTxn.isBlank()) return null

        return resolvedTxn
    }

    /**
     * Validates whether a payment can be reverted or marked failed.
     * Rejects already settled/paid tabs.
     * If valid, returns the tab updated to ACTIVE with cleared billedAt and paymentTransactionId.
     */
    fun validatePaymentFailureOrCancellation(
        tab: DiningTab,
        reason: String = "Payment failed"
    ): DiningTab? {
        if (isSettledOrPaid(tab.status)) return null

        return tab.copy(
            status = DiningTabStatus.ACTIVE,
            billedAt = "",
            paymentTransactionId = ""
        )
    }

    /**
     * Validates that the payment amount equals the authoritative bill total down to the paisa.
     */
    fun isPaymentPayloadTotalConsistent(
        authoritativeTotalPaise: Long,
        payloadTotalPaise: Long
    ): Boolean {
        return authoritativeTotalPaise > 0L && authoritativeTotalPaise == payloadTotalPaise
    }

    /**
     * Validates payment security against tampering, duplicate settlement, and invalid quantities.
     */
    fun validateDineInPaymentSecurity(
        tab: DiningTab,
        expectedTotalPaise: Long,
        tipPaise: Long = 0L,
        isServiceChargeOptedOut: Boolean = false,
        partySize: Int = 1
    ): DineInSecurityValidationResult {
        if (isSettledOrPaid(tab.status)) {
            return DineInSecurityValidationResult.Error(
                "TAB_ALREADY_PAID",
                "Dining tab has already been settled and paid."
            )
        }
        if (tab.items.isEmpty()) {
            return DineInSecurityValidationResult.Error(
                "EMPTY_TAB",
                "Cannot settle a dining tab with no items."
            )
        }
        if (tab.items.any { it.quantity <= 0 }) {
            return DineInSecurityValidationResult.Error(
                "NEGATIVE_QUANTITY_ATTACK",
                "Dish quantity must be a positive integer greater than 0."
            )
        }
        if (tipPaise < 0L) {
            return DineInSecurityValidationResult.Error(
                "INVALID_TIP",
                "Tip must be a non-negative number."
            )
        }
        if (partySize < 1) {
            return DineInSecurityValidationResult.Error(
                "INVALID_PARTY_SIZE",
                "Party size must be a positive integer of at least 1."
            )
        }

        val subtotal = tab.items.sumOf { it.price * it.quantity }
        val tax = Math.round(subtotal * 0.05)
        val sc = if (isServiceChargeOptedOut || tab.isServiceChargeOptedOut) 0L else Math.round(subtotal * 0.05)
        val tip = if (tipPaise > 0L) tipPaise else tab.tip
        val authoritativeTotal = subtotal + tax + sc + tip

        if (expectedTotalPaise != authoritativeTotal) {
            return DineInSecurityValidationResult.Error(
                "DINE_IN_PRICE_TAMPERING_DETECTED",
                "Price integrity violation: authoritative total is $authoritativeTotal paise, but received $expectedTotalPaise paise.",
                authoritativeTotal = authoritativeTotal
            )
        }

        return DineInSecurityValidationResult.Success(
            subtotal = subtotal,
            tax = tax,
            serviceCharge = sc,
            tip = tip,
            total = authoritativeTotal
        )
    }

    /**
     * Checks if a payment is eligible based on restaurant.acceptsDiningPayment.
     * Online Dine-In is blocked when acceptsDiningPayment is false.
     * Delivery orders remain eligible regardless of acceptsDiningPayment.
     */
    fun isPaymentEligible(restaurant: Restaurant?, isDineIn: Boolean): Boolean {
        if (!isDineIn) return true
        return restaurant?.acceptsDiningPayment ?: true
    }

    /**
     * Validates callback fields for Razorpay signature verification.
     * All 3 fields (order ID, payment ID, signature) must be non-blank.
     */
    fun validateRazorpayCallbackPayload(orderId: String?, paymentId: String?, signature: String?): Boolean {
        return !orderId.isNullOrBlank() && !paymentId.isNullOrBlank() && !signature.isNullOrBlank()
    }

    /**
     * Validates that backend verification response confirmed payment verification.
     * Fails closed if response is null, verified is not true, or error is present.
     */
    fun isBackendVerificationSuccessful(response: Map<String, Any?>?): Boolean {
        if (response == null) return false
        val isVerified = response["verified"] == true
        val hasError = response["error"] != null
        return isVerified && !hasError
    }

    fun isBackendVerificationSuccessful(result: RazorpayVerificationResult?): Boolean {
        if (result == null) return false
        return result.success && result.verified && result.errorCode == null && result.orderId.isNotBlank() && result.paymentId.isNotBlank()
    }
}

data class RazorpayPaymentCallback(
    val razorpay_order_id: String,
    val razorpay_payment_id: String,
    val razorpay_signature: String
) {
    val razorpayOrderId: String get() = razorpay_order_id
    val razorpayPaymentId: String get() = razorpay_payment_id
    val razorpaySignature: String get() = razorpay_signature
}

data class RazorpayVerificationResult(
    val success: Boolean = true,
    val verified: Boolean = false,
    val orderId: String = "",
    val paymentId: String = "",
    val amount: Long = 0L,
    val currency: String = "INR",
    val duplicate: Boolean = false,
    val message: String = "",
    val errorCode: String? = null
)

object WaiterTablePairingRules {
    /**
     * Resolves the applicable active booking for a given table.
     * Rules:
     * 1. A table must match tableNumber or tableId.
     * 2. Only active bookings (SEATED or CONFIRMED) are considered; CANCELLED or COMPLETED bookings are strictly excluded.
     * 3. A SEATED booking represents a customer currently at the table and has highest priority.
     *    - If exactly one SEATED booking exists, return it.
     *    - If multiple SEATED bookings exist, pick the one matching the current service date or most recent.
     * 4. If no SEATED booking exists, check CONFIRMED bookings for the service date.
     *    - If exactly one CONFIRMED booking exists for the date, return it.
     *    - If multiple CONFIRMED bookings exist for different time slots on the same date:
     *      Match by current time slot / meal period if available; if ambiguous, return null (treat safely as walk-in to avoid associating the wrong customer).
     * 5. If no active reservation exists, return null (walk-in table).
     */
    fun resolveActiveBookingForTable(
        tableNumber: String,
        bookings: List<TableBooking>,
        serviceDate: String = LocalDate.now().toString(),
        currentTimeSlot: String? = null,
        restaurantId: String = ""
    ): TableBooking? {
        if (tableNumber.isBlank()) return null

        val activeStatuses = setOf(TableBookingStatus.SEATED, TableBookingStatus.CONFIRMED)
        val candidates = bookings.filter { booking ->
            (booking.tableNumber.equals(tableNumber, ignoreCase = true) ||
             booking.tableId.equals(tableNumber, ignoreCase = true) ||
             booking.tableId.endsWith("_$tableNumber")) &&
            activeStatuses.contains(booking.status) &&
            (restaurantId.isBlank() || booking.restaurantId.isBlank() || booking.restaurantId == restaurantId)
        }

        if (candidates.isEmpty()) return null

        // 1. Check for physically seated customers first
        val seatedCandidates = candidates.filter { it.status == TableBookingStatus.SEATED }
        if (seatedCandidates.isNotEmpty()) {
            if (seatedCandidates.size == 1) return seatedCandidates.first()
            val dateMatching = seatedCandidates.filter { it.bookingDate == serviceDate }
            return if (dateMatching.size == 1) dateMatching.first()
            else seatedCandidates.maxByOrNull { it.createdAt }
        }

        // 2. Check for confirmed bookings for today's service date
        val confirmedCandidates = candidates.filter {
            it.status == TableBookingStatus.CONFIRMED && it.bookingDate == serviceDate
        }

        if (confirmedCandidates.isEmpty()) return null
        if (confirmedCandidates.size == 1) return confirmedCandidates.first()

        // 3. Multiple confirmed candidates for the same date:
        if (!currentTimeSlot.isNullOrBlank()) {
            val slotMatches = confirmedCandidates.filter { it.timeSlot.equals(currentTimeSlot, ignoreCase = true) }
            if (slotMatches.size == 1) return slotMatches.first()
        }

        // Ambiguity safety: if multiple confirmed bookings exist for the same table on the same date and no unambiguous match,
        // do not guess an arbitrary customer! Return null to preserve integrity.
        return null
    }

    /**
     * Determines the formatted serving table header text.
     */
    fun formatServingHeader(tableNumber: String, activeBooking: TableBooking?): String {
        return if (activeBooking != null) {
            val statusDisplay = activeBooking.status.lowercase().replaceFirstChar { it.uppercase() }
            "Serving Table: $tableNumber (${activeBooking.userName} — $statusDisplay)"
        } else {
            "Serving Table: $tableNumber (Walk-in)"
        }
    }

    /**
     * Appends new items to an existing dining tab or creates a new tab if none exists.
     * Preserves existing items, customer associations, booking associations, and recomputes billing.
     */
    fun appendOrOpenDiningTab(
        existingTab: DiningTab?,
        tableNumber: String,
        tableId: String,
        newItems: List<TableBillItem>,
        associatedBooking: TableBooking? = null,
        restaurantId: String = ""
    ): DiningTab {
        require(tableNumber.isNotBlank()) { "Table number must not be blank" }
        require(newItems.isNotEmpty()) { "Cannot submit empty items to dining tab" }
        for (item in newItems) {
            require(item.quantity > 0) { "Item quantity must be greater than zero (${item.name})" }
            require(item.price >= 0L) { "Item price cannot be negative (${item.name})" }
        }

        val combinedItems = if (existingTab != null) {
            existingTab.items + newItems
        } else {
            newItems
        }

        val billing = DiningTab.calculateBilling(
            items = combinedItems,
            existingServiceCharge = existingTab?.serviceChargeRupees ?: 0.0,
            isServiceChargeOptedOut = existingTab?.isServiceChargeOptedOut ?: false,
            tip = existingTab?.tipRupees ?: 0.0
        )

        val customerId = existingTab?.customerId ?: associatedBooking?.userId
        val customerName = existingTab?.customerName ?: associatedBooking?.userName
        val customerPhone = existingTab?.customerPhone ?: associatedBooking?.userPhone
        val bookingId = existingTab?.bookingId ?: associatedBooking?.id
        val targetRestId = if (restaurantId.isNotBlank()) restaurantId else (existingTab?.restaurantId ?: associatedBooking?.restaurantId ?: "")

        val effectiveStatus = if (existingTab != null) existingTab.status else DiningTabStatus.ACTIVE

        return if (existingTab != null) {
            existingTab.copy(
                items = combinedItems,
                customerId = customerId,
                customerName = customerName,
                customerPhone = customerPhone,
                bookingId = bookingId,
                userId = customerId,
                userName = customerName ?: "Guest Diner",
                subtotal = billing.subtotalPaise,
                tax = billing.taxPaise,
                serviceCharge = billing.serviceChargePaise,
                tip = billing.tipPaise,
                isServiceChargeOptedOut = billing.isServiceChargeOptedOut,
                total = billing.totalPaise,
                updatedAt = Instant.now().toString(),
                restaurantId = targetRestId
            )
        } else {
            val nowStr = Instant.now().toString()
            DiningTab(
                id = DiningTab.generateId(),
                tableId = tableId.ifBlank { tableNumber },
                tableNumber = tableNumber,
                customerId = customerId,
                customerName = customerName,
                customerPhone = customerPhone,
                bookingId = bookingId,
                userId = customerId,
                userName = customerName ?: "Guest Diner",
                status = effectiveStatus,
                items = combinedItems,
                subtotal = billing.subtotalPaise,
                tax = billing.taxPaise,
                serviceCharge = billing.serviceChargePaise,
                tip = billing.tipPaise,
                isServiceChargeOptedOut = billing.isServiceChargeOptedOut,
                total = billing.totalPaise,
                createdAt = nowStr,
                updatedAt = nowStr,
                restaurantId = targetRestId,
                guestCount = associatedBooking?.partySize ?: 2,
                seatedAt = nowStr
            )
        }
    }
}

object KotTimerRules {
    /**
     * Formats the elapsed time since an order was placed using real persisted timestamps.
     * Examples:
     * - "Ordered just now"
     * - "Ordered 8m ago"
     * - "Ordered 1h 15m ago"
     * Gracefully handles missing, null, or malformed timestamps without crashing or fabricating elapsed times.
     */
    fun formatElapsedOrderTime(
        createdAtString: String?,
        now: Instant = Instant.now()
    ): String {
        if (createdAtString.isNullOrBlank()) return "Ordered just now"
        return try {
            val createdInstant = try {
                Instant.parse(createdAtString)
            } catch (_: Exception) {
                val epochMs = createdAtString.toLongOrNull()
                if (epochMs != null) Instant.ofEpochMilli(epochMs) else null
            } ?: return "Ordered recently"

            val duration = Duration.between(createdInstant, now)
            val seconds = duration.seconds
            val minutes = duration.toMinutes()

            when {
                seconds < 0 -> "Ordered just now"
                seconds < 60 -> {
                    if (seconds <= 10) "Ordered just now" else "Ordered ${seconds}s ago"
                }
                minutes < 60 -> "Ordered ${minutes}m ago"
                minutes < 1440 -> {
                    val hours = minutes / 60
                    val remMins = minutes % 60
                    if (remMins == 0L) "Ordered ${hours}h ago" else "Ordered ${hours}h ${remMins}m ago"
                }
                else -> {
                    val days = minutes / 1440
                    "Ordered ${days}d ago"
                }
            }
        } catch (_: Exception) {
            "Ordered recently"
        }
    }

    /**
     * Returns the standardized in-app notification message for ready KOT orders.
     * Example: "Order #KOT-12 for Table T3 is Ready for Pickup!"
     */
    fun formatReadyNotificationMessage(ticketId: Any, tableNumber: String): String {
        val raw = ticketId.toString()
        val formattedId = if (raw.startsWith("KOT-", ignoreCase = true)) raw else "KOT-$raw"
        val cleanTable = tableNumber.ifBlank { "Unassigned" }
        return "Order #$formattedId for Table $cleanTable is Ready for Pickup!"
    }
}

object KotRoundRules {
    val STANDARD_COURSES = listOf("Starters", "Mains", "Desserts", "Beverages")

    /**
     * Determines the round name based on item categories or default fallback.
     */
    fun detectRoundName(
        cartItems: List<com.restaurant.mobile.model.CartItem>,
        categories: List<com.restaurant.mobile.model.Category>
    ): String {
        if (cartItems.isEmpty()) return "Starters"
        val categoryMap = categories.associateBy { it.id }
        val categoryCounts = mutableMapOf<String, Int>()
        for (item in cartItems) {
            val catName = categoryMap[item.menuItem.categoryId]?.name ?: ""
            if (catName.isNotBlank()) {
                categoryCounts[catName] = (categoryCounts[catName] ?: 0) + item.quantity
            }
        }
        val topCategory = categoryCounts.maxByOrNull { it.value }?.key ?: ""
        return when {
            topCategory.contains("Starter", ignoreCase = true) -> "Starters"
            topCategory.contains("Main", ignoreCase = true) -> "Mains"
            topCategory.contains("Dessert", ignoreCase = true) -> "Desserts"
            topCategory.contains("Beverage", ignoreCase = true) || topCategory.contains("Drink", ignoreCase = true) -> "Beverages"
            topCategory.isNotBlank() -> topCategory
            else -> "Mains"
        }
    }
}

object TableLifecycleRules {
    const val RESERVATION_LEAD_MINUTES = 30L

    /**
     * Parses booking date (e.g., "2026-10-19") and time slot (e.g., "7:00 PM" or "19:00")
     * into a LocalDateTime. Handles missing or malformed dates/slots gracefully.
     */
    fun parseBookingDateTime(dateString: String?, timeSlotString: String?): LocalDateTime? {
        if (dateString.isNullOrBlank() || timeSlotString.isNullOrBlank()) return null
        val date = try {
            LocalDate.parse(dateString.trim())
        } catch (_: Exception) {
            return null
        }

        val cleanSlot = timeSlotString.trim().uppercase()
        val formatters = listOf(
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("h:mma", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("hh:mma", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("h a", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("ha", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("H:mm", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH)
        )
        for (fmt in formatters) {
            try {
                val time = LocalTime.parse(cleanSlot, fmt)
                return LocalDateTime.of(date, time)
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Determines whether a confirmed booking is currently within the active reservation window
     * (starting 30 minutes before the booking start time, until seated or booking window ends).
     */
    fun isWithinReservationWindow(
        bookingDateString: String?,
        timeSlotString: String?,
        now: LocalDateTime = LocalDateTime.now()
    ): Boolean {
        val bookingStart = parseBookingDateTime(bookingDateString, timeSlotString) ?: return false
        val windowStart = bookingStart.minusMinutes(RESERVATION_LEAD_MINUTES)
        val windowEnd = bookingStart.plusMinutes(90) // Standard reservation dining window
        return !now.isBefore(windowStart) && now.isBefore(windowEnd)
    }

    /**
     * Formats elapsed dining time from an ISO timestamp or epoch millis.
     * e.g., "Dining 12m", "Dining 1h 5m", "Dining just now"
     */
    fun formatElapsedDiningTime(startTimeString: String?, now: Instant = Instant.now()): String {
        if (startTimeString.isNullOrBlank()) return "Dining just now"
        return try {
            val startInstant = try {
                Instant.parse(startTimeString)
            } catch (_: Exception) {
                val epochMs = startTimeString.toLongOrNull()
                if (epochMs != null) Instant.ofEpochMilli(epochMs) else null
            } ?: return "Dining recently"

            val duration = Duration.between(startInstant, now)
            val seconds = duration.seconds
            val minutes = duration.toMinutes()
            when {
                seconds < 60 -> "Dining just now"
                minutes < 60 -> "Dining ${minutes}m"
                else -> {
                    val hours = minutes / 60
                    val remMins = minutes % 60
                    if (remMins == 0L) "Dining ${hours}h" else "Dining ${hours}h ${remMins}m"
                }
            }
        } catch (_: Exception) {
            "Dining recently"
        }
    }

    /**
     * Derives table lifecycle state deterministically from persisted bookings, dining tabs,
     * and payment status according to Prompt 15 requirements:
     * 1. Billed/Payment Pending (Red): Active tab with PAYMENT_PENDING status.
     * 2. Occupied/Seated (Amber): Active tab with ACTIVE status OR a physically SEATED booking.
     * 3. Reserved (Purple): Confirmed booking within 30 minutes of start time.
     * 4. Available (Green): No active reservation, seated diner, or active dining session.
     */
    fun deriveTableState(
        tableNumber: String,
        diningTab: DiningTab?,
        bookingsForTable: List<TableBooking>,
        now: LocalDateTime = LocalDateTime.now()
    ): DerivedTableState {
        // 1. Check for payment pending dining tab (Red)
        if (diningTab != null && diningTab.status == DiningTabStatus.PAYMENT_PENDING) {
            val startTime = diningTab.seatedAt.ifBlank { diningTab.createdAt }
            val elapsed = formatElapsedDiningTime(startTime)
            val guests = diningTab.guestCount.takeIf { it > 0 }
                ?: bookingsForTable.firstOrNull { it.id == diningTab.bookingId }?.partySize
                ?: bookingsForTable.firstOrNull { it.tableNumber.equals(tableNumber, ignoreCase = true) && it.status == TableBookingStatus.SEATED }?.partySize
                ?: 2
            return DerivedTableState(
                tableNumber = tableNumber,
                lifecycleState = TableLifecycleState.BILLED,
                guestCount = guests,
                elapsedDiningTime = elapsed,
                activeTab = diningTab
            )
        }

        // 2. Check for active occupied dining tab (Amber)
        if (diningTab != null && (diningTab.status == DiningTabStatus.ACTIVE || diningTab.status == DiningTabStatus.OPEN)) {
            val startTime = diningTab.seatedAt.ifBlank { diningTab.createdAt }
            val elapsed = formatElapsedDiningTime(startTime)
            val guests = diningTab.guestCount.takeIf { it > 0 }
                ?: bookingsForTable.firstOrNull { it.id == diningTab.bookingId }?.partySize
                ?: bookingsForTable.firstOrNull { it.tableNumber.equals(tableNumber, ignoreCase = true) && it.status == TableBookingStatus.SEATED }?.partySize
                ?: 2
            return DerivedTableState(
                tableNumber = tableNumber,
                lifecycleState = TableLifecycleState.OCCUPIED,
                guestCount = guests,
                elapsedDiningTime = elapsed,
                activeTab = diningTab
            )
        }

        // 3. Check for physically seated booking (guests seated before first order placed)
        val seatedBooking = bookingsForTable.firstOrNull {
            it.tableNumber.equals(tableNumber, ignoreCase = true) &&
            it.status == TableBookingStatus.SEATED
        }
        if (seatedBooking != null) {
            val seatedTime = seatedBooking.updatedAt.ifBlank { seatedBooking.createdAt }
            val elapsed = formatElapsedDiningTime(seatedTime)
            return DerivedTableState(
                tableNumber = tableNumber,
                lifecycleState = TableLifecycleState.OCCUPIED,
                guestCount = seatedBooking.partySize,
                elapsedDiningTime = elapsed,
                associatedBooking = seatedBooking
            )
        }

        // 4. Check for confirmed bookings within 30 minutes of start time (Purple)
        val activeConfirmedBooking = bookingsForTable.firstOrNull {
            it.tableNumber.equals(tableNumber, ignoreCase = true) &&
            it.status == TableBookingStatus.CONFIRMED &&
            isWithinReservationWindow(it.bookingDate, it.timeSlot, now)
        }
        if (activeConfirmedBooking != null) {
            return DerivedTableState(
                tableNumber = tableNumber,
                lifecycleState = TableLifecycleState.RESERVED,
                guestCount = activeConfirmedBooking.partySize,
                reservationInfo = "${activeConfirmedBooking.timeSlot} · ${activeConfirmedBooking.userName}",
                associatedBooking = activeConfirmedBooking
            )
        }

        // 5. Available (Green)
        return DerivedTableState(
            tableNumber = tableNumber,
            lifecycleState = TableLifecycleState.AVAILABLE
        )
    }
}

data class TableOccupancyMetrics(
    val occupiedTables: Int = 0,
    val totalTables: Int = 0,
    val occupancyRate: Double = 0.0
)

data class TopDishStat(
    val dishId: String,
    val name: String,
    val totalQuantity: Int,
    val dineInQuantity: Int = 0,
    val deliveryQuantity: Int = 0
)

data class AnalyticsSummary(
    val settledDineInRevenue: Long = 0L,
    val eligibleDeliveryRevenue: Long = 0L,
    val totalRevenue: Long = 0L,
    val averageDineInBillValue: Long = 0L,
    val averageDiningDurationMinutes: Long = 0L,
    val tableTurnoverRate: Double = 0.0,
    val totalDinersServed: Int = 0,
    val tableOccupancy: TableOccupancyMetrics = TableOccupancyMetrics(),
    val topDishes: List<TopDishStat> = emptyList()
)

object AnalyticsEngine {
    val ELIGIBLE_DELIVERY_STATUSES = setOf("Confirmed", "Preparing", "Out for delivery", "Delivered")
    val EXCLUDED_DELIVERY_STATUSES = setOf("Cancelled", "Failed")

    /**
     * Calculates total settled dine-in revenue in paise.
     * Only tabs with status = 'SETTLED' contribute. Unsettled tabs are excluded.
     */
    fun calculateSettledDineInRevenue(tabs: List<DiningTab>): Long {
        return tabs.filter { it.status == "SETTLED" }.sumOf { it.total }
    }

    /**
     * Calculates total delivery revenue in paise for eligible orders.
     * Only orders with status in ELIGIBLE_DELIVERY_STATUSES and not in EXCLUDED_DELIVERY_STATUSES contribute.
     */
    fun calculateEligibleDeliveryRevenue(orders: List<DeliveryOrder>): Long {
        return orders
            .filter { it.status in ELIGIBLE_DELIVERY_STATUSES && it.status !in EXCLUDED_DELIVERY_STATUSES }
            .sumOf { it.quote.total }
    }

    /**
     * Calculates average settled dine-in bill value in paise.
     * Returns 0L if no settled tabs exist.
     */
    fun calculateAverageDineInBillValue(tabs: List<DiningTab>): Long {
        val settledTabs = tabs.filter { it.status == "SETTLED" }
        if (settledTabs.isEmpty()) return 0L
        return settledTabs.sumOf { it.total } / settledTabs.size
    }

    /**
     * Calculates average dining duration in minutes across settled tabs.
     * Uses real timestamps: session start is derived from the earliest item orderedAt
     * (or associated booking createdAt), and session end is derived from tab updatedAt.
     * Persisted fields note: TABLE_DINING_TABS has no separate seated_at column; tabs without
     * parseable timestamps are excluded rather than fabricating arbitrary durations.
     */
    fun calculateAverageDiningDurationMinutes(
        tabs: List<DiningTab>,
        bookingsById: Map<String, TableBooking> = emptyMap()
    ): Long {
        val settledTabs = tabs.filter { it.status == "SETTLED" }
        var totalMinutes = 0L
        var validCount = 0

        for (tab in settledTabs) {
            val endInstant = try {
                Instant.parse(tab.updatedAt)
            } catch (_: Exception) {
                null
            } ?: continue

            val earliestItemInstant = tab.items.mapNotNull { item ->
                try { Instant.parse(item.orderedAt) } catch (_: Exception) { null }
            }.minOrNull()

            val bookingInstant = tab.bookingId?.let { bookingsById[it] }?.let { b ->
                try { Instant.parse(b.createdAt) } catch (_: Exception) { null }
            }

            val startInstant = earliestItemInstant ?: bookingInstant ?: continue

            val durationMinutes = Duration.between(startInstant, endInstant).toMinutes()
            if (durationMinutes >= 0) {
                totalMinutes += durationMinutes
                validCount++
            }
        }

        return if (validCount > 0) totalMinutes / validCount else 0L
    }

    /**
     * Calculates table turnover rate: settled tabs count divided by total floor inventory.
     */
    fun calculateTableTurnoverRate(
        settledTabsCount: Int,
        totalTablesCount: Int = RestaurantTableDefaults.DEFAULT_TABLES.size
    ): Double {
        if (totalTablesCount <= 0) return 0.0
        return settledTabsCount.toDouble() / totalTablesCount.toDouble()
    }

    /**
     * Calculates total diners served using guest counts where available.
     * Prevents duplicate counting:
     * - Settled tabs linked to table bookings count the booking partySize.
     * - Settled walk-in tabs count 1 guest.
     * - Completed bookings not linked to any settled tab count their partySize.
     */
    fun calculateTotalDinersServed(
        tabs: List<DiningTab>,
        bookings: List<TableBooking>
    ): Int {
        val bookingsById = bookings.associateBy { it.id }
        val settledTabs = tabs.filter { it.status == "SETTLED" }
        var count = 0
        val accountedBookingIds = mutableSetOf<String>()

        for (tab in settledTabs) {
            val booking = tab.bookingId?.let { bookingsById[it] }
            if (booking != null) {
                accountedBookingIds.add(booking.id)
                count += booking.partySize.coerceAtLeast(1)
            } else {
                count += 1
            }
        }

        for (booking in bookings) {
            if (booking.status == TableBookingStatus.COMPLETED && booking.id !in accountedBookingIds) {
                count += booking.partySize.coerceAtLeast(1)
            }
        }

        return count
    }

    /**
     * Calculates table occupancy using the table inventory and occupancy check.
     */
    fun calculateTableOccupancy(
        tables: List<RestaurantTable> = RestaurantTableDefaults.DEFAULT_TABLES,
        isTableOccupied: (tableNumber: String) -> Boolean
    ): TableOccupancyMetrics {
        val total = tables.size
        if (total == 0) return TableOccupancyMetrics(0, 0, 0.0)
        val occupied = tables.count { isTableOccupied(it.tableNumber) }
        val rate = occupied.toDouble() / total.toDouble()
        return TableOccupancyMetrics(
            occupiedTables = occupied,
            totalTables = total,
            occupancyRate = rate
        )
    }

    /**
     * Aggregates top dishes across settled dine-in tabs and eligible delivery orders.
     * Genuinely aggregates dish-level quantities from both channels.
     */
    fun aggregateTopDishes(
        settledTabs: List<DiningTab>,
        eligibleOrders: List<DeliveryOrder>,
        limit: Int = 5
    ): List<TopDishStat> {
        data class DishAccumulator(
            var name: String,
            var dineInQty: Int = 0,
            var deliveryQty: Int = 0
        )

        val map = mutableMapOf<String, DishAccumulator>()

        for (tab in settledTabs) {
            if (tab.status != "SETTLED") continue
            for (item in tab.items) {
                val acc = map.getOrPut(item.dishId) { DishAccumulator(name = item.name) }
                acc.dineInQty += item.quantity
                if (acc.name.isBlank() && item.name.isNotBlank()) {
                    acc.name = item.name
                }
            }
        }

        for (order in eligibleOrders) {
            if (order.status !in ELIGIBLE_DELIVERY_STATUSES || order.status in EXCLUDED_DELIVERY_STATUSES) continue
            for (line in order.quote.lines) {
                val acc = map.getOrPut(line.dishId) { DishAccumulator(name = line.name) }
                acc.deliveryQty += line.quantity
                if (acc.name.isBlank() && line.name.isNotBlank()) {
                    acc.name = line.name
                }
            }
        }

        return map.entries
            .map { (dishId, acc) ->
                TopDishStat(
                    dishId = dishId,
                    name = acc.name,
                    totalQuantity = acc.dineInQty + acc.deliveryQty,
                    dineInQuantity = acc.dineInQty,
                    deliveryQuantity = acc.deliveryQty
                )
            }
            .filter { it.totalQuantity > 0 }
            .sortedWith(
                compareByDescending<TopDishStat> { it.totalQuantity }
                    .thenBy { it.name }
            )
            .take(limit)
    }
}

data class InventoryItem(
    val id: String,
    val name: String,
    val category: String,
    val unit: String,
    val currentStock: Double,
    val reorderLevel: Double,
    val costPerUnitPaise: Long,
    val updatedAt: String = Instant.now().toString()
) {
    val isLowStock: Boolean
        get() = currentStock <= reorderLevel
}

data class StockMovement(
    val id: String = "MOV-${UUID.randomUUID().toString().take(8).uppercase()}",
    val itemId: String,
    val itemName: String,
    val delta: Double,
    val reason: String, // "ORDER_CONSUMPTION", "RESTOCK", "WASTAGE", "ADJUSTMENT"
    val referenceId: String = "",
    val actor: String = "System",
    val createdAt: String = Instant.now().toString()
)

data class AuditLogEntry(
    val id: String = "AUD-${UUID.randomUUID().toString().take(8).uppercase()}",
    val action: String,
    val actorEmail: String,
    val actorRole: String,
    val targetId: String,
    val details: String,
    val createdAt: String = Instant.now().toString()
)

object KotLifecycleRules {
    private val STATUS_RANK = mapOf(
        "NEW" to 0,
        "PREPARING" to 1,
        "READY" to 2,
        "SERVED" to 3
    )

    fun canTransition(currentStatus: String, nextStatus: String): Boolean {
        val currentRank = STATUS_RANK[currentStatus.trim().uppercase()] ?: 0
        val nextRank = STATUS_RANK[nextStatus.trim().uppercase()] ?: return false
        return nextRank > currentRank
    }
}
