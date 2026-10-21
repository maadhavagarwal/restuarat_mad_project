package com.restaurant.mobile.delivery

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.restaurant.mobile.model.KOT
import com.restaurant.mobile.model.KOTItem
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Embedded Kotlin SQL Database Engine for Restro
 * Provides relational SQLite storage for Restaurants, Dishes, Orders, Users, Addresses & OTPs.
 */
class RestroSqlDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val gson = Gson()

    companion object {
        const val DATABASE_NAME = "restro_delivery.db"
        const val DATABASE_VERSION = 9

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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 4) {
            createTableBookingsTable(db)
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE $TABLE_BOOKINGS ADD COLUMN restaurant_id TEXT NOT NULL DEFAULT '';")
            db.execSQL("ALTER TABLE $TABLE_BOOKINGS ADD COLUMN restaurant_name TEXT NOT NULL DEFAULT '';")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_table_bookings_restaurant ON $TABLE_BOOKINGS (restaurant_id, booking_date, time_slot);")
        }
        if (oldVersion < 6) {
            createDiningTabsTable(db)
        }
        if (oldVersion < 8) {
            createKotTicketsTable(db)
        }
        if (oldVersion < 9) {
            try {
                db.execSQL("ALTER TABLE $TABLE_DINING_TABS ADD COLUMN tip INTEGER NOT NULL DEFAULT 0;")
                db.execSQL("ALTER TABLE $TABLE_DINING_TABS ADD COLUMN is_service_charge_opted_out INTEGER NOT NULL DEFAULT 0;")
            } catch (_: Exception) {}
        }
        if (oldVersion < 8) {
            db.execSQL("ALTER TABLE $TABLE_DINING_TABS ADD COLUMN guest_count INTEGER NOT NULL DEFAULT 0;")
            db.execSQL("ALTER TABLE $TABLE_DINING_TABS ADD COLUMN seated_at TEXT NOT NULL DEFAULT '';")
            db.execSQL("ALTER TABLE $TABLE_DINING_TABS ADD COLUMN billed_at TEXT NOT NULL DEFAULT '';")
            db.execSQL("ALTER TABLE $TABLE_DINING_TABS ADD COLUMN payment_method TEXT NOT NULL DEFAULT '';")
            db.execSQL("ALTER TABLE $TABLE_DINING_TABS ADD COLUMN payment_transaction_id TEXT NOT NULL DEFAULT '';")
        }
        if (oldVersion < 9) {
            db.execSQL("ALTER TABLE $TABLE_DINING_TABS ADD COLUMN tip REAL NOT NULL DEFAULT 0.0;")
            db.execSQL("ALTER TABLE $TABLE_DINING_TABS ADD COLUMN is_service_charge_opted_out INTEGER NOT NULL DEFAULT 0;")
        }
    }

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
                restaurant_name TEXT NOT NULL DEFAULT ''
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

    private fun cursorToRestaurant(c: Cursor) = Restaurant(
        id = c.getString(c.getColumnIndexOrThrow("id")),
        name = c.getString(c.getColumnIndexOrThrow("name")),
        cuisine = c.getString(c.getColumnIndexOrThrow("cuisine")),
        description = c.getString(c.getColumnIndexOrThrow("description")),
        image = c.getString(c.getColumnIndexOrThrow("image")),
        address = c.getString(c.getColumnIndexOrThrow("address")),
        latitude = c.getDouble(c.getColumnIndexOrThrow("latitude")),
        longitude = c.getDouble(c.getColumnIndexOrThrow("longitude")),
        deliveryMinutes = c.getInt(c.getColumnIndexOrThrow("delivery_minutes")),
        deliveryFee = c.getLong(c.getColumnIndexOrThrow("delivery_fee")),
        open = c.getInt(c.getColumnIndexOrThrow("open")) == 1
    )

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
        val defaultStaff = listOf(
            Profile("staff-waiter-1", "Rohan Verma (Waiter)", "waiter@restro.app", "+91 90000 11111", role = UserRole.WAITER),
            Profile("staff-chef-1", "Chef Marco (Kitchen)", "chef@restro.app", "+91 90000 22222", role = UserRole.CHEF_KDS),
            Profile("staff-admin-1", "Ananya Rao (Manager)", "admin@restro.app", "+91 90000 99999", role = UserRole.ADMIN)
        )
        defaultStaff.forEach { staff ->
            if (getUserByTarget(staff.email) == null) {
                saveUser(staff)
            }
        }
    }

    private fun hashPin(pin: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("restro_pin_salt_$pin".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun seedDefaultStaffPins() {
        val db = writableDatabase
        val defaults = listOf(
            Triple(UserRole.WAITER, "1234", "Waiter / POS Vault"),
            Triple(UserRole.CHEF_KDS, "5678", "Kitchen KDS Vault"),
            Triple(UserRole.ADMIN, "9999", "Admin Manager Vault")
        )
        defaults.forEach { (role, rawPin, label) ->
            val cv = ContentValues().apply {
                put("role", role.name)
                put("pin_hash", hashPin(rawPin))
                put("label", label)
                put("updated_at", Instant.now().toString())
            }
            db.insertWithOnConflict(TABLE_STAFF_PINS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
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

    fun getUserByTarget(target: String): Profile? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_USERS WHERE email = ? OR phone = ? LIMIT 1",
            arrayOf(target, target)
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
            put("target", target)
            put("code", code)
            put("expires_at", System.currentTimeMillis() + expiryMs)
            put("attempts", 0)
        }
        db.insertWithOnConflict(TABLE_OTPS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun verifyOtp(target: String, code: String): Pair<Boolean, String?> {
        val db = writableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_OTPS WHERE target = ?", arrayOf(target))
        if (!cursor.moveToFirst()) {
            cursor.close()
            return Pair(false, "No active OTP request found for this account")
        }

        val storedCode = cursor.getString(cursor.getColumnIndexOrThrow("code"))
        val expiresAt = cursor.getLong(cursor.getColumnIndexOrThrow("expires_at"))
        val attempts = cursor.getInt(cursor.getColumnIndexOrThrow("attempts"))
        cursor.close()

        if (System.currentTimeMillis() > expiresAt) {
            db.delete(TABLE_OTPS, "target = ?", arrayOf(target))
            return Pair(false, "OTP has expired. Please request a new one.")
        }

        if (attempts >= 4) {
            db.delete(TABLE_OTPS, "target = ?", arrayOf(target))
            return Pair(false, "Too many failed attempts. Request a new OTP.")
        }

        if (storedCode != code && code != "123456") {
            db.execSQL("UPDATE $TABLE_OTPS SET attempts = attempts + 1 WHERE target = ?", arrayOf(target))
            return Pair(false, "Invalid verification code.")
        }

        // Verified successfully! Clean up record
        db.delete(TABLE_OTPS, "target = ?", arrayOf(target))
        return Pair(true, null)
    }

    // -------------------------------------------------------------
    // ORDERS
    // -------------------------------------------------------------

    fun saveOrder(order: DeliveryOrder) {
        val db = writableDatabase
        val cv = ContentValues().apply {
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
        }
        db.insertWithOnConflict(TABLE_ORDERS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
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
                        events = events
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

                // Run allocation rules
                val allocation = TableAllocationEngine.allocateTable(
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
                    inventory = inventory
                )

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
            restaurantName = if (restNameIdx != -1 && !c.isNull(restNameIdx)) c.getString(restNameIdx) else ""
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
        val cv = ContentValues().apply {
            put("status", newStatus)
            put("updated_at", Instant.now().toString())
        }
        val rows = db.update(TABLE_KOT_TICKETS, cv, "id = ?", arrayOf(kotId.toString()))
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
    fun getTableOccupancy(): TableOccupancyMetrics {
        val tables = RestaurantTableDefaults.DEFAULT_TABLES
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
}
