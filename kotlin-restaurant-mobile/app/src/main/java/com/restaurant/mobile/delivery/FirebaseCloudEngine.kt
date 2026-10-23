package com.restaurant.mobile.delivery

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
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

    var isFirebaseInitialized: Boolean = false
        private set

    private var firestore: FirebaseFirestore? = null
    private var auth: FirebaseAuth? = null

    private var ordersListenerReg: ListenerRegistration? = null
    private var tabListenerReg: ListenerRegistration? = null
    private var bookingsListenerReg: ListenerRegistration? = null

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
            onSuccess()
            return
        }

        try {
            val orderMap = mapOf(
                "id" to order.id,
                "restaurantId" to order.restaurantId,
                "restaurantName" to order.restaurantName,
                "address" to gson.toJson(order.address),
                "quote" to gson.toJson(order.quote),
                "paymentMethod" to order.paymentMethod,
                "paymentStatus" to order.paymentStatus,
                "status" to order.status,
                "events" to gson.toJson(order.events),
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

    fun observeOrdersRealTime(
        userId: String,
        onOrdersUpdated: (List<DeliveryOrder>) -> Unit
    ) {
        ordersListenerReg?.remove()

        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            onOrdersUpdated(localSqlDb.getAllOrders())
            return
        }

        try {
            ordersListenerReg = db.collection("orders")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        onOrdersUpdated(localSqlDb.getAllOrders())
                        return@addSnapshotListener
                    }

                    val cloudOrders = mutableListOf<DeliveryOrder>()
                    for (doc in snapshot.documents) {
                        try {
                            val id = doc.getString("id") ?: doc.id
                            val restId = doc.getString("restaurantId") ?: ""
                            val restName = doc.getString("restaurantName") ?: ""
                            val status = doc.getString("status") ?: "Confirmed"
                            val paymentMethod = doc.getString("paymentMethod") ?: "Cash on delivery"
                            val paymentStatus = doc.getString("paymentStatus") ?: "Due on delivery"
                            val addrJson = doc.getString("address") ?: ""
                            val quoteJson = doc.getString("quote") ?: ""
                            val eventsJson = doc.getString("events") ?: ""

                            val address = try {
                                gson.fromJson(addrJson, Address::class.java)
                            } catch (_: Exception) {
                                Address("addr-default", "Home", "Current Location", "City", "000000", "+91 98765 43210")
                            }
                            val quote = try {
                                gson.fromJson(quoteJson, Quote::class.java)
                            } catch (_: Exception) {
                                Quote(
                                    restaurantId = restId,
                                    lines = emptyList(),
                                    subtotal = 50000L,
                                    tax = 2500L,
                                    delivery = 3500L,
                                    discount = 0L,
                                    total = 56000L
                                )
                            }
                            val eventsType = object : TypeToken<List<Event>>() {}.type
                            val events: List<Event> = try {
                                gson.fromJson(eventsJson, eventsType) ?: listOf(Event(status))
                            } catch (_: Exception) {
                                listOf(Event(status))
                            }

                            val order = DeliveryOrder(
                                id = id,
                                restaurantId = restId,
                                restaurantName = restName,
                                address = address,
                                quote = quote,
                                paymentMethod = paymentMethod,
                                paymentStatus = paymentStatus,
                                status = status,
                                events = events
                            )
                            cloudOrders.add(order)
                            localSqlDb.saveOrder(order)
                        } catch (e: Exception) {
                            Log.w(tag, "Failed parsing cloud order: ${e.message}")
                        }
                    }

                    if (cloudOrders.isNotEmpty()) {
                        onOrdersUpdated(cloudOrders)
                    } else {
                        onOrdersUpdated(localSqlDb.getAllOrders())
                    }
                }
        } catch (_: Exception) {
            onOrdersUpdated(localSqlDb.getAllOrders())
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
            onSuccess()
            return
        }

        try {
            val bookingMap = mapOf(
                "id" to booking.id,
                "restaurantId" to booking.restaurantId,
                "restaurantName" to booking.restaurantName,
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
                "updatedAt" to Instant.now().toString()
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
        userId: String,
        onBookingsUpdated: (List<TableBooking>) -> Unit
    ) {
        bookingsListenerReg?.remove()

        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            onBookingsUpdated(localSqlDb.getTableBookingsForUser(userId))
            return
        }

        try {
            bookingsListenerReg = db.collection("table_bookings")
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        onBookingsUpdated(localSqlDb.getTableBookingsForUser(userId))
                        return@addSnapshotListener
                    }

                    val bookings = mutableListOf<TableBooking>()
                    for (doc in snapshot.documents) {
                        try {
                            val id = doc.getString("id") ?: doc.id
                            val restId = doc.getString("restaurantId") ?: ""
                            val restName = doc.getString("restaurantName") ?: ""
                            val tableNum = doc.getString("tableNumber") ?: "T1"
                            val uid = doc.getString("userId") ?: userId
                            val uName = doc.getString("userName") ?: "Guest"
                            val uPhone = doc.getString("userPhone") ?: "+91 98765 43210"
                            val party = doc.getLong("partySize")?.toInt() ?: 2
                            val bDate = doc.getString("bookingDate") ?: "Today"
                            val slot = doc.getString("timeSlot") ?: "7:00 PM"
                            val area = doc.getString("seatingArea") ?: "Main Dining Room"
                            val special = doc.getString("specialRequests") ?: ""
                            val statusStr = doc.getString("status") ?: TableBookingStatus.CONFIRMED
                            val createdAt = doc.getString("createdAt") ?: Instant.now().toString()

                            val b = TableBooking(
                                id = id,
                                restaurantId = restId,
                                restaurantName = restName,
                                tableNumber = tableNum,
                                userId = uid,
                                userName = uName,
                                userPhone = uPhone,
                                partySize = party,
                                bookingDate = bDate,
                                timeSlot = slot,
                                seatingArea = area,
                                specialRequests = special,
                                status = statusStr,
                                createdAt = createdAt
                            )
                            bookings.add(b)
                            localSqlDb.saveTableBooking(b)
                        } catch (e: Exception) {
                            Log.w(tag, "Failed parsing cloud booking: ${e.message}")
                        }
                    }

                    if (bookings.isNotEmpty()) {
                        onBookingsUpdated(bookings)
                    } else {
                        onBookingsUpdated(localSqlDb.getTableBookingsForUser(userId))
                    }
                }
        } catch (_: Exception) {
            onBookingsUpdated(localSqlDb.getTableBookingsForUser(userId))
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
            onSuccess()
            return
        }

        try {
            val tabMap = mapOf(
                "id" to tab.id,
                "tableNumber" to tab.tableNumber,
                "customerName" to (tab.customerName ?: "Guest"),
                "userId" to (tab.userId ?: "guest"),
                "itemsJson" to gson.toJson(tab.items),
                "subtotal" to tab.subtotal,
                "tax" to tab.tax,
                "serviceCharge" to tab.serviceCharge,
                "tip" to tab.tip,
                "total" to tab.total,
                "status" to tab.status,
                "paymentMethod" to (tab.paymentMethod ?: ""),
                "createdAt" to tab.createdAt,
                "updatedAt" to Instant.now().toString()
            )
            db.collection("dining_tabs").document(tab.id)
                .set(tabMap)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onError(it) }
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun observeLiveDiningTabRealTime(
        tableNumber: String,
        onTabUpdated: (DiningTab?) -> Unit
    ) {
        tabListenerReg?.remove()

        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            onTabUpdated(localSqlDb.getOpenTabForTable(tableNumber))
            return
        }

        try {
            tabListenerReg = db.collection("dining_tabs")
                .whereEqualTo("tableNumber", tableNumber)
                .whereEqualTo("status", DiningTabStatus.OPEN)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || snapshot.isEmpty) {
                        onTabUpdated(localSqlDb.getOpenTabForTable(tableNumber))
                        return@addSnapshotListener
                    }

                    val doc = snapshot.documents.firstOrNull() ?: run {
                        onTabUpdated(null)
                        return@addSnapshotListener
                    }

                    try {
                        val id = doc.getString("id") ?: doc.id
                        val tNum = doc.getString("tableNumber") ?: tableNumber
                        val custName = doc.getString("customerName") ?: "Guest"
                        val uid = doc.getString("userId") ?: "guest"
                        val itemsJson = doc.getString("itemsJson") ?: ""
                        val itemsType = object : TypeToken<List<TableBillItem>>() {}.type
                        val items: List<TableBillItem> = try {
                            gson.fromJson(itemsJson, itemsType) ?: emptyList()
                        } catch (_: Exception) {
                            emptyList()
                        }
                        val subtotal = doc.getLong("subtotal") ?: 0L
                        val tax = doc.getLong("tax") ?: 0L
                        val serviceCharge = doc.getLong("serviceCharge") ?: 0L
                        val tip = doc.getLong("tip") ?: 0L
                        val total = doc.getLong("total") ?: 0L
                        val pMethod = doc.getString("paymentMethod")
                        val createdAt = doc.getString("createdAt") ?: Instant.now().toString()
                        val updatedAt = doc.getString("updatedAt") ?: Instant.now().toString()

                        val tab = DiningTab(
                            id = id,
                            tableNumber = tNum,
                            customerId = uid,
                            customerName = custName,
                            userId = uid,
                            userName = custName,
                            items = items,
                            subtotal = subtotal,
                            tax = tax,
                            serviceCharge = serviceCharge,
                            tip = tip,
                            total = total,
                            status = DiningTabStatus.OPEN,
                            paymentMethod = pMethod,
                            createdAt = createdAt,
                            updatedAt = updatedAt
                        )
                        localSqlDb.saveOrUpdateTab(tab)
                        onTabUpdated(tab)
                    } catch (e: Exception) {
                        Log.w(tag, "Failed parsing cloud dining tab: ${e.message}")
                        onTabUpdated(localSqlDb.getOpenTabForTable(tableNumber))
                    }
                }
        } catch (_: Exception) {
            onTabUpdated(localSqlDb.getOpenTabForTable(tableNumber))
        }
    }

    fun cleanUpListeners() {
        ordersListenerReg?.remove()
        tabListenerReg?.remove()
        bookingsListenerReg?.remove()
        ordersListenerReg = null
        tabListenerReg = null
        bookingsListenerReg = null
    }
}
