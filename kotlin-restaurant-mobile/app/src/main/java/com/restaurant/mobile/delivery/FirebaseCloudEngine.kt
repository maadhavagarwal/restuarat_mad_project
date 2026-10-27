package com.restaurant.mobile.delivery

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.restaurant.mobile.model.KOT
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Instant

/**
 * Enterprise Production Firebase Cloud Engine
 * Connects the mobile app directly to Cloud Firestore & Firebase Auth.
 * Features:
 * - Real-time Firestore Snapshot Listeners for multi-device sync
 * - Built-in offline persistence cache
 * - Seamless fallback to RestroSqlDatabase when credentials/network are offline
 */
class FirebaseCloudEngine(
    private val context: Context,
    private val localSqlDb: RestroSqlDatabase
) {
    private val gson = Gson()
    private val tag = "FirebaseCloudEngine"

    val subscriptionManager = FirestoreSubscriptionManager()

    var isFirebaseInitialized: Boolean = false
        private set

    private var firestore: FirebaseFirestore? = null
    private var auth: FirebaseAuth? = null

    private var ordersListenerReg: ListenerRegistration? = null
    private var tabListenerReg: ListenerRegistration? = null
    private var diningTabsListenerReg: ListenerRegistration? = null
    private var bookingsListenerReg: ListenerRegistration? = null
    private var userListenerReg: ListenerRegistration? = null
    private var addressesListenerReg: ListenerRegistration? = null
    private var catalogListenerReg: ListenerRegistration? = null
    private var restaurantsListenerReg: ListenerRegistration? = null
    private var dishesListenerReg: ListenerRegistration? = null
    private var settingsListenerReg: ListenerRegistration? = null
    private var kotTicketsListenerReg: ListenerRegistration? = null
    private var riderLocationListenerReg: ListenerRegistration? = null
    private var ridersListenerReg: ListenerRegistration? = null
    private var riderRidesListenerReg: ListenerRegistration? = null
    private var riderPayoutsListenerReg: ListenerRegistration? = null
    private var riderRejectedOffersListenerReg: ListenerRegistration? = null

    var storage: com.google.firebase.storage.FirebaseStorage? = null
        private set

    val storageEngine: FirebaseStorageEngine by lazy {
        FirebaseStorageEngine(context, storage)
    }

    init {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            auth = FirebaseAuth.getInstance()
            firestore = FirebaseFirestore.getInstance().apply {
                firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .build()
            }
            try {
                storage = com.google.firebase.storage.FirebaseStorage.getInstance()
            } catch (se: Exception) {
                Log.w(tag, "Firebase Storage initialization deferred: ${se.message}")
            }
            isFirebaseInitialized = true
            Log.i(tag, "Firebase Cloud Engine successfully initialized with Firestore offline persistence.")
        } catch (e: Exception) {
            Log.w(tag, "Firebase initialization deferred / local mode active: ${e.message}")
            isFirebaseInitialized = false
        }
    }

    // --- ORDERS REAL-TIME CLOUD SYNC ---

    fun saveOrderToCloud(
        order: DeliveryOrder,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        localSqlDb.saveOrder(order)

        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            onError(IllegalStateException("Cloud Firestore is not initialized or offline"))
            return
        }

        try {
            val orderMap = mapOf(
                "id" to order.id,
                "userId" to order.userId.ifBlank { "guest" },
                "restaurantId" to order.restaurantId,
                "restaurantName" to order.restaurantName,
                "total" to (order.quote.total / 100),
                "deliveryAddress" to order.address.line,
                "address" to gson.toJson(order.address),
                "quote" to gson.toJson(order.quote),
                "paymentMethod" to order.paymentMethod,
                "paymentStatus" to order.paymentStatus,
                "status" to order.status,
                "riderId" to order.riderId,
                "riderName" to order.riderName,
                "riderPhone" to order.riderPhone,
                "riderVehicle" to order.riderVehicle,
                "riderRating" to order.riderRating,
                "riderLatitude" to order.riderLatitude,
                "riderLongitude" to order.riderLongitude,
                "events" to gson.toJson(order.events),
                "idempotencyKey" to (order.idempotencyKey ?: ""),
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("orders").document(order.id)
                .set(orderMap)
                .addOnSuccessListener {
                    Log.d(tag, "Order ${order.id} synced to Cloud Firestore.")
                    onSuccess()
                }
                .addOnFailureListener { err ->
                    Log.w(tag, "Order saved locally; Firestore sync pending: ${err.message}")
                    onError(err)
                }
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun updateOrderStatusInCloud(
        orderId: String,
        newStatus: String,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        localSqlDb.updateOrderStatus(orderId, newStatus)
        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            onError(IllegalStateException("Cloud Firestore is not initialized or offline"))
            return
        }
        db.collection("orders").document(orderId)
            .update(
                mapOf(
                    "status" to newStatus,
                    "updatedAt" to System.currentTimeMillis()
                )
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun observeOrdersRealTime(
        userId: String = "",
        restaurantId: String = "",
        isStaffOrRider: Boolean = false,
        onOrdersUpdated: (List<DeliveryOrder>) -> Unit
    ) {
        val channelId = when {
            isStaffOrRider && restaurantId.isNotBlank() -> "orders:restaurant:$restaurantId"
            isStaffOrRider -> "orders:staff"
            userId.isNotBlank() && userId != "guest" -> "orders:user:$userId"
            else -> "orders:all"
        }
        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            val fallback = if (userId.isNotBlank() && userId != "guest" && !isStaffOrRider) {
                localSqlDb.getOrdersForUser(userId).filterNot { it.id.startsWith("ORD-SEED") }
            } else {
                localSqlDb.getAllOrders()
            }
            onOrdersUpdated(fallback)
            return
        }

        try {
            val query: Query = when {
                isStaffOrRider && restaurantId.isNotBlank() ->
                    db.collection("orders").whereEqualTo("restaurantId", restaurantId)
                isStaffOrRider ->
                    db.collection("orders")
                userId.isNotBlank() && userId != "guest" ->
                    db.collection("orders").whereEqualTo("userId", userId)
                else ->
                    db.collection("orders")
            }

            ordersListenerReg = subscriptionManager.attachQueryListener(
                channelId = channelId,
                scope = FirestoreSubscriptionManager.ChannelScope.USER_SCOPED,
                query = query,
                onSnapshot = { snapshot, _ ->
                    subscriptionManager.processDocumentChanges(
                        channelId = channelId,
                        snapshot = snapshot,
                        mapper = { id, data -> FirestoreModelMapper.mapToDeliveryOrder(id, data) },
                        onAdded = { order -> localSqlDb.upsertOrderFromCloud(order) },
                        onModified = { order -> localSqlDb.upsertOrderFromCloud(order) },
                        onRemoved = { /* order retention in history */ }
                    )

                    val relevantOrders = if (userId.isNotBlank() && userId != "guest" && !isStaffOrRider) {
                        localSqlDb.getOrdersForUser(userId).filterNot { it.id.startsWith("ORD-SEED") }
                    } else {
                        localSqlDb.getAllOrders()
                    }
                    onOrdersUpdated(relevantOrders)
                },
                onError = { err ->
                    Log.w(tag, "Orders subscription error: ${err.message}")
                    val fallback = if (userId.isNotBlank() && userId != "guest" && !isStaffOrRider) {
                        localSqlDb.getOrdersForUser(userId).filterNot { it.id.startsWith("ORD-SEED") }
                    } else {
                        localSqlDb.getAllOrders()
                    }
                    onOrdersUpdated(fallback)
                }
            )
        } catch (_: Exception) {
            val fallback = if (userId.isNotBlank() && userId != "guest" && !isStaffOrRider) {
                localSqlDb.getOrdersForUser(userId).filterNot { it.id.startsWith("ORD-SEED") }
            } else {
                localSqlDb.getAllOrders()
            }
            onOrdersUpdated(fallback)
        }
    }

    // --- KITCHEN ORDER TICKETS (KOT) REAL-TIME CLOUD SYNC ---

    fun saveKotToCloud(
        kot: KOT,
        restaurantId: String = "rest-1",
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            onError(IllegalStateException("Cloud Firestore is not initialized or offline"))
            return
        }
        val docId = "KOT-${kot.id}"
        val kotMap = mapOf(
            "id" to kot.id,
            "orderId" to kot.orderId,
            "restaurantId" to restaurantId,
            "tableNumber" to kot.tableNumber,
            "tableId" to kot.tableId,
            "round" to kot.round,
            "roundName" to kot.roundName,
            "status" to kot.status,
            "items" to kot.items.map { mapOf("id" to it.id, "menuItemName" to it.menuItemName, "quantity" to it.quantity, "priceAtTimeOfOrder" to it.priceAtTimeOfOrder) },
            "customerName" to (kot.customerName ?: ""),
            "createdAt" to kot.createdAt,
            "updatedAt" to Instant.now().toString()
        )
        db.collection("kot_tickets").document(docId).set(kotMap)
            .addOnSuccessListener {
                Log.d(tag, "KOT ticket #$docId synced to Cloud Firestore.")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.w(tag, "KOT ticket saved locally; Firestore sync pending: ${e.message}")
                onError(e)
            }
    }

    fun updateKotStatusInCloud(
        kotId: Long,
        newStatus: String,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            onError(IllegalStateException("Cloud Firestore is not initialized or offline"))
            return
        }
        val docId = "KOT-$kotId"
        db.collection("kot_tickets").document(docId)
            .update(
                mapOf(
                    "status" to newStatus,
                    "updatedAt" to Instant.now().toString()
                )
            )
            .addOnSuccessListener {
                Log.d(tag, "KOT #$kotId status updated to '$newStatus' in Cloud Firestore.")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.w(tag, "Failed to update KOT status in Cloud Firestore: ${e.message}")
                onError(e)
            }
    }

    fun deleteKotFromCloud(
        kotId: Long,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            onError(IllegalStateException("Cloud Firestore is not initialized or offline"))
            return
        }
        val docId = "KOT-$kotId"
        db.collection("kot_tickets").document(docId).delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun observeKotTicketsRealTime(
        restaurantId: String = "",
        onKotsUpdated: (List<KOT>) -> Unit
    ) {
        val channelId = if (restaurantId.isNotBlank()) "kot_tickets:$restaurantId" else "kot_tickets:all"
        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            onKotsUpdated(localSqlDb.getAllKotTickets())
            return
        }

        try {
            val query: Query = if (restaurantId.isNotBlank()) {
                db.collection("kot_tickets").whereEqualTo("restaurantId", restaurantId)
            } else {
                db.collection("kot_tickets")
            }

            kotTicketsListenerReg = subscriptionManager.attachQueryListener(
                channelId = channelId,
                scope = FirestoreSubscriptionManager.ChannelScope.USER_SCOPED,
                query = query,
                onSnapshot = { snapshot, _ ->
                    subscriptionManager.processDocumentChanges(
                        channelId = channelId,
                        snapshot = snapshot,
                        mapper = { id, data -> FirestoreModelMapper.mapToKot(id, data) },
                        onAdded = { kot -> localSqlDb.upsertKotFromCloud(kot) },
                        onModified = { kot -> localSqlDb.upsertKotFromCloud(kot) },
                        onRemoved = { docId ->
                            val numId = docId.removePrefix("KOT-").filter { it.isDigit() }.toLongOrNull()
                            if (numId != null) localSqlDb.deleteKotFromCloud(numId)
                        }
                    )
                    onKotsUpdated(localSqlDb.getAllKotTickets())
                },
                onError = { err ->
                    Log.w(tag, "KOT tickets subscription error: ${err.message}")
                    onKotsUpdated(localSqlDb.getAllKotTickets())
                }
            )
        } catch (e: Exception) {
            Log.w(tag, "KOT tickets setup error: ${e.message}")
            onKotsUpdated(localSqlDb.getAllKotTickets())
        }
    }

    // --- TABLE BOOKINGS REAL-TIME CLOUD SYNC ---

    fun saveBookingToCloud(
        booking: TableBooking,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        localSqlDb.saveTableBooking(booking)

        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            onError(IllegalStateException("Cloud Firestore is not initialized or offline"))
            return
        }

        try {
            val bookingMap = mapOf(
                "id" to booking.id,
                "restaurantId" to booking.restaurantId,
                "restaurantName" to booking.restaurantName,
                "tableId" to booking.tableId,
                "tableNumber" to booking.tableNumber,
                "userId" to booking.userId,
                "userName" to booking.userName,
                "userPhone" to booking.userPhone,
                "partySize" to booking.partySize,
                "bookingDate" to booking.bookingDate,
                "timeSlot" to booking.timeSlot,
                "seatingArea" to booking.seatingArea,
                "specialRequests" to booking.specialRequests,
                "status" to booking.status,
                "createdAt" to booking.createdAt,
                "updatedAt" to (if (booking.updatedAt.isNotBlank()) booking.updatedAt else Instant.now().toString())
            )
            db.collection("table_bookings").document(booking.id)
                .set(bookingMap)
                .addOnSuccessListener {
                    Log.d(tag, "Booking ${booking.id} synced to Cloud Firestore.")
                    onSuccess()
                }
                .addOnFailureListener { err ->
                    Log.w(tag, "Booking saved locally; Firestore sync pending: ${err.message}")
                    onError(err)
                }
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun observeTableBookingsRealTime(
        userId: String = "",
        restaurantId: String = "",
        isStaff: Boolean = false,
        onBookingsUpdated: (List<TableBooking>) -> Unit
    ) {
        val channelId = if (isStaff && restaurantId.isNotBlank()) {
            "bookings:rest:$restaurantId"
        } else if (isStaff) {
            "bookings:staff"
        } else if (userId.isNotBlank() && userId != "guest") {
            "bookings:user:$userId"
        } else {
            "bookings:all"
        }

        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            val fallback = if (isStaff) {
                if (restaurantId.isNotBlank()) localSqlDb.getAllTableBookings().filter { it.restaurantId == restaurantId } else localSqlDb.getAllTableBookings()
            } else {
                localSqlDb.getTableBookingsForUser(userId)
            }
            onBookingsUpdated(fallback)
            return
        }

        try {
            val query: Query = when {
                isStaff && restaurantId.isNotBlank() ->
                    db.collection("table_bookings").whereEqualTo("restaurantId", restaurantId)
                isStaff ->
                    db.collection("table_bookings")
                userId.isNotBlank() && userId != "guest" ->
                    db.collection("table_bookings").whereEqualTo("userId", userId)
                else ->
                    db.collection("table_bookings")
            }

            bookingsListenerReg = subscriptionManager.attachQueryListener(
                channelId = channelId,
                scope = FirestoreSubscriptionManager.ChannelScope.USER_SCOPED,
                query = query,
                onSnapshot = { snapshot, _ ->
                    subscriptionManager.processDocumentChanges(
                        channelId = channelId,
                        snapshot = snapshot,
                        mapper = { id, data -> FirestoreModelMapper.mapToTableBooking(id, data) },
                        onAdded = { booking -> localSqlDb.upsertTableBookingFromCloud(booking) },
                        onModified = { booking -> localSqlDb.upsertTableBookingFromCloud(booking) },
                        onRemoved = { docId -> localSqlDb.deleteTableBookingFromCloud(docId) }
                    )

                    val updated = if (isStaff) {
                        if (restaurantId.isNotBlank()) localSqlDb.getAllTableBookings().filter { it.restaurantId == restaurantId } else localSqlDb.getAllTableBookings()
                    } else {
                        localSqlDb.getTableBookingsForUser(userId)
                    }
                    onBookingsUpdated(updated)
                },
                onError = { err ->
                    Log.w(tag, "Table bookings subscription error: ${err.message}")
                    val fallback = if (isStaff) {
                        if (restaurantId.isNotBlank()) localSqlDb.getAllTableBookings().filter { it.restaurantId == restaurantId } else localSqlDb.getAllTableBookings()
                    } else {
                        localSqlDb.getTableBookingsForUser(userId)
                    }
                    onBookingsUpdated(fallback)
                }
            )
        } catch (_: Exception) {
            val fallback = if (isStaff) {
                if (restaurantId.isNotBlank()) localSqlDb.getAllTableBookings().filter { it.restaurantId == restaurantId } else localSqlDb.getAllTableBookings()
            } else {
                localSqlDb.getTableBookingsForUser(userId)
            }
            onBookingsUpdated(fallback)
        }
    }

    // --- DINING TABS REAL-TIME CLOUD SYNC ---

    fun saveDiningTabToCloud(
        tab: DiningTab,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        localSqlDb.saveOrUpdateTab(tab)

        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            onError(IllegalStateException("Cloud Firestore is not initialized or offline"))
            return
        }

        try {
            val effectiveId = if (tab.id.isNotBlank()) tab.id else java.util.UUID.randomUUID().toString()
            val updatedAt = if (tab.updatedAt.isNotBlank()) tab.updatedAt else Instant.now().toString()
            val tabMap = mapOf(
                "id" to effectiveId,
                "tableId" to tab.tableId.ifBlank { tab.tableNumber.trim() },
                "tableNumber" to tab.tableNumber.trim(),
                "bookingId" to (tab.bookingId ?: ""),
                "userId" to (tab.userId ?: tab.customerId ?: "guest"),
                "userName" to (tab.userName.ifBlank { tab.customerName ?: "Guest Diner" }),
                "customerId" to (tab.customerId ?: tab.userId ?: ""),
                "customerName" to (tab.customerName ?: tab.userName),
                "customerPhone" to (tab.customerPhone ?: ""),
                "itemsJson" to gson.toJson(tab.items),
                "subtotal" to tab.subtotal,
                "tax" to tab.tax,
                "serviceCharge" to tab.serviceCharge,
                "tip" to tab.tip,
                "isServiceChargeOptedOut" to tab.isServiceChargeOptedOut,
                "total" to tab.total,
                "status" to tab.status,
                "paymentMethod" to (tab.paymentMethod ?: ""),
                "paymentTransactionId" to tab.paymentTransactionId,
                "guestCount" to tab.guestCount,
                "seatedAt" to tab.seatedAt,
                "billedAt" to tab.billedAt,
                "restaurantId" to tab.restaurantId,
                "createdAt" to tab.createdAt.ifBlank { updatedAt },
                "updatedAt" to updatedAt
            )
            db.collection("dining_tabs").document(effectiveId)
                .set(tabMap)
                .addOnSuccessListener {
                    Log.d(tag, "Dining tab $effectiveId synced to Cloud Firestore.")
                    onSuccess()
                }
                .addOnFailureListener { err ->
                    Log.w(tag, "Dining tab saved locally; Firestore sync pending: ${err.message}")
                    onError(err)
                }
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun observeDiningTabsRealTime(
        restaurantId: String = "",
        onTabsUpdated: (List<DiningTab>) -> Unit
    ) {
        val channelId = if (restaurantId.isNotBlank()) "dining_tabs:rest:$restaurantId" else "dining_tabs:all"
        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            val fallback = localSqlDb.getAllDiningTabs().filter { restaurantId.isBlank() || it.restaurantId == restaurantId }
            onTabsUpdated(fallback)
            return
        }

        try {
            val query: Query = if (restaurantId.isNotBlank()) {
                db.collection("dining_tabs").whereEqualTo("restaurantId", restaurantId)
            } else {
                db.collection("dining_tabs")
            }

            diningTabsListenerReg = subscriptionManager.attachQueryListener(
                channelId = channelId,
                scope = FirestoreSubscriptionManager.ChannelScope.USER_SCOPED,
                query = query,
                onSnapshot = { snapshot, _ ->
                    subscriptionManager.processDocumentChanges(
                        channelId = channelId,
                        snapshot = snapshot,
                        mapper = { id, data -> FirestoreModelMapper.mapToDiningTab(id, data) },
                        onAdded = { tab -> localSqlDb.upsertDiningTabFromCloud(tab) },
                        onModified = { tab -> localSqlDb.upsertDiningTabFromCloud(tab) },
                        onRemoved = { docId -> localSqlDb.deleteDiningTabFromCloud(docId) }
                    )

                    val updated = localSqlDb.getAllDiningTabs().filter { restaurantId.isBlank() || it.restaurantId == restaurantId }
                    onTabsUpdated(updated)
                },
                onError = { err ->
                    Log.w(tag, "Dining tabs subscription error: ${err.message}")
                    onTabsUpdated(localSqlDb.getAllDiningTabs().filter { restaurantId.isBlank() || it.restaurantId == restaurantId })
                }
            )
        } catch (_: Exception) {
            onTabsUpdated(localSqlDb.getAllDiningTabs().filter { restaurantId.isBlank() || it.restaurantId == restaurantId })
        }
    }

    fun observeLiveDiningTabRealTime(
        tableNumber: String,
        onTabUpdated: (DiningTab?) -> Unit
    ) {
        val cleanTable = tableNumber.trim()
        if (cleanTable.isBlank()) {
            onTabUpdated(null)
            return
        }

        val channelId = "dining_tab:table:$cleanTable"
        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            onTabUpdated(localSqlDb.getOpenTabForTable(cleanTable))
            return
        }

        try {
            val query = db.collection("dining_tabs")
                .whereEqualTo("tableNumber", cleanTable)

            tabListenerReg = subscriptionManager.attachQueryListener(
                channelId = channelId,
                scope = FirestoreSubscriptionManager.ChannelScope.USER_SCOPED,
                query = query,
                onSnapshot = { snapshot, _ ->
                    subscriptionManager.processDocumentChanges(
                        channelId = channelId,
                        snapshot = snapshot,
                        mapper = { id, data -> FirestoreModelMapper.mapToDiningTab(id, data) },
                        onAdded = { tab -> localSqlDb.upsertDiningTabFromCloud(tab) },
                        onModified = { tab -> localSqlDb.upsertDiningTabFromCloud(tab) },
                        onRemoved = { docId -> localSqlDb.deleteDiningTabFromCloud(docId) }
                    )

                    val openTab = localSqlDb.getOpenTabForTable(cleanTable)
                    onTabUpdated(openTab)
                },
                onError = { err ->
                    Log.w(tag, "Live dining tab subscription error: ${err.message}")
                    onTabUpdated(localSqlDb.getOpenTabForTable(cleanTable))
                }
            )
        } catch (_: Exception) {
            onTabUpdated(localSqlDb.getOpenTabForTable(cleanTable))
        }
    }

    fun stopObservingLiveDiningTab(tableNumber: String) {
        val cleanTable = tableNumber.trim()
        if (cleanTable.isNotBlank()) {
            subscriptionManager.cancelSubscription("dining_tab:table:$cleanTable")
            if (tabListenerReg != null) {
                tabListenerReg = null
            }
        }
    }

    // --- USERS REAL-TIME CLOUD SYNC ---

    fun saveUserToCloud(
        profile: Profile,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        localSqlDb.saveUser(profile)
        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            onSuccess()
            return
        }
        try {
            val userMap = mapOf(
                "id" to profile.id,
                "name" to profile.name,
                "email" to profile.email,
                "phone" to profile.phone,
                "role" to profile.role.name,
                "isDarkMode" to profile.isDarkMode,
                "reducedGlass" to profile.reducedGlass,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("users").document(profile.id)
                .set(userMap)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onError(it) }
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun observeUserRealTime(
        userId: String,
        onUserUpdated: (Profile?) -> Unit
    ) {
        userListenerReg?.remove()
        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            onUserUpdated(localSqlDb.getUserById(userId))
            return
        }
        try {
            userListenerReg = db.collection("users").document(userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) {
                        onUserUpdated(localSqlDb.getUserById(userId))
                        return@addSnapshotListener
                    }
                    try {
                        val name = snapshot.getString("name") ?: ""
                        val email = snapshot.getString("email") ?: ""
                        val phone = snapshot.getString("phone") ?: ""
                        val roleStr = snapshot.getString("role") ?: UserRole.CUSTOMER.name
                        val isDark = snapshot.getBoolean("isDarkMode") ?: true
                        val reduced = snapshot.getBoolean("reducedGlass") ?: false
                        val role = try { UserRole.valueOf(roleStr) } catch (_: Exception) { UserRole.CUSTOMER }
                        val p = Profile(
                            id = userId,
                            name = name,
                            email = email,
                            phone = phone,
                            role = role,
                            reducedGlass = reduced,
                            isDarkMode = isDark
                        )
                        localSqlDb.saveUser(p)
                        onUserUpdated(p)
                    } catch (_: Exception) {
                        onUserUpdated(localSqlDb.getUserById(userId))
                    }
                }
        } catch (_: Exception) {
            onUserUpdated(localSqlDb.getUserById(userId))
        }
    }

    // --- DELIVERY ADDRESSES REAL-TIME CLOUD SYNC ---

    fun saveAddressToCloud(
        userId: String,
        address: Address,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        localSqlDb.saveAddress(address)
        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            onSuccess()
            return
        }
        try {
            val addrMap = mapOf(
                "id" to address.id,
                "userId" to userId,
                "label" to address.label,
                "line" to address.line,
                "city" to address.city,
                "postcode" to address.postcode,
                "phone" to address.phone,
                "latitude" to (address.latitude ?: 0.0),
                "longitude" to (address.longitude ?: 0.0),
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("users").document(userId)
                .collection("addresses").document(address.id)
                .set(addrMap)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onError(it) }
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun deleteAddressFromCloud(
        userId: String,
        addressId: String,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        localSqlDb.deleteAddress(addressId)
        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            onSuccess()
            return
        }
        try {
            db.collection("users").document(userId)
                .collection("addresses").document(addressId)
                .delete()
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onError(it) }
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun observeAddressesRealTime(
        userId: String,
        onAddressesUpdated: (List<Address>) -> Unit
    ) {
        val channelId = "addresses:$userId"
        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            onAddressesUpdated(localSqlDb.getAllAddresses())
            return
        }
        try {
            addressesListenerReg = subscriptionManager.attachQueryListener(
                channelId = channelId,
                scope = FirestoreSubscriptionManager.ChannelScope.USER_SCOPED,
                query = db.collection("users").document(userId).collection("addresses"),
                onSnapshot = { snapshot, _ ->
                    val list = mutableListOf<Address>()
                    for (doc in snapshot.documents) {
                        val parsed = FirestoreModelMapper.mapToAddress(doc.id, doc.data ?: emptyMap())
                        if (parsed != null) {
                            list.add(parsed)
                            localSqlDb.saveAddress(parsed)
                        }
                    }
                    if (list.isNotEmpty()) {
                        onAddressesUpdated(list)
                    } else {
                        onAddressesUpdated(localSqlDb.getAllAddresses())
                    }
                },
                onError = { err ->
                    Log.w(tag, "Addresses subscription error: ${err.message}")
                    onAddressesUpdated(localSqlDb.getAllAddresses())
                }
            )
        } catch (_: Exception) {
            onAddressesUpdated(localSqlDb.getAllAddresses())
        }
    }

    // --- REVIEWS REAL-TIME CLOUD SYNC ---

    fun saveReviewToCloud(
        review: Review,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        localSqlDb.saveReview(review)
        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            onSuccess()
            return
        }
        try {
            val revMap = mapOf(
                "orderId" to review.orderId,
                "restaurantId" to review.restaurantId,
                "author" to review.author,
                "rating" to review.rating,
                "text" to review.text,
                "timestamp" to System.currentTimeMillis()
            )
            db.collection("reviews").document(review.orderId)
                .set(revMap)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onError(it) }
        } catch (e: Exception) {
            onError(e)
        }
    }

    // --- RIDER REAL-TIME GPS CLOUD SYNC ---

    fun saveRiderLocationToCloud(
        orderId: String,
        latitude: Double,
        longitude: Double,
        etaMinutes: Int,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            onSuccess()
            return
        }
        try {
            val locMap = mapOf(
                "orderId" to orderId,
                "latitude" to latitude,
                "longitude" to longitude,
                "etaMinutes" to etaMinutes,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("rider_locations").document(orderId)
                .set(locMap)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onError(it) }
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun observeRiderLocationRealTime(
        orderId: String,
        onLocationUpdated: (Double, Double, Int) -> Unit
    ): ListenerRegistration? {
        stopObservingRiderLocation(orderId)
        val db = firestore
        if (db == null || !isFirebaseInitialized) return null
        val channelId = "rider_locations:$orderId"
        return try {
            val docRef = db.collection("rider_locations").document(orderId)
            val reg = subscriptionManager.attachDocumentListener(
                channelId = channelId,
                scope = FirestoreSubscriptionManager.ChannelScope.USER_SCOPED,
                docRef = docRef,
                onSnapshot = { snapshot, _ ->
                    if (!snapshot.exists()) return@attachDocumentListener
                    val lat = snapshot.getDouble("latitude") ?: return@attachDocumentListener
                    val lng = snapshot.getDouble("longitude") ?: return@attachDocumentListener
                    val eta = snapshot.getLong("etaMinutes")?.toInt() ?: 15
                    onLocationUpdated(lat, lng, eta)
                },
                onError = { e ->
                    Log.w(tag, "Rider location sync error on $channelId: ${e.message}")
                }
            )
            riderLocationListenerReg = reg
            reg
        } catch (e: Exception) {
            Log.w(tag, "Failed to observe rider location $channelId: ${e.message}")
            null
        }
    }

    fun stopObservingRiderLocation(orderId: String? = null) {
        if (orderId != null) {
            subscriptionManager.cancelSubscription("rider_locations:$orderId")
        }
        riderLocationListenerReg?.remove()
        riderLocationListenerReg = null
    }

    // --- RIDER FLEET CLOUD SYNC ---

    fun seedRidersToCloud(
        riders: List<Rider>,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            onSuccess()
            return
        }
        try {
            riders.forEach { rider ->
                val riderMap = mapOf(
                    "id" to rider.id,
                    "name" to rider.name,
                    "phone" to rider.phone,
                    "vehicleNumber" to rider.vehicleNumber,
                    "vehicleModel" to rider.vehicleModel,
                    "rating" to rider.rating,
                    "totalDeliveries" to rider.totalDeliveries,
                    "latitude" to rider.latitude,
                    "longitude" to rider.longitude,
                    "isAvailable" to rider.isAvailable,
                    "activeOrderId" to (rider.activeOrderId ?: ""),
                    "batteryPercent" to rider.batteryPercent,
                    "lastUpdated" to rider.lastUpdated
                )
                db.collection("riders").document(rider.id)
                    .set(riderMap)
            }
            onSuccess()
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun observeRidersRealTime(
        onRidersUpdated: (List<Rider>) -> Unit
    ) {
        ridersListenerReg?.remove()
        val db = firestore
        if (db == null || !isFirebaseInitialized) return
        try {
            ridersListenerReg = db.collection("riders")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val ridersList = mutableListOf<Rider>()
                    for (doc in snapshot.documents) {
                        try {
                            ridersList.add(
                                Rider(
                                    id = doc.getString("id") ?: doc.id,
                                    name = doc.getString("name") ?: "Rider",
                                    phone = doc.getString("phone") ?: "+91 98112 34567",
                                    vehicleNumber = doc.getString("vehicleNumber") ?: "MH-02-EM-8821",
                                    vehicleModel = doc.getString("vehicleModel") ?: "Hero Splendor Electric",
                                    rating = doc.getDouble("rating") ?: 4.8,
                                    totalDeliveries = doc.getLong("totalDeliveries")?.toInt() ?: 300,
                                    latitude = doc.getDouble("latitude") ?: 19.1672,
                                    longitude = doc.getDouble("longitude") ?: 72.8518,
                                    isAvailable = doc.getBoolean("isAvailable") ?: true,
                                    activeOrderId = doc.getString("activeOrderId")?.ifBlank { null },
                                    batteryPercent = doc.getLong("batteryPercent")?.toInt() ?: 90,
                                    lastUpdated = doc.getLong("lastUpdated") ?: System.currentTimeMillis()
                                )
                            )
                        } catch (_: Exception) {}
                    }
                    if (ridersList.isNotEmpty()) {
                        onRidersUpdated(ridersList)
                    }
                }
        } catch (_: Exception) {}
    }

    fun updateRiderStatusInCloud(
        riderId: String,
        isAvailable: Boolean,
        activeOrderId: String?,
        currentLat: Double? = null,
        currentLng: Double? = null
    ) {
        val db = firestore ?: return
        try {
            val updates = mutableMapOf<String, Any>(
                "isAvailable" to isAvailable,
                "activeOrderId" to (activeOrderId ?: ""),
                "lastUpdated" to System.currentTimeMillis()
            )
            if (currentLat != null) updates["latitude"] = currentLat
            if (currentLng != null) updates["longitude"] = currentLng

            db.collection("riders").document(riderId).update(updates)
        } catch (_: Exception) {}
    }

    // --- RIDER RIDES, PAYOUTS & REJECTED OFFERS REAL-TIME SYNC ---

    fun observeRiderRidesRealTime(
        riderId: String = "",
        isAdmin: Boolean = false,
        onRidesUpdated: (List<RiderRideRecord>) -> Unit
    ): ListenerRegistration? {
        riderRidesListenerReg?.remove()
        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            val fallback = if (riderId.isNotBlank()) localSqlDb.getRiderRides(riderId) else emptyList()
            onRidesUpdated(fallback)
            return null
        }
        if (!isAdmin && riderId.isBlank()) {
            Log.w(tag, "Rider ride observation rejected: missing riderId for non-admin request.")
            onRidesUpdated(emptyList())
            return null
        }

        val channelId = if (isAdmin) "rider_rides:admin" else "rider_rides:rider:$riderId"
        val query: Query = if (isAdmin) {
            db.collection("rider_rides")
        } else {
            db.collection("rider_rides").whereEqualTo("riderId", riderId)
        }

        return try {
            val reg = subscriptionManager.attachQueryListener(
                channelId = channelId,
                scope = if (isAdmin) FirestoreSubscriptionManager.ChannelScope.GLOBAL else FirestoreSubscriptionManager.ChannelScope.USER_SCOPED,
                query = query,
                onSnapshot = { snapshot, _ ->
                    for (change in snapshot.documentChanges) {
                        val doc = change.document
                        val docId = doc.id
                        val rId = doc.getString("riderId") ?: riderId
                        val data = doc.data ?: emptyMap<String, Any?>()

                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                if (!subscriptionManager.shouldProcessModification(channelId, docId, data)) {
                                    continue
                                }
                                val ride = FirestoreModelMapper.mapToRiderRideRecord(docId, data)
                                if (ride != null) {
                                    localSqlDb.upsertRiderRideFromCloud(ride)
                                }
                            }
                            DocumentChange.Type.REMOVED -> {
                                subscriptionManager.removePayloadHash(channelId, docId)
                                localSqlDb.deleteRiderRideFromCloud(docId, rId)
                            }
                        }
                    }

                    val rides = snapshot.documents.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        FirestoreModelMapper.mapToRiderRideRecord(doc.id, data)
                    }
                    onRidesUpdated(rides)
                },
                onError = { error ->
                    Log.w(tag, "Rider rides sync error on $channelId: ${error.message}")
                    val fallback = if (riderId.isNotBlank()) localSqlDb.getRiderRides(riderId) else emptyList()
                    onRidesUpdated(fallback)
                }
            )
            riderRidesListenerReg = reg
            reg
        } catch (e: Exception) {
            Log.w(tag, "Failed to attach rider rides listener for $channelId: ${e.message}")
            val fallback = if (riderId.isNotBlank()) localSqlDb.getRiderRides(riderId) else emptyList()
            onRidesUpdated(fallback)
            null
        }
    }

    fun observeRiderPayoutsRealTime(
        riderId: String = "",
        isAdmin: Boolean = false,
        onPayoutsUpdated: (List<RiderPayoutRecord>) -> Unit
    ): ListenerRegistration? {
        riderPayoutsListenerReg?.remove()
        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            val fallback = if (riderId.isNotBlank()) localSqlDb.getRiderPayouts(riderId) else emptyList()
            onPayoutsUpdated(fallback)
            return null
        }
        if (!isAdmin && riderId.isBlank()) {
            Log.w(tag, "Rider payouts observation rejected: riderId required for non-admin scope.")
            onPayoutsUpdated(emptyList())
            return null
        }

        val channelId = if (isAdmin) "rider_payouts:admin" else "rider_payouts:rider:$riderId"
        val query: Query = if (isAdmin) {
            db.collection("rider_payouts")
        } else {
            db.collection("rider_payouts").whereEqualTo("riderId", riderId)
        }

        return try {
            val reg = subscriptionManager.attachQueryListener(
                channelId = channelId,
                scope = if (isAdmin) FirestoreSubscriptionManager.ChannelScope.GLOBAL else FirestoreSubscriptionManager.ChannelScope.USER_SCOPED,
                query = query,
                onSnapshot = { snapshot, _ ->
                    for (change in snapshot.documentChanges) {
                        val doc = change.document
                        val docId = doc.id
                        val rId = doc.getString("riderId") ?: riderId
                        val data = doc.data ?: emptyMap<String, Any?>()

                        // Strict scoping guard: rider cannot see foreign payouts
                        if (!isAdmin && rId != riderId) {
                            Log.w(tag, "Security violation: foreign payout doc $docId for rider $rId ignored on $riderId listener")
                            continue
                        }

                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                if (!subscriptionManager.shouldProcessModification(channelId, docId, data)) {
                                    continue
                                }
                                val payout = FirestoreModelMapper.mapToRiderPayoutRecord(docId, data)
                                if (payout != null) {
                                    localSqlDb.upsertRiderPayoutFromCloud(payout)
                                }
                            }
                            DocumentChange.Type.REMOVED -> {
                                subscriptionManager.removePayloadHash(channelId, docId)
                                localSqlDb.deleteRiderPayoutFromCloud(docId, rId)
                            }
                        }
                    }

                    val payouts = snapshot.documents.mapNotNull { doc ->
                        val rId = doc.getString("riderId") ?: ""
                        if (!isAdmin && rId != riderId) null
                        else {
                            val data = doc.data ?: return@mapNotNull null
                            FirestoreModelMapper.mapToRiderPayoutRecord(doc.id, data)
                        }
                    }
                    onPayoutsUpdated(payouts)
                },
                onError = { error ->
                    Log.w(tag, "Rider payouts sync error on $channelId: ${error.message}")
                    val fallback = if (riderId.isNotBlank()) localSqlDb.getRiderPayouts(riderId) else emptyList()
                    onPayoutsUpdated(fallback)
                }
            )
            riderPayoutsListenerReg = reg
            reg
        } catch (e: Exception) {
            Log.w(tag, "Failed to attach rider payouts listener for $channelId: ${e.message}")
            val fallback = if (riderId.isNotBlank()) localSqlDb.getRiderPayouts(riderId) else emptyList()
            onPayoutsUpdated(fallback)
            null
        }
    }

    fun observeRiderRejectedOffersRealTime(
        riderId: String,
        onRejectedUpdated: (Set<String>) -> Unit
    ): ListenerRegistration? {
        riderRejectedOffersListenerReg?.remove()
        val db = firestore
        if (db == null || !isFirebaseInitialized || riderId.isBlank()) {
            val fallback = if (riderId.isNotBlank()) localSqlDb.getRejectedRideIds(riderId) else emptySet()
            onRejectedUpdated(fallback)
            return null
        }

        val channelId = "rider_rejected:rider:$riderId"
        val query = db.collection("rider_rejected_offers").whereEqualTo("riderId", riderId)

        return try {
            val reg = subscriptionManager.attachQueryListener(
                channelId = channelId,
                scope = FirestoreSubscriptionManager.ChannelScope.USER_SCOPED,
                query = query,
                onSnapshot = { snapshot, _ ->
                    for (change in snapshot.documentChanges) {
                        val doc = change.document
                        val docId = doc.id
                        val rId = doc.getString("riderId") ?: riderId
                        val offerId = doc.getString("offerId") ?: docId.substringAfter('_', "")
                        val data = doc.data ?: emptyMap<String, Any?>()

                        if (rId != riderId) continue

                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                if (!subscriptionManager.shouldProcessModification(channelId, docId, data)) {
                                    continue
                                }
                                val rej = FirestoreModelMapper.mapToRiderRejectedOffer(docId, data)
                                if (rej != null) {
                                    localSqlDb.upsertRejectedOfferFromCloud(rej.riderId, rej.offerId)
                                }
                            }
                            DocumentChange.Type.REMOVED -> {
                                subscriptionManager.removePayloadHash(channelId, docId)
                                localSqlDb.deleteRejectedOfferFromCloud(rId, offerId)
                            }
                        }
                    }

                    onRejectedUpdated(localSqlDb.getRejectedRideIds(riderId))
                },
                onError = { error ->
                    Log.w(tag, "Rider rejected offers sync error on $channelId: ${error.message}")
                    onRejectedUpdated(localSqlDb.getRejectedRideIds(riderId))
                }
            )
            riderRejectedOffersListenerReg = reg
            reg
        } catch (e: Exception) {
            Log.w(tag, "Failed to attach rider rejected offers listener: ${e.message}")
            onRejectedUpdated(localSqlDb.getRejectedRideIds(riderId))
            null
        }
    }

    fun saveRiderRideToCloud(
        ride: RiderRideRecord,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            onSuccess()
            return
        }
        try {
            val map = mapOf(
                "id" to ride.id,
                "riderId" to ride.riderId,
                "orderId" to ride.orderId,
                "restaurantName" to ride.restaurantName,
                "dropAddress" to ride.dropAddress,
                "distanceKm" to ride.distanceKm,
                "durationMinutes" to ride.durationMinutes,
                "incomeRupees" to ride.incomeRupees,
                "completedAtMs" to ride.completedAtMs
            )
            db.collection("rider_rides").document(ride.id)
                .set(map)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onError(it) }
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun saveRiderPayoutToCloud(
        payout: RiderPayoutRecord,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            onSuccess()
            return
        }
        try {
            val map = mapOf(
                "id" to payout.id,
                "riderId" to payout.riderId,
                "amountRupees" to payout.amountRupees,
                "accountDestination" to payout.accountDestination,
                "payoutMethod" to payout.payoutMethod,
                "status" to payout.status,
                "referenceId" to payout.referenceId,
                "utr" to payout.utr,
                "failureReason" to payout.failureReason,
                "idempotencyKey" to payout.idempotencyKey,
                "receiptNumber" to payout.receiptNumber,
                "timestampMs" to payout.timestampMs
            )
            db.collection("rider_payouts").document(payout.id)
                .set(map)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onError(it) }
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun saveRiderRejectedOfferToCloud(
        riderId: String,
        offerId: String,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            onSuccess()
            return
        }
        try {
            val docId = "${riderId}_$offerId"
            val map = mapOf(
                "riderId" to riderId,
                "offerId" to offerId,
                "rejectedAtMs" to System.currentTimeMillis()
            )
            db.collection("rider_rejected_offers").document(docId)
                .set(map)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onError(it) }
        } catch (e: Exception) {
            onError(e)
        }
    }

    // --- CATALOG CLOUD SYNC ---

    fun seedCatalogToCloud(
        catalog: Catalog,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            onSuccess()
            return
        }
        try {
            catalog.restaurants.forEach { rest ->
                val restMap = mapOf(
                    "id" to rest.id,
                    "name" to rest.name,
                    "cuisine" to rest.cuisine,
                    "description" to rest.description,
                    "image" to rest.image,
                    "address" to rest.address,
                    "latitude" to rest.latitude,
                    "longitude" to rest.longitude,
                    "deliveryMinutes" to rest.deliveryMinutes,
                    "deliveryFee" to rest.deliveryFee,
                    "open" to rest.open,
                    "rating" to rest.rating,
                    "reviewCount" to rest.reviewCount,
                    "costForTwo" to rest.costForTwo,
                    "tableBookingAvailable" to rest.tableBookingAvailable,
                    "maxPartySize" to rest.maxPartySize,
                    "prepMinutes" to rest.prepMinutes,
                    "acceptsDiningPayment" to rest.acceptsDiningPayment,
                    "logoUrl" to rest.logoUrl,
                    "fssaiNumber" to rest.fssaiNumber,
                    "gstNumber" to rest.gstNumber,
                    "vibes" to rest.vibes,
                    "amenities" to rest.amenities
                )
                db.collection("restaurants").document(rest.id).set(restMap)

                val settingsMap = mapOf(
                    "restaurantId" to rest.id,
                    "open" to rest.open,
                    "prepMinutes" to rest.prepMinutes,
                    "acceptsDiningPayment" to rest.acceptsDiningPayment,
                    "updatedAt" to Instant.now().toString()
                )
                db.collection("restaurant_settings").document(rest.id).set(settingsMap)
            }
            catalog.dishes.forEach { dish ->
                val dishMap = mapOf(
                    "id" to dish.id,
                    "restaurantId" to dish.restaurantId,
                    "name" to dish.name,
                    "description" to dish.description,
                    "price" to dish.price,
                    "image" to dish.image,
                    "category" to dish.category,
                    "vegetarian" to dish.vegetarian,
                    "available" to dish.available,
                    "addons" to gson.toJson(dish.addons),
                    "updatedAt" to Instant.now().toString()
                )
                db.collection("dishes").document(dish.id).set(dishMap)
            }
            onSuccess()
        } catch (e: Exception) {
            onError(e)
        }
    }

    /**
     * Seeds initial catalog data to Cloud Firestore ONLY if the remote restaurants collection is empty.
     * Prevents accidental overwriting of remote live data with local mock catalog data.
     */
    fun seedCatalogIfRemoteEmpty(
        catalog: Catalog,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            onSuccess()
            return
        }
        db.collection("restaurants").limit(1).get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    Log.i(tag, "Remote restaurants collection is empty. Seeding catalog to cloud...")
                    seedCatalogToCloud(catalog, onSuccess, onError)
                } else {
                    Log.i(tag, "Remote restaurants collection is already populated (${snap.size()} docs). Skipping seed.")
                    onSuccess()
                }
            }
            .addOnFailureListener { e ->
                Log.w(tag, "Failed to check if remote catalog is empty: ${e.message}")
                onError(e)
            }
    }

    fun observeCatalogRealTime(
        onCatalogUpdated: (Catalog) -> Unit
    ) {
        val db = firestore
        val fallbackCatalog = Catalog(localSqlDb.getAllRestaurants(), localSqlDb.getAllDishes())
        if (db == null || !isFirebaseInitialized) {
            onCatalogUpdated(fallbackCatalog)
            return
        }

        fun emitLatestCatalog() {
            try {
                val currentRests = localSqlDb.getAllRestaurants()
                val currentDishes = localSqlDb.getAllDishes()
                onCatalogUpdated(Catalog(currentRests, currentDishes))
            } catch (e: Exception) {
                Log.w(tag, "Error loading catalog after cloud update: ${e.message}")
            }
        }

        try {
            // 1. Restaurants Subscription (global)
            restaurantsListenerReg = subscriptionManager.attachQueryListener(
                channelId = "catalog:restaurants",
                scope = FirestoreSubscriptionManager.ChannelScope.GLOBAL,
                query = db.collection("restaurants"),
                onSnapshot = { snapshot, _ ->
                    subscriptionManager.processDocumentChanges(
                        channelId = "catalog:restaurants",
                        snapshot = snapshot,
                        mapper = { id, data -> FirestoreModelMapper.mapToRestaurant(id, data) },
                        onAdded = { rest -> localSqlDb.upsertRestaurantFromCloud(rest) },
                        onModified = { rest -> localSqlDb.upsertRestaurantFromCloud(rest) },
                        onRemoved = { restId -> localSqlDb.deleteRestaurantFromCloud(restId) }
                    )
                    emitLatestCatalog()
                },
                onError = { err ->
                    Log.w(tag, "Restaurants subscription error: ${err.message}")
                    emitLatestCatalog()
                }
            )

            // 2. Dishes Subscription (global)
            dishesListenerReg = subscriptionManager.attachQueryListener(
                channelId = "catalog:dishes",
                scope = FirestoreSubscriptionManager.ChannelScope.GLOBAL,
                query = db.collection("dishes"),
                onSnapshot = { snapshot, _ ->
                    subscriptionManager.processDocumentChanges(
                        channelId = "catalog:dishes",
                        snapshot = snapshot,
                        mapper = { id, data -> FirestoreModelMapper.mapToDish(id, data) },
                        onAdded = { dish -> localSqlDb.upsertDishFromCloud(dish) },
                        onModified = { dish -> localSqlDb.upsertDishFromCloud(dish) },
                        onRemoved = { dishId -> localSqlDb.deleteDishFromCloud(dishId) }
                    )
                    emitLatestCatalog()
                },
                onError = { err ->
                    Log.w(tag, "Dishes subscription error: ${err.message}")
                    emitLatestCatalog()
                }
            )

            // 3. Restaurant Settings Subscription (open status, prep time, acceptsDiningPayment)
            settingsListenerReg = subscriptionManager.attachQueryListener(
                channelId = "catalog:restaurant_settings",
                scope = FirestoreSubscriptionManager.ChannelScope.GLOBAL,
                query = db.collection("restaurant_settings"),
                onSnapshot = { snapshot, _ ->
                    subscriptionManager.processDocumentChanges(
                        channelId = "catalog:restaurant_settings",
                        snapshot = snapshot,
                        mapper = { id, data -> FirestoreModelMapper.mapToRestaurantSettings(id, data) },
                        onAdded = { s -> localSqlDb.saveRestaurantSettingsFromCloud(s.restaurantId, s.open, s.prepMinutes, s.acceptsDiningPayment) },
                        onModified = { s -> localSqlDb.saveRestaurantSettingsFromCloud(s.restaurantId, s.open, s.prepMinutes, s.acceptsDiningPayment) },
                        onRemoved = { /* preserve settings */ }
                    )
                    emitLatestCatalog()
                },
                onError = { err ->
                    Log.w(tag, "Restaurant settings subscription error: ${err.message}")
                    emitLatestCatalog()
                }
            )
        } catch (e: Exception) {
            Log.w(tag, "Catalog subscription setup error: ${e.message}")
            onCatalogUpdated(fallbackCatalog)
        }
    }

    fun cleanUpListeners() {
        subscriptionManager.cancelAll()
        ordersListenerReg = null
        kotTicketsListenerReg = null
        tabListenerReg = null
        diningTabsListenerReg = null
        bookingsListenerReg = null
        userListenerReg = null
        addressesListenerReg = null
        catalogListenerReg = null
        restaurantsListenerReg = null
        dishesListenerReg = null
        settingsListenerReg = null
        riderLocationListenerReg = null
        ridersListenerReg = null
        riderRidesListenerReg = null
        riderPayoutsListenerReg = null
        riderRejectedOffersListenerReg = null
    }

    fun cancelUserScopedListeners() {
        subscriptionManager.cancelUserScopedSubscriptions()
        ordersListenerReg = null
        kotTicketsListenerReg = null
        tabListenerReg = null
        diningTabsListenerReg = null
        bookingsListenerReg = null
        userListenerReg = null
        addressesListenerReg = null
        riderRidesListenerReg = null
        riderPayoutsListenerReg = null
        riderRejectedOffersListenerReg = null
        riderLocationListenerReg = null
    }
}
