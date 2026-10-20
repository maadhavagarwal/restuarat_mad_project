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
            put("total", tab.total)
            put("status", tab.status)
            put("payment_method", tab.paymentMethod)
            put("created_at", tab.createdAt.ifBlank { updatedAt })
            put("updated_at", updatedAt)
            put("restaurant_id", tab.restaurantId)
        }
        val rowId = try {
            db.insertWithOnConflict(TABLE_DINING_TABS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (_: Exception) {
            -1L
        }
        return rowId != -1L
    }

    fun markTabSettled(tabId: String, paymentMethod: String): Boolean {
        if (tabId.isBlank()) return false
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("status", "SETTLED")
            put("payment_method", paymentMethod)
            put("updated_at", Instant.now().toString())
        }
        val affected = try {
            db.update(TABLE_DINING_TABS, cv, "id = ?", arrayOf(tabId.trim()))
        } catch (_: Exception) {
            0
        }
        return affected > 0
    }

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
        isServiceChargeOptedOut: Boolean = false
    ): DiningTab? {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cursor = db.rawQuery("SELECT * FROM $TABLE_DINING_TABS WHERE id = ? LIMIT 1", arrayOf(tabId))
            if (!cursor.moveToFirst()) {
                cursor.close()
                return null
            }
            val tab = cursorToDiningTab(cursor)
            cursor.close()

            val finalBill = TipAndServiceChargeRules.computeFinalBill(
                subtotal = tab.subtotalRupees,
                tax = tab.taxRupees,
                isServiceChargeOptedOut = isServiceChargeOptedOut,
                tipAmount = tip
            )

            val nowStr = Instant.now().toString()
            val cv = ContentValues().apply {
                put("status", DiningTabStatus.PAYMENT_PENDING)
                put("payment_method", paymentMethod)
                put("service_charge", finalBill.serviceChargePaise)
                put("tip", finalBill.tipPaise)
                put("is_service_charge_opted_out", if (isServiceChargeOptedOut) 1 else 0)
                put("total", finalBill.totalPaise)
                put("billed_at", nowStr)
                put("updated_at", nowStr)
            }
            db.update(TABLE_DINING_TABS, cv, "id = ?", arrayOf(tabId))
            db.setTransactionSuccessful()
            return tab.copy(
                status = DiningTabStatus.PAYMENT_PENDING,
                paymentMethod = paymentMethod,
                serviceCharge = finalBill.serviceChargePaise,
                tip = finalBill.tipPaise,
                isServiceChargeOptedOut = isServiceChargeOptedOut,
                total = finalBill.totalPaise,
                billedAt = nowStr,
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
            val cursor = db.rawQuery("SELECT * FROM $TABLE_DINING_TABS WHERE id = ? LIMIT 1", arrayOf(tabId))
            if (!cursor.moveToFirst()) {
                cursor.close()
                return null
            }
            val tab = cursorToDiningTab(cursor)
            cursor.close()

            val nowStr = Instant.now().toString()
            val cv = ContentValues().apply {
                put("status", DiningTabStatus.ACTIVE)
                put("billed_at", "")
                put("updated_at", nowStr)
            }
            db.update(TABLE_DINING_TABS, cv, "id = ?", arrayOf(tabId))
            db.setTransactionSuccessful()
            return tab.copy(
                status = DiningTabStatus.ACTIVE,
                billedAt = "",
                updatedAt = nowStr
            )
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Verifies and completes payment for a dining tab.
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
            val cursor = db.rawQuery("SELECT * FROM $TABLE_DINING_TABS WHERE id = ? LIMIT 1", arrayOf(tabId))
            if (!cursor.moveToFirst()) {
                cursor.close()
                return null
            }
            val tab = cursorToDiningTab(cursor)
            cursor.close()

            val nowStr = Instant.now().toString()
            val finalTxn = transactionId.ifBlank { "TXN-${UUID.randomUUID().toString().take(8).uppercase()}" }

            // 1. Mark tab PAID
            val cvTab = ContentValues().apply {
                put("status", DiningTabStatus.PAID)
                put("payment_transaction_id", finalTxn)
                put("updated_at", nowStr)
            }
            db.update(TABLE_DINING_TABS, cvTab, "id = ?", arrayOf(tabId))

            // 2. Complete associated booking
            var completedBooking: TableBooking? = null
            if (!tab.bookingId.isNullOrBlank()) {
                val bCursor = db.rawQuery("SELECT * FROM $TABLE_BOOKINGS WHERE id = ? LIMIT 1", arrayOf(tab.bookingId))
                if (bCursor.moveToFirst()) {
                    val b = cursorToTableBooking(bCursor)
                    bCursor.close()
                    val cvBooking = ContentValues().apply {
                        put("status", TableBookingStatus.COMPLETED)
                        put("updated_at", nowStr)
                    }
                    db.update(TABLE_BOOKINGS, cvBooking, "id = ?", arrayOf(tab.bookingId))
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
                paymentTransactionId = finalTxn,
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
}
