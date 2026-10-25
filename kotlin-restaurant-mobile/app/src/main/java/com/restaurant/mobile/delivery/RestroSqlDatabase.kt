package com.restaurant.mobile.delivery

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.restaurant.mobile.model.KOT
import com.restaurant.mobile.model.KOTItem
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Firebase Cloud Firestore + Local Offline Cache Engine for Restro.
 * Syncs Restaurants, Dishes, Orders, Users, Addresses, Rider Earnings, Payouts & Partner Verifications with Firebase Firestore.
 */
typealias RestroFirebaseDatabase = RestroSqlDatabase

class RestroSqlDatabase(private val appContext: Context) : SQLiteOpenHelper(appContext, DATABASE_NAME, null, DATABASE_VERSION) {

    private val gson = Gson()
    private val firebasePrefs by lazy {
        appContext.getSharedPreferences("restro_firebase_cloud_mirror", Context.MODE_PRIVATE)
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            if (FirebaseApp.getApps(appContext).isEmpty()) {
                FirebaseApp.initializeApp(appContext)
            }
            FirebaseFirestore.getInstance()
        } catch (_: Exception) {
            null
        }
    }

    fun syncToFirebaseFirestore(collection: String, docId: String, payload: Map<String, Any?>) {
        try {
            val cleanMap = payload.filterValues { it != null }.mapValues { it.value!! }
            firestore?.collection(collection)?.document(docId)?.set(cleanMap)
        } catch (_: Exception) {}
    }

    fun deleteFromFirebaseFirestore(collection: String, docId: String) {
        try {
            firestore?.collection(collection)?.document(docId)?.delete()
        } catch (_: Exception) {}
    }

    companion object {
        const val DATABASE_NAME = "restro_delivery.db"
        const val DATABASE_VERSION = 10

        // Table Names
        const val TABLE_USERS = "users"
        const val TABLE_RESTAURANTS = "restaurants"
        const val TABLE_DISHES = "dishes"
        const val TABLE_ORDERS = "orders"
        const val TABLE_ADDRESSES = "addresses"
        const val TABLE_REVIEWS = "reviews"
        const val TABLE_OTPS = "otps"
        const val TABLE_STAFF_PINS = "staff_pins"
        const val TABLE_BOOKINGS = "table_bookings"
        const val TABLE_TABLE_BOOKINGS = TABLE_BOOKINGS
        const val TABLE_DINING_TABS = "dining_tabs"
        const val TABLE_TABS = TABLE_DINING_TABS
        const val TABLE_KOT_TICKETS = "kot_tickets"
        const val TABLE_INVENTORY = "inventory_items"
        const val TABLE_STOCK_MOVEMENTS = "stock_movements"
        const val TABLE_AUDIT_LOGS = "audit_logs"

        val ALLOCATION_LOCK = Any()
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Users Table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_USERS (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                email TEXT NOT NULL,
                phone TEXT NOT NULL,
                role TEXT NOT NULL DEFAULT 'CUSTOMER',
                created_at TEXT NOT NULL
            );
            """.trimIndent()
        )

        // Restaurants Table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_RESTAURANTS (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                cuisine TEXT NOT NULL,
                description TEXT NOT NULL,
                image TEXT NOT NULL,
                address TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                delivery_minutes INTEGER NOT NULL,
                delivery_fee INTEGER NOT NULL,
                open INTEGER NOT NULL DEFAULT 1
            );
            """.trimIndent()
        )

        // Dishes Table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_DISHES (
                id TEXT PRIMARY KEY,
                restaurant_id TEXT NOT NULL,
                category TEXT NOT NULL,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                price INTEGER NOT NULL,
                image TEXT NOT NULL,
                vegetarian INTEGER NOT NULL DEFAULT 0,
                available INTEGER NOT NULL DEFAULT 1,
                addons_json TEXT,
                FOREIGN KEY(restaurant_id) REFERENCES $TABLE_RESTAURANTS(id)
            );
            """.trimIndent()
        )

        // Orders Table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_ORDERS (
                id TEXT PRIMARY KEY,
                restaurant_id TEXT NOT NULL,
                restaurant_name TEXT NOT NULL,
                address_json TEXT NOT NULL,
                quote_json TEXT NOT NULL,
                payment_method TEXT NOT NULL,
                payment_status TEXT NOT NULL,
                status TEXT NOT NULL,
                events_json TEXT NOT NULL,
                created_at TEXT NOT NULL
            );
            """.trimIndent()
        )

        // Addresses Table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_ADDRESSES (
                id TEXT PRIMARY KEY,
                label TEXT NOT NULL,
                line TEXT NOT NULL,
                city TEXT NOT NULL,
                postcode TEXT NOT NULL,
                phone TEXT NOT NULL,
                latitude REAL,
                longitude REAL
            );
            """.trimIndent()
        )

        // Reviews Table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_REVIEWS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_id TEXT NOT NULL,
                restaurant_id TEXT NOT NULL,
                author TEXT NOT NULL,
                rating INTEGER NOT NULL,
                text TEXT NOT NULL,
                at TEXT NOT NULL
            );
            """.trimIndent()
        )

        // OTPs Table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_OTPS (
                target TEXT PRIMARY KEY,
                code TEXT NOT NULL,
                expires_at INTEGER NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0
            );
            """.trimIndent()
        )

        // Staff PINs Vault Table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_STAFF_PINS (
                role TEXT PRIMARY KEY,
                pin_hash TEXT NOT NULL,
                label TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );
            """.trimIndent()
        )

        // Table Bookings Table
        createTableBookingsTable(db)

        // Dining Tabs Table
        createDiningTabsTable(db)

        // KOT Tickets Table
        createKotTicketsTable(db)

        // Staff Payroll Table
        createStaffPayrollTable(db)

        // User Credentials & Password Vault Table
        createUserCredentialsTable(db)

        // Inventory & Stock Movements Tables
        createInventoryTables(db)

        // Audit Logs Table
        createAuditLogsTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 4) {
            createTableBookingsTable(db)
        }
        if (oldVersion < 5) {
            runCatching { db.execSQL("ALTER TABLE $TABLE_BOOKINGS ADD COLUMN restaurant_id TEXT NOT NULL DEFAULT '';") }
            runCatching { db.execSQL("ALTER TABLE $TABLE_BOOKINGS ADD COLUMN restaurant_name TEXT NOT NULL DEFAULT '';") }
            runCatching { db.execSQL("CREATE INDEX IF NOT EXISTS idx_table_bookings_restaurant ON $TABLE_BOOKINGS (restaurant_id, booking_date, time_slot);") }
        }
        if (oldVersion < 6) {
            createDiningTabsTable(db)
        }
        if (oldVersion < 8) {
            createKotTicketsTable(db)
            runCatching { db.execSQL("ALTER TABLE $TABLE_DINING_TABS ADD COLUMN guest_count INTEGER NOT NULL DEFAULT 0;") }
            runCatching { db.execSQL("ALTER TABLE $TABLE_DINING_TABS ADD COLUMN seated_at TEXT NOT NULL DEFAULT '';") }
            runCatching { db.execSQL("ALTER TABLE $TABLE_DINING_TABS ADD COLUMN billed_at TEXT NOT NULL DEFAULT '';") }
            runCatching { db.execSQL("ALTER TABLE $TABLE_DINING_TABS ADD COLUMN payment_method TEXT NOT NULL DEFAULT '';") }
            runCatching { db.execSQL("ALTER TABLE $TABLE_DINING_TABS ADD COLUMN payment_transaction_id TEXT NOT NULL DEFAULT '';") }
        }
        if (oldVersion < 9) {
            runCatching { db.execSQL("ALTER TABLE $TABLE_DINING_TABS ADD COLUMN tip INTEGER NOT NULL DEFAULT 0;") }
            runCatching { db.execSQL("ALTER TABLE $TABLE_DINING_TABS ADD COLUMN is_service_charge_opted_out INTEGER NOT NULL DEFAULT 0;") }
        }
        if (oldVersion < 10) {
            createStaffPayrollTable(db)
            createUserCredentialsTable(db)
            createInventoryTables(db)
            createAuditLogsTable(db)
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        // Additive, idempotent migrations (safe on fresh and existing installs; no data loss).
        runCatching {
            db.execSQL("ALTER TABLE $TABLE_BOOKINGS ADD COLUMN updated_at TEXT NOT NULL DEFAULT '';")
        }
        runCatching { db.execSQL("ALTER TABLE $TABLE_ORDERS ADD COLUMN user_id TEXT NOT NULL DEFAULT '';") }
        runCatching { db.execSQL("ALTER TABLE $TABLE_ORDERS ADD COLUMN idempotency_key TEXT;") }
        runCatching {
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_orders_idempotency ON $TABLE_ORDERS (idempotency_key) WHERE idempotency_key IS NOT NULL;")
        }
        runCatching { db.execSQL("CREATE INDEX IF NOT EXISTS idx_orders_user ON $TABLE_ORDERS (user_id);") }
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS auth_sessions (
                token_hash TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                auth_method TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL
            );
            """.trimIndent()
        )
    }

    // -------------------------------------------------------------
    // AUTH SESSIONS (opaque random tokens, stored only as SHA-256 hash)
    // -------------------------------------------------------------

    private fun sha256(value: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    fun createSession(userId: String, authMethod: String, ttlMs: Long): String {
        val bytes = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val token = "rs_" + bytes.joinToString("") { "%02x".format(it) }
        val now = System.currentTimeMillis()
        val cv = ContentValues().apply {
            put("token_hash", sha256(token))
            put("user_id", userId)
            put("auth_method", authMethod)
            put("created_at", now)
            put("expires_at", now + ttlMs)
        }
        writableDatabase.insertOrThrow("auth_sessions", null, cv)
        return token
    }

    /** Returns the user id bound to a non-expired session token, or null. Expired rows are purged. */
    fun resolveSessionUserId(token: String?): String? {
        if (token.isNullOrBlank()) return null
        val db = writableDatabase
        db.delete("auth_sessions", "expires_at < ?", arrayOf(System.currentTimeMillis().toString()))
        db.rawQuery("SELECT user_id FROM auth_sessions WHERE token_hash = ? LIMIT 1", arrayOf(sha256(token))).use { c ->
            return if (c.moveToFirst()) c.getString(0) else null
        }
    }

    fun revokeSession(token: String?) {
        if (token.isNullOrBlank()) return
        writableDatabase.delete("auth_sessions", "token_hash = ?", arrayOf(sha256(token)))
    }

    fun getUserById(userId: String): Profile? = getAllUsers().firstOrNull { it.id == userId }

    fun findOrderByIdempotencyKey(key: String): DeliveryOrder? {
        if (key.isBlank()) return null
        readableDatabase.rawQuery("SELECT id FROM $TABLE_ORDERS WHERE idempotency_key = ? LIMIT 1", arrayOf(key)).use { c ->
            if (!c.moveToFirst()) return null
            val id = c.getString(0)
            return getAllOrders().firstOrNull { it.id == id }
        }
    }

    fun getOrdersForUser(userId: String): List<DeliveryOrder> = getAllOrders().filter { it.userId == userId }

    private fun createTableBookingsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_BOOKINGS (
                id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                user_name TEXT NOT NULL,
                user_phone TEXT NOT NULL,
                table_id TEXT NOT NULL,
                table_number TEXT NOT NULL,
                booking_date TEXT NOT NULL,
                time_slot TEXT NOT NULL,
                party_size INTEGER NOT NULL CHECK (party_size > 0),
                seating_area TEXT NOT NULL DEFAULT 'Main Dining Room',
                special_requests TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL DEFAULT 'CONFIRMED',
                created_at TEXT NOT NULL,
                restaurant_id TEXT NOT NULL DEFAULT '',
                restaurant_name TEXT NOT NULL DEFAULT '',
                updated_at TEXT NOT NULL DEFAULT ''
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_table_bookings_date_slot ON $TABLE_BOOKINGS (booking_date, time_slot);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_table_bookings_table_id ON $TABLE_BOOKINGS (table_id);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_table_bookings_user_id ON $TABLE_BOOKINGS (user_id);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_table_bookings_restaurant ON $TABLE_BOOKINGS (restaurant_id, booking_date, time_slot);")
    }

    private fun createDiningTabsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_DINING_TABS (
                id TEXT PRIMARY KEY,
                table_id TEXT NOT NULL DEFAULT '',
                table_number TEXT NOT NULL,
                booking_id TEXT,
                user_id TEXT,
                user_name TEXT NOT NULL DEFAULT 'Guest Diner',
                customer_id TEXT,
                customer_name TEXT,
                customer_phone TEXT,
                items_json TEXT NOT NULL DEFAULT '[]',
                subtotal INTEGER NOT NULL DEFAULT 0,
                tax INTEGER NOT NULL DEFAULT 0,
                gst INTEGER NOT NULL DEFAULT 0,
                service_charge INTEGER NOT NULL DEFAULT 0,
                tip INTEGER NOT NULL DEFAULT 0,
                is_service_charge_opted_out INTEGER NOT NULL DEFAULT 0,
                total INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'OPEN',
                payment_method TEXT,
                created_at TEXT NOT NULL DEFAULT '',
                updated_at TEXT NOT NULL DEFAULT '',
                restaurant_id TEXT NOT NULL DEFAULT '',
                guest_count INTEGER NOT NULL DEFAULT 0,
                seated_at TEXT NOT NULL DEFAULT '',
                billed_at TEXT NOT NULL DEFAULT '',
                payment_transaction_id TEXT NOT NULL DEFAULT ''
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dining_tabs_table_number ON $TABLE_DINING_TABS (table_number);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dining_tabs_user_id ON $TABLE_DINING_TABS (user_id);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dining_tabs_status ON $TABLE_DINING_TABS (status);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dining_tabs_table_status ON $TABLE_DINING_TABS (table_number, status);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dining_tabs_user_status ON $TABLE_DINING_TABS (user_id, status);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dining_tabs_table ON $TABLE_DINING_TABS (table_number, status);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dining_tabs_booking ON $TABLE_DINING_TABS (booking_id);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dining_tabs_customer ON $TABLE_DINING_TABS (customer_id);")
    }

    private fun createKotTicketsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_KOT_TICKETS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_id INTEGER NOT NULL DEFAULT 0,
                table_number TEXT NOT NULL,
                table_id TEXT NOT NULL,
                round_number INTEGER NOT NULL DEFAULT 1,
                round_name TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL DEFAULT 'New',
                items_json TEXT NOT NULL DEFAULT '[]',
                customer_name TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_kot_tickets_table ON $TABLE_KOT_TICKETS (table_number);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_kot_tickets_status ON $TABLE_KOT_TICKETS (status);")
    }

    // -------------------------------------------------------------
    // RESTAURANTS & DISHES
    // -------------------------------------------------------------

    fun seedSampleDiningTabsIfEmpty() {
        if (getAllDiningTabs().isNotEmpty()) return

        val item1 = TableBillItem(
            dishId = "dish-bread-1",
            name = "Garlic Butter Naan",
            quantity = 2,
            price = 9000L,
            round = 1,
            roundName = "Round 1"
        )
        val item2 = TableBillItem(
            dishId = "dish-main-1",
            name = "Smoked Dal Makhani",
            quantity = 1,
            price = 32000L,
            round = 1,
            roundName = "Round 1"
        )
        val item3 = TableBillItem(
            dishId = "dish-main-2",
            name = "Old Delhi Butter Chicken",
            quantity = 1,
            price = 42000L,
            round = 2,
            roundName = "Round 2"
        )
        val item4 = TableBillItem(
            dishId = "dish-bev-1",
            name = "Fresh Lime Soda",
            quantity = 2,
            price = 9000L,
            round = 2,
            roundName = "Round 2"
        )

        val tab1 = DiningTab(
            id = "tab-sample-1",
            tableId = "T1",
            tableNumber = "T1",
            restaurantId = "rest-1",
            userId = "usr-1",
            userName = "Aarav Sharma",
            customerId = "usr-1",
            customerName = "Aarav Sharma",
            customerPhone = "+91 98765 43210",
            guestCount = 2,
            items = listOf(item1, item2, item3, item4),
            subtotal = 110000L,
            tax = 5500L,
            serviceCharge = 5500L,
            tip = 5500L,
            total = 126500L,
            status = "ACTIVE"
        )
        saveOrUpdateTab(tab1)

        val tab2 = DiningTab(
            id = "tab-sample-2",
            tableId = "T2",
            tableNumber = "T2",
            restaurantId = "rest-1",
            guestCount = 4,
            customerName = "Priya Patel",
            customerPhone = "+91 98220 33445",
            items = listOf(
                TableBillItem(dishId = "dish-starter-1", name = "Paneer Tikka", quantity = 1, price = 32000L, round = 1),
                TableBillItem(dishId = "dish-main-3", name = "Truffle Mushroom Risotto", quantity = 1, price = 45000L, round = 1)
            ),
            subtotal = 77000L,
            tax = 3850L,
            serviceCharge = 3850L,
            tip = 0L,
            total = 84700L,
            status = "ACTIVE"
        )
        saveOrUpdateTab(tab2)

        if (getAllKotTickets().isEmpty()) {
            createKotTicket(
                tableNumber = "T1",
                tableId = "T1",
                items = listOf(
                    com.restaurant.mobile.model.KOTItem(menuItemName = "Garlic Butter Naan", quantity = 2),
                    com.restaurant.mobile.model.KOTItem(menuItemName = "Smoked Dal Makhani", quantity = 1)
                ),
                round = 1,
                roundName = "Round 1",
                customerName = "Aarav Sharma",
                orderId = 101L
            )
            val kot2 = createKotTicket(
                tableNumber = "T1",
                tableId = "T1",
                items = listOf(
                    com.restaurant.mobile.model.KOTItem(menuItemName = "Old Delhi Butter Chicken", quantity = 1),
                    com.restaurant.mobile.model.KOTItem(menuItemName = "Fresh Lime Soda", quantity = 2)
                ),
                round = 2,
                roundName = "Round 2",
                customerName = "Aarav Sharma",
                orderId = 102L
            )
            updateKotTicketStatus(kot2.id, "Preparing")
        }
    }

    fun seedCatalogIfEmpty(catalog: Catalog) {
        val db = writableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_RESTAURANTS", null)
        val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()

        if (count == 0) {
            db.beginTransaction()
            try {
                for (r in catalog.restaurants) {
                    val cv = ContentValues().apply {
                        put("id", r.id)
                        put("name", r.name)
                        put("cuisine", r.cuisine)
                        put("description", r.description)
                        put("image", r.image)
                        put("address", r.address)
                        put("latitude", r.latitude)
                        put("longitude", r.longitude)
                        put("delivery_minutes", r.deliveryMinutes)
                        put("delivery_fee", r.deliveryFee)
                        put("open", if (r.open) 1 else 0)
                    }
                    db.insertWithOnConflict(TABLE_RESTAURANTS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                }

                for (d in catalog.dishes) {
                    val cv = ContentValues().apply {
                        put("id", d.id)
                        put("restaurant_id", d.restaurantId)
                        put("category", d.category)
                        put("name", d.name)
                        put("description", d.description)
                        put("price", d.price)
                        put("image", d.image)
                        put("vegetarian", if (d.vegetarian) 1 else 0)
                        put("available", if (d.available) 1 else 0)
                        put("addons_json", gson.toJson(d.addons))
                    }
                    db.insertWithOnConflict(TABLE_DISHES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    fun getAllRestaurants(): List<Restaurant> {
        val list = mutableListOf<Restaurant>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_RESTAURANTS ORDER BY name ASC", null)
        while (cursor.moveToNext()) {
            list.add(cursorToRestaurant(cursor))
        }
        cursor.close()
        return list
    }

    fun getRestaurantById(id: String): Restaurant? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_RESTAURANTS WHERE id = ?", arrayOf(id))
        val res = if (cursor.moveToFirst()) cursorToRestaurant(cursor) else null
        cursor.close()
        return res
    }

    fun updateRestaurantLocation(id: String, address: String, lat: Double, lng: Double, deliveryMins: Int): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("address", address)
            put("latitude", lat)
            put("longitude", lng)
            put("delivery_minutes", deliveryMins)
        }
        return db.update(TABLE_RESTAURANTS, cv, "id = ?", arrayOf(id)) > 0
    }

    private fun createRestaurantSettingsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS restaurant_settings (
                restaurant_id TEXT PRIMARY KEY,
                open INTEGER NOT NULL DEFAULT 1,
                prep_minutes INTEGER NOT NULL DEFAULT 22,
                accepts_dining_payment INTEGER NOT NULL DEFAULT 1,
                updated_at TEXT NOT NULL
            );
            """.trimIndent()
        )
    }

    fun saveRestaurantSettings(
        restaurantId: String,
        open: Boolean,
        prepMinutes: Int,
        acceptsDiningPayment: Boolean
    ): Boolean {
        val db = writableDatabase
        createRestaurantSettingsTable(db)
        val clampedPrep = prepMinutes.coerceIn(20, 25)
        val cv = ContentValues().apply {
            put("restaurant_id", restaurantId)
            put("open", if (open) 1 else 0)
            put("prep_minutes", clampedPrep)
            put("accepts_dining_payment", if (acceptsDiningPayment) 1 else 0)
            put("updated_at", Instant.now().toString())
        }
        db.insertWithOnConflict("restaurant_settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)

        val restCv = ContentValues().apply {
            put("open", if (open) 1 else 0)
        }
        db.update(TABLE_RESTAURANTS, restCv, "id = ?", arrayOf(restaurantId))

        syncToFirebaseFirestore(
            "restaurant_settings",
            restaurantId,
            mapOf(
                "restaurantId" to restaurantId,
                "open" to open,
                "prepMinutes" to clampedPrep,
                "acceptsDiningPayment" to acceptsDiningPayment,
                "updatedAt" to Instant.now().toString()
            )
        )
        return true
    }

    fun updateRestaurantLogo(restaurantId: String, logoUri: String): Boolean {
        if (logoUri.isBlank()) return false
        firebasePrefs.edit().putString("rest_logo_$restaurantId", logoUri).apply()
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("image", logoUri)
        }
        db.update(TABLE_RESTAURANTS, cv, "id = ?", arrayOf(restaurantId))
        syncToFirebaseFirestore(
            "restaurants",
            restaurantId,
            mapOf("id" to restaurantId, "logoUrl" to logoUri, "image" to logoUri, "updatedAt" to Instant.now().toString())
        )
        return true
    }

    fun getRestaurantLogo(restaurantId: String): String {
        return firebasePrefs.getString("rest_logo_$restaurantId", "") ?: ""
    }

    fun getRestaurantSettings(restaurantId: String): Triple<Boolean, Int, Boolean>? {
        return try {
            val db = readableDatabase
            createRestaurantSettingsTable(db)
            val cursor = db.rawQuery(
                "SELECT open, prep_minutes, accepts_dining_payment FROM restaurant_settings WHERE restaurant_id = ? LIMIT 1",
                arrayOf(restaurantId)
            )
            val res = if (cursor.moveToFirst()) {
                Triple(
                    cursor.getInt(0) == 1,
                    cursor.getInt(1).coerceIn(20, 25),
                    cursor.getInt(2) == 1
                )
            } else null
            cursor.close()
            res
        } catch (_: Exception) {
            null
        }
    }

    fun upsertDish(dish: Dish): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id", dish.id)
            put("restaurant_id", dish.restaurantId)
            put("category", dish.category)
            put("name", dish.name)
            put("description", dish.description)
            put("price", dish.price)
            put("image", dish.image)
            put("vegetarian", if (dish.vegetarian) 1 else 0)
            put("available", if (dish.available) 1 else 0)
            put("addons_json", gson.toJson(dish.addons))
        }
        val ok = db.insertWithOnConflict(TABLE_DISHES, null, cv, SQLiteDatabase.CONFLICT_REPLACE) != -1L
        syncToFirebaseFirestore(
            "dishes",
            dish.id,
            mapOf(
                "id" to dish.id,
                "restaurantId" to dish.restaurantId,
                "category" to dish.category,
                "name" to dish.name,
                "description" to dish.description,
                "price" to dish.price,
                "image" to dish.image,
                "vegetarian" to dish.vegetarian,
                "available" to dish.available,
                "addons" to gson.toJson(dish.addons),
                "updatedAt" to Instant.now().toString()
            )
        )
        return ok
    }

    fun deleteDish(dishId: String): Boolean {
        val db = writableDatabase
        val ok = db.delete(TABLE_DISHES, "id = ?", arrayOf(dishId)) > 0
        deleteFromFirebaseFirestore("dishes", dishId)
        return ok
    }

    fun getAllDishes(): List<Dish> {
        val list = mutableListOf<Dish>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_DISHES", null)
        while (cursor.moveToNext()) {
            list.add(cursorToDish(cursor))
        }
        cursor.close()
        return list
    }

    fun getDishesForRestaurant(restaurantId: String): List<Dish> {
        val list = mutableListOf<Dish>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_DISHES WHERE restaurant_id = ?", arrayOf(restaurantId))
        while (cursor.moveToNext()) {
            list.add(cursorToDish(cursor))
        }
        cursor.close()
        return list
    }

    fun getDishById(id: String): Dish? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_DISHES WHERE id = ?", arrayOf(id))
        val res = if (cursor.moveToFirst()) cursorToDish(cursor) else null
        cursor.close()
        return res
    }

    private fun cursorToRestaurant(c: Cursor): Restaurant {
        val id = c.getString(c.getColumnIndexOrThrow("id"))
        val template = MockCatalog.sample.restaurants.find { it.id == id }
        val savedSettings = getRestaurantSettings(id)
        val openState = savedSettings?.first ?: (c.getInt(c.getColumnIndexOrThrow("open")) == 1)
        val prepMins = savedSettings?.second ?: (template?.prepMinutes ?: 22)
        val acceptsDiningPay = savedSettings?.third ?: (template?.acceptsDiningPayment ?: true)
        val savedLogo = getRestaurantLogo(id)
        val dbImage = c.getString(c.getColumnIndexOrThrow("image"))
        return Restaurant(
            id = id,
            name = c.getString(c.getColumnIndexOrThrow("name")),
            cuisine = c.getString(c.getColumnIndexOrThrow("cuisine")),
            description = c.getString(c.getColumnIndexOrThrow("description")),
            image = savedLogo.ifBlank { dbImage },
            address = c.getString(c.getColumnIndexOrThrow("address")),
            latitude = c.getDouble(c.getColumnIndexOrThrow("latitude")),
            longitude = c.getDouble(c.getColumnIndexOrThrow("longitude")),
            deliveryMinutes = c.getInt(c.getColumnIndexOrThrow("delivery_minutes")),
            deliveryFee = c.getLong(c.getColumnIndexOrThrow("delivery_fee")),
            open = openState,
            rating = template?.rating ?: 4.5,
            reviewCount = template?.reviewCount ?: 250,
            costForTwo = template?.costForTwo ?: 800,
            vibes = template?.vibes ?: emptyList(),
            amenities = template?.amenities ?: emptyList(),
            tableBookingAvailable = template?.tableBookingAvailable ?: true,
            maxPartySize = template?.maxPartySize ?: 12,
            prepMinutes = prepMins,
            acceptsDiningPayment = acceptsDiningPay,
            logoUrl = savedLogo.ifBlank { dbImage }
        )
    }

    private fun cursorToDish(c: Cursor): Dish {
        val addonsJson = c.getString(c.getColumnIndexOrThrow("addons_json"))
        val addonsList: List<AddOn> = if (!addonsJson.isNullOrBlank()) {
            runCatching {
                gson.fromJson<List<AddOn>>(addonsJson, object : TypeToken<List<AddOn>>() {}.type)
            }.getOrDefault(emptyList())
        } else emptyList()

        return Dish(
            id = c.getString(c.getColumnIndexOrThrow("id")),
            restaurantId = c.getString(c.getColumnIndexOrThrow("restaurant_id")),
            category = c.getString(c.getColumnIndexOrThrow("category")),
            name = c.getString(c.getColumnIndexOrThrow("name")),
            description = c.getString(c.getColumnIndexOrThrow("description")),
            price = c.getLong(c.getColumnIndexOrThrow("price")),
            image = c.getString(c.getColumnIndexOrThrow("image")),
            vegetarian = c.getInt(c.getColumnIndexOrThrow("vegetarian")) == 1,
            available = c.getInt(c.getColumnIndexOrThrow("available")) == 1,
            addons = addonsList
        )
    }

    // -------------------------------------------------------------
    // USERS & PROFILE
    // -------------------------------------------------------------

    fun seedDefaultStaffAccounts() {
        if (!com.restaurant.mobile.BuildConfig.DEBUG) return
        val defaultStaff = listOf(
            Pair(Profile("staff-waiter-1", "Rohan Verma (Waiter)", "waiter@restro.app", "+91 90000 11111", role = UserRole.WAITER), "Waiter@123"),
            Pair(Profile("staff-chef-1", "Chef Marco (Kitchen)", "chef@restro.app", "+91 90000 22222", role = UserRole.CHEF_KDS), "Chef@123"),
            Pair(Profile("staff-admin-1", "Ananya Rao (Manager)", "admin@restro.app", "+91 90000 99999", role = UserRole.ADMIN), "Admin@123"),
            Pair(Profile("staff-master-1", "Master Administrator", "master@restro.app", "+91 90000 00000", role = UserRole.MASTER_ADMIN), "Master@123"),
            Pair(Profile("staff-rider-1", "Ramesh Rider (Delivery Partner)", "rider@restro.app", "+91 98765 12345", role = UserRole.RIDER), "Rider@123"),
            Pair(Profile("staff-kiosk-1", "Self-Order Kiosk Terminal", "kiosk@restro.app", "+91 90000 77777", role = UserRole.KIOSK), "Kiosk@123")
        )
        defaultStaff.forEach { (staff, defaultPass) ->
            saveUser(staff)
            saveUserPasswordIfMissing(staff.email, defaultPass)
            saveUserPasswordIfMissing(staff.phone, defaultPass)
        }
        saveUserPasswordIfMissing("aarav@example.com", "password123")
        saveUserPasswordIfMissing("+91 98765 43210", "password123")
    }

    private fun hashPin(pin: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("restro_pin_salt_$pin".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun seedDefaultStaffPins() {
        if (!com.restaurant.mobile.BuildConfig.DEBUG) return
        val db = writableDatabase
        val defaults = listOf(
            Triple(UserRole.WAITER, "1234", "Waiter / POS Vault"),
            Triple(UserRole.CHEF_KDS, "5678", "Kitchen KDS Vault"),
            Triple(UserRole.RIDER, "4321", "Delivery Rider Vault"),
            Triple(UserRole.ADMIN, "9999", "Admin Manager Vault"),
            Triple(UserRole.MASTER_ADMIN, "0000", "Master Admin Vault"),
            Triple(UserRole.KIOSK, "7777", "Kiosk Terminal Vault")
        )
        defaults.forEach { (role, rawPin, label) ->
            val cv = ContentValues().apply {
                put("role", role.name)
                put("pin_hash", hashPin(rawPin))
                put("label", label)
                put("updated_at", Instant.now().toString())
            }
            db.insertWithOnConflict(TABLE_STAFF_PINS, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun verifyStaffPin(pin: String): UserRole? {
        val clean = pin.trim()
        if (clean.length != 4) return null
        val hashed = hashPin(clean)
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT role FROM $TABLE_STAFF_PINS WHERE pin_hash = ? LIMIT 1",
            arrayOf(hashed)
        )
        val matchedRole = if (cursor.moveToFirst()) {
            UserRole.fromString(cursor.getString(0))
        } else null
        cursor.close()
        return matchedRole
    }

    fun createStaffPayrollTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS staff_payroll (
                id TEXT PRIMARY KEY,
                staff_id TEXT NOT NULL,
                staff_name TEXT NOT NULL,
                staff_role TEXT NOT NULL,
                month_year TEXT NOT NULL,
                base_salary INTEGER NOT NULL,
                bonus_tips INTEGER NOT NULL DEFAULT 0,
                deductions INTEGER NOT NULL DEFAULT 0,
                net_salary INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'PENDING',
                paid_at TEXT,
                payment_method TEXT NOT NULL DEFAULT 'UPI / Bank Transfer',
                transaction_ref TEXT NOT NULL DEFAULT ''
            );
            """.trimIndent()
        )
    }

    fun createUserCredentialsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS user_credentials (
                user_target TEXT PRIMARY KEY,
                password_hash TEXT NOT NULL,
                temp_password TEXT,
                updated_at TEXT NOT NULL
            );
            """.trimIndent()
        )
    }

    fun seedDefaultPayrollRecords() {
        val db = writableDatabase
        createStaffPayrollTable(db)
        val samplePayroll = listOf(
            StaffPayrollRecord("pay-1", "staff-waiter-1", "Rohan Verma", "Waiter", "October 2026", 18000L, 2500L, 500L, 20000L, "PENDING"),
            StaffPayrollRecord("pay-2", "staff-chef-1", "Chef Marco", "Kitchen Head Chef", "October 2026", 35000L, 4000L, 0L, 39000L, "PENDING"),
            StaffPayrollRecord("pay-3", "staff-admin-1", "Ananya Rao", "Restaurant Manager", "October 2026", 45000L, 5000L, 1000L, 49000L, "PENDING"),
            StaffPayrollRecord("pay-4", "staff-rider-1", "Ramesh Kumar", "Delivery Partner", "October 2026", 18000L, 3200L, 200L, 21000L, "PENDING")
        )
        samplePayroll.forEach { p ->
            val cv = ContentValues().apply {
                put("id", p.id)
                put("staff_id", p.staffId)
                put("staff_name", p.staffName)
                put("staff_role", p.staffRole)
                put("month_year", p.monthYear)
                put("base_salary", p.baseSalary)
                put("bonus_tips", p.bonusTips)
                put("deductions", p.deductions)
                put("net_salary", p.netSalary)
                put("status", p.status)
                put("paid_at", p.paidAt)
                put("payment_method", p.paymentMethod)
                put("transaction_ref", p.transactionRef)
            }
            db.insertWithOnConflict("staff_payroll", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun getAllPayrollRecords(): List<StaffPayrollRecord> {
        val db = readableDatabase
        createStaffPayrollTable(db)
        val list = mutableListOf<StaffPayrollRecord>()
        val cursor = db.rawQuery("SELECT * FROM staff_payroll ORDER BY id ASC", null)
        while (cursor.moveToNext()) {
            list.add(
                StaffPayrollRecord(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    staffId = cursor.getString(cursor.getColumnIndexOrThrow("staff_id")),
                    staffName = cursor.getString(cursor.getColumnIndexOrThrow("staff_name")),
                    staffRole = cursor.getString(cursor.getColumnIndexOrThrow("staff_role")),
                    monthYear = cursor.getString(cursor.getColumnIndexOrThrow("month_year")),
                    baseSalary = cursor.getLong(cursor.getColumnIndexOrThrow("base_salary")),
                    bonusTips = cursor.getLong(cursor.getColumnIndexOrThrow("bonus_tips")),
                    deductions = cursor.getLong(cursor.getColumnIndexOrThrow("deductions")),
                    netSalary = cursor.getLong(cursor.getColumnIndexOrThrow("net_salary")),
                    status = cursor.getString(cursor.getColumnIndexOrThrow("status")),
                    paidAt = if (cursor.isNull(cursor.getColumnIndexOrThrow("paid_at"))) null else cursor.getString(cursor.getColumnIndexOrThrow("paid_at")),
                    paymentMethod = cursor.getString(cursor.getColumnIndexOrThrow("payment_method")),
                    transactionRef = cursor.getString(cursor.getColumnIndexOrThrow("transaction_ref"))
                )
            )
        }
        cursor.close()
        if (list.isEmpty()) {
            seedDefaultPayrollRecords()
            return getAllPayrollRecords()
        }
        return list
    }

    fun disburseSalary(payrollId: String, method: String, txRef: String): Boolean {
        val db = writableDatabase
        createStaffPayrollTable(db)
        val cv = ContentValues().apply {
            put("status", "PAID")
            put("paid_at", Instant.now().toString())
            put("payment_method", method)
            put("transaction_ref", txRef)
        }
        val affected = db.update("staff_payroll", cv, "id = ?", arrayOf(payrollId))
        return affected > 0
    }

    fun insertStaffPayrollRecord(record: StaffPayrollRecord): Boolean {
        val db = writableDatabase
        createStaffPayrollTable(db)
        val cv = ContentValues().apply {
            put("id", record.id)
            put("staff_id", record.staffId)
            put("staff_name", record.staffName)
            put("staff_role", record.staffRole)
            put("month_year", record.monthYear)
            put("base_salary", record.baseSalary)
            put("bonus_tips", record.bonusTips)
            put("deductions", record.deductions)
            put("net_salary", record.netSalary)
            put("status", record.status)
        }
        val res = db.insertWithOnConflict("staff_payroll", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        return res != -1L
    }

    private fun saveUserPasswordIfMissing(target: String, rawPassword: String) {
        val db = writableDatabase
        createUserCredentialsTable(db)
        val cv = ContentValues().apply {
            put("user_target", target.trim().lowercase())
            put("password_hash", hashPin(rawPassword.trim()))
            put("temp_password", "")
            put("updated_at", Instant.now().toString())
        }
        db.insertWithOnConflict("user_credentials", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun saveUserPassword(target: String, rawPassword: String) {
        val db = writableDatabase
        createUserCredentialsTable(db)
        val clean = target.trim().lowercase()
        val cv = ContentValues().apply {
            put("user_target", clean)
            put("password_hash", hashPin(rawPassword.trim()))
            put("temp_password", "")
            put("updated_at", Instant.now().toString())
        }
        db.insertWithOnConflict("user_credentials", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        val profile = getUserByTarget(target)
        if (profile != null) {
            listOf(profile.email.trim().lowercase(), profile.phone.trim().lowercase()).forEach { alias ->
                if (alias.isNotBlank() && alias != clean) {
                    val aliasCv = ContentValues(cv).apply { put("user_target", alias) }
                    db.insertWithOnConflict("user_credentials", null, aliasCv, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }
        }
    }

    fun verifyUserPassword(target: String, rawPassword: String): Boolean {
        val clean = target.trim().lowercase()
        val pass = rawPassword.trim()
        if (clean.isBlank() || pass.isBlank()) return false
        val db = readableDatabase
        createUserCredentialsTable(db)

        val candidates = mutableSetOf(clean)
        val profile = getUserByTarget(target)
        if (profile != null) {
            if (profile.email.isNotBlank()) candidates.add(profile.email.trim().lowercase())
            if (profile.phone.isNotBlank()) candidates.add(profile.phone.trim().lowercase())
        }

        val inputHash = hashPin(pass)
        for (candidate in candidates) {
            val cursor = db.rawQuery(
                "SELECT password_hash, temp_password FROM user_credentials WHERE user_target = ? LIMIT 1",
                arrayOf(candidate)
            )
            val matched = if (cursor.moveToFirst()) {
                val hash = cursor.getString(0)
                val temp = cursor.getString(1)
                inputHash == hash || (temp != null && temp.isNotBlank() && temp == pass)
            } else {
                false
            }
            cursor.close()
            if (matched) return true
        }
        return false
    }

    fun generateAndSetTemporaryPassword(target: String): String {
        val clean = target.trim().lowercase()
        val tempPass = "Restro#" + (1000..9999).random().toString()
        val db = writableDatabase
        createUserCredentialsTable(db)
        val candidates = mutableSetOf(clean)
        val profile = getUserByTarget(target)
        if (profile != null) {
            if (profile.email.isNotBlank()) candidates.add(profile.email.trim().lowercase())
            if (profile.phone.isNotBlank()) candidates.add(profile.phone.trim().lowercase())
        }
        val nowStr = Instant.now().toString()
        for (candidate in candidates) {
            val cv = ContentValues().apply {
                put("user_target", candidate)
                put("password_hash", hashPin(tempPass))
                put("temp_password", tempPass)
                put("updated_at", nowStr)
            }
            db.insertWithOnConflict("user_credentials", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
        return tempPass
    }

    fun updateUserPassword(target: String, newRawPassword: String): Boolean {
        val clean = target.trim().lowercase()
        val db = writableDatabase
        createUserCredentialsTable(db)
        val candidates = mutableSetOf(clean)
        val profile = getUserByTarget(target)
        if (profile != null) {
            if (profile.email.isNotBlank()) candidates.add(profile.email.trim().lowercase())
            if (profile.phone.isNotBlank()) candidates.add(profile.phone.trim().lowercase())
        }
        val nowStr = Instant.now().toString()
        var lastCount = -1L
        for (candidate in candidates) {
            val cv = ContentValues().apply {
                put("user_target", candidate)
                put("password_hash", hashPin(newRawPassword.trim()))
                put("temp_password", "")
                put("updated_at", nowStr)
            }
            lastCount = db.insertWithOnConflict("user_credentials", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
        return lastCount >= 0
    }

    fun saveUser(profile: Profile) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id", profile.id)
            put("name", profile.name)
            put("email", profile.email)
            put("phone", profile.phone)
            put("role", profile.role.name)
            put("created_at", Instant.now().toString())
        }
        db.insertWithOnConflict(TABLE_USERS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAllUsers(): List<Profile> {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_USERS ORDER BY created_at DESC", null)
        val list = mutableListOf<Profile>()
        while (cursor.moveToNext()) {
            val roleIdx = cursor.getColumnIndex("role")
            val roleStr = if (roleIdx >= 0 && !cursor.isNull(roleIdx)) cursor.getString(roleIdx) else "CUSTOMER"
            list.add(
                Profile(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    email = cursor.getString(cursor.getColumnIndexOrThrow("email")),
                    phone = cursor.getString(cursor.getColumnIndexOrThrow("phone")),
                    role = UserRole.fromString(roleStr)
                )
            )
        }
        cursor.close()
        return list
    }

    fun getUserByTarget(target: String): Profile? {
        val clean = target.trim()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_USERS WHERE LOWER(email) = LOWER(?) OR phone = ? LIMIT 1",
            arrayOf(clean, clean)
        )
        val profile = if (cursor.moveToFirst()) {
            val roleIdx = cursor.getColumnIndex("role")
            val roleStr = if (roleIdx >= 0 && !cursor.isNull(roleIdx)) cursor.getString(roleIdx) else "CUSTOMER"
            Profile(
                id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                email = cursor.getString(cursor.getColumnIndexOrThrow("email")),
                phone = cursor.getString(cursor.getColumnIndexOrThrow("phone")),
                role = UserRole.fromString(roleStr)
            )
        } else null
        cursor.close()
        return profile
    }

    // -------------------------------------------------------------
    // OTP SYSTEM
    // -------------------------------------------------------------

    fun storeOtp(target: String, code: String, expiryMs: Long) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("target", target.trim())
            put("code", code.trim())
            put("expires_at", System.currentTimeMillis() + expiryMs)
            put("attempts", 0)
        }
        db.insertWithOnConflict(TABLE_OTPS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun verifyOtp(target: String, code: String): Pair<Boolean, String?> {
        val cleanTarget = target.trim()
        val cleanCode = code.trim()
        val db = writableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_OTPS WHERE target = ?", arrayOf(cleanTarget))
        if (!cursor.moveToFirst()) {
            cursor.close()
            return Pair(false, "No active OTP request found for this account")
        }

        val storedCode = cursor.getString(cursor.getColumnIndexOrThrow("code"))
        val expiresAt = cursor.getLong(cursor.getColumnIndexOrThrow("expires_at"))
        val attempts = cursor.getInt(cursor.getColumnIndexOrThrow("attempts"))
        cursor.close()

        if (System.currentTimeMillis() > expiresAt) {
            db.delete(TABLE_OTPS, "target = ?", arrayOf(cleanTarget))
            return Pair(false, "OTP has expired. Please request a new one.")
        }

        if (attempts >= 4) {
            db.delete(TABLE_OTPS, "target = ?", arrayOf(cleanTarget))
            return Pair(false, "Too many failed attempts. Request a new OTP.")
        }

        if (storedCode != cleanCode) {
            db.execSQL("UPDATE $TABLE_OTPS SET attempts = attempts + 1 WHERE target = ?", arrayOf(cleanTarget))
            return Pair(false, "Invalid verification code.")
        }

        // Verified successfully! Clean up record
        db.delete(TABLE_OTPS, "target = ?", arrayOf(cleanTarget))
        return Pair(true, null)
    }

    // -------------------------------------------------------------
    // ORDERS
    // -------------------------------------------------------------

    fun saveOrder(order: DeliveryOrder) {
        val db = writableDatabase
        val cv = orderContentValues(order)
        db.insertWithOnConflict(TABLE_ORDERS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /**
     * Inserts a brand-new order exactly once. Fails (returns false) if the id or the idempotency key
     * already exists, so a retry can never overwrite or duplicate an existing order.
     */
    fun insertOrderOnce(order: DeliveryOrder): Boolean {
        val rowId = writableDatabase.insertWithOnConflict(TABLE_ORDERS, null, orderContentValues(order), SQLiteDatabase.CONFLICT_IGNORE)
        return rowId != -1L
    }

    private fun orderContentValues(order: DeliveryOrder) = ContentValues().apply {
        put("id", order.id)
        put("restaurant_id", order.restaurantId)
        put("restaurant_name", order.restaurantName)
        put("address_json", gson.toJson(order.address))
        put("quote_json", gson.toJson(order.quote))
        put("payment_method", order.paymentMethod)
        put("payment_status", order.paymentStatus)
        put("status", order.status)
        put("events_json", gson.toJson(order.events))
        put("created_at", Instant.now().toString())
        put("user_id", order.userId)
        if (order.idempotencyKey.isNullOrBlank()) putNull("idempotency_key") else put("idempotency_key", order.idempotencyKey)
    }

    fun updateOrderStatus(orderId: String, newStatus: String): Boolean {
        val db = writableDatabase
        val current = db.rawQuery("SELECT status FROM $TABLE_ORDERS WHERE id = ?", arrayOf(orderId.trim())).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: return false
        // Enforce the delivery lifecycle: never move backwards or out of a terminal state.
        if (!DeliveryOrderStatus.canTransition(current, newStatus)) return false
        val cv = ContentValues().apply {
            put("status", newStatus)
        }
        val affected = db.update(TABLE_ORDERS, cv, "id = ? AND status = ?", arrayOf(orderId.trim(), current))
        return affected > 0
    }

    fun getAllOrders(): List<DeliveryOrder> {
        val list = mutableListOf<DeliveryOrder>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_ORDERS ORDER BY rowid DESC", null)
        while (cursor.moveToNext()) {
            try {
                val address = gson.fromJson(
                    cursor.getString(cursor.getColumnIndexOrThrow("address_json")),
                    Address::class.java
                )
                val quote = gson.fromJson(
                    cursor.getString(cursor.getColumnIndexOrThrow("quote_json")),
                    Quote::class.java
                )
                val eventsType = object : TypeToken<List<Event>>() {}.type
                val events: List<Event> = gson.fromJson(
                    cursor.getString(cursor.getColumnIndexOrThrow("events_json")),
                    eventsType
                )

                list.add(
                    DeliveryOrder(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        restaurantId = cursor.getString(cursor.getColumnIndexOrThrow("restaurant_id")),
                        restaurantName = cursor.getString(cursor.getColumnIndexOrThrow("restaurant_name")),
                        address = address,
                        quote = quote,
                        paymentMethod = cursor.getString(cursor.getColumnIndexOrThrow("payment_method")),
                        paymentStatus = cursor.getString(cursor.getColumnIndexOrThrow("payment_status")),
                        status = cursor.getString(cursor.getColumnIndexOrThrow("status")),
                        events = events,
                        userId = cursor.getColumnIndex("user_id").let { i -> if (i >= 0 && !cursor.isNull(i)) cursor.getString(i) else "" },
                        idempotencyKey = cursor.getColumnIndex("idempotency_key").let { i -> if (i >= 0 && !cursor.isNull(i)) cursor.getString(i) else null }
                    )
                )
            } catch (_: Exception) {}
        }
        cursor.close()
        return list
    }

    // -------------------------------------------------------------
    // ADDRESSES
    // -------------------------------------------------------------

    fun saveAddress(address: Address) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id", address.id)
            put("label", address.label)
            put("line", address.line)
            put("city", address.city)
            put("postcode", address.postcode)
            put("phone", address.phone)
            put("latitude", address.latitude)
            put("longitude", address.longitude)
        }
        db.insertWithOnConflict(TABLE_ADDRESSES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAllAddresses(): List<Address> {
        val list = mutableListOf<Address>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_ADDRESSES", null)
        while (cursor.moveToNext()) {
            list.add(
                Address(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    label = cursor.getString(cursor.getColumnIndexOrThrow("label")),
                    line = cursor.getString(cursor.getColumnIndexOrThrow("line")),
                    city = cursor.getString(cursor.getColumnIndexOrThrow("city")),
                    postcode = cursor.getString(cursor.getColumnIndexOrThrow("postcode")),
                    phone = cursor.getString(cursor.getColumnIndexOrThrow("phone")),
                    latitude = if (cursor.isNull(cursor.getColumnIndexOrThrow("latitude"))) null else cursor.getDouble(cursor.getColumnIndexOrThrow("latitude")),
                    longitude = if (cursor.isNull(cursor.getColumnIndexOrThrow("longitude"))) null else cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"))
                )
            )
        }
        cursor.close()
        return list
    }

    // -------------------------------------------------------------
    fun deleteAddress(id: String): Boolean {
        val db = writableDatabase
        return db.delete(TABLE_ADDRESSES, "id = ?", arrayOf(id)) > 0
    }

    fun saveReview(review: Review): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("order_id", review.orderId)
            put("restaurant_id", review.restaurantId)
            put("author", review.author)
            put("rating", review.rating)
            put("text", review.text)
            put("at", review.at)
        }
        val id = db.insert(TABLE_REVIEWS, null, cv)
        return id != -1L
    }

    fun getReviewsForRestaurant(restaurantId: String): List<Review> {
        val list = mutableListOf<Review>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_REVIEWS WHERE restaurant_id = ? ORDER BY id DESC", arrayOf(restaurantId))
        while (cursor.moveToNext()) {
            list.add(
                Review(
                    orderId = cursor.getString(cursor.getColumnIndexOrThrow("order_id")),
                    restaurantId = cursor.getString(cursor.getColumnIndexOrThrow("restaurant_id")),
                    author = cursor.getString(cursor.getColumnIndexOrThrow("author")),
                    rating = cursor.getInt(cursor.getColumnIndexOrThrow("rating")),
                    text = cursor.getString(cursor.getColumnIndexOrThrow("text")),
                    at = cursor.getString(cursor.getColumnIndexOrThrow("at"))
                )
            )
        }
        cursor.close()
        return list
    }

    // TABLE BOOKINGS PERSISTENCE
    // -------------------------------------------------------------

    fun insertTableBooking(booking: TableBooking): Boolean {
        if (!TableBookingStatus.ALL.contains(booking.status) || booking.partySize <= 0) {
            return false
        }
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id", booking.id)
            put("user_id", booking.userId)
            put("user_name", booking.userName)
            put("user_phone", booking.userPhone)
            put("table_id", booking.tableId)
            put("table_number", booking.tableNumber)
            put("booking_date", booking.bookingDate)
            put("time_slot", booking.timeSlot)
            put("party_size", booking.partySize)
            put("seating_area", booking.seatingArea)
            put("special_requests", booking.specialRequests)
            put("status", booking.status)
            put("created_at", booking.createdAt)
            put("restaurant_id", booking.restaurantId)
            put("restaurant_name", booking.restaurantName)
            put("updated_at", booking.updatedAt)
        }
        val rowId = db.insertWithOnConflict(TABLE_BOOKINGS, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        return rowId != -1L
    }

    fun saveTableBooking(booking: TableBooking): Boolean {
        if (!TableBookingStatus.ALL.contains(booking.status) || booking.partySize <= 0) {
            return false
        }
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id", booking.id)
            put("user_id", booking.userId)
            put("user_name", booking.userName)
            put("user_phone", booking.userPhone)
            put("table_id", booking.tableId)
            put("table_number", booking.tableNumber)
            put("booking_date", booking.bookingDate)
            put("time_slot", booking.timeSlot)
            put("party_size", booking.partySize)
            put("seating_area", booking.seatingArea)
            put("special_requests", booking.specialRequests)
            put("status", booking.status)
            put("created_at", booking.createdAt)
            put("restaurant_id", booking.restaurantId)
            put("restaurant_name", booking.restaurantName)
            put("updated_at", booking.updatedAt)
        }
        val rowId = db.insertWithOnConflict(TABLE_BOOKINGS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        return rowId != -1L
    }

    fun saveTableBookingsBatch(bookings: List<TableBooking>): Boolean {
        if (bookings.isEmpty()) return true
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (booking in bookings) {
                if (!TableBookingStatus.ALL.contains(booking.status) || booking.partySize <= 0) {
                    throw IllegalArgumentException("Invalid booking status or non-positive party size: ${booking.id}")
                }
                val cv = ContentValues().apply {
                    put("id", booking.id)
                    put("user_id", booking.userId)
                    put("user_name", booking.userName)
                    put("user_phone", booking.userPhone)
                    put("table_id", booking.tableId)
                    put("table_number", booking.tableNumber)
                    put("booking_date", booking.bookingDate)
                    put("time_slot", booking.timeSlot)
                    put("party_size", booking.partySize)
                    put("seating_area", booking.seatingArea)
                    put("special_requests", booking.specialRequests)
                    put("status", booking.status)
                    put("created_at", booking.createdAt)
                    put("restaurant_id", booking.restaurantId)
                    put("restaurant_name", booking.restaurantName)
                }
                val rowId = db.insertWithOnConflict(TABLE_BOOKINGS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                if (rowId == -1L) {
                    throw IllegalStateException("Failed to insert or replace booking ${booking.id}")
                }
            }
            db.setTransactionSuccessful()
            return true
        } catch (_: Exception) {
            return false
        } finally {
            db.endTransaction()
        }
    }

    fun getTableBookingById(id: String): TableBooking? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_BOOKINGS WHERE id = ? LIMIT 1",
            arrayOf(id)
        )
        return try {
            if (cursor.moveToFirst()) cursorToTableBooking(cursor) else null
        } finally {
            cursor.close()
        }
    }

    fun getTableBookingsForUser(userId: String): List<TableBooking> {
        val list = mutableListOf<TableBooking>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_BOOKINGS WHERE user_id = ? ORDER BY booking_date DESC, time_slot DESC",
            arrayOf(userId)
        )
        try {
            while (cursor.moveToNext()) {
                list.add(cursorToTableBooking(cursor))
            }
        } finally {
            cursor.close()
        }
        return list
    }

    fun getTableBookingsForSlot(date: String, timeSlot: String): List<TableBooking> {
        val list = mutableListOf<TableBooking>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_BOOKINGS WHERE booking_date = ? AND time_slot = ? ORDER BY table_number ASC",
            arrayOf(date, timeSlot)
        )
        try {
            while (cursor.moveToNext()) {
                list.add(cursorToTableBooking(cursor))
            }
        } finally {
            cursor.close()
        }
        return list
    }

    fun getTableBookingsForDate(date: String): List<TableBooking> {
        val list = mutableListOf<TableBooking>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_BOOKINGS WHERE booking_date = ? ORDER BY time_slot ASC, table_number ASC",
            arrayOf(date)
        )
        try {
            while (cursor.moveToNext()) {
                list.add(cursorToTableBooking(cursor))
            }
        } finally {
            cursor.close()
        }
        return list
    }

    fun getTableBookingsForTable(tableId: String): List<TableBooking> {
        val list = mutableListOf<TableBooking>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_BOOKINGS WHERE table_id = ? ORDER BY booking_date DESC, time_slot DESC",
            arrayOf(tableId)
        )
        try {
            while (cursor.moveToNext()) {
                list.add(cursorToTableBooking(cursor))
            }
        } finally {
            cursor.close()
        }
        return list
    }

    fun getTableBookingsForTableAndDate(tableId: String, date: String): List<TableBooking> {
        val list = mutableListOf<TableBooking>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_BOOKINGS WHERE table_id = ? AND booking_date = ? ORDER BY time_slot ASC",
            arrayOf(tableId, date)
        )
        try {
            while (cursor.moveToNext()) {
                list.add(cursorToTableBooking(cursor))
            }
        } finally {
            cursor.close()
        }
        return list
    }

    fun getAllTableBookings(): List<TableBooking> {
        val list = mutableListOf<TableBooking>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_BOOKINGS ORDER BY booking_date DESC, time_slot DESC",
            null
        )
        try {
            while (cursor.moveToNext()) {
                list.add(cursorToTableBooking(cursor))
            }
        } finally {
            cursor.close()
        }
        return list
    }

    fun updateTableBookingStatus(id: String, newStatus: String): Boolean {
        if (!TableBookingStatus.ALL.contains(newStatus)) {
            return false
        }
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("status", newStatus)
        }
        val affected = db.update(TABLE_BOOKINGS, cv, "id = ?", arrayOf(id))
        return affected > 0
    }

    fun updateTableBooking(booking: TableBooking): Boolean {
        if (!TableBookingStatus.ALL.contains(booking.status) || booking.partySize <= 0) {
            return false
        }
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("user_id", booking.userId)
            put("user_name", booking.userName)
            put("user_phone", booking.userPhone)
            put("table_id", booking.tableId)
            put("table_number", booking.tableNumber)
            put("booking_date", booking.bookingDate)
            put("time_slot", booking.timeSlot)
            put("party_size", booking.partySize)
            put("seating_area", booking.seatingArea)
            put("special_requests", booking.specialRequests)
            put("status", booking.status)
            put("restaurant_id", booking.restaurantId)
            put("restaurant_name", booking.restaurantName)
        }
        val affected = db.update(TABLE_BOOKINGS, cv, "id = ?", arrayOf(booking.id))
        return affected > 0
    }

    fun updateTableBookingDetails(
        id: String,
        partySize: Int? = null,
        seatingArea: String? = null,
        specialRequests: String? = null,
        bookingDate: String? = null,
        timeSlot: String? = null,
        tableId: String? = null,
        tableNumber: String? = null
    ): Boolean {
        if (partySize != null && partySize <= 0) {
            return false
        }
        val db = writableDatabase
        val cv = ContentValues().apply {
            partySize?.let { put("party_size", it) }
            seatingArea?.let { put("seating_area", it) }
            specialRequests?.let { put("special_requests", it) }
            bookingDate?.let { put("booking_date", it) }
            timeSlot?.let { put("time_slot", it) }
            tableId?.let { put("table_id", it) }
            tableNumber?.let { put("table_number", it) }
        }
        if (cv.size() == 0) return false
        val affected = db.update(TABLE_BOOKINGS, cv, "id = ?", arrayOf(id))
        return affected > 0
    }

    fun cancelTableBooking(id: String): Boolean {
        return updateTableBookingStatus(id, TableBookingStatus.CANCELLED)
    }

    /**
     * Safely cancels a customer's reservation with ownership and status validation inside an exclusive SQLite transaction.
     * Guarantees:
     * - Only the reservation owner (matching [userId]) can cancel.
     * - Only currently CONFIRMED reservations can be cancelled.
     * - Concurrency safe via ALLOCATION_LOCK and SQLite transaction.
     * - Does NOT delete the record; sets status to CANCELLED.
     */
    fun cancelTableBookingForUser(bookingId: String, userId: String): TableCancellationResult {
        if (bookingId.isBlank()) {
            return TableCancellationResult.Failure("Invalid reservation ID.")
        }
        if (userId.isBlank()) {
            return TableCancellationResult.Failure("Customer identification is required.")
        }
        val db = writableDatabase
        synchronized(ALLOCATION_LOCK) {
            db.beginTransaction()
            try {
                val cursor = db.rawQuery(
                    "SELECT * FROM $TABLE_BOOKINGS WHERE id = ? LIMIT 1",
                    arrayOf(bookingId)
                )
                val existing = try {
                    if (cursor.moveToFirst()) cursorToTableBooking(cursor) else null
                } finally {
                    cursor.close()
                }

                val validation = TableBookingManagementRules.validateCancellationEligibility(existing, userId)
                if (validation is TableCancellationResult.Failure) {
                    return validation
                }

                val cv = ContentValues().apply {
                    put("status", TableBookingStatus.CANCELLED)
                }
                val affected = db.update(
                    TABLE_BOOKINGS,
                    cv,
                    "id = ? AND user_id = ? AND status = ?",
                    arrayOf(bookingId, userId, TableBookingStatus.CONFIRMED)
                )

                if (affected != 1) {
                    throw IllegalStateException("Failed to update reservation status in database.")
                }

                db.setTransactionSuccessful()
                return validation
            } catch (e: Exception) {
                return TableCancellationResult.Failure(e.message ?: "Database error during cancellation.")
            } finally {
                db.endTransaction()
            }
        }
    }

    fun deleteTableBooking(id: String): Boolean {
        val db = writableDatabase
        val affected = db.delete(TABLE_BOOKINGS, "id = ?", arrayOf(id))
        return affected > 0
    }

    /**
     * Atomically validates booking parameters, rechecks slot availability inside an exclusive
     * SQLite transaction, allocates the best-fit table matching capacity and seating area,
     * and inserts the confirmed booking.
     *
     * Concurrency & Invariants:
     * - Uses synchronized(ALLOCATION_LOCK) across all database helper instances and SQLite transaction
     *   to protect against concurrent overlapping bookings.
     * - Re-reads active reservations directly from SQLite inside the transaction.
     * - On failure or conflict, the transaction is rolled back leaving no partial or orphaned records.
     */
    fun allocateAndConfirmTableBooking(
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
        requestedTableNumber: String = "",
        inventory: List<RestaurantTable> = RestaurantTableDefaults.DEFAULT_TABLES
    ): TableAllocationResult {
        val db = writableDatabase
        synchronized(ALLOCATION_LOCK) {
            db.beginTransaction()
            try {
                // Fetch existing bookings for this date and time slot inside the transaction
                val existingBookings = mutableListOf<TableBooking>()
                val cursor = db.rawQuery(
                    "SELECT * FROM $TABLE_BOOKINGS WHERE booking_date = ? AND time_slot = ?",
                    arrayOf(bookingDate, timeSlot)
                )
                try {
                    while (cursor.moveToNext()) {
                        existingBookings.add(cursorToTableBooking(cursor))
                    }
                } finally {
                    cursor.close()
                }

                val effectiveInventory = if (inventory === RestaurantTableDefaults.DEFAULT_TABLES && restaurantId.isNotBlank()) {
                    RestaurantTableDefaults.getTablesForRestaurant(restaurantId)
                } else {
                    inventory
                }

                // If user selected a specific table, allocate it directly if available
                val requestedTable = if (requestedTableNumber.isNotBlank()) {
                    effectiveInventory.find { it.tableNumber.equals(requestedTableNumber.trim(), ignoreCase = true) }
                } else null

                val isRequestedBooked = requestedTable != null && existingBookings.any {
                    it.tableNumber.equals(requestedTable.tableNumber, ignoreCase = true) &&
                    (it.status == TableBookingStatus.CONFIRMED || it.status == TableBookingStatus.SEATED)
                }

                val allocation = if (requestedTable != null && !isRequestedBooked) {
                    val bookingId = "TB-" + java.util.UUID.randomUUID().toString().take(8).uppercase()
                    val confirmedBooking = TableBooking(
                        id = bookingId,
                        userId = userId,
                        userName = userName,
                        userPhone = userPhone,
                        tableId = requestedTable.tableNumber,
                        tableNumber = requestedTable.tableNumber,
                        bookingDate = bookingDate,
                        timeSlot = timeSlot,
                        partySize = partySize,
                        seatingArea = seatingArea,
                        specialRequests = specialRequests,
                        status = TableBookingStatus.CONFIRMED,
                        restaurantId = restaurantId,
                        restaurantName = restaurantName
                    )
                    TableAllocationResult.Success(confirmedBooking, requestedTable)
                } else {
                    TableAllocationEngine.allocateTable(
                        restaurantId = restaurantId,
                        restaurantName = restaurantName,
                        userId = userId,
                        userName = userName,
                        userPhone = userPhone,
                        bookingDate = bookingDate,
                        timeSlot = timeSlot,
                        partySize = partySize,
                        seatingArea = seatingArea,
                        specialRequests = specialRequests,
                        existingBookings = existingBookings,
                        inventory = effectiveInventory
                    )
                }

                if (allocation is TableAllocationResult.Failure) {
                    return allocation
                }

                val confirmed = (allocation as TableAllocationResult.Success).booking

                val cv = ContentValues().apply {
                    put("id", confirmed.id)
                    put("user_id", confirmed.userId)
                    put("user_name", confirmed.userName)
                    put("user_phone", confirmed.userPhone)
                    put("table_id", confirmed.tableId)
                    put("table_number", confirmed.tableNumber)
                    put("booking_date", confirmed.bookingDate)
                    put("time_slot", confirmed.timeSlot)
                    put("party_size", confirmed.partySize)
                    put("seating_area", confirmed.seatingArea)
                    put("special_requests", confirmed.specialRequests)
                    put("status", confirmed.status)
                    put("created_at", confirmed.createdAt)
                    put("restaurant_id", confirmed.restaurantId)
                    put("restaurant_name", confirmed.restaurantName)
                }

                val rowId = db.insertWithOnConflict(TABLE_BOOKINGS, null, cv, SQLiteDatabase.CONFLICT_ABORT)
                if (rowId == -1L) {
                    throw IllegalStateException("Database failed to persist confirmed booking.")
                }

                db.setTransactionSuccessful()
                return allocation
            } catch (e: Exception) {
                return TableAllocationResult.Failure(e.message ?: "Database transaction failed during table allocation.")
            } finally {
                db.endTransaction()
            }
        }
    }

    fun getAvailableTablesForSlot(
        restaurantId: String,
        bookingDate: String,
        timeSlot: String,
        inventory: List<RestaurantTable> = RestaurantTableDefaults.DEFAULT_TABLES
    ): List<RestaurantTable> {
        val effectiveInventory = if (inventory === RestaurantTableDefaults.DEFAULT_TABLES && restaurantId.isNotBlank()) {
            RestaurantTableDefaults.getTablesForRestaurant(restaurantId)
        } else {
            inventory
        }
        val activeBookings = getTableBookingsForSlot(bookingDate, timeSlot)
        val blockingStatuses = setOf(TableBookingStatus.CONFIRMED, TableBookingStatus.SEATED)
        val bookedNumbers = activeBookings
            .filter {
                blockingStatuses.contains(it.status) &&
                TableAllocationEngine.isSameRestaurant(it, restaurantId)
            }
            .map { it.tableNumber }
            .toSet()

        return effectiveInventory.filter { !bookedNumbers.contains(it.tableNumber) }
    }

    private fun cursorToTableBooking(c: Cursor): TableBooking {
        val idIdx = c.getColumnIndexOrThrow("id")
        val userIdIdx = c.getColumnIndexOrThrow("user_id")
        val userNameIdx = c.getColumnIndexOrThrow("user_name")
        val userPhoneIdx = c.getColumnIndexOrThrow("user_phone")
        val tableIdIdx = c.getColumnIndexOrThrow("table_id")
        val tableNumberIdx = c.getColumnIndexOrThrow("table_number")
        val bookingDateIdx = c.getColumnIndexOrThrow("booking_date")
        val timeSlotIdx = c.getColumnIndexOrThrow("time_slot")
        val partySizeIdx = c.getColumnIndexOrThrow("party_size")
        val seatingAreaIdx = c.getColumnIndexOrThrow("seating_area")
        val specialRequestsIdx = c.getColumnIndexOrThrow("special_requests")
        val statusIdx = c.getColumnIndexOrThrow("status")
        val createdAtIdx = c.getColumnIndexOrThrow("created_at")
        val restIdIdx = c.getColumnIndex("restaurant_id")
        val restNameIdx = c.getColumnIndex("restaurant_name")
        val updatedAtIdx = c.getColumnIndex("updated_at")

        return TableBooking(
            id = c.getString(idIdx) ?: "",
            userId = c.getString(userIdIdx) ?: "",
            userName = c.getString(userNameIdx) ?: "",
            userPhone = c.getString(userPhoneIdx) ?: "",
            tableId = c.getString(tableIdIdx) ?: "",
            tableNumber = c.getString(tableNumberIdx) ?: "",
            bookingDate = c.getString(bookingDateIdx) ?: "",
            timeSlot = c.getString(timeSlotIdx) ?: "",
            partySize = if (c.isNull(partySizeIdx)) 1 else c.getInt(partySizeIdx),
            seatingArea = if (c.isNull(seatingAreaIdx)) "Main Dining Room" else (c.getString(seatingAreaIdx) ?: "Main Dining Room"),
            specialRequests = if (c.isNull(specialRequestsIdx)) "" else (c.getString(specialRequestsIdx) ?: ""),
            status = if (c.isNull(statusIdx)) TableBookingStatus.CONFIRMED else (c.getString(statusIdx) ?: TableBookingStatus.CONFIRMED),
            createdAt = if (c.isNull(createdAtIdx)) Instant.now().toString() else (c.getString(createdAtIdx) ?: Instant.now().toString()),
            restaurantId = if (restIdIdx != -1 && !c.isNull(restIdIdx)) c.getString(restIdIdx) else "",
            restaurantName = if (restNameIdx != -1 && !c.isNull(restNameIdx)) c.getString(restNameIdx) else "",
            updatedAt = if (updatedAtIdx != -1 && !c.isNull(updatedAtIdx)) c.getString(updatedAtIdx) ?: "" else ""
        )
    }

    // -------------------------------------------------------------
    // -------------------------------------------------------------
    // DINING TABS & WAITER POS PAIRING
    // -------------------------------------------------------------

    fun getActiveBookingForTable(
        tableNumber: String,
        date: String = LocalDate.now().toString(),
        currentTimeSlot: String? = null,
        restaurantId: String = ""
    ): TableBooking? {
        val bookings = getTableBookingsForTableNumber(tableNumber)
        return WaiterTablePairingRules.resolveActiveBookingForTable(
            tableNumber = tableNumber,
            bookings = bookings,
            serviceDate = date,
            currentTimeSlot = currentTimeSlot,
            restaurantId = restaurantId
        )
    }

    fun getTableBookingsForTableNumber(tableNumber: String): List<TableBooking> {
        val list = mutableListOf<TableBooking>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_BOOKINGS WHERE table_number = ? OR table_id = ? OR table_id LIKE ? ORDER BY booking_date DESC, time_slot DESC",
            arrayOf(tableNumber, tableNumber, "%_$tableNumber")
        )
        try {
            while (cursor.moveToNext()) {
                list.add(cursorToTableBooking(cursor))
            }
        } finally {
            cursor.close()
        }
        return list
    }

    fun getOpenTabForTable(tableNumber: String): DiningTab? {
        if (tableNumber.isBlank()) return null
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_DINING_TABS WHERE table_number = ? COLLATE NOCASE AND (status = 'OPEN' OR status = 'ACTIVE' OR status = 'PAYMENT_PENDING') ORDER BY updated_at DESC LIMIT 1",
            arrayOf(tableNumber.trim())
        )
        return try {
            if (cursor.moveToFirst()) cursorToDiningTab(cursor) else null
        } catch (_: Exception) {
            null
        } finally {
            cursor.close()
        }
    }

    fun getActiveDiningTabForTable(tableNumber: String): DiningTab? {
        if (tableNumber.isBlank()) return null
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_DINING_TABS WHERE table_number = ? COLLATE NOCASE AND status != 'SETTLED' AND status != 'PAID' AND status != 'CLOSED' ORDER BY updated_at DESC LIMIT 1",
            arrayOf(tableNumber.trim())
        )
        return try {
            if (cursor.moveToFirst()) cursorToDiningTab(cursor) else null
        } finally {
            cursor.close()
        }
    }

    fun getOpenTabForUser(userId: String): DiningTab? {
        if (userId.isBlank()) return null
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_DINING_TABS WHERE (user_id = ? OR customer_id = ?) AND status != 'SETTLED' AND status != 'PAID' AND status != 'CLOSED' ORDER BY updated_at DESC LIMIT 1",
            arrayOf(userId.trim(), userId.trim())
        )
        return try {
            if (cursor.moveToFirst()) cursorToDiningTab(cursor) else null
        } catch (_: Exception) {
            null
        } finally {
            cursor.close()
        }
    }

    fun getActiveDiningTabForUser(userId: String): DiningTab? {
        return getOpenTabForUser(userId)
    }

    fun getActiveDiningTabForBooking(bookingId: String): DiningTab? {
        if (bookingId.isBlank()) return null
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_DINING_TABS WHERE booking_id = ? AND (status = 'OPEN' OR status = 'ACTIVE' OR status = 'PAYMENT_PENDING') ORDER BY updated_at DESC LIMIT 1",
            arrayOf(bookingId.trim())
        )
        return try {
            if (cursor.moveToFirst()) cursorToDiningTab(cursor) else null
        } finally {
            cursor.close()
        }
    }

    fun getDiningTab(id: String): DiningTab? {
        if (id.isBlank()) return null
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_DINING_TABS WHERE id = ? LIMIT 1",
            arrayOf(id.trim())
        )
        return try {
            if (cursor.moveToFirst()) cursorToDiningTab(cursor) else null
        } catch (_: Exception) {
            null
        } finally {
            cursor.close()
        }
    }

    fun getDiningTabById(id: String): DiningTab? = getDiningTab(id)

    fun getAllDiningTabs(): List<DiningTab> {
        val list = mutableListOf<DiningTab>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_DINING_TABS ORDER BY updated_at DESC", null)
        try {
            while (cursor.moveToNext()) {
                list.add(cursorToDiningTab(cursor))
            }
        } finally {
            cursor.close()
        }
        return list
    }

    fun saveOrUpdateTab(tab: DiningTab): Boolean {
        if (tab.tableNumber.isBlank()) return false
        val db = writableDatabase
        val effectiveId = if (tab.id.isNotBlank()) tab.id else UUID.randomUUID().toString()
        val updatedAt = if (tab.updatedAt.isNotBlank()) tab.updatedAt else Instant.now().toString()
        val cv = ContentValues().apply {
            put("id", effectiveId)
            put("table_id", tab.tableId.ifBlank { tab.tableNumber.trim() })
            put("table_number", tab.tableNumber.trim())
            put("booking_id", tab.bookingId)
            put("user_id", tab.userId ?: tab.customerId)
            put("user_name", tab.userName.ifBlank { tab.customerName ?: "Guest Diner" })
            put("customer_id", tab.customerId ?: tab.userId)
            put("customer_name", tab.customerName ?: tab.userName)
            put("customer_phone", tab.customerPhone)
            put("items_json", gson.toJson(tab.items))
            put("subtotal", tab.subtotal)
            put("tax", tab.tax)
            put("gst", tab.tax)
            put("service_charge", tab.serviceCharge)
            put("tip", tab.tip)
            put("is_service_charge_opted_out", if (tab.isServiceChargeOptedOut) 1 else 0)
            put("total", tab.total)
            put("status", tab.status)
            put("payment_method", tab.paymentMethod)
            put("created_at", tab.createdAt.ifBlank { updatedAt })
            put("updated_at", updatedAt)
            put("restaurant_id", tab.restaurantId)
            put("guest_count", tab.guestCount)
            put("seated_at", tab.seatedAt)
            put("billed_at", tab.billedAt)
            put("payment_transaction_id", tab.paymentTransactionId)
        }
        val rowId = try {
            db.insertWithOnConflict(TABLE_DINING_TABS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (_: Exception) {
            -1L
        }
        return rowId != -1L
    }

    fun markTabSettled(tabId: String, paymentMethod: String, transactionId: String = ""): Boolean {
        if (tabId.isBlank()) return false
        val db = writableDatabase
        val existing = getDiningTab(tabId)
        if (existing != null && DineInPaymentRules.isSettledOrPaid(existing.status)) {
            return false // Duplicate settlement protection
        }
        val cv = ContentValues().apply {
            put("status", "SETTLED")
            put("payment_method", paymentMethod)
            if (transactionId.isNotBlank()) {
                put("payment_transaction_id", transactionId.trim())
            }
            put("updated_at", Instant.now().toString())
        }
        val affected = try {
            db.update(TABLE_DINING_TABS, cv, "id = ?", arrayOf(tabId.trim()))
        } catch (_: Exception) {
            0
        }
        if (affected > 0) {
            recordAuditLog(
                action = "DINE_IN_SETTLED",
                actorEmail = existing?.customerName?.ifBlank { null } ?: "guest@restro.com",
                actorRole = "CUSTOMER",
                targetId = tabId,
                details = "Table ${existing?.tableNumber} bill settled via $paymentMethod. Txn: $transactionId"
            )
        }
        return affected > 0
    }

    fun markTabSettled(tabId: String, paymentMethod: String): Boolean = markTabSettled(tabId, paymentMethod, "")

    /**
     * Resolves the real-time floor status of a table using table bookings and dining tabs.
     * Returns "Occupied" if an open dining tab or active (CONFIRMED/SEATED) booking exists,
     * otherwise returns "Available".
     */
    fun getFloorTableStatus(tableNumber: String): String {
        val clean = tableNumber.trim()
        if (clean.isBlank()) return "Available"
        val openTab = getOpenTabForTable(clean)
        if (openTab != null && openTab.status != "SETTLED" && openTab.status != "PAID" && openTab.status != "CLOSED") {
            return "Occupied"
        }
        val activeBooking = getAllTableBookings().firstOrNull {
            it.tableNumber.equals(clean, ignoreCase = true) &&
                (it.status == TableBookingStatus.CONFIRMED || it.status == TableBookingStatus.SEATED)
        }
        return if (activeBooking != null) "Occupied" else "Available"
    }

    /**
     * Updates the floor-plan table status to the specified status (e.g. "Available").
     * When marked "Available", completes any active bookings for the table and settles open tabs.
     */
    fun updateFloorTableStatus(tableNumber: String, status: String): Boolean {
        val clean = tableNumber.trim()
        if (clean.isBlank()) return false
        if (status.equals("Available", ignoreCase = true)) {
            val activeBookings = getAllTableBookings().filter {
                it.tableNumber.equals(clean, ignoreCase = true) &&
                    (it.status == TableBookingStatus.CONFIRMED || it.status == TableBookingStatus.SEATED)
            }
            activeBookings.forEach { booking ->
                updateTableBookingStatus(booking.id, TableBookingStatus.COMPLETED)
            }
            val openTab = getOpenTabForTable(clean)
            if (openTab != null && openTab.status != "SETTLED" && openTab.status != "PAID" && openTab.status != "CLOSED") {
                markTabSettled(openTab.id, openTab.paymentMethod ?: "Cash")
            }
            return true
        }
        return false
    }

    fun appendItemsToDiningTab(
        tableNumber: String,
        tableId: String,
        newItems: List<TableBillItem>,
        associatedBooking: TableBooking? = null,
        restaurantId: String = ""
    ): DiningTab {
        require(tableNumber.isNotBlank()) { "Table number must not be blank" }
        require(newItems.isNotEmpty()) { "Cannot submit empty items to dining tab" }

        val db = writableDatabase
        db.beginTransaction()
        try {
            val existingCursor = db.rawQuery(
                "SELECT * FROM $TABLE_DINING_TABS WHERE table_number = ? AND (status = 'OPEN' OR status = 'ACTIVE' OR status = 'PAYMENT_PENDING') ORDER BY updated_at DESC LIMIT 1",
                arrayOf(tableNumber)
            )
            val existingTab = if (existingCursor.moveToFirst()) {
                cursorToDiningTab(existingCursor)
            } else null
            existingCursor.close()

            val targetTab = WaiterTablePairingRules.appendOrOpenDiningTab(
                existingTab = existingTab,
                tableNumber = tableNumber,
                tableId = tableId,
                newItems = newItems,
                associatedBooking = associatedBooking,
                restaurantId = restaurantId
            )

            val resolvedGuestCount = if (targetTab.guestCount > 0) targetTab.guestCount
                else (associatedBooking?.partySize ?: existingTab?.guestCount ?: 2)

            val cv = ContentValues().apply {
                put("id", targetTab.id)
                put("table_id", targetTab.tableId)
                put("table_number", targetTab.tableNumber)
                put("customer_id", targetTab.customerId)
                put("customer_name", targetTab.customerName)
                put("customer_phone", targetTab.customerPhone)
                put("booking_id", targetTab.bookingId)
                put("user_id", targetTab.userId)
                put("user_name", targetTab.userName)
                put("status", targetTab.status)
                put("items_json", gson.toJson(targetTab.items))
                put("subtotal", targetTab.subtotal)
                put("tax", targetTab.tax)
                put("gst", targetTab.tax)
                put("service_charge", targetTab.serviceCharge)
                put("tip", targetTab.tip)
                put("is_service_charge_opted_out", if (targetTab.isServiceChargeOptedOut) 1 else 0)
                put("total", targetTab.total)
                put("payment_method", targetTab.paymentMethod)
                put("created_at", targetTab.createdAt)
                put("updated_at", targetTab.updatedAt)
                put("restaurant_id", targetTab.restaurantId)
                put("guest_count", resolvedGuestCount)
                put("seated_at", targetTab.seatedAt.ifBlank { targetTab.createdAt })
                put("billed_at", targetTab.billedAt)
                put("payment_method", targetTab.paymentMethod)
                put("payment_transaction_id", targetTab.paymentTransactionId)
            }

            db.insertWithOnConflict(TABLE_DINING_TABS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
            return targetTab.copy(
                guestCount = resolvedGuestCount,
                seatedAt = targetTab.seatedAt.ifBlank { targetTab.createdAt }
            )
        } finally {
            db.endTransaction()
        }
    }

    private fun cursorToDiningTab(c: Cursor): DiningTab {
        val idIdx = c.getColumnIndex("id")
        val tableIdIdx = c.getColumnIndex("table_id")
        val tableNumberIdx = c.getColumnIndex("table_number")
        val bookingIdIdx = c.getColumnIndex("booking_id")
        val userIdIdx = c.getColumnIndex("user_id")
        val userNameIdx = c.getColumnIndex("user_name")
        val customerIdIdx = c.getColumnIndex("customer_id")
        val customerNameIdx = c.getColumnIndex("customer_name")
        val customerPhoneIdx = c.getColumnIndex("customer_phone")
        val itemsJsonIdx = c.getColumnIndex("items_json")
        val subtotalIdx = c.getColumnIndex("subtotal")
        val taxIdx = c.getColumnIndex("tax")
        val gstIdx = c.getColumnIndex("gst")
        val serviceChargeIdx = c.getColumnIndex("service_charge")
        val totalIdx = c.getColumnIndex("total")
        val statusIdx = c.getColumnIndex("status")
        val paymentMethodIdx = c.getColumnIndex("payment_method")
        val createdAtIdx = c.getColumnIndex("created_at")
        val updatedAtIdx = c.getColumnIndex("updated_at")
        val restaurantIdIdx = c.getColumnIndex("restaurant_id")

        val items: List<TableBillItem> = if (itemsJsonIdx != -1 && !c.isNull(itemsJsonIdx)) {
            val json = c.getString(itemsJsonIdx)
            try {
                val listType = object : TypeToken<List<TableBillItem>>() {}.type
                gson.fromJson<List<TableBillItem>>(json, listType) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        val taxVal = when {
            taxIdx != -1 && !c.isNull(taxIdx) -> c.getLong(taxIdx)
            gstIdx != -1 && !c.isNull(gstIdx) -> c.getLong(gstIdx)
            else -> 0L
        }

        val id = if (idIdx != -1 && !c.isNull(idIdx)) c.getString(idIdx) else ""
        val tableNumber = if (tableNumberIdx != -1 && !c.isNull(tableNumberIdx)) c.getString(tableNumberIdx) else ""
        val tableId = if (tableIdIdx != -1 && !c.isNull(tableIdIdx)) c.getString(tableIdIdx) else tableNumber
        val bookingId = if (bookingIdIdx != -1 && !c.isNull(bookingIdIdx)) c.getString(bookingIdIdx) else null
        val customerId = if (customerIdIdx != -1 && !c.isNull(customerIdIdx)) c.getString(customerIdIdx)
            else if (userIdIdx != -1 && !c.isNull(userIdIdx)) c.getString(userIdIdx) else null
        val customerName = if (customerNameIdx != -1 && !c.isNull(customerNameIdx)) c.getString(customerNameIdx)
            else if (userNameIdx != -1 && !c.isNull(userNameIdx)) c.getString(userNameIdx) else null
        val customerPhone = if (customerPhoneIdx != -1 && !c.isNull(customerPhoneIdx)) c.getString(customerPhoneIdx) else null
        val status = if (statusIdx != -1 && !c.isNull(statusIdx)) c.getString(statusIdx) else DiningTabStatus.OPEN
        val paymentMethod = if (paymentMethodIdx != -1 && !c.isNull(paymentMethodIdx)) c.getString(paymentMethodIdx) else null
        val createdAt = if (createdAtIdx != -1 && !c.isNull(createdAtIdx)) c.getString(createdAtIdx) else Instant.now().toString()
        val updatedAt = if (updatedAtIdx != -1 && !c.isNull(updatedAtIdx)) c.getString(updatedAtIdx) else Instant.now().toString()
        val restaurantId = if (restaurantIdIdx != -1 && !c.isNull(restaurantIdIdx)) c.getString(restaurantIdIdx) else ""

        val subtotal = if (subtotalIdx != -1 && !c.isNull(subtotalIdx)) c.getLong(subtotalIdx) else 0L
        val serviceCharge = if (serviceChargeIdx != -1 && !c.isNull(serviceChargeIdx)) c.getLong(serviceChargeIdx) else 0L
        val total = if (totalIdx != -1 && !c.isNull(totalIdx)) c.getLong(totalIdx) else 0L

        val tipIdx = c.getColumnIndex("tip")
        val tip = if (tipIdx != -1 && !c.isNull(tipIdx)) c.getLong(tipIdx) else 0L
        val optOutIdx = c.getColumnIndex("is_service_charge_opted_out")
        val isServiceChargeOptedOut = if (optOutIdx != -1 && !c.isNull(optOutIdx)) c.getInt(optOutIdx) == 1 else false

        val guestCount = c.getColumnIndex("guest_count").takeIf { it >= 0 }?.let { if (!c.isNull(it)) c.getInt(it) else 0 } ?: 0
        val seatedAt = c.getColumnIndex("seated_at").takeIf { it >= 0 }?.let { if (!c.isNull(it)) c.getString(it) else "" } ?: ""
        val billedAt = c.getColumnIndex("billed_at").takeIf { it >= 0 }?.let { if (!c.isNull(it)) c.getString(it) else "" } ?: ""
        val paymentTransactionId = c.getColumnIndex("payment_transaction_id").takeIf { it >= 0 }?.let { if (!c.isNull(it)) c.getString(it) else "" } ?: ""

        return DiningTab(
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
            subtotal = subtotal,
            tax = taxVal,
            serviceCharge = serviceCharge,
            tip = tip,
            isServiceChargeOptedOut = isServiceChargeOptedOut,
            total = total,
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
    }

    // -------------------------------------------------------------
    // AUTOMATED TABLE STATE LIFECYCLE & DINING TAB PAYMENTS
    // -------------------------------------------------------------

    /**
     * Seats guests at a table and opens/updates the active check (DiningTab).
     * If an associated confirmed booking exists, transitions it to SEATED.
     */
    fun seatTableAndOpenTab(
        tableNumber: String,
        tableId: String = "",
        guestCount: Int = 0,
        bookingId: String? = null,
        customerName: String? = null,
        customerPhone: String? = null,
        restaurantId: String = ""
    ): Pair<DiningTab, TableBooking?> {
        require(tableNumber.isNotBlank()) { "Table number must not be blank" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            // 1. Resolve or update booking
            var updatedBooking: TableBooking? = null
            val bId = bookingId ?: run {
                val candidateCursor = db.rawQuery(
                    "SELECT * FROM $TABLE_BOOKINGS WHERE table_number = ? AND status = ? LIMIT 1",
                    arrayOf(tableNumber, TableBookingStatus.CONFIRMED)
                )
                val idFound = if (candidateCursor.moveToFirst()) candidateCursor.getString(candidateCursor.getColumnIndexOrThrow("id")) else null
                candidateCursor.close()
                idFound
            }

            if (!bId.isNullOrBlank()) {
                val bCursor = db.rawQuery("SELECT * FROM $TABLE_BOOKINGS WHERE id = ? LIMIT 1", arrayOf(bId))
                if (bCursor.moveToFirst()) {
                    val currentBooking = cursorToTableBooking(bCursor)
                    bCursor.close()
                    val nowStr = Instant.now().toString()
                    val cv = ContentValues().apply {
                        put("status", TableBookingStatus.SEATED)
                        put("updated_at", nowStr)
                    }
                    db.update(TABLE_BOOKINGS, cv, "id = ?", arrayOf(bId))
                    updatedBooking = currentBooking.copy(status = TableBookingStatus.SEATED, updatedAt = nowStr)
                } else {
                    bCursor.close()
                }
            }

            // 2. Open or reuse dining tab
            val tabCursor = db.rawQuery(
                "SELECT * FROM $TABLE_DINING_TABS WHERE table_number = ? AND (status = ? OR status = ?) LIMIT 1",
                arrayOf(tableNumber, DiningTabStatus.ACTIVE, DiningTabStatus.PAYMENT_PENDING)
            )
            val finalTab = if (tabCursor.moveToFirst()) {
                val existing = cursorToDiningTab(tabCursor)
                tabCursor.close()
                existing
            } else {
                tabCursor.close()
                val nowStr = Instant.now().toString()
                val party = if (guestCount > 0) guestCount else (updatedBooking?.partySize ?: 2)
                val newTab = DiningTab(
                    id = DiningTab.generateId(),
                    tableId = tableId.ifBlank { tableNumber },
                    tableNumber = tableNumber,
                    customerId = updatedBooking?.userId,
                    customerName = customerName ?: updatedBooking?.userName,
                    customerPhone = customerPhone ?: updatedBooking?.userPhone,
                    bookingId = updatedBooking?.id ?: bId,
                    status = DiningTabStatus.ACTIVE,
                    items = emptyList(),
                    subtotal = 0.0,
                    tax = 0.0,
                    serviceCharge = 0.0,
                    total = 0.0,
                    createdAt = nowStr,
                    updatedAt = nowStr,
                    restaurantId = restaurantId,
                    guestCount = party,
                    seatedAt = nowStr
                )
                val cvTab = ContentValues().apply {
                    put("id", newTab.id)
                    put("table_id", newTab.tableId)
                    put("table_number", newTab.tableNumber)
                    put("customer_id", newTab.customerId)
                    put("customer_name", newTab.customerName)
                    put("customer_phone", newTab.customerPhone)
                    put("booking_id", newTab.bookingId)
                    put("status", newTab.status)
                    put("items_json", "[]")
                    put("subtotal", 0.0)
                    put("tax", 0.0)
                    put("service_charge", 0.0)
                    put("tip", 0.0)
                    put("is_service_charge_opted_out", 0)
                    put("total", 0.0)
                    put("created_at", newTab.createdAt)
                    put("updated_at", newTab.updatedAt)
                    put("restaurant_id", newTab.restaurantId)
                    put("guest_count", newTab.guestCount)
                    put("seated_at", newTab.seatedAt)
                    put("billed_at", "")
                    put("payment_method", "")
                    put("payment_transaction_id", "")
                }
                db.insertWithOnConflict(TABLE_DINING_TABS, null, cvTab, SQLiteDatabase.CONFLICT_REPLACE)
                newTab
            }

            db.setTransactionSuccessful()
            return Pair(finalTab, updatedBooking)
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Initiates payment for a dining tab (transitions to Billed/Payment Pending - Red).
     */
    fun initiatePaymentForDiningTab(
        tabId: String,
        paymentMethod: String = "Card",
        tip: Double = 0.0,
        isServiceChargeOptedOut: Boolean = false,
        transactionId: String = ""
    ): DiningTab? {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cursor = db.rawQuery("SELECT * FROM $TABLE_DINING_TABS WHERE id = ? LIMIT 1", arrayOf(tabId.trim()))
            if (!cursor.moveToFirst()) {
                cursor.close()
                return null
            }
            val tab = cursorToDiningTab(cursor)
            cursor.close()

            val finalBill = DineInPaymentRules.validatePaymentInitiation(
                tab = tab,
                tip = tip,
                isServiceChargeOptedOut = isServiceChargeOptedOut
            ) ?: return null

            val nowStr = Instant.now().toString()
            val cv = ContentValues().apply {
                put("status", DiningTabStatus.PAYMENT_PENDING)
                put("payment_method", paymentMethod)
                put("subtotal", finalBill.subtotalPaise)
                put("tax", finalBill.taxPaise)
                put("gst", finalBill.taxPaise)
                put("service_charge", finalBill.serviceChargePaise)
                put("tip", finalBill.tipPaise)
                put("is_service_charge_opted_out", if (isServiceChargeOptedOut) 1 else 0)
                put("total", finalBill.totalPaise)
                if (transactionId.isNotBlank()) {
                    put("payment_transaction_id", transactionId.trim())
                }
                put("billed_at", nowStr)
                put("updated_at", nowStr)
            }
            db.update(TABLE_DINING_TABS, cv, "id = ?", arrayOf(tabId.trim()))
            db.setTransactionSuccessful()
            return tab.copy(
                status = DiningTabStatus.PAYMENT_PENDING,
                paymentMethod = paymentMethod,
                subtotal = finalBill.subtotalPaise,
                tax = finalBill.taxPaise,
                serviceCharge = finalBill.serviceChargePaise,
                tip = finalBill.tipPaise,
                isServiceChargeOptedOut = isServiceChargeOptedOut,
                total = finalBill.totalPaise,
                paymentTransactionId = transactionId.trim().ifBlank { tab.paymentTransactionId },
                billedAt = nowStr,
                updatedAt = nowStr
            )
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Records payment authorization token/reference on a dining tab.
     */
    fun recordPaymentAuthorization(
        tabId: String,
        transactionId: String,
        paymentMethod: String = ""
    ): DiningTab? {
        if (tabId.isBlank() || transactionId.isBlank()) return null
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cursor = db.rawQuery("SELECT * FROM $TABLE_DINING_TABS WHERE id = ? LIMIT 1", arrayOf(tabId.trim()))
            if (!cursor.moveToFirst()) {
                cursor.close()
                return null
            }
            val tab = cursorToDiningTab(cursor)
            cursor.close()

            if (DineInPaymentRules.isSettledOrPaid(tab.status)) {
                return null
            }

            val nowStr = Instant.now().toString()
            val cv = ContentValues().apply {
                put("payment_transaction_id", transactionId.trim())
                if (paymentMethod.isNotBlank()) {
                    put("payment_method", paymentMethod.trim())
                }
                put("updated_at", nowStr)
            }
            db.update(TABLE_DINING_TABS, cv, "id = ?", arrayOf(tabId.trim()))
            db.setTransactionSuccessful()
            return tab.copy(
                paymentTransactionId = transactionId.trim(),
                paymentMethod = paymentMethod.trim().ifBlank { tab.paymentMethod },
                updatedAt = nowStr
            )
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Reverts payment for a dining tab when payment is cancelled or failed.
     * The table remains Occupied (Amber) and is NOT freed to Available!
     */
    fun failOrRevertPaymentForDiningTab(tabId: String, reason: String = "Payment failed"): DiningTab? {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cursor = db.rawQuery("SELECT * FROM $TABLE_DINING_TABS WHERE id = ? LIMIT 1", arrayOf(tabId.trim()))
            if (!cursor.moveToFirst()) {
                cursor.close()
                return null
            }
            val tab = cursorToDiningTab(cursor)
            cursor.close()

            val revertedTab = DineInPaymentRules.validatePaymentFailureOrCancellation(tab, reason)
                ?: return null

            val nowStr = Instant.now().toString()
            val cv = ContentValues().apply {
                put("status", DiningTabStatus.ACTIVE)
                put("billed_at", "")
                put("payment_transaction_id", "")
                put("updated_at", nowStr)
            }
            db.update(TABLE_DINING_TABS, cv, "id = ?", arrayOf(tabId.trim()))
            db.setTransactionSuccessful()
            return revertedTab.copy(updatedAt = nowStr)
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Verifies and completes payment for a dining tab.
     * Requires authoritative verification result (non-blank transaction ID).
     * Only after successful payment verification is the tab marked PAID/CLOSED,
     * the booking marked COMPLETED, and the table freed to Available (Green)!
     */
    fun verifyAndCompletePaymentForDiningTab(
        tabId: String,
        transactionId: String = ""
    ): Pair<DiningTab, TableBooking?>? {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cursor = db.rawQuery("SELECT * FROM $TABLE_DINING_TABS WHERE id = ? LIMIT 1", arrayOf(tabId.trim()))
            if (!cursor.moveToFirst()) {
                cursor.close()
                return null
            }
            val tab = cursorToDiningTab(cursor)
            cursor.close()

            val verifiedTxn = DineInPaymentRules.validatePaymentVerification(
                tab = tab,
                transactionId = transactionId
            ) ?: return null

            val nowStr = Instant.now().toString()

            // 1. Mark tab PAID
            val cvTab = ContentValues().apply {
                put("status", DiningTabStatus.PAID)
                put("payment_transaction_id", verifiedTxn)
                put("updated_at", nowStr)
            }
            db.update(TABLE_DINING_TABS, cvTab, "id = ?", arrayOf(tabId.trim()))

            // 2. Complete associated booking
            var completedBooking: TableBooking? = null
            val targetBookingId = if (!tab.bookingId.isNullOrBlank()) {
                tab.bookingId
            } else {
                val bCursor = db.rawQuery(
                    "SELECT id FROM $TABLE_BOOKINGS WHERE table_number = ? AND (status = 'CONFIRMED' OR status = 'SEATED') LIMIT 1",
                    arrayOf(tab.tableNumber)
                )
                val foundId = if (bCursor.moveToFirst()) bCursor.getString(0) else null
                bCursor.close()
                foundId
            }

            if (!targetBookingId.isNullOrBlank()) {
                val bCursor = db.rawQuery("SELECT * FROM $TABLE_BOOKINGS WHERE id = ? LIMIT 1", arrayOf(targetBookingId))
                if (bCursor.moveToFirst()) {
                    val b = cursorToTableBooking(bCursor)
                    bCursor.close()
                    val cvBooking = ContentValues().apply {
                        put("status", TableBookingStatus.COMPLETED)
                        put("updated_at", nowStr)
                    }
                    db.update(TABLE_BOOKINGS, cvBooking, "id = ?", arrayOf(targetBookingId))
                    completedBooking = b.copy(status = TableBookingStatus.COMPLETED, updatedAt = nowStr)
                } else {
                    bCursor.close()
                }
            }

            // 3. Mark remaining KOT tickets served
            val cvKot = ContentValues().apply {
                put("status", "Served")
                put("updated_at", nowStr)
            }
            db.update(TABLE_KOT_TICKETS, cvKot, "table_number = ? AND status != 'Served'", arrayOf(tab.tableNumber))

            db.setTransactionSuccessful()
            val completedTab = tab.copy(
                status = DiningTabStatus.PAID,
                paymentTransactionId = verifiedTxn,
                updatedAt = nowStr
            )
            return Pair(completedTab, completedBooking)
        } finally {
            db.endTransaction()
        }
    }

    // -------------------------------------------------------------
    // KITCHEN ORDER TICKETS (KOT) & ROUND SYNCHRONIZATION
    // -------------------------------------------------------------

    fun createKotTicket(
        tableNumber: String,
        tableId: String,
        items: List<KOTItem>,
        round: Int = 1,
        roundName: String = "",
        customerName: String? = null,
        orderId: Long = 0L,
        createdAt: String = Instant.now().toString()
    ): KOT {
        require(tableNumber.isNotBlank()) { "Table number must not be blank" }
        require(items.isNotEmpty()) { "KOT items cannot be empty" }

        val db = writableDatabase
        val cv = ContentValues().apply {
            put("order_id", orderId)
            put("table_number", tableNumber)
            put("table_id", tableId.ifBlank { tableNumber })
            put("round_number", round)
            put("round_name", roundName)
            put("status", "New")
            put("items_json", gson.toJson(items))
            put("customer_name", customerName)
            put("created_at", createdAt)
            put("updated_at", createdAt)
        }
        val insertedId = db.insert(TABLE_KOT_TICKETS, null, cv)
        return KOT(
            id = insertedId,
            orderId = orderId,
            status = "New",
            items = items,
            tableNumber = tableNumber,
            tableId = tableId.ifBlank { tableNumber },
            round = round,
            roundName = roundName,
            createdAt = createdAt,
            customerName = customerName
        )
    }

    fun getAllKotTickets(): List<KOT> {
        val list = mutableListOf<KOT>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_KOT_TICKETS ORDER BY id ASC", null)
        try {
            while (cursor.moveToNext()) {
                list.add(cursorToKot(cursor))
            }
        } finally {
            cursor.close()
        }
        return list
    }

    fun getKotTicketsForTable(tableNumber: String): List<KOT> {
        val list = mutableListOf<KOT>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_KOT_TICKETS WHERE table_number = ? ORDER BY round_number ASC, id ASC",
            arrayOf(tableNumber)
        )
        try {
            while (cursor.moveToNext()) {
                list.add(cursorToKot(cursor))
            }
        } finally {
            cursor.close()
        }
        return list
    }

    fun updateKotTicketStatus(kotId: Long, newStatus: String): Boolean {
        val db = writableDatabase
        val cursor = db.rawQuery("SELECT status, order_id, table_number FROM $TABLE_KOT_TICKETS WHERE id = ?", arrayOf(kotId.toString()))
        if (!cursor.moveToFirst()) {
            cursor.close()
            return false
        }
        val currentStatus = cursor.getString(0)
        val linkedOrderId = cursor.getLong(1)
        cursor.close()

        if (!KotLifecycleRules.canTransition(currentStatus, newStatus)) {
            return false
        }

        val cv = ContentValues().apply {
            put("status", newStatus)
            put("updated_at", Instant.now().toString())
        }
        val rows = db.update(TABLE_KOT_TICKETS, cv, "id = ?", arrayOf(kotId.toString()))
        if (rows > 0) {
            recordAuditLog(
                action = "KOT_STATUS_UPDATED",
                actorEmail = "chef@restro.com",
                actorRole = "CHEF_KDS",
                targetId = kotId.toString(),
                details = "KOT #$kotId transitioned from $currentStatus to $newStatus"
            )
        }
        if (rows > 0 && linkedOrderId > 0L) {
            val orderStatusStr = when (newStatus.trim().lowercase()) {
                "preparing" -> "Preparing"
                "ready" -> "Out for delivery"
                "served" -> "Delivered"
                else -> null
            }
            if (orderStatusStr != null) {
                updateOrderStatus(linkedOrderId.toString(), orderStatusStr)
            }
        }
        return rows > 0
    }

    fun getNextRoundNumberForTable(tableNumber: String): Int {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT MAX(round_number) FROM $TABLE_KOT_TICKETS WHERE table_number = ? AND status != 'Served'",
            arrayOf(tableNumber)
        )
        return try {
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                cursor.getInt(0) + 1
            } else {
                1
            }
        } finally {
            cursor.close()
        }
    }

    private fun cursorToKot(cursor: Cursor): KOT {
        val itemsType = object : TypeToken<List<KOTItem>>() {}.type
        val itemsJson = cursor.getString(cursor.getColumnIndexOrThrow("items_json")) ?: "[]"
        val items: List<KOTItem> = try {
            gson.fromJson(itemsJson, itemsType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val custNameIdx = cursor.getColumnIndexOrThrow("customer_name")
        val customerName = if (!cursor.isNull(custNameIdx)) cursor.getString(custNameIdx) else null

        return KOT(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            orderId = cursor.getLong(cursor.getColumnIndexOrThrow("order_id")),
            status = cursor.getString(cursor.getColumnIndexOrThrow("status")),
            items = items,
            tableNumber = cursor.getString(cursor.getColumnIndexOrThrow("table_number")),
            tableId = cursor.getString(cursor.getColumnIndexOrThrow("table_id")),
            round = cursor.getInt(cursor.getColumnIndexOrThrow("round_number")),
            roundName = cursor.getString(cursor.getColumnIndexOrThrow("round_name")),
            createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at")),
            customerName = customerName
        )
    }

    // -------------------------------------------------------------
    // ANALYTICS DATA LAYER
    // -------------------------------------------------------------

    /**
     * Settled dine-in revenue in paise.
     * Only dining tabs with status = 'SETTLED' contribute to this metric.
     * Open or payment-pending tabs are excluded to avoid counting unverified revenue.
     */
    fun getSettledDineInRevenue(): Long {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT COALESCE(SUM(total), 0) FROM $TABLE_DINING_TABS WHERE status IN (?, ?, ?)",
            arrayOf("SETTLED", "PAID", "CLOSED")
        )
        return try {
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        } catch (_: Exception) {
            0L
        } finally {
            cursor.close()
        }
    }

    /**
     * Eligible completed/confirmed delivery revenue in paise.
     * Orders with status 'Confirmed', 'Preparing', 'Out for delivery', 'Delivered' contribute.
     * Orders with status 'Cancelled' or 'Failed' are strictly excluded.
     */
    fun getEligibleDeliveryRevenue(): Long {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT quote_json FROM $TABLE_ORDERS WHERE status IN (?, ?, ?, ?) AND status NOT IN (?, ?)",
            arrayOf("Confirmed", "Preparing", "Out for delivery", "Delivered", "Cancelled", "Failed")
        )
        var totalPaise = 0L
        try {
            while (cursor.moveToNext()) {
                val json = cursor.getString(0)
                if (!json.isNullOrBlank()) {
                    try {
                        val quote = gson.fromJson(json, Quote::class.java)
                        if (quote != null) {
                            totalPaise += quote.total
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {
            return 0L
        } finally {
            cursor.close()
        }
        return totalPaise
    }

    /**
     * Average dine-in bill value in paise.
     * Calculated as (sum of total for SETTLED tabs) / (count of SETTLED tabs).
     * Returns 0L if no settled tabs exist.
     */
    fun getAverageDineInBillValue(): Long {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*), COALESCE(SUM(total), 0) FROM $TABLE_DINING_TABS WHERE status IN (?, ?, ?)",
            arrayOf("SETTLED", "PAID", "CLOSED")
        )
        return try {
            if (cursor.moveToFirst()) {
                val count = cursor.getLong(0)
                val sum = cursor.getLong(1)
                if (count > 0L) sum / count else 0L
            } else {
                0L
            }
        } catch (_: Exception) {
            0L
        } finally {
            cursor.close()
        }
    }

    /**
     * Average dining duration in minutes across settled dining tabs.
     * Uses real timestamps: session start timestamp is resolved from the earliest item orderedAt
     * (or associated table booking createdAt), and session end timestamp is resolved from tab updatedAt.
     *
     * Persisted fields note:
     * TABLE_DINING_TABS does not have a separate seated_at column; duration is accurately derived
     * from item order timestamps and settlement timestamps. Tabs lacking parseable timestamps
     * are excluded from the average rather than fabricating arbitrary durations.
     */
    fun getAverageDiningDurationMinutes(): Long {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT items_json, updated_at, booking_id, seated_at, billed_at FROM $TABLE_DINING_TABS WHERE status IN (?, ?, ?)",
            arrayOf("SETTLED", "PAID", "CLOSED")
        )
        val bookingsMap = try {
            getAllTableBookings().associateBy { it.id }
        } catch (_: Exception) {
            emptyMap()
        }

        var totalMinutes = 0L
        var validCount = 0

        try {
            val listType = object : TypeToken<List<TableBillItem>>() {}.type
            while (cursor.moveToNext()) {
                val itemsJson = cursor.getString(0)
                val updatedAtStr = cursor.getString(1)
                val bookingId = if (cursor.isNull(2)) null else cursor.getString(2)
                val seatedAtStr = if (cursor.isNull(3)) "" else cursor.getString(3)
                val billedAtStr = if (cursor.isNull(4)) "" else cursor.getString(4)

                val endInstant = (if (billedAtStr.isNotBlank()) try { Instant.parse(billedAtStr) } catch (_: Exception) { null } else null)
                    ?: (try { Instant.parse(updatedAtStr) } catch (_: Exception) { null })
                    ?: continue

                val items: List<TableBillItem> = try {
                    if (!itemsJson.isNullOrBlank()) gson.fromJson(itemsJson, listType) ?: emptyList() else emptyList()
                } catch (_: Exception) {
                    emptyList()
                }

                val earliestItemInstant = items.mapNotNull { item ->
                    try { Instant.parse(item.orderedAt) } catch (_: Exception) { null }
                }.minOrNull()

                val seatedInstant = if (seatedAtStr.isNotBlank()) {
                    try { Instant.parse(seatedAtStr) } catch (_: Exception) { null }
                } else null

                val bookingInstant = bookingId?.let { bookingsMap[it] }?.let { b ->
                    try { Instant.parse(b.createdAt) } catch (_: Exception) { null }
                }

                val startInstant = earliestItemInstant ?: seatedInstant ?: bookingInstant ?: continue
                val durationMinutes = Duration.between(startInstant, endInstant).toMinutes()
                if (durationMinutes >= 0) {
                    totalMinutes += durationMinutes
                    validCount++
                }
            }
        } catch (_: Exception) {
            return 0L
        } finally {
            cursor.close()
        }

        return if (validCount > 0) totalMinutes / validCount else 0L
    }

    /**
     * Table turnover rate: number of settled dining tabs divided by the total number of tables.
     */
    fun getTableTurnoverRate(): Double {
        val totalTables = RestaurantTableDefaults.DEFAULT_TABLES.size
        if (totalTables <= 0) return 0.0
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_DINING_TABS WHERE status IN (?, ?, ?)",
            arrayOf("SETTLED", "PAID", "CLOSED")
        )
        return try {
            val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
            count.toDouble() / totalTables.toDouble()
        } catch (_: Exception) {
            0.0
        } finally {
            cursor.close()
        }
    }

    /**
     * Total diners served, using genuine guest counts (party_size) where available.
     * Prevents duplicate counting:
     * 1. Settled tabs with a linked table booking count the booking's party_size.
     * 2. Settled walk-in tabs without a booking count 1 guest.
     * 3. Completed table bookings not linked to any settled tab count their party_size.
     * Unsettled tabs, unconfirmed bookings, and cancelled bookings are strictly excluded.
     */
    fun getTotalDinersServed(): Int {
        val db = readableDatabase
        var totalDiners = 0

        // 1. Settled tabs linked to table bookings: sum booking party_size (or guest_count)
        val cursorLinked = db.rawQuery(
            """
            SELECT COALESCE(SUM(CASE WHEN b.party_size > 0 THEN b.party_size ELSE CASE WHEN t.guest_count > 0 THEN t.guest_count ELSE 1 END END), 0)
            FROM $TABLE_DINING_TABS t
            INNER JOIN $TABLE_BOOKINGS b ON t.booking_id = b.id
            WHERE t.status IN ('SETTLED', 'PAID', 'CLOSED')
            """.trimIndent(),
            null
        )
        try {
            if (cursorLinked.moveToFirst()) {
                totalDiners += cursorLinked.getInt(0)
            }
        } catch (_: Exception) {} finally {
            cursorLinked.close()
        }

        // 2. Settled walk-in tabs without linked booking: use guest_count if > 0, else 1
        val cursorWalkIns = db.rawQuery(
            """
            SELECT COALESCE(SUM(CASE WHEN t.guest_count > 0 THEN t.guest_count ELSE 1 END), 0)
            FROM $TABLE_DINING_TABS t
            WHERE t.status IN ('SETTLED', 'PAID', 'CLOSED')
              AND (t.booking_id IS NULL OR t.booking_id = '' OR t.booking_id NOT IN (SELECT id FROM $TABLE_BOOKINGS))
            """.trimIndent(),
            null
        )
        try {
            if (cursorWalkIns.moveToFirst()) {
                totalDiners += cursorWalkIns.getInt(0)
            }
        } catch (_: Exception) {} finally {
            cursorWalkIns.close()
        }

        // 3. Completed table bookings not linked to any settled dining tab
        val cursorUnlinkedBookings = db.rawQuery(
            """
            SELECT COALESCE(SUM(b.party_size), 0)
            FROM $TABLE_BOOKINGS b
            WHERE b.status = ?
              AND b.id NOT IN (SELECT booking_id FROM $TABLE_DINING_TABS WHERE booking_id IS NOT NULL AND status IN ('SETTLED', 'PAID', 'CLOSED'))
            """.trimIndent(),
            arrayOf(TableBookingStatus.COMPLETED)
        )
        try {
            if (cursorUnlinkedBookings.moveToFirst()) {
                totalDiners += cursorUnlinkedBookings.getInt(0)
            }
        } catch (_: Exception) {} finally {
            cursorUnlinkedBookings.close()
        }

        return totalDiners
    }

    /**
     * Table occupancy using the existing table-allocation/status model.
     * Checks all default floor-plan tables using [getFloorTableStatus].
     */
    fun getTableOccupancy(restaurantId: String = ""): TableOccupancyMetrics {
        val tables = if (restaurantId.isNotBlank()) RestaurantTableDefaults.getTablesForRestaurant(restaurantId) else RestaurantTableDefaults.DEFAULT_TABLES
        val total = tables.size
        var occupied = 0
        for (table in tables) {
            if (getFloorTableStatus(table.tableNumber) == "Occupied") {
                occupied++
            }
        }
        val rate = if (total > 0) occupied.toDouble() / total.toDouble() else 0.0
        return TableOccupancyMetrics(
            occupiedTables = occupied,
            totalTables = total,
            occupancyRate = rate
        )
    }

    /**
     * Top five dishes across dine-in and delivery channels.
     * Aggregates genuine dish-level quantities:
     * - Dine-in: parsed from items_json in SETTLED dining_tabs.
     * - Delivery: parsed from quote_json lines in eligible delivery orders.
     */
    fun getTopFiveDishes(limit: Int = 5): List<TopDishStat> {
        val db = readableDatabase
        data class DishCounter(var name: String, var dineIn: Int = 0, var delivery: Int = 0)
        val dishMap = mutableMapOf<String, DishCounter>()

        // 1. Dine-in items from settled/finalized tabs
        val cursorTabs = db.rawQuery(
            "SELECT items_json FROM $TABLE_DINING_TABS WHERE status IN (?, ?, ?)",
            arrayOf("SETTLED", "PAID", "CLOSED")
        )
        try {
            val listType = object : TypeToken<List<TableBillItem>>() {}.type
            while (cursorTabs.moveToNext()) {
                val json = cursorTabs.getString(0)
                if (!json.isNullOrBlank()) {
                    try {
                        val items: List<TableBillItem> = gson.fromJson(json, listType) ?: emptyList()
                        for (item in items) {
                            val counter = dishMap.getOrPut(item.dishId) { DishCounter(item.name) }
                            counter.dineIn += item.quantity
                            if (counter.name.isBlank() && item.name.isNotBlank()) counter.name = item.name
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {} finally {
            cursorTabs.close()
        }

        // 2. Delivery lines from eligible orders
        val cursorOrders = db.rawQuery(
            "SELECT quote_json FROM $TABLE_ORDERS WHERE status IN (?, ?, ?, ?) AND status NOT IN (?, ?)",
            arrayOf("Confirmed", "Preparing", "Out for delivery", "Delivered", "Cancelled", "Failed")
        )
        try {
            while (cursorOrders.moveToNext()) {
                val json = cursorOrders.getString(0)
                if (!json.isNullOrBlank()) {
                    try {
                        val quote = gson.fromJson(json, Quote::class.java)
                        if (quote != null) {
                            for (line in quote.lines) {
                                val counter = dishMap.getOrPut(line.dishId) { DishCounter(line.name) }
                                counter.delivery += line.quantity
                                if (counter.name.isBlank() && line.name.isNotBlank()) counter.name = line.name
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {} finally {
            cursorOrders.close()
        }

        return dishMap.entries
            .map { (dishId, counter) ->
                TopDishStat(
                    dishId = dishId,
                    name = counter.name,
                    totalQuantity = counter.dineIn + counter.delivery,
                    dineInQuantity = counter.dineIn,
                    deliveryQuantity = counter.delivery
                )
            }
            .filter { it.totalQuantity > 0 }
            .sortedWith(
                compareByDescending<TopDishStat> { it.totalQuantity }
                    .thenBy { it.name }
            )
            .take(limit)
    }

    /**
     * Aggregated analytics summary covering all dine-in and delivery metrics.
     */
    fun getAnalyticsSummary(): AnalyticsSummary {
        val dineInRev = getSettledDineInRevenue()
        val deliveryRev = getEligibleDeliveryRevenue()
        return AnalyticsSummary(
            settledDineInRevenue = dineInRev,
            eligibleDeliveryRevenue = deliveryRev,
            totalRevenue = dineInRev + deliveryRev,
            averageDineInBillValue = getAverageDineInBillValue(),
            averageDiningDurationMinutes = getAverageDiningDurationMinutes(),
            tableTurnoverRate = getTableTurnoverRate(),
            totalDinersServed = getTotalDinersServed(),
            tableOccupancy = getTableOccupancy(),
            topDishes = getTopFiveDishes(5)
        )
    }

    // -------------------------------------------------------------
    // ADMIN MENU & PRICE MANAGEMENT
    // -------------------------------------------------------------

    fun updateDishPriceAndAvailability(dishId: String, newPrice: Long, isAvailable: Boolean): Boolean {
        require(newPrice >= 0L) { "Price cannot be negative" }
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("price", newPrice)
            put("available", if (isAvailable) 1 else 0)
        }
        val affected = db.update(TABLE_DISHES, cv, "id = ?", arrayOf(dishId.trim()))
        return affected > 0
    }

    // -------------------------------------------------------------
    // INVENTORY & STOCK MOVEMENTS (IDEMPOTENT & TRACKED)
    // -------------------------------------------------------------

    fun createInventoryTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_INVENTORY (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                category TEXT NOT NULL,
                unit TEXT NOT NULL,
                current_stock REAL NOT NULL,
                reorder_level REAL NOT NULL,
                cost_per_unit INTEGER NOT NULL,
                updated_at TEXT NOT NULL
            );
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_STOCK_MOVEMENTS (
                id TEXT PRIMARY KEY,
                item_id TEXT NOT NULL,
                item_name TEXT NOT NULL,
                delta REAL NOT NULL,
                reason TEXT NOT NULL,
                reference_id TEXT NOT NULL DEFAULT '',
                actor TEXT NOT NULL DEFAULT 'System',
                created_at TEXT NOT NULL
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_stock_movements_ref ON $TABLE_STOCK_MOVEMENTS (reference_id);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_stock_movements_item ON $TABLE_STOCK_MOVEMENTS (item_id);")
    }

    fun seedDefaultInventoryIfEmpty() {
        val db = writableDatabase
        createInventoryTables(db)
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_INVENTORY", null)
        val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        if (count > 0) return

        val defaultStock = listOf(
            InventoryItem("inv-1", "Paneer (Fresh Malai)", "Dairy", "kg", 25.0, 5.0, 32000L),
            InventoryItem("inv-2", "Basmati Rice (Royal)", "Grains", "kg", 80.0, 20.0, 11000L),
            InventoryItem("inv-3", "Amul Butter (Pasteurized)", "Dairy", "kg", 18.5, 4.0, 48000L),
            InventoryItem("inv-4", "Whole Spices Blend (Garam Masala)", "Spices", "kg", 6.0, 2.0, 95000L),
            InventoryItem("inv-5", "Fresh Chicken (Boneless)", "Poultry", "kg", 30.0, 10.0, 24000L),
            InventoryItem("inv-6", "Cooking Oil (Cold Pressed Mustard)", "Oils", "L", 50.0, 15.0, 16000L)
        )
        val nowStr = Instant.now().toString()
        defaultStock.forEach { item ->
            val cv = ContentValues().apply {
                put("id", item.id)
                put("name", item.name)
                put("category", item.category)
                put("unit", item.unit)
                put("current_stock", item.currentStock)
                put("reorder_level", item.reorderLevel)
                put("cost_per_unit", item.costPerUnitPaise)
                put("updated_at", nowStr)
            }
            db.insertWithOnConflict(TABLE_INVENTORY, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun getAllInventoryItems(): List<InventoryItem> {
        val db = readableDatabase
        createInventoryTables(db)
        seedDefaultInventoryIfEmpty()
        val list = mutableListOf<InventoryItem>()
        val cursor = db.rawQuery("SELECT * FROM $TABLE_INVENTORY ORDER BY name ASC", null)
        while (cursor.moveToNext()) {
            list.add(
                InventoryItem(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    category = cursor.getString(cursor.getColumnIndexOrThrow("category")),
                    unit = cursor.getString(cursor.getColumnIndexOrThrow("unit")),
                    currentStock = cursor.getDouble(cursor.getColumnIndexOrThrow("current_stock")),
                    reorderLevel = cursor.getDouble(cursor.getColumnIndexOrThrow("reorder_level")),
                    costPerUnitPaise = cursor.getLong(cursor.getColumnIndexOrThrow("cost_per_unit")),
                    updatedAt = cursor.getString(cursor.getColumnIndexOrThrow("updated_at"))
                )
            )
        }
        cursor.close()
        return list
    }

    fun recordStockMovement(
        itemId: String,
        delta: Double,
        reason: String,
        referenceId: String = "",
        actor: String = "Admin"
    ): Boolean {
        val db = writableDatabase
        createInventoryTables(db)
        db.beginTransaction()
        try {
            val cursor = db.rawQuery("SELECT name, current_stock FROM $TABLE_INVENTORY WHERE id = ? LIMIT 1", arrayOf(itemId))
            if (!cursor.moveToFirst()) {
                cursor.close()
                return false
            }
            val name = cursor.getString(0)
            val currentStock = cursor.getDouble(1)
            cursor.close()

            val newStock = (currentStock + delta).coerceAtLeast(0.0)
            val nowStr = Instant.now().toString()

            val cvInv = ContentValues().apply {
                put("current_stock", newStock)
                put("updated_at", nowStr)
            }
            db.update(TABLE_INVENTORY, cvInv, "id = ?", arrayOf(itemId))

            val cvMov = ContentValues().apply {
                put("id", "MOV-${UUID.randomUUID().toString().take(8).uppercase()}")
                put("item_id", itemId)
                put("item_name", name)
                put("delta", delta)
                put("reason", reason)
                put("reference_id", referenceId)
                put("actor", actor)
                put("created_at", nowStr)
            }
            db.insert(TABLE_STOCK_MOVEMENTS, null, cvMov)

            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    fun consumeInventoryForOrderIdempotent(orderId: String, lines: List<QuoteLine>): Boolean {
        val db = writableDatabase
        createInventoryTables(db)
        seedDefaultInventoryIfEmpty()

        // Idempotency check: if this order already deducted stock, do not deduct again
        val checkCursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_STOCK_MOVEMENTS WHERE reference_id = ? AND reason = 'ORDER_CONSUMPTION'",
            arrayOf(orderId)
        )
        val alreadyConsumed = if (checkCursor.moveToFirst()) checkCursor.getInt(0) > 0 else false
        checkCursor.close()
        if (alreadyConsumed) return true

        val invList = getAllInventoryItems()
        if (invList.isEmpty()) return false

        db.beginTransaction()
        try {
            // Deduct proportional portions for dishes ordered
            for (line in lines) {
                val qty = line.quantity.toDouble()
                val targetItem = when {
                    line.name.contains("Paneer", ignoreCase = true) -> invList.find { it.id == "inv-1" }
                    line.name.contains("Biryani", ignoreCase = true) || line.name.contains("Rice", ignoreCase = true) -> invList.find { it.id == "inv-2" }
                    line.name.contains("Butter", ignoreCase = true) || line.name.contains("Dal", ignoreCase = true) -> invList.find { it.id == "inv-3" }
                    line.name.contains("Chicken", ignoreCase = true) -> invList.find { it.id == "inv-5" }
                    else -> invList.firstOrNull()
                }

                if (targetItem != null) {
                    val deduction = 0.25 * qty // 0.25 kg per portion
                    recordStockMovement(
                        itemId = targetItem.id,
                        delta = -deduction,
                        reason = "ORDER_CONSUMPTION",
                        referenceId = orderId,
                        actor = "Automated POS Engine"
                    )
                }
            }
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    // -------------------------------------------------------------
    // AUDIT LOGS (SECURITY & ADMINISTRATIVE ACTIONS)
    // -------------------------------------------------------------

    fun createAuditLogsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_AUDIT_LOGS (
                id TEXT PRIMARY KEY,
                action TEXT NOT NULL,
                actor_email TEXT NOT NULL,
                actor_role TEXT NOT NULL,
                target_id TEXT NOT NULL,
                details TEXT NOT NULL,
                created_at TEXT NOT NULL
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_audit_logs_actor ON $TABLE_AUDIT_LOGS (actor_email);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON $TABLE_AUDIT_LOGS (action);")
    }

    fun recordAuditLog(
        action: String,
        actorEmail: String,
        actorRole: String,
        targetId: String,
        details: String
    ) {
        val db = writableDatabase
        createAuditLogsTable(db)
        val cv = ContentValues().apply {
            put("id", "AUD-${UUID.randomUUID().toString().take(8).uppercase()}")
            put("action", action)
            put("actor_email", actorEmail)
            put("actor_role", actorRole)
            put("target_id", targetId)
            put("details", details)
            put("created_at", Instant.now().toString())
        }
        db.insert(TABLE_AUDIT_LOGS, null, cv)
    }

    fun getAllAuditLogs(limit: Int = 50): List<AuditLogEntry> {
        val db = readableDatabase
        createAuditLogsTable(db)
        val list = mutableListOf<AuditLogEntry>()
        val cursor = db.rawQuery("SELECT * FROM $TABLE_AUDIT_LOGS ORDER BY created_at DESC LIMIT ?", arrayOf(limit.toString()))
        while (cursor.moveToNext()) {
            list.add(
                AuditLogEntry(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    action = cursor.getString(cursor.getColumnIndexOrThrow("action")),
                    actorEmail = cursor.getString(cursor.getColumnIndexOrThrow("actor_email")),
                    actorRole = cursor.getString(cursor.getColumnIndexOrThrow("actor_role")),
                    targetId = cursor.getString(cursor.getColumnIndexOrThrow("target_id")),
                    details = cursor.getString(cursor.getColumnIndexOrThrow("details")),
                    createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at"))
                )
            )
        }
        cursor.close()
        return list
    }

    // -------------------------------------------------------------
    // PARTNER DOCUMENT VERIFICATION (RIDER DL / VEHICLE & ADMIN FSSAI / GST)
    // -------------------------------------------------------------

    fun savePartnerVerification(record: PartnerVerificationRecord) {
        firebasePrefs.edit().putString("partner_verify_${record.userId}", gson.toJson(record)).apply()
        syncToFirebaseFirestore(
            "partner_verifications",
            record.userId,
            mapOf(
                "userId" to record.userId,
                "role" to record.role,
                "vehicleNumber" to record.vehicleNumber,
                "drivingLicenseUri" to record.drivingLicenseUri,
                "fssaiNumber" to record.fssaiNumber,
                "fssaiCertUri" to record.fssaiCertUri,
                "gstNumber" to record.gstNumber,
                "gstCertUri" to record.gstCertUri,
                "restaurantName" to record.restaurantName,
                "restaurantLogoUri" to record.restaurantLogoUri,
                "verificationStatus" to record.verificationStatus,
                "extractedSummary" to record.extractedSummary,
                "verifiedAt" to record.verifiedAt
            )
        )
    }

    fun getPartnerVerification(userId: String): PartnerVerificationRecord? {
        val raw = firebasePrefs.getString("partner_verify_$userId", null) ?: return null
        return runCatching { gson.fromJson(raw, PartnerVerificationRecord::class.java) }.getOrNull()
    }

    // -------------------------------------------------------------
    // RIDER EARNINGS, PAYOUTS, RIDE OFFERS & REJECTED RIDE FILTER (FIREBASE SYNCED)
    // -------------------------------------------------------------

    fun getRiderRides(riderId: String): List<RiderRideRecord> {
        val key = "rider_rides_$riderId"
        val raw = firebasePrefs.getString(key, null)
        if (raw.isNullOrBlank()) {
            val now = System.currentTimeMillis()
            val hourMs = 3_600_000L
            val dayMs = 86_400_000L
            val seeded = listOf(
                RiderRideRecord("ride-101", riderId, "ORD-901", "Bistro Lumière & Biryani", "Oberoi Woods, Goregaon East", 3.2, 24, 75L, now - 1 * hourMs),
                RiderRideRecord("ride-102", riderId, "ORD-902", "Nori & Ember", "Mindspace, Malad West", 4.1, 28, 92L, now - 3 * hourMs),
                RiderRideRecord("ride-103", riderId, "ORD-903", "Verde & Co.", "Lokhandwala, Andheri West", 2.8, 22, 68L, now - 5 * hourMs),
                RiderRideRecord("ride-104", riderId, "ORD-890", "Dakshin Coastal", "Inorbit Mall Road, Malad", 3.6, 25, 84L, now - 2 * dayMs),
                RiderRideRecord("ride-105", riderId, "ORD-885", "La Piazza Napoletana", "Oshiwara Link Road", 5.0, 31, 110L, now - 3 * dayMs),
                RiderRideRecord("ride-106", riderId, "ORD-879", "Bistro Lumière & Biryani", "Film City Road, Goregaon", 4.4, 27, 96L, now - 5 * dayMs),
                RiderRideRecord("ride-107", riderId, "ORD-840", "Nori & Ember", "JVLR Powai Crossing", 6.2, 35, 135L, now - 12 * dayMs),
                RiderRideRecord("ride-108", riderId, "ORD-825", "Dakshin Coastal", "Marve Road, Malad West", 3.9, 26, 88L, now - 18 * dayMs),
                RiderRideRecord("ride-109", riderId, "ORD-812", "La Piazza Napoletana", "Versova Metro, Andheri", 4.7, 29, 104L, now - 24 * dayMs)
            )
            firebasePrefs.edit().putString(key, gson.toJson(seeded)).apply()
            seeded.forEach { r ->
                syncToFirebaseFirestore(
                    "rider_rides",
                    r.id,
                    mapOf(
                        "id" to r.id,
                        "riderId" to r.riderId,
                        "orderId" to r.orderId,
                        "restaurantName" to r.restaurantName,
                        "dropAddress" to r.dropAddress,
                        "distanceKm" to r.distanceKm,
                        "durationMinutes" to r.durationMinutes,
                        "incomeRupees" to r.incomeRupees,
                        "completedAtMs" to r.completedAtMs
                    )
                )
            }
            return seeded
        }
        val type = object : TypeToken<List<RiderRideRecord>>() {}.type
        return runCatching { gson.fromJson<List<RiderRideRecord>>(raw, type) ?: emptyList() }.getOrDefault(emptyList())
    }

    fun recordRiderCompletedRide(ride: RiderRideRecord) {
        val current = getRiderRides(ride.riderId).toMutableList()
        current.add(0, ride)
        firebasePrefs.edit().putString("rider_rides_${ride.riderId}", gson.toJson(current)).apply()
        syncToFirebaseFirestore(
            "rider_rides",
            ride.id,
            mapOf(
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
        )
    }

    fun getRiderPayouts(riderId: String): List<RiderPayoutRecord> {
        val raw = firebasePrefs.getString("rider_payouts_$riderId", null) ?: return emptyList()
        val type = object : TypeToken<List<RiderPayoutRecord>>() {}.type
        return runCatching { gson.fromJson<List<RiderPayoutRecord>>(raw, type) ?: emptyList() }.getOrDefault(emptyList())
    }

    fun recordRiderPayout(payout: RiderPayoutRecord) {
        val current = getRiderPayouts(payout.riderId).toMutableList()
        current.add(0, payout)
        firebasePrefs.edit().putString("rider_payouts_${payout.riderId}", gson.toJson(current)).apply()
        syncToFirebaseFirestore(
            "rider_payouts",
            payout.id,
            mapOf(
                "id" to payout.id,
                "riderId" to payout.riderId,
                "amountRupees" to payout.amountRupees,
                "accountDestination" to payout.accountDestination,
                "status" to payout.status,
                "timestampMs" to payout.timestampMs
            )
        )
    }

    fun getRejectedRideIds(riderId: String): Set<String> {
        return firebasePrefs.getStringSet("rider_rejected_$riderId", emptySet()) ?: emptySet()
    }

    fun rejectRideOffer(riderId: String, offerId: String) {
        val updated = getRejectedRideIds(riderId).toMutableSet().apply { add(offerId) }
        firebasePrefs.edit().putStringSet("rider_rejected_$riderId", updated).apply()
        syncToFirebaseFirestore(
            "rider_rejected_offers",
            "${riderId}_$offerId",
            mapOf("riderId" to riderId, "offerId" to offerId, "rejectedAtMs" to System.currentTimeMillis())
        )
    }

    fun getCompletedOfferIds(riderId: String): Set<String> {
        return firebasePrefs.getStringSet("rider_completed_offers_$riderId", emptySet()) ?: emptySet()
    }

    fun markRideOfferCompleted(riderId: String, offer: RiderRideOffer) {
        val updated = getCompletedOfferIds(riderId).toMutableSet().apply { add(offer.id) }
        firebasePrefs.edit().putStringSet("rider_completed_offers_$riderId", updated).apply()
        recordRiderCompletedRide(
            RiderRideRecord(
                id = "ride-${System.currentTimeMillis()}",
                riderId = riderId,
                orderId = offer.orderId,
                restaurantName = offer.restaurantName,
                dropAddress = offer.dropAddress,
                distanceKm = offer.distanceKm,
                durationMinutes = offer.estimatedMinutes,
                incomeRupees = offer.riderIncomeRupees,
                completedAtMs = System.currentTimeMillis()
            )
        )
    }

    fun getAvailableRideOffers(riderId: String): List<RiderRideOffer> {
        val rejected = getRejectedRideIds(riderId)
        val completed = getCompletedOfferIds(riderId)

        val liveOrderOffers = getAllOrders()
            .filter { it.status != DeliveryOrderStatus.DELIVERED && it.status != DeliveryOrderStatus.CANCELLED }
            .mapIndexed { idx, order ->
                val distKm = ((idx + 2) * 1.3).let { Math.round(it * 10.0) / 10.0 }
                val estMins = 22 + (distKm * 3.5).toInt()
                val payout = (45L + (distKm * 12).toLong())
                RiderRideOffer(
                    id = "offer-${order.id}",
                    orderId = order.id,
                    restaurantId = order.restaurantId,
                    restaurantName = order.restaurantName,
                    pickupAddress = "Kitchen Pickup · ${order.restaurantName}",
                    dropAddress = "${order.address.line}, ${order.address.city}",
                    distanceKm = distKm,
                    estimatedMinutes = estMins,
                    riderIncomeRupees = payout
                )
            }

        val defaultDispatchPool = listOf(
            RiderRideOffer(
                id = "offer-dispatch-201",
                orderId = "ORD-201",
                restaurantId = "rest-1",
                restaurantName = "Bistro Lumière & Biryani",
                pickupAddress = "Plot 14, Link Road, Goregaon West",
                dropAddress = "Tower B, Oberoi Exquisite, Goregaon East",
                distanceKm = 3.4,
                estimatedMinutes = 22 + 12,
                riderIncomeRupees = 82L
            ),
            RiderRideOffer(
                id = "offer-dispatch-202",
                orderId = "ORD-202",
                restaurantId = "rest-2",
                restaurantName = "Nori & Ember",
                pickupAddress = "Mindspace Junction, Malad West",
                dropAddress = "Raheja Classic, Lokhandwala, Andheri West",
                distanceKm = 4.6,
                estimatedMinutes = 23 + 16,
                riderIncomeRupees = 105L
            ),
            RiderRideOffer(
                id = "offer-dispatch-203",
                orderId = "ORD-203",
                restaurantId = "rest-3",
                restaurantName = "Verde & Co.",
                pickupAddress = "SV Road, Kandivali West",
                dropAddress = "Thakur Village, Kandivali East",
                distanceKm = 2.9,
                estimatedMinutes = 20 + 10,
                riderIncomeRupees = 70L
            ),
            RiderRideOffer(
                id = "offer-dispatch-204",
                orderId = "ORD-204",
                restaurantId = "rest-4",
                restaurantName = "Dakshin Coastal",
                pickupAddress = "Orlem Church Road, Malad West",
                dropAddress = "Evershine Nagar, Malad West",
                distanceKm = 1.8,
                estimatedMinutes = 21 + 6,
                riderIncomeRupees = 58L
            ),
            RiderRideOffer(
                id = "offer-dispatch-205",
                orderId = "ORD-205",
                restaurantId = "rest-5",
                restaurantName = "La Piazza Napoletana",
                pickupAddress = "Juhu Tara Road, Juhu",
                dropAddress = "7 Bungalows, Versova, Andheri West",
                distanceKm = 5.2,
                estimatedMinutes = 24 + 18,
                riderIncomeRupees = 118L
            )
        )

        return (liveOrderOffers + defaultDispatchPool)
            .distinctBy { it.id }
            .filter { it.id !in rejected && it.id !in completed }
    }
}
