package com.restaurant.mobile.delivery

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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
    val restaurantId: String = "",
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

    fun getTablesForRestaurant(restaurantId: String): List<RestaurantTable> {
        return DEFAULT_TABLES.map { table ->
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

data class BillingSummary(
    val subtotal: Double,
    val tax: Double,
    val serviceCharge: Double,
    val total: Double
) {
    val subtotalPaise: Long get() = CurrencyConversionRules.rupeesToPaise(subtotal)
    val taxPaise: Long get() = CurrencyConversionRules.rupeesToPaise(tax)
    val serviceChargePaise: Long get() = CurrencyConversionRules.rupeesToPaise(serviceCharge)
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
    val total: Long = 0L,
    val status: String = if (tableId.isNotBlank()) DiningTabStatus.ACTIVE else DiningTabStatus.OPEN,
    val paymentMethod: String? = null,
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val restaurantId: String = ""
) {
    val subtotalRupees: Double get() = CurrencyConversionRules.paiseToRupees(subtotal)
    val taxRupees: Double get() = CurrencyConversionRules.paiseToRupees(tax)
    val serviceChargeRupees: Double get() = CurrencyConversionRules.paiseToRupees(serviceCharge)
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
        status: String = DiningTabStatus.ACTIVE,
        items: List<TableBillItem> = emptyList(),
        subtotal: Double,
        tax: Double = 0.0,
        serviceCharge: Double = 0.0,
        total: Double = 0.0,
        createdAt: String = Instant.now().toString(),
        updatedAt: String = Instant.now().toString(),
        restaurantId: String = ""
    ) : this(
        id = id,
        tableId = tableId.ifBlank { tableNumber },
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
        total = CurrencyConversionRules.rupeesToPaise(total),
        status = status,
        paymentMethod = null,
        createdAt = createdAt,
        updatedAt = updatedAt,
        restaurantId = restaurantId
    )

    companion object {
        fun generateId(): String = "TAB-${UUID.randomUUID().toString().take(8).uppercase()}"

        fun calculateBilling(
            items: List<TableBillItem>,
            existingServiceCharge: Double = 0.0
        ): BillingSummary {
            val subtotal = items.sumOf { it.itemTotal }
            val tax = items.sumOf { (it.itemTotal) * (it.taxRate / 100.0) }
            val serviceCharge = existingServiceCharge
            val total = subtotal + tax + serviceCharge
            return BillingSummary(
                subtotal = subtotal,
                tax = tax,
                serviceCharge = serviceCharge,
                total = total
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
            existingServiceCharge = existingTab?.serviceChargeRupees ?: 0.0
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
                total = billing.totalPaise,
                updatedAt = Instant.now().toString(),
                restaurantId = targetRestId
            )
        } else {
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
                total = billing.totalPaise,
                createdAt = Instant.now().toString(),
                updatedAt = Instant.now().toString(),
                restaurantId = targetRestId
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
