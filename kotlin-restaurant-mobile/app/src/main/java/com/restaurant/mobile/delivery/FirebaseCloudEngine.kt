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
    private var userListenerReg: ListenerRegistration? = null
    private var addressesListenerReg: ListenerRegistration? = null
    private var catalogListenerReg: ListenerRegistration? = null
    private var riderLocationListenerReg: ListenerRegistration? = null

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
                                if (addrJson.isNotBlank()) gson.fromJson(addrJson, Address::class.java) else null
                            } catch (_: Exception) {
                                null
                            } ?: Address("addr-default", "Home", doc.getString("deliveryAddress") ?: "Goregaon West, Mumbai", "Mumbai", "400062", "+91 98765 43210")

                            val quote = try {
                                if (quoteJson.isNotBlank()) gson.fromJson(quoteJson, Quote::class.java) else null
                            } catch (_: Exception) {
                                null
                            } ?: Quote(
                                restaurantId = restId,
                                lines = emptyList(),
                                subtotal = (doc.getLong("total") ?: 500L) * 100L,
                                tax = 2500L,
                                delivery = 3500L,
                                discount = 0L,
                                total = (doc.getLong("total") ?: 560L) * 100L
                            )
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
        addressesListenerReg?.remove()
        val db = firestore
        if (db == null || !isFirebaseInitialized) {
            onAddressesUpdated(localSqlDb.getAllAddresses())
            return
        }
        try {
            addressesListenerReg = db.collection("users").document(userId)
                .collection("addresses")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        onAddressesUpdated(localSqlDb.getAllAddresses())
                        return@addSnapshotListener
                    }
                    val list = mutableListOf<Address>()
                    for (doc in snapshot.documents) {
                        try {
                            val id = doc.getString("id") ?: doc.id
                            val label = doc.getString("label") ?: "Home"
                            val line = doc.getString("line") ?: ""
                            val city = doc.getString("city") ?: ""
                            val postcode = doc.getString("postcode") ?: ""
                            val phone = doc.getString("phone") ?: ""
                            val lat = doc.getDouble("latitude")
                            val lng = doc.getDouble("longitude")
                            val addr = Address(id, label, line, city, postcode, phone, lat, lng)
                            list.add(addr)
                            localSqlDb.saveAddress(addr)
                        } catch (_: Exception) {}
                    }
                    if (list.isNotEmpty()) {
                        onAddressesUpdated(list)
                    } else {
                        onAddressesUpdated(localSqlDb.getAllAddresses())
                    }
                }
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
    ) {
        riderLocationListenerReg?.remove()
        val db = firestore
        if (db == null || !isFirebaseInitialized) return
        try {
            riderLocationListenerReg = db.collection("rider_locations").document(orderId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    val lat = snapshot.getDouble("latitude") ?: return@addSnapshotListener
                    val lng = snapshot.getDouble("longitude") ?: return@addSnapshotListener
                    val eta = snapshot.getLong("etaMinutes")?.toInt() ?: 15
                    onLocationUpdated(lat, lng, eta)
                }
        } catch (_: Exception) {}
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
                    "tableBookingAvailable" to rest.tableBookingAvailable,
                    "vibes" to rest.vibes
                )
                db.collection("restaurants").document(rest.id).set(restMap)
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
                    "addons" to gson.toJson(dish.addons)
                )
                db.collection("dishes").document(dish.id).set(dishMap)
            }
            onSuccess()
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun observeCatalogRealTime(
        onCatalogUpdated: (Catalog) -> Unit
    ) {
        catalogListenerReg?.remove()
        val db = firestore
        val fallbackCatalog = Catalog(localSqlDb.getAllRestaurants(), localSqlDb.getAllDishes())
        if (db == null || !isFirebaseInitialized) {
            onCatalogUpdated(fallbackCatalog)
            return
        }
        try {
            catalogListenerReg = db.collection("restaurants")
                .addSnapshotListener { restSnap, restErr ->
                    if (restErr != null || restSnap == null || restSnap.isEmpty) {
                        onCatalogUpdated(fallbackCatalog)
                        return@addSnapshotListener
                    }
                    val restaurants = mutableListOf<Restaurant>()
                    for (doc in restSnap.documents) {
                        try {
                            val id = doc.getString("id") ?: doc.id
                            val name = doc.getString("name") ?: ""
                            val cuisine = doc.getString("cuisine") ?: ""
                            val desc = doc.getString("description") ?: ""
                            val image = doc.getString("image") ?: ""
                            val address = doc.getString("address") ?: "Mumbai"
                            val lat = doc.getDouble("latitude") ?: 19.1663
                            val lng = doc.getDouble("longitude") ?: 72.8526
                            val delMins = doc.getLong("deliveryMinutes")?.toInt() ?: 28
                            val delFee = doc.getLong("deliveryFee") ?: 4000L
                            val open = doc.getBoolean("open") ?: true
                            val rating = doc.getDouble("rating") ?: 4.8
                            val tableBooking = doc.getBoolean("tableBookingAvailable") ?: true
                            val vibes = (doc.get("vibes") as? List<*>)?.mapNotNull { it as? String } ?: listOf("Fine Dining", "Romantic")
                            restaurants.add(
                                Restaurant(
                                    id = id,
                                    name = name,
                                    cuisine = cuisine,
                                    description = desc,
                                    image = image,
                                    address = address,
                                    latitude = lat,
                                    longitude = lng,
                                    deliveryMinutes = delMins,
                                    deliveryFee = delFee,
                                    open = open,
                                    rating = rating,
                                    vibes = vibes,
                                    tableBookingAvailable = tableBooking
                                )
                            )
                        } catch (_: Exception) {}
                    }
                    if (restaurants.isNotEmpty()) {
                        val currentDishes = localSqlDb.getAllDishes()
                        onCatalogUpdated(Catalog(restaurants, currentDishes))
                    }
                }
        } catch (_: Exception) {
            onCatalogUpdated(fallbackCatalog)
        }
    }

    fun cleanUpListeners() {
        ordersListenerReg?.remove()
        tabListenerReg?.remove()
        bookingsListenerReg?.remove()
        userListenerReg?.remove()
        addressesListenerReg?.remove()
        catalogListenerReg?.remove()
        riderLocationListenerReg?.remove()
        ordersListenerReg = null
        tabListenerReg = null
        bookingsListenerReg = null
        userListenerReg = null
        addressesListenerReg = null
        catalogListenerReg = null
        riderLocationListenerReg = null
    }
}
