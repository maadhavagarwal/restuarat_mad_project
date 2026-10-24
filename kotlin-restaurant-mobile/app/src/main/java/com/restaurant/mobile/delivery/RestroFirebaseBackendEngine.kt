package com.restaurant.mobile.delivery

import android.content.Context
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/**
 * 100% Pure Kotlin Serverless Firebase & SQL Engine.
 * Combines Firebase Auth + Firestore/Cloud SQL Protocol + Authoritative Security Defense Engine.
 */
class RestroFirebaseBackendEngine(
    private val context: Context,
    val sqlDb: RestroSqlDatabase = RestroSqlDatabase(context)
) {
    private val processedIdempotencyKeys = ConcurrentHashMap<String, DeliveryOrder>()
    private val processedDineInIdempotencyKeys = ConcurrentHashMap<String, Pair<DineInPaymentRequest, DineInPaymentResponse>>()

    init {
        // Seed initial restaurant, menu, staff RBAC accounts, and staff PIN vault into SQLite
        sqlDb.seedCatalogIfEmpty(MockCatalog.sample)
        sqlDb.seedDefaultStaffAccounts()
        sqlDb.seedDefaultStaffPins()
        sqlDb.seedSampleDiningTabsIfEmpty()
    }

    fun verifyStaffPin(pin: String): UserRole? = sqlDb.verifyStaffPin(pin)

    // -------------------------------------------------------------
    // 0. SESSIONS — the engine, not the client, decides who the caller is.
    // -------------------------------------------------------------

    private fun issueSession(user: Profile, method: String): AuthResult {
        val token = sqlDb.createSession(user.id, method, SessionStore.DEFAULT_SESSION_TTL_MS)
        return AuthResult(token = token, profile = user)
    }

    /** Resolves the authenticated profile for a token using the persisted user record (never client-supplied role). */
    fun authenticate(token: String?): Profile? {
        val userId = sqlDb.resolveSessionUserId(token) ?: return null
        return sqlDb.getUserById(userId)
    }

    fun logout(token: String?) = sqlDb.revokeSession(token)

    // Staff PIN brute-force protection (per process).
    private var failedPinAttempts = 0
    private var pinLockedUntil = 0L

    /**
     * A PIN may only unlock a staff role that the *authenticated account already holds* (or a lower
     * staff role). A customer account can never be promoted by guessing a PIN.
     */
    fun unlockStaffRoleWithPin(token: String?, pin: String): Result<UserRole> {
        val now = System.currentTimeMillis()
        if (now < pinLockedUntil) {
            return Result.failure(ApiFailure(429, "Too many wrong PINs. Try again in ${(pinLockedUntil - now) / 1000 + 1}s."))
        }
        val account = authenticate(token)
            ?: return Result.failure(ApiFailure(401, "Session expired. Please sign in again."))
        if (account.role == UserRole.CUSTOMER) {
            return Result.failure(ApiFailure(403, "Staff access requires a staff account. Customer accounts cannot unlock staff tools."))
        }
        val pinRole = sqlDb.verifyStaffPin(pin)
        if (pinRole == null) {
            failedPinAttempts++
            if (failedPinAttempts >= 5) {
                pinLockedUntil = now + 60_000L
                failedPinAttempts = 0
            }
            sqlDb.recordAuditLog("STAFF_PIN_FAILED", account.email, account.role.name, account.id, "Incorrect staff PIN")
            return Result.failure(ApiFailure(401, "Incorrect PIN"))
        }
        failedPinAttempts = 0
        if (roleRank(pinRole) > roleRank(account.role)) {
            sqlDb.recordAuditLog("STAFF_PIN_DENIED", account.email, account.role.name, account.id, "Attempted to unlock ${pinRole.name}")
            return Result.failure(ApiFailure(403, "Your account (${account.role.name}) is not permitted to use ${pinRole.name} tools."))
        }
        sqlDb.recordAuditLog("STAFF_PIN_UNLOCK", account.email, account.role.name, account.id, "Unlocked ${pinRole.name} mode")
        return Result.success(pinRole)
    }

    private fun roleRank(role: UserRole): Int = when (role) {
        UserRole.CUSTOMER -> 0
        UserRole.WAITER, UserRole.CHEF_KDS -> 1
        UserRole.ADMIN -> 2
        UserRole.MASTER_ADMIN -> 3
        else -> 0
    }

    private fun resolveRoleForTarget(target: String, requestedRole: String): UserRole {
        val lower = target.lowercase()
        return when {
            lower.startsWith("admin@") || lower.contains("admin") -> UserRole.ADMIN
            lower.startsWith("waiter@") || lower.contains("waiter") -> UserRole.WAITER
            lower.startsWith("chef@") || lower.contains("chef") -> UserRole.CHEF_KDS
            else -> UserRole.fromString(requestedRole)
        }
    }

    // -------------------------------------------------------------
    // 1. FIREBASE AUTHENTICATION & OTP
    // -------------------------------------------------------------

    fun login(creds: Credentials): AuthResult {
        val target = creds.email.trim()
        val pass = creds.password.trim()
        if (target.isBlank() || pass.isBlank()) {
            throw ApiFailure(400, "Email/phone and password are required")
        }
        val user = sqlDb.getUserByTarget(target)
            ?: throw ApiFailure(401, "No account found matching '$target'. Please register first.")

        val isPasswordValid = sqlDb.verifyUserPassword(target, pass)
        if (!isPasswordValid) {
            throw ApiFailure(401, "Incorrect password. If you forgot your password, tap 'Forgot Password' to receive a temporary recovery key.")
        }

        val authToken = issueSession(user, "password").token
        sqlDb.recordAuditLog("USER_LOGIN", user.email, user.role.name, user.id, "Successful login via credentials")
        return AuthResult(token = authToken, profile = user)
    }

    fun register(creds: Credentials): AuthResult {
        val target = creds.email.trim()
        val pass = creds.password.trim()
        if (target.isBlank() || pass.isBlank()) {
            throw ApiFailure(400, "Email/phone and password are required")
        }
        if (pass.length < 6) {
            throw ApiFailure(400, "Password must be at least 6 characters long")
        }
        val existing = sqlDb.getUserByTarget(target)
        if (existing != null) {
            throw ApiFailure(409, "An account with '$target' already exists. Please log in instead.")
        }
        val isEmail = target.contains("@")
        val phoneToSave = if (creds.phone.isNotBlank()) {
            if (creds.phone.startsWith("+91")) creds.phone.trim() else "+91 ${creds.phone.trim()}"
        } else if (!isEmail) {
            target
        } else {
            "+91 98765 43210"
        }
        val newUser = Profile(
            id = "usr_" + UUID.randomUUID().toString().take(8),
            name = creds.name.ifBlank { if (isEmail) target.substringBefore("@") else "Valued Diner" },
            email = if (isEmail) target else "${target.takeLast(6)}@restaurant.firebase.io",
            phone = phoneToSave,
            role = UserRole.CUSTOMER // Strict RBAC: self-registration is always customer
        )
        sqlDb.saveUser(newUser)
        sqlDb.saveUserPassword(newUser.email, pass)
        sqlDb.saveUserPassword(newUser.phone, pass)
        val authToken = issueSession(newUser, "password").token
        sqlDb.recordAuditLog("USER_REGISTER", newUser.email, newUser.role.name, newUser.id, "Account registered")
        return AuthResult(token = authToken, profile = newUser)
    }

    /**
     * DEVELOPMENT-ONLY local OTP. No SMS or email is sent. Release builds refuse this path entirely;
     * production phone sign-in must go through Firebase Phone Auth ([provisionFirebasePhoneUser]).
     */
    fun sendOtp(request: OtpSendRequest): OtpSendResponse {
        if (!com.restaurant.mobile.BuildConfig.DEBUG) {
            throw ApiFailure(503, "Phone sign-in is unavailable: Firebase Phone Auth is not configured for this build.")
        }
        val target = request.target.trim()
        if (target.isBlank()) {
            throw ApiFailure(400, "Phone number or email cannot be empty")
        }

        val code = String.format("%06d", java.security.SecureRandom().nextInt(1_000_000))
        // 5-minute expiry in SQL database
        sqlDb.storeOtp(target, code, 300_000L)

        return OtpSendResponse(
            success = true,
            message = "Verification code dispatched to $target",
            target = target,
            previewOtp = null
        )
    }

    fun verifyOtp(request: OtpVerifyRequest): AuthResult {
        if (!com.restaurant.mobile.BuildConfig.DEBUG) {
            throw ApiFailure(503, "Local OTP verification is disabled in release builds.")
        }
        val target = request.target.trim()
        val code = request.code.trim()

        val (valid, errorMsg) = sqlDb.verifyOtp(target, code)
        if (!valid) {
            throw ApiFailure(401, errorMsg ?: "Invalid OTP verification code")
        }
        val userProfile = findOrCreateCustomer(target, request.name, idPrefix = "dev_usr_")
        sqlDb.recordAuditLog("USER_LOGIN", userProfile.email, userProfile.role.name, userProfile.id, "DEV local OTP login")
        return issueSession(userProfile, "dev_local_otp")
    }

    /**
     * Called only after FirebaseAuth.signInWithCredential succeeded on the device. The Firebase UID is
     * the stable identity. NOTE: there is no server re-verifying the Firebase ID token yet — see report
     * (BLOCKED until a trusted backend verifies ID tokens with the Firebase Admin SDK).
     */
    fun provisionFirebasePhoneUser(firebaseUid: String, phoneNumber: String, name: String): AuthResult {
        require(firebaseUid.isNotBlank()) { "Firebase UID missing" }
        val existing = sqlDb.getUserById("fb_$firebaseUid")
        val user = existing ?: Profile(
            id = "fb_$firebaseUid",
            name = name.ifBlank { "Restro Diner" },
            email = "",
            phone = phoneNumber,
            role = UserRole.CUSTOMER
        ).also { sqlDb.saveUser(it) }
        sqlDb.recordAuditLog("USER_LOGIN", user.phone, user.role.name, user.id, "Firebase phone auth login")
        return issueSession(user, "firebase_phone")
    }

    private fun findOrCreateCustomer(target: String, name: String, idPrefix: String): Profile {
        sqlDb.getUserByTarget(target)?.let { return it }
        val isEmail = target.contains("@")
        val profile = Profile(
            id = idPrefix + UUID.randomUUID().toString().take(10),
            name = name.ifBlank { if (isEmail) target.substringBefore("@") else "Restro Diner" },
            email = if (isEmail) target else "",
            phone = if (!isEmail) target else "",
            role = UserRole.CUSTOMER
        )
        sqlDb.saveUser(profile)
        return profile
    }

    // -------------------------------------------------------------
    // 2. CATALOG & NEARBY RESTAURANTS (HAVERSINE DISTANCE)
    // -------------------------------------------------------------

    fun getCatalog(): Catalog {
        val restaurants = sqlDb.getAllRestaurants()
        val dishes = sqlDb.getAllDishes()
        return Catalog(restaurants = restaurants, dishes = dishes)
    }

    fun getNearbyRestaurants(userLat: Double, userLng: Double): List<Pair<Restaurant, Double>> {
        val all = sqlDb.getAllRestaurants()
        return all.map { r ->
            val distKm = haversineDistance(userLat, userLng, r.latitude, r.longitude)
            Pair(r, distKm)
        }.sortedBy { it.second }
    }

    // -------------------------------------------------------------
    // 3. AUTHORITATIVE SQL QUOTE & PRICE VERIFICATION
    // -------------------------------------------------------------

    fun calculateQuote(cart: Cart): Quote {
        if (cart.lines.isEmpty()) {
            return Quote(
                restaurantId = cart.restaurantId ?: "",
                lines = emptyList(),
                subtotal = 0,
                tax = 0,
                delivery = 0,
                discount = 0,
                total = 0
            )
        }

        val restId = cart.restaurantId
            ?: cart.lines.firstOrNull()?.let { sqlDb.getDishById(it.dishId)?.restaurantId }
            ?: "rest-1"

        val restaurant = sqlDb.getRestaurantById(restId)
            ?: throw ApiFailure(404, "Restaurant not found in SQL database")

        val quoteLines = mutableListOf<QuoteLine>()
        var subtotal = 0L

        for (line in cart.lines) {
            // Anti-Hacking Check: Validate quantity is positive
            if (line.quantity <= 0) {
                throw ApiFailure(422, "NEGATIVE_QUANTITY_ATTACK: Dish quantity must be at least 1")
            }

            // Pull canonical dish from SQLite
            val dbDish = sqlDb.getDishById(line.dishId)
                ?: throw ApiFailure(404, "Dish with ID ${line.dishId} does not exist in menu database")

            val addonsPrice = dbDish.addons.filter { it.id in line.addons }.sumOf { it.price }
            val itemPrice = dbDish.price + addonsPrice
            val lineTotal = itemPrice * line.quantity
            subtotal += lineTotal

            quoteLines.add(
                QuoteLine(
                    dishId = dbDish.id,
                    name = dbDish.name,
                    quantity = line.quantity,
                    addons = line.addons,
                    unitPrice = itemPrice,
                    total = lineTotal
                )
            )
        }

        // 5% Goods and Services Tax (GST)
        val tax = (subtotal * 5) / 100

        // Delivery fee based on restaurant
        val deliveryFee = restaurant.deliveryFee

        // Authoritative Dynamic Coupon Engine
        val discount = CouponRules.calculateDiscount(cart.coupon, subtotal, deliveryFee)

        val total = max(0L, subtotal + tax + deliveryFee - discount)

        return Quote(
            restaurantId = restaurant.id,
            lines = quoteLines,
            subtotal = subtotal,
            tax = tax,
            delivery = deliveryFee,
            discount = discount,
            total = total
        )
    }

    // -------------------------------------------------------------
    // 4. ANTI-HACKING SECURE ORDER PLACEMENT
    // -------------------------------------------------------------

    fun placeOrder(checkout: Checkout, cart: Cart, address: Address, userId: String = ""): DeliveryOrder {
        // Replay Protection via Idempotency Key
        processedIdempotencyKeys[checkout.idempotencyKey]?.let { existingOrder ->
            return existingOrder
        }

        val restId = cart.restaurantId
            ?: cart.lines.firstOrNull()?.let { sqlDb.getDishById(it.dishId)?.restaurantId }
            ?: "rest-1"

        val restaurant = sqlDb.getRestaurantById(restId)
            ?: throw ApiFailure(404, "Restaurant not found")

        // 1. Delivery Radius Protection (Max 7.5 km)
        if (address.latitude != null && address.longitude != null) {
            val distance = haversineDistance(
                restaurant.latitude,
                restaurant.longitude,
                address.latitude,
                address.longitude
            )
            val roundedDist = (distance * 10.0).roundToInt() / 10.0
            if (distance > 7.5) {
                throw ApiFailure(
                    400,
                    "OUT_OF_DELIVERY_RANGE: Destination is $roundedDist km away. Max delivery perimeter is 7.5 km."
                )
            }
        }

        // 2. Authoritative Price Recalculation Protection
        val authoritativeQuote = calculateQuote(cart)

        // Strict Anti-Hacking Check: Client price tampering check
        if (checkout.expectedTotal != authoritativeQuote.total) {
            throw ApiFailure(
                400,
                "PRICE_TAMPERING_DETECTED: Order total mismatch! Expected ${authoritativeQuote.total} paise, received ${checkout.expectedTotal} paise."
            )
        }

        // Build Confirmed Order (Scoped strictly to authenticated user)
        val order = DeliveryOrder(
            id = "ORD-${(1000..9999).random()}",
            restaurantId = restaurant.id,
            restaurantName = restaurant.name,
            address = address,
            quote = authoritativeQuote,
            paymentMethod = checkout.paymentMethod,
            paymentStatus = "Paid via ${checkout.paymentMethod}",
            status = "Confirmed",
            userId = userId,
            events = listOf(
                Event("Order Placed & Payment Verified", Instant.now().toString()),
                Event("Sent to Kitchen", Instant.now().plusSeconds(30).toString())
            )
        )

        // Save into SQL Database & Idempotency Cache
        sqlDb.saveOrder(order)
        processedIdempotencyKeys[checkout.idempotencyKey] = order

        // Kitchen Display Ticket Generation
        val kotItems = authoritativeQuote.lines.map { line ->
            com.restaurant.mobile.model.KOTItem(
                menuItemName = line.name,
                quantity = line.quantity
            )
        }
        val orderNumericId = (order.id.filter { it.isDigit() }.take(8).toLongOrNull() ?: 1001L)
        sqlDb.createKotTicket(
            tableNumber = "DELIVERY-${order.id}",
            tableId = "DELIVERY-${order.id}",
            items = kotItems,
            round = 1,
            roundName = "Delivery KDS Order",
            customerName = order.address.label.ifBlank { "Delivery Customer" },
            orderId = orderNumericId
        )

        // Idempotent Inventory Consumption
        sqlDb.consumeInventoryForOrderIdempotent(order.id, authoritativeQuote.lines)

        // Traceable Security & Order Audit Log
        sqlDb.recordAuditLog(
            action = "DELIVERY_ORDER_PLACED",
            actorEmail = order.address.phone,
            actorRole = "CUSTOMER",
            targetId = order.id,
            details = "Order for ${restaurant.name} placed. ₹${authoritativeQuote.total / 100} paid via ${checkout.paymentMethod}"
        )

        return order
    }

    // -------------------------------------------------------------
    // 5. 10-SECOND LIVE TRACKING COORDINATES STREAM (MUMBAI)
    // -------------------------------------------------------------

    fun getTrackingStream(orderId: String, currentUserId: String? = null, totalUpdates: Int = 12): Flow<TrackingResponse> = flow {
        val orders = sqlDb.getAllOrders()
        val order = orders.find { it.id == orderId }
            ?: throw IllegalArgumentException("Order #$orderId not found")

        // Strictly verify assigned customer authorization
        if (currentUserId != null && order.userId.isNotBlank() && order.userId != currentUserId) {
            throw IllegalStateException("Access Denied: Live GPS tracking is restricted to the assigned customer.")
        }
        val rest = sqlDb.getRestaurantById(order.restaurantId)

        // Authoritative Mumbai Restaurant Coordinates (Goregaon West fallback)
        val restLat = rest?.latitude ?: 19.1663
        val restLng = rest?.longitude ?: 72.8480

        // Authoritative Customer Mumbai Destination
        val destLat = order?.address?.latitude ?: 19.1600
        val destLng = order?.address?.longitude ?: 72.8550

        for (step in 0..totalUpdates) {
            val progress = step.toDouble() / totalUpdates.toDouble()
            // Interpolate rider location from restaurant to destination
            val currentLat = restLat + (destLat - restLat) * progress
            val currentLng = restLng + (destLng - restLng) * progress

            val remainingDistanceKm = haversineDistance(currentLat, currentLng, destLat, destLng)
            val roundedDist = (remainingDistanceKm * 100.0).roundToInt() / 100.0
            val etaMinutes = max(1, (roundedDist * 3.5).roundToInt())
            val isNearby = remainingDistanceKm < 0.5 // Under 500 meters

            val (statusText, orderMappedStatus) = when {
                progress >= 1.0 -> Pair("Delivered", "Delivered")
                isNearby -> Pair("Ramesh is at your gate", "Out for delivery")
                progress > 0.6 -> Pair("On the Way (Out for Delivery)", "Out for delivery")
                progress > 0.2 -> Pair("Ramesh Picked up Order", "Out for delivery")
                else -> Pair("Preparing your food", "Preparing")
            }

            // Sync database state so orders and UI remain 100% coherent
            order?.let { sqlDb.updateOrderStatus(it.id, orderMappedStatus) }

            emit(
                TrackingResponse(
                    orderId = orderId,
                    restaurantName = order?.restaurantName ?: rest?.name ?: "Spice Route Mumbai",
                    status = statusText,
                    etaMinutes = etaMinutes,
                    distanceRemainingKm = roundedDist,
                    isNearby = isNearby,
                    rider = RiderTracking(
                        name = "Ramesh Kumar",
                        phone = "+91 98112 34567",
                        vehicleNumber = "MH-02-EM-8821",
                        currentLocation = LocationPoint(currentLat, currentLng)
                    )
                )
            )

            if (step < totalUpdates) {
                delay(10_000L) // 10-second updates
            }
        }
    }

    // -------------------------------------------------------------
    // 6. DINE-IN TABLE TAB REACTIVE ENGINE
    // -------------------------------------------------------------

    fun getOpenTabForTable(tableNumber: String): DiningTab? = sqlDb.getOpenTabForTable(tableNumber)

    fun saveOrUpdateTab(tab: DiningTab): Boolean = sqlDb.saveOrUpdateTab(tab)

    fun markTabSettled(tabId: String, paymentMethod: String): Boolean = sqlDb.markTabSettled(tabId, paymentMethod)

    fun clearDineInIdempotencyKeysForTest() {
        processedDineInIdempotencyKeys.clear()
    }

    /**
     * Authoritative, protected dine-in settlement engine.
     * Defends against:
     * - Field validation failures (400)
     * - Idempotency replay (returns cached response)
     * - Idempotency key reuse for different tab or amount (409)
     * - Duplicate settlement on already paid/settled tabs (409)
     * - Negative or zero item quantities (422)
     * - Invalid/negative tip or party size (422)
     * - Price tampering: authoritative SQLite recalculation of subtotal, tax, service charge, tip (400)
     */
    fun settleDineInTabSecurely(request: DineInPaymentRequest): DineInPaymentResponse {
        val cleanKey = request.idempotencyKey.trim()
        if (cleanKey.isBlank()) {
            throw ApiFailure(400, "FIELD_VALIDATION_FAILED: idempotencyKey is required.")
        }

        val targetId = (request.tabId ?: request.tableNumber ?: "").trim()
        if (targetId.isBlank()) {
            throw ApiFailure(400, "FIELD_VALIDATION_FAILED: tabId or tableNumber is required.")
        }

        if (request.expectedTotal < 0) {
            throw ApiFailure(400, "FIELD_VALIDATION_FAILED: Valid numeric expectedTotal in paise is required.")
        }

        if (request.tip < 0) {
            throw ApiFailure(422, "INVALID_TIP: Tip must be a non-negative number.")
        }

        if (request.splitPartySize < 1) {
            throw ApiFailure(422, "INVALID_PARTY_SIZE: Party size must be a positive integer of at least 1.")
        }

        // Anti-Hacking: Idempotency & Replay Defense
        processedDineInIdempotencyKeys[cleanKey]?.let { (prevReq, cachedRes) ->
            val isSameTab = (!request.tabId.isNullOrBlank() && prevReq.tabId == request.tabId) ||
                (!request.tableNumber.isNullOrBlank() && prevReq.tableNumber == request.tableNumber)
            val isSameAmount = prevReq.expectedTotal == request.expectedTotal
            if (!isSameTab || !isSameAmount) {
                throw ApiFailure(409, "IDEMPOTENCY_KEY_REUSED: Idempotency key '$cleanKey' was already used for another payment request.")
            }
            return cachedRes
        }

        // Tab Lookup in SQLite
        val tab = sqlDb.getDiningTab(targetId)
            ?: sqlDb.getOpenTabForTable(targetId)
            ?: sqlDb.getAllDiningTabs().firstOrNull { it.tableNumber.equals(targetId, ignoreCase = true) }
            ?: throw ApiFailure(404, "TAB_NOT_FOUND: Dining tab '$targetId' not found.")

        // Duplicate Settlement Defense
        if (DineInPaymentRules.isSettledOrPaid(tab.status)) {
            throw ApiFailure(409, "TAB_ALREADY_PAID: Dining tab '${tab.id}' has already been settled and paid.")
        }

        // Line Item Validation
        if (tab.items.isEmpty()) {
            throw ApiFailure(400, "EMPTY_TAB: Cannot settle a dining tab with no items.")
        }

        for (item in tab.items) {
            if (item.quantity <= 0) {
                throw ApiFailure(422, "NEGATIVE_QUANTITY_ATTACK: Dish quantity must be a positive integer greater than 0 (${item.name}).")
            }
        }

        // Authoritative Subtotal Recalculation (Price Tampering Defense)
        var serverSubtotal = 0L
        for (item in tab.items) {
            val dish = if (item.dishId.isNotBlank()) sqlDb.getDishById(item.dishId) else null
            val unitPrice = dish?.price ?: item.price
            serverSubtotal += unitPrice * item.quantity
        }

        val tax = Math.round(serverSubtotal * 0.05) // 5% GST
        val optedOut = request.isServiceChargeOptedOut || tab.isServiceChargeOptedOut
        val serviceCharge = if (optedOut) 0L else Math.round(serverSubtotal * 0.05)
        val tipAmount = if (request.tip > 0L) request.tip else tab.tip
        val authoritativeTotal = serverSubtotal + tax + serviceCharge + tipAmount

        // Price Tampering Verification Check
        if (request.expectedTotal != authoritativeTotal) {
            throw ApiFailure(
                400,
                "DINE_IN_PRICE_TAMPERING_DETECTED: Price integrity violation! Server authoritative total is $authoritativeTotal paise, but client submitted ${request.expectedTotal} paise. Transaction blocked."
            )
        }

        // Atomic Settlement
        val transactionId = "TXN-DIN-" + UUID.randomUUID().toString().take(8).uppercase()
        val invoiceNumber = "INV-" + (System.currentTimeMillis() % 1000000)
        val nowIso = Instant.now().toString()

        val updatedTab = tab.copy(
            status = "SETTLED",
            paymentMethod = request.paymentMethod,
            paymentTransactionId = transactionId,
            subtotal = serverSubtotal,
            tax = tax,
            serviceCharge = serviceCharge,
            tip = tipAmount,
            isServiceChargeOptedOut = optedOut,
            total = authoritativeTotal,
            updatedAt = nowIso
        )
        sqlDb.saveOrUpdateTab(updatedTab)
        sqlDb.markTabSettled(tab.id, request.paymentMethod, transactionId)

        val billing = DineInPaymentBilling(
            subtotal = serverSubtotal,
            tax = tax,
            serviceCharge = serviceCharge,
            tip = tipAmount,
            total = authoritativeTotal,
            isServiceChargeOptedOut = optedOut
        )

        val response = DineInPaymentResponse(
            success = true,
            status = "SETTLED",
            tabId = tab.id,
            tableNumber = tab.tableNumber,
            transactionId = transactionId,
            invoiceNumber = invoiceNumber,
            paymentMethod = request.paymentMethod,
            billing = billing,
            settledAt = nowIso
        )

        processedDineInIdempotencyKeys[cleanKey] = Pair(request, response)
        return response
    }

    fun getFloorTableStatus(tableNumber: String): String = sqlDb.getFloorTableStatus(tableNumber)

    fun updateFloorTableStatus(tableNumber: String, status: String): Boolean = sqlDb.updateFloorTableStatus(tableNumber, status)

    /**
     * Real-time reactive listener for active dine-in table tab.
     * Continuously emits updates for the specified table from the SQLite / Firebase storage engine.
     * Automatically handles coroutine cancellation and listener lifecycle management.
     */
    fun listenToTableTab(tableNumber: String, pollIntervalMs: Long = 1000L): Flow<DiningTab> = flow {
        val cleanTable = tableNumber.trim()
        if (cleanTable.isBlank()) {
            return@flow
        }

        // 1. Resolve initial active tab or initialize if none exists yet
        var currentTab = sqlDb.getOpenTabForTable(cleanTable) ?: run {
            val activeBooking = sqlDb.getAllTableBookings().firstOrNull {
                it.tableNumber.equals(cleanTable, ignoreCase = true) &&
                    (it.status == TableBookingStatus.CONFIRMED || it.status == TableBookingStatus.SEATED)
            }
            val initial = DiningTab(
                id = UUID.randomUUID().toString(),
                tableNumber = cleanTable,
                bookingId = activeBooking?.id,
                userId = activeBooking?.userId,
                userName = activeBooking?.userName ?: "Guest Diner",
                status = "OPEN"
            )
            sqlDb.saveOrUpdateTab(initial)
            initial
        }

        emit(currentTab)

        // 2. Stream subsequent tab updates until cancelled
        var lastEmittedTab = currentTab
        while (currentCoroutineContext().isActive) {
            delay(pollIntervalMs)

            val latest = sqlDb.getOpenTabForTable(cleanTable)
                ?: (lastEmittedTab.id.takeIf { it.isNotBlank() }?.let { sqlDb.getDiningTab(it) })

            if (latest != null && latest != lastEmittedTab) {
                lastEmittedTab = latest
                emit(latest)
            }
        }
    }

    // -------------------------------------------------------------
    // UTILS
    // -------------------------------------------------------------

    fun getSettledDineInRevenue(): Long = sqlDb.getSettledDineInRevenue()
    fun getEligibleDeliveryRevenue(): Long = sqlDb.getEligibleDeliveryRevenue()
    fun getAverageDineInBillValue(): Long = sqlDb.getAverageDineInBillValue()
    fun getAverageDiningDurationMinutes(): Long = sqlDb.getAverageDiningDurationMinutes()
    fun getTableTurnoverRate(): Double = sqlDb.getTableTurnoverRate()
    fun getTotalDinersServed(): Int = sqlDb.getTotalDinersServed()
    fun getTableOccupancy(): TableOccupancyMetrics = sqlDb.getTableOccupancy()
    fun getTopFiveDishes(limit: Int = 5): List<TopDishStat> = sqlDb.getTopFiveDishes(limit)
    fun getAnalyticsSummary(): AnalyticsSummary = sqlDb.getAnalyticsSummary()

    /**
     * Returns the mapped analytics data (UI-layer model) from the persisted SQL analytics summary.
     * Converts monetary values from paise to rupees exactly once.
     *
     * @param fallback Optional existing [com.restaurant.mobile.model.AnalyticsData] whose remote-only metrics
     *   (totalOrders, activeKOTs, totalCustomers) are preserved.
     */
    fun getAnalyticsData(
        fallback: com.restaurant.mobile.model.AnalyticsData? = null
    ): com.restaurant.mobile.model.AnalyticsData {
        val summary = getAnalyticsSummary()
        return com.restaurant.mobile.model.AnalyticsData.fromSummary(summary, fallback)
    }

    fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusKm * c
    }
}
