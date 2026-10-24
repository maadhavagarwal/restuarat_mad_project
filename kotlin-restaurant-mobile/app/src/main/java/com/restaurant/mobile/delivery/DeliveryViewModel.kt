package com.restaurant.mobile.delivery

import android.app.Application
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.Locale
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AuthResult(val token: String, val profile: Profile)

object MockCatalog {
    val sample = Catalog(
        restaurants = listOf(
            Restaurant(
                id = "rest-1",
                name = "Bistro Lumière & Biryani",
                cuisine = "Awadhi · Mughlai · Biryani",
                description = "Artisanal dum cooked biryanis and royal kebabs with slow-cooked aromatic spices.",
                image = "https://images.unsplash.com/photo-1589302168068-964664d93dc0?w=800&auto=format&fit=crop",
                address = "SV Road, Near Station, Goregaon West, Mumbai",
                latitude = 19.1663,
                longitude = 72.8480,
                deliveryMinutes = 25,
                deliveryFee = 3500,
                open = true,
                rating = 4.9,
                reviewCount = 2340,
                costForTwo = 950,
                vibes = listOf("Fine Dining & Luxury", "Royal & Heritage", "Family & Kids Friendly", "Romantic Candlelight"),
                amenities = listOf("AC Dining", "Valet Parking", "Live Sufi Music", "Private Dining Booths"),
                tableBookingAvailable = true,
                maxPartySize = 16
            ),
            Restaurant(
                id = "rest-2",
                name = "Trattoria Milano",
                cuisine = "Italian · Woodfired Pizza · Pasta",
                description = "Authentic Neapolitan hand-stretched sourdough pizzas and homemade fettuccine.",
                image = "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=800&auto=format&fit=crop",
                address = "Lokhandwala Complex, Andheri West, Mumbai",
                latitude = 19.1412,
                longitude = 72.8290,
                deliveryMinutes = 30,
                deliveryFee = 4500,
                open = true,
                rating = 4.8,
                reviewCount = 1650,
                costForTwo = 1200,
                vibes = listOf("Rooftop & City View", "Romantic Candlelight", "Fine Dining & Luxury", "Outdoor Garden Seating"),
                amenities = listOf("AC Dining", "Woodfired Oven", "Wine Bar", "Valet Parking"),
                tableBookingAvailable = true,
                maxPartySize = 12
            ),
            Restaurant(
                id = "rest-3",
                name = "Dakshin Delights",
                cuisine = "South Indian · Chettinad · Dosa",
                description = "Crispy ghee roast dosas, flaky Malabar parottas, and traditional filter coffee.",
                image = "https://images.unsplash.com/photo-1668236543090-82eba5ee5976?w=800&auto=format&fit=crop",
                address = "Linking Road, Bandra West, Mumbai",
                latitude = 19.0596,
                longitude = 72.8295,
                deliveryMinutes = 20,
                deliveryFee = 2500,
                open = true,
                rating = 4.7,
                reviewCount = 3100,
                costForTwo = 500,
                vibes = listOf("Family & Kids Friendly", "Casual Bistro & Cafe", "Quick Bite"),
                amenities = listOf("Pure Vegetarian Available", "Fast Service", "AC Seating", "High Chairs"),
                tableBookingAvailable = true,
                maxPartySize = 20
            ),
            Restaurant(
                id = "rest-4",
                name = "Tokyo Zen & Bao",
                cuisine = "Pan-Asian · Ramen · Dim Sum",
                description = "Silky broth ramen bowls, handmade bao buns, and freshly rolled sushi.",
                image = "https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=800&auto=format&fit=crop",
                address = "Juhu Tara Road, Juhu Beach, Mumbai",
                latitude = 19.0988,
                longitude = 72.8267,
                deliveryMinutes = 35,
                deliveryFee = 4000,
                open = true,
                rating = 4.8,
                reviewCount = 1420,
                costForTwo = 1100,
                vibes = listOf("Modern & Trendy", "Casual Bistro & Cafe", "Live Music & Bar", "Romantic Candlelight"),
                amenities = listOf("AC Dining", "Craft Cocktails", "Sushi Counter", "Live Teppanyaki"),
                tableBookingAvailable = true,
                maxPartySize = 10
            ),
            Restaurant(
                id = "rest-5",
                name = "The Green Bowl & Bakery",
                cuisine = "Healthy Bowls · Salads · Sourdough",
                description = "Nutrient-packed grain bowls, crisp salads, organic smoothies and fresh sourdough.",
                image = "https://images.unsplash.com/photo-1540420773420-3366772f4999?w=800&auto=format&fit=crop",
                address = "Central Avenue, Hiranandani Gardens, Powai, Mumbai",
                latitude = 19.1176,
                longitude = 72.9060,
                deliveryMinutes = 20,
                deliveryFee = 3000,
                open = true,
                rating = 4.9,
                reviewCount = 1890,
                costForTwo = 650,
                vibes = listOf("Outdoor Garden Seating", "Casual Bistro & Cafe", "Health & Wellness", "Pet Friendly"),
                amenities = listOf("Outdoor Garden Seating", "Artisanal Bakery", "High Speed Wi-Fi", "Pure Vegetarian Available"),
                tableBookingAvailable = true,
                maxPartySize = 8
            )
        ),
        dishes = listOf(
            // Restaurant 1: Bistro Lumière & Biryani
            Dish(
                id = "dish-1",
                restaurantId = "rest-1",
                category = "Biryani",
                name = "Hyderabadi Dum Gosht Biryani",
                description = "Slow-cooked tender meat layered with saffron basmati rice, caramelised onions and royal spices.",
                price = 38000,
                image = "https://images.unsplash.com/photo-1563379091339-03b21ab4a4f8?w=800&auto=format&fit=crop",
                vegetarian = false,
                addons = listOf(
                    AddOn("addon-1", "Extra Mirchi Ka Salan", 4000),
                    AddOn("addon-2", "Boiled Egg (2 pcs)", 3000),
                    AddOn("addon-3", "Raita Bowl", 2500)
                )
            ),
            Dish(
                id = "dish-2",
                restaurantId = "rest-1",
                category = "Biryani",
                name = "Subz Paneer Dum Biryani",
                description = "Fragrant basmati rice infused with spiced paneer cubes, garden vegetables and fresh mint.",
                price = 29000,
                image = "https://images.unsplash.com/photo-1642821373181-696a54913e9a?w=800&auto=format&fit=crop",
                vegetarian = true,
                addons = listOf(
                    AddOn("addon-4", "Extra Spiced Paneer", 6000),
                    AddOn("addon-5", "Crispy Fried Onions", 2000)
                )
            ),
            Dish(
                id = "dish-101",
                restaurantId = "rest-1",
                category = "Starters",
                name = "Melt-in-Mouth Galouti Kebab",
                description = "Finely minced smoked patties infused with raw papaya and 32 aromatic herbs.",
                price = 34000,
                image = "https://images.unsplash.com/photo-1599488615731-7e5c2823ff28?w=800&auto=format&fit=crop",
                vegetarian = false,
                addons = listOf(AddOn("addon-101", "Mini Mint Chutney & Taftan", 3500))
            ),
            Dish(
                id = "dish-102",
                restaurantId = "rest-1",
                category = "Starters",
                name = "Tandoori Paneer Angara",
                description = "Cottage cheese marinated in Kashmiri chili, charred in clay oven, served with glazed onions.",
                price = 28000,
                image = "https://images.unsplash.com/photo-1567188040759-fb8a883dc6d8?w=800&auto=format&fit=crop",
                vegetarian = true
            ),
            Dish(
                id = "dish-103",
                restaurantId = "rest-1",
                category = "Curries",
                name = "Royal Murgh Makhani",
                description = "Clay oven roasted chicken braised in velvety tomato cashew gravy with fenugreek butter.",
                price = 36000,
                image = "https://images.unsplash.com/photo-1603894584373-5ac82b2ae398?w=800&auto=format&fit=crop",
                vegetarian = false
            ),
            Dish(
                id = "dish-104",
                restaurantId = "rest-1",
                category = "Curries",
                name = "Dal Bukhara Slow-Simmered",
                description = "Black lentils slow-cooked overnight with churned butter and hand-crushed tomatoes.",
                price = 24000,
                image = "https://images.unsplash.com/photo-1546833999-b9f581a1996d?w=800&auto=format&fit=crop",
                vegetarian = true
            ),
            Dish(
                id = "dish-105",
                restaurantId = "rest-1",
                category = "Breads",
                name = "Butter Garlic Naan",
                description = "Fluffy leavened flatbread baked in tandoor, brushed with rich garlic herb butter.",
                price = 7000,
                image = "https://images.unsplash.com/photo-1626074353765-517a681e40be?w=800&auto=format&fit=crop",
                vegetarian = true
            ),
            Dish(
                id = "dish-106",
                restaurantId = "rest-1",
                category = "Desserts",
                name = "Shahi Tukda with Kesari Rabri",
                description = "Golden crisp ghee toast steeped in cardamom syrup and crowned with saffron reduction.",
                price = 18000,
                image = "https://images.unsplash.com/photo-1589301760014-d929f3979dbc?w=800&auto=format&fit=crop",
                vegetarian = true
            ),

            // Restaurant 2: Trattoria Milano
            Dish(
                id = "dish-3",
                restaurantId = "rest-2",
                category = "Pizza",
                name = "Woodfired Margherita Pizza",
                description = "San Marzano tomato sauce, fresh buffalo mozzarella, extra virgin olive oil and torn sweet basil.",
                price = 45000,
                image = "https://images.unsplash.com/photo-1604382355076-af4b0eb60143?w=800&auto=format&fit=crop",
                vegetarian = true,
                addons = listOf(
                    AddOn("addon-6", "Fresh Burrata Cheese", 9000),
                    AddOn("addon-7", "Roasted Garlic Confit", 3500)
                )
            ),
            Dish(
                id = "dish-201",
                restaurantId = "rest-2",
                category = "Pizza",
                name = "Quattro Formaggi Bianca",
                description = "Gorgonzola, fontina, smoked provolone and parmesan on hand-stretched sourdough crust.",
                price = 52000,
                image = "https://images.unsplash.com/photo-1513104890138-7c749659a591?w=800&auto=format&fit=crop",
                vegetarian = true
            ),
            Dish(
                id = "dish-202",
                restaurantId = "rest-2",
                category = "Pizza",
                name = "Diavola Spicy Salami Pizza",
                description = "Smoked mozzarella, spicy artisanal pepperoni, fresh oregano and hot pepper infused honey.",
                price = 56000,
                image = "https://images.unsplash.com/photo-1534308983496-4fabb1a015ee?w=800&auto=format&fit=crop",
                vegetarian = false
            ),
            Dish(
                id = "dish-203",
                restaurantId = "rest-2",
                category = "Pasta",
                name = "Handmade Truffle Tagliatelle",
                description = "Silk egg ribbons tossed in black truffle cream, aged Parmigiano-Reggiano and wild mushrooms.",
                price = 48000,
                image = "https://images.unsplash.com/photo-1621996346565-e3d5d628169e?w=800&auto=format&fit=crop",
                vegetarian = true
            ),
            Dish(
                id = "dish-204",
                restaurantId = "rest-2",
                category = "Starters",
                name = "Bruschetta al Pomodoro",
                description = "Toasted sourdough ciabatta topped with heirloom tomatoes, garlic, extra virgin olive oil and basil.",
                price = 22000,
                image = "https://images.unsplash.com/photo-1572695157366-5e585ab2b69f?w=800&auto=format&fit=crop",
                vegetarian = true
            ),
            Dish(
                id = "dish-205",
                restaurantId = "rest-2",
                category = "Desserts",
                name = "Classic Espresso Tiramisu",
                description = "Savoiardi ladyfingers soaked in dark roast espresso, layered with mascarpone cream and Valrhona cocoa.",
                price = 26000,
                image = "https://images.unsplash.com/photo-1571877227200-a0d98ea607e9?w=800&auto=format&fit=crop",
                vegetarian = true
            ),

            // Restaurant 3: Dakshin Delights
            Dish(
                id = "dish-4",
                restaurantId = "rest-3",
                category = "Dosa",
                name = "Ghee Roast Masala Dosa",
                description = "Crispy golden crepe roasted in pure desi ghee, filled with spiced potato masala, served with 3 chutneys.",
                price = 16000,
                image = "https://images.unsplash.com/photo-1589301760014-d929f3979dbc?w=800&auto=format&fit=crop",
                vegetarian = true,
                addons = listOf(
                    AddOn("addon-8", "Gunpowder (Podi) & Ghee", 3000),
                    AddOn("addon-9", "Extra Sambar Bowl", 2000)
                )
            ),
            Dish(
                id = "dish-301",
                restaurantId = "rest-3",
                category = "Dosa",
                name = "Mysore Masala Dosa",
                description = "Crispy crepe coated with spicy red chutney, stuffed with spiced potato mash and butter.",
                price = 18000,
                image = "https://images.unsplash.com/photo-1668236543090-82eba5ee5976?w=800&auto=format&fit=crop",
                vegetarian = true
            ),
            Dish(
                id = "dish-302",
                restaurantId = "rest-3",
                category = "Tiffin",
                name = "Steamed Thatte Idli with Podi",
                description = "Fluffy giant Mumbai Matunga-style steamed rice cake drenched in hot spiced ghee and gunpowder.",
                price = 12000,
                image = "https://images.unsplash.com/photo-1589301760014-d929f3979dbc?w=800&auto=format&fit=crop",
                vegetarian = true
            ),
            Dish(
                id = "dish-303",
                restaurantId = "rest-3",
                category = "Tiffin",
                name = "Crispy Medu Vada (2 pcs)",
                description = "Crispy deep-fried savory lentil doughnuts served with fresh coconut chutney and piping hot sambar.",
                price = 11000,
                image = "https://images.unsplash.com/photo-1668236543090-82eba5ee5976?w=800&auto=format&fit=crop",
                vegetarian = true
            ),
            Dish(
                id = "dish-304",
                restaurantId = "rest-3",
                category = "Mains",
                name = "Malabar Parotta with Veg Kurma",
                description = "Layered flaky Kerala parottas paired with aromatic vegetable coconut kurma.",
                price = 21000,
                image = "https://images.unsplash.com/photo-1626074353765-517a681e40be?w=800&auto=format&fit=crop",
                vegetarian = true
            ),
            Dish(
                id = "dish-305",
                restaurantId = "rest-3",
                category = "Mains",
                name = "Chettinad Pepper Chicken",
                description = "Tender country chicken simmered in stone-ground black pepper, shallots and curry leaves.",
                price = 31000,
                image = "https://images.unsplash.com/photo-1603894584373-5ac82b2ae398?w=800&auto=format&fit=crop",
                vegetarian = false
            ),
            Dish(
                id = "dish-306",
                restaurantId = "rest-3",
                category = "Beverages",
                name = "Mysore Pure Filter Coffee",
                description = "Traditional freshly brewed chicory-infused decoction with frothy hot milk.",
                price = 6000,
                image = "https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?w=800&auto=format&fit=crop",
                vegetarian = true
            ),

            // Restaurant 4: Tokyo Zen & Bao
            Dish(
                id = "dish-401",
                restaurantId = "rest-4",
                category = "Ramen",
                name = "Tokyo Shoyu Chashu Ramen",
                description = "Artisanal wheat noodles in rich soy-broth with braised chashu, ajitsuke tamago egg, nori and menma.",
                price = 42000,
                image = "https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=800&auto=format&fit=crop",
                vegetarian = false
            ),
            Dish(
                id = "dish-402",
                restaurantId = "rest-4",
                category = "Ramen",
                name = "Creamy Miso Veggie Ramen",
                description = "Savory fermented soybean broth with bok choy, sweet corn, wood ear mushrooms and sesame chili oil.",
                price = 36000,
                image = "https://images.unsplash.com/photo-1552611052-33e04de081de?w=800&auto=format&fit=crop",
                vegetarian = true
            ),
            Dish(
                id = "dish-403",
                restaurantId = "rest-4",
                category = "Dim Sum",
                name = "Truffle Edamame Dim Sum (4 pcs)",
                description = "Translucent steamed dumplings filled with crushed edamame, water chestnut and white truffle oil.",
                price = 32000,
                image = "https://images.unsplash.com/photo-1496116218417-1a781b1c416c?w=800&auto=format&fit=crop",
                vegetarian = true
            ),
            Dish(
                id = "dish-404",
                restaurantId = "rest-4",
                category = "Bao",
                name = "Crispy Karaage Chicken Bao (2 pcs)",
                description = "Steamed lotus leaf buns packed with crunchy Japanese fried chicken, spicy mayo and pickled daikon.",
                price = 29000,
                image = "https://images.unsplash.com/photo-1563245372-f21724e3856d?w=800&auto=format&fit=crop",
                vegetarian = false
            ),

            // Restaurant 5: The Green Bowl & Bakery
            Dish(
                id = "dish-501",
                restaurantId = "rest-5",
                category = "Bowls",
                name = "Beet Hummus & Falafel Buddha Bowl",
                description = "Herb quinoa, roasted chickpeas, avocado, baby spinach, beetroot tahini hummus and citrus vinaigrette.",
                price = 32000,
                image = "https://images.unsplash.com/photo-1540420773420-3366772f4999?w=800&auto=format&fit=crop",
                vegetarian = true
            ),
            Dish(
                id = "dish-502",
                restaurantId = "rest-5",
                category = "Toast",
                name = "Sourdough Avocado & Poached Egg",
                description = "Toasted country sourdough, smashed Hass avocado, free-range poached egg, chili flakes and microgreens.",
                price = 28000,
                image = "https://images.unsplash.com/photo-1525351484163-7529414344d8?w=800&auto=format&fit=crop",
                vegetarian = false
            ),
            Dish(
                id = "dish-503",
                restaurantId = "rest-5",
                category = "Smoothies",
                name = "Wild Berry Acai Smoothie Bowl",
                description = "Frozen acai blended with blueberries, almond milk, topped with house granola, chia and banana coins.",
                price = 26000,
                image = "https://images.unsplash.com/photo-1590301157890-4810ed352733?w=800&auto=format&fit=crop",
                vegetarian = true
            )
        )
    )
}

class DeliveryViewModel(app: Application) : AndroidViewModel(app) {
    private val session = SessionStore(app)
    val backend = RestroFirebaseBackendEngine(app)
    val sqlDb get() = backend.sqlDb
    val cloudEngine = FirebaseCloudEngine(app, backend.sqlDb)
    val api = DeliveryApi().apply { engine = backend }
    var starting by mutableStateOf(true)
        private set

    var busy by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
    var profile by mutableStateOf<Profile?>(null)
        private set

    var catalog by mutableStateOf(MockCatalog.sample)
        private set

    var cart by mutableStateOf(Cart())
        private set

    var quote by mutableStateOf<Quote?>(null)
        private set

    var addresses by mutableStateOf<List<Address>>(emptyList())
        private set

    var orders by mutableStateOf<List<DeliveryOrder>>(emptyList())
        private set

    var favorites by mutableStateOf<List<String>>(emptyList())
        private set

    var reviews by mutableStateOf<List<Review>>(emptyList())
        private set

    var route by mutableStateOf("Home")
    var restaurantId by mutableStateOf("")
    var dishId by mutableStateOf("")
    var orderId by mutableStateOf("")
    var query by mutableStateOf("")
    var category by mutableStateOf("")
    var vegetarian by mutableStateOf(false)
    var sortPrice by mutableStateOf(false)
    private val checkoutPrefs =
        app.getSharedPreferences("restro-checkout", android.content.Context.MODE_PRIVATE)
    private var checkoutKey =
        checkoutPrefs.getString("key", null)
            ?: UUID.randomUUID().toString().also {
                checkoutPrefs.edit().putString("key", it).commit()
            }

    private fun resetCheckoutKey() {
        checkoutKey = UUID.randomUUID().toString()
        checkoutPrefs.edit().putString("key", checkoutKey).commit()
    }

    var editingAddress by mutableStateOf<Address?>(null)

    // OTP Authentication State
    var currentActivity: android.app.Activity? = null
    var otpSent by mutableStateOf(false)
    var otpTarget by mutableStateOf("")
    var otpPreviewCode by mutableStateOf<String?>(null)
    var otpNotificationBanner by mutableStateOf<String?>(null)
    var sessionExpiredBanner by mutableStateOf<String?>(null)

    // Coupon Validation & User Feedback State
    var couponError by mutableStateOf<String?>(null)
    var couponMessage by mutableStateOf<String?>(null)

    // Proximity & Landmark Feed State
    var currentLandmark by mutableStateOf("Goregaon West, Mumbai")
    var currentLatitude by mutableDoubleStateOf(19.1663)
    var currentLongitude by mutableDoubleStateOf(72.8480)
    val maxDeliveryRadiusKm = 15.0

    // In-Menu Multi-Restaurant Guard State
    var showMultiRestaurantDialog by mutableStateOf(false)
    var pendingDishToAdd by mutableStateOf<Dish?>(null)
    var pendingAddonsToAdd by mutableStateOf<List<String>>(emptyList())
    var pendingQuantityToAdd by mutableIntStateOf(1)

    // Android Native Notification Channel Manager
    var notificationHelper: DeliveryNotificationHelper = DeliveryNotificationHelper(app)
    private val notifiedReminderBookingKeys = mutableSetOf<String>()
    private val notifiedBillReadyKeys = mutableSetOf<String>()

    fun triggerReservationReminder(tableNumber: String, minutesRemaining: Int, bookingId: String = "") {
        if (minutesRemaining < 0) return
        val key = if (bookingId.isNotBlank()) "${bookingId}_$minutesRemaining" else "${tableNumber}_$minutesRemaining"
        if (notifiedReminderBookingKeys.add(key)) {
            try {
                notificationHelper.showReservationReminder(
                    tableNumber = tableNumber,
                    minutesRemaining = minutesRemaining
                )
            } catch (_: Exception) {
            }
        }
    }

    fun notifyTableBillReadyIfEligible(tab: DiningTab) {
        if (tab.total <= 0L || tab.items.isEmpty()) return
        val key = "${tab.id}_${tab.total}"
        if (notifiedBillReadyKeys.add(key)) {
            try {
                notificationHelper.showTableBillReady(
                    tableNumber = tab.tableNumber,
                    amount = tab.total
                )
            } catch (_: Exception) {
            }
        }
    }

    fun handleNotificationNavigation(destination: String, tableNumber: String? = null) {
        when (destination) {
            "MyBookings" -> {
                loadUserTableBookings()
                route = "MyBookings"
            }
            "Home" -> {
                route = "Home"
                if (!tableNumber.isNullOrBlank()) {
                    observeActiveTableTab(tableNumber)
                }
            }
            "Orders" -> {
                route = "Orders"
            }
            else -> {
                route = destination
            }
        }
    }

    // Customer Table-Booking UI Draft and Confirmation State
    var tableBookingDraft by mutableStateOf(TableBookingDraft())
    var isConfirmingBooking by mutableStateOf(false)
    var bookingConfirmationError by mutableStateOf<String?>(null)
    var confirmedBooking by mutableStateOf<TableBooking?>(null)

    fun resetTableBookingDraft(restId: String = "", restName: String = "") {
        tableBookingDraft = TableBookingDraft(
            restaurantId = restId,
            restaurantName = restName,
            bookingDate = "",
            timeSlot = "",
            partySize = TableBookingDefaults.DEFAULT_PARTY_SIZE,
            seatingArea = TableBookingDefaults.DEFAULT_SEATING_AREA,
            specialRequests = ""
        )
        bookingConfirmationError = null
    }

    fun confirmTableBooking(onSuccess: ((TableBooking) -> Unit)? = null) {
        if (isConfirmingBooking) return
        val draft = tableBookingDraft
        val validationErr = computeBookingValidationError(draft)
        if (validationErr != null) {
            bookingConfirmationError = validationErr
            return
        }

        isConfirmingBooking = true
        bookingConfirmationError = null

        viewModelScope.launch {
            try {
                val user = profile
                val currentRest = catalog.restaurants.find { it.id == draft.restaurantId || it.id == restaurantId }
                val targetRestName = currentRest?.name ?: draft.restaurantName.ifBlank { "Restro Fine Dining" }
                val targetRestId = currentRest?.id ?: draft.restaurantId.ifBlank { restaurantId }

                val result = sqlDb.allocateAndConfirmTableBooking(
                    restaurantId = targetRestId,
                    restaurantName = targetRestName,
                    userId = user?.id ?: "guest",
                    userName = user?.name ?: "Valued Guest",
                    userPhone = user?.phone ?: "",
                    bookingDate = draft.bookingDate,
                    timeSlot = draft.timeSlot,
                    partySize = draft.partySize,
                    seatingArea = draft.seatingArea,
                    specialRequests = draft.specialRequests
                )

                when (result) {
                    is TableAllocationResult.Success -> {
                        confirmedBooking = result.booking
                        bookingConfirmationError = null
                        isConfirmingBooking = false
                        userTableBookings = listOf(result.booking) + userTableBookings.filterNot { it.id == result.booking.id }
                        cloudEngine.saveBookingToCloud(result.booking)
                        observeActiveTableTab(result.booking.tableNumber)
                        try {
                            notificationHelper.showReservationConfirmed(
                                tableNumber = result.booking.tableNumber,
                                slot = result.booking.timeSlot
                            )
                        } catch (_: Exception) {
                        }
                        if (onSuccess != null) {
                            onSuccess(result.booking)
                        } else {
                            route = "BookTableSuccess"
                        }
                    }
                    is TableAllocationResult.Failure -> {
                        isConfirmingBooking = false
                        bookingConfirmationError = result.reason
                    }
                }
            } catch (e: Exception) {
                isConfirmingBooking = false
                bookingConfirmationError = e.message ?: "Failed to confirm reservation."
            }
        }
    }

    // Customer Table Booking Management State
    var userTableBookings by mutableStateOf<List<TableBooking>>(emptyList())
        private set
    var isLoadingBookings by mutableStateOf(false)
        private set
    var bookingManagementError by mutableStateOf<String?>(null)
    var cancellingBookingId by mutableStateOf<String?>(null)
        private set
    var selectedBookingForDetails by mutableStateOf<TableBooking?>(null)
    var bookingManagementFilter by mutableStateOf(TableBookingFilter.UPCOMING)
    var bookingCancellationNotice by mutableStateOf<String?>(null)

    // Active Dine-in Table Tab State & Real-Time Observer
    var activeTableTab by mutableStateOf<DiningTab?>(null)
    var walkInTableError by mutableStateOf<String?>(null)
    var lastDineInPaymentResult by mutableStateOf<DineInPaymentResult?>(null)
    var showDineInReceipt by mutableStateOf(false)
    private var tableTabObservationJob: Job? = null
    private var observedTableNumber: String? = null

    fun dismissDineInReceipt() {
        showDineInReceipt = false
    }

    /**
     * Connects activeTableTab to the reactive Flow from RestroFirebaseBackendEngine.
     * Observes real-time additions of dishes/items by waiters without requiring an app restart.
     */
    fun observeActiveTableTab(explicitTableNumber: String? = null) {
        val user = profile
        val bookingTable = userTableBookings
            .firstOrNull { it.status == TableBookingStatus.SEATED || it.status == TableBookingStatus.CONFIRMED }
            ?.tableNumber
            ?: (user?.id?.let { sqlDb.getTableBookingsForUser(it) }
                ?.firstOrNull { it.status == TableBookingStatus.SEATED || it.status == TableBookingStatus.CONFIRMED }
                ?.tableNumber)

        val targetTable = explicitTableNumber?.trim()?.ifBlank { null }
            ?: activeTableTab?.tableNumber
            ?: bookingTable

        if (targetTable.isNullOrBlank()) {
            tableTabObservationJob?.cancel()
            tableTabObservationJob = null
            observedTableNumber = null
            return
        }

        if (observedTableNumber.equals(targetTable, ignoreCase = true) && tableTabObservationJob?.isActive == true) {
            return
        }

        tableTabObservationJob?.cancel()
        observedTableNumber = targetTable

        tableTabObservationJob = viewModelScope.launch {
            try {
                backend.listenToTableTab(targetTable).collect { updatedTab ->
                    if (updatedTab.status == "SETTLED") {
                        activeTableTab = null
                        tableTabObservationJob?.cancel()
                        tableTabObservationJob = null
                        observedTableNumber = null
                    } else {
                        activeTableTab = updatedTab
                        if (updatedTab.total > 0L && updatedTab.items.isNotEmpty()) {
                            notifyTableBillReadyIfEligible(updatedTab)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keep active tab state on network/transient failure
            }
        }
    }

    /**
     * Retrieves the active unpaid DiningTab for the selected table using the SQLite / Firebase architecture.
     * If an active unpaid bill exists, updates activeTableTab and starts real-time observation.
     * If no active unpaid bill exists, records the message:
     * "No active unpaid bill found for Table {tableNumber}. Please check with your server."
     *
     * @return The active unpaid DiningTab if found, or null otherwise.
     */
    fun lookupWalkInTableBill(tableNumber: String): DiningTab? {
        val cleanTable = tableNumber.trim()
        if (cleanTable.isBlank()) {
            val msg = "Please enter a valid table number."
            walkInTableError = msg
            error = msg
            return null
        }
        val tab = sqlDb.getOpenTabForTable(cleanTable) ?: backend.getOpenTabForTable(cleanTable)
        return if (tab != null && tab.status != "SETTLED") {
            activeTableTab = tab
            walkInTableError = null
            error = null
            observeActiveTableTab(cleanTable)
            if (tab.total > 0L) {
                notifyTableBillReadyIfEligible(tab)
            }
            tab
        } else {
            val msg = "No active unpaid bill found for Table $cleanTable. Please check with your server."
            walkInTableError = msg
            error = msg
            null
        }
    }

    /**
     * Records an authorized dine-in payment result without yet marking the tab as settled in SQLite / backend.
     * Settlement, table booking completion, and notifications are handled in subsequent implementation steps.
     *
     * @return The recorded DineInPaymentResult.
     */
    fun recordDineInPaymentAuthorized(tab: DiningTab, paymentMethod: String, transactionId: String): DineInPaymentResult {
        val result = DineInPaymentResult(
            tab = tab,
            paymentMethod = paymentMethod,
            transactionId = transactionId
        )
        lastDineInPaymentResult = result
        return result
    }

    /**
     * Settles an active dine-in table tab after successful payment authorization.
     *
     * 1. Updates the corresponding dining_tabs record to SETTLED and stores the payment method in SQLite.
     * 2. Updates the associated table_bookings record to COMPLETED, where applicable.
     * 3. Updates the floor-plan table status to Available, using the existing table and booking logic.
     * 4. Clears activeTableTab, walkInTableError, and cancels real-time observation so the customer
     *    homepage returns to its normal state.
     *
     * @param tab The DiningTab to settle (defaults to activeTableTab).
     * @param paymentMethod The authorized payment method (e.g., UPI, Card, Netbanking).
     * @param transactionId The payment authorization transaction ID.
     * @return True if settlement succeeded, false otherwise.
     */
    fun settleDineInTab(
        tab: DiningTab? = activeTableTab,
        paymentMethod: String,
        transactionId: String? = null
    ): Boolean {
        val targetTab = tab ?: activeTableTab ?: return false

        // Duplicate settlement guard: reject already settled/paid tabs
        if (DineInPaymentRules.isSettledOrPaid(targetTab.status)) {
            return false
        }
        val existingInDb = sqlDb.getDiningTab(targetTab.id)
        if (existingInDb != null && DineInPaymentRules.isSettledOrPaid(existingInDb.status)) {
            return false
        }

        val resolvedTxnId = transactionId?.trim()?.ifBlank { null }
            ?: lastDineInPaymentResult?.transactionId
            ?: "TXN-${UUID.randomUUID().toString().take(8).uppercase()}"

        // 1. Update corresponding dining_tabs record to SETTLED and store payment method and transactionId
        val settledInDb = sqlDb.markTabSettled(targetTab.id, paymentMethod, resolvedTxnId)
        if (!settledInDb) {
            sqlDb.saveOrUpdateTab(
                targetTab.copy(
                    status = "SETTLED",
                    paymentMethod = paymentMethod,
                    paymentTransactionId = resolvedTxnId,
                    updatedAt = Instant.now().toString()
                )
            )
        }

        // 2. Update associated table_bookings record to COMPLETED where applicable
        val targetBookingId = if (!targetTab.bookingId.isNullOrBlank()) {
            targetTab.bookingId
        } else {
            val inMemory = userTableBookings.firstOrNull {
                it.tableNumber.equals(targetTab.tableNumber, ignoreCase = true) &&
                    (it.status == TableBookingStatus.CONFIRMED || it.status == TableBookingStatus.SEATED)
            }?.id
            inMemory ?: sqlDb.getAllTableBookings().firstOrNull {
                it.tableNumber.equals(targetTab.tableNumber, ignoreCase = true) &&
                    (it.status == TableBookingStatus.CONFIRMED || it.status == TableBookingStatus.SEATED)
            }?.id
        }

        if (!targetBookingId.isNullOrBlank()) {
            sqlDb.updateTableBookingStatus(targetBookingId, TableBookingStatus.COMPLETED)
            userTableBookings = userTableBookings.map {
                if (it.id == targetBookingId) it.copy(status = TableBookingStatus.COMPLETED) else it
            }
            if (selectedBookingForDetails?.id == targetBookingId) {
                selectedBookingForDetails = selectedBookingForDetails?.copy(status = TableBookingStatus.COMPLETED)
            }
            if (confirmedBooking?.id == targetBookingId) {
                confirmedBooking = confirmedBooking?.copy(status = TableBookingStatus.COMPLETED)
            }
        }

        // 3. Update floor-plan table status to Available
        sqlDb.updateFloorTableStatus(targetTab.tableNumber, "Available")

        // 4. Update last payment result and show digital GST receipt
        val result = DineInPaymentResult(
            tab = targetTab.copy(
                status = "SETTLED",
                paymentMethod = paymentMethod,
                updatedAt = Instant.now().toString()
            ),
            paymentMethod = paymentMethod,
            transactionId = resolvedTxnId
        )
        lastDineInPaymentResult = result
        showDineInReceipt = true

        // 5. Trigger native notification: "Bill Settled! Payment of {total} for Table {tableNumber} verified. Thank you for dining with us!"
        try {
            notificationHelper.showBillSettledReceipt(
                tableNumber = targetTab.tableNumber,
                amount = targetTab.total,
                invoiceId = resolvedTxnId
            )
        } catch (_: Exception) {
            // Graceful fallback in environments without notification service
        }

        // 6. Clear activeTableTab and stop observation so customer homepage returns to normal state
        tableTabObservationJob?.cancel()
        tableTabObservationJob = null
        observedTableNumber = null
        activeTableTab = null
        walkInTableError = null

        return true
    }

    /**
     * Overload for settling from a DineInPaymentResult.
     */
    fun settleDineInTab(result: DineInPaymentResult): Boolean {
        return settleDineInTab(
            tab = result.tab,
            paymentMethod = result.paymentMethod,
            transactionId = result.transactionId
        )
    }

    /**
     * Resolves the current floor-plan status of a table ("Available" or "Occupied").
     */
    fun getFloorTableStatus(tableNumber: String): String = sqlDb.getFloorTableStatus(tableNumber)

    fun loadUserTableBookings() {
        val currentUserId = profile?.id ?: "guest"
        isLoadingBookings = true
        bookingManagementError = null
        viewModelScope.launch {
            try {
                val list = sqlDb.getTableBookingsForUser(currentUserId)
                userTableBookings = list
            } catch (e: Exception) {
                bookingManagementError = e.message ?: "Failed to load table bookings."
            } finally {
                isLoadingBookings = false
            }
        }
    }

    fun cancelUserTableBooking(bookingId: String, onSuccess: (() -> Unit)? = null) {
        if (cancellingBookingId != null) return
        val currentUserId = profile?.id ?: "guest"
        cancellingBookingId = bookingId
        bookingManagementError = null

        viewModelScope.launch {
            try {
                val result = sqlDb.cancelTableBookingForUser(bookingId, currentUserId)
                when (result) {
                    is TableCancellationResult.Success -> {
                        userTableBookings = userTableBookings.map {
                            if (it.id == bookingId) result.booking else it
                        }
                        if (selectedBookingForDetails?.id == bookingId) {
                            selectedBookingForDetails = result.booking
                        }
                        cancellingBookingId = null
                        bookingCancellationNotice = "Reservation ${result.booking.id} cancelled successfully."
                        onSuccess?.invoke()
                    }
                    is TableCancellationResult.Failure -> {
                        cancellingBookingId = null
                        bookingManagementError = result.reason
                    }
                }
            } catch (e: Exception) {
                cancellingBookingId = null
                bookingManagementError = e.message ?: "Failed to cancel reservation."
            }
        }
    }

    fun dismissBookingCancellationNotice() {
        bookingCancellationNotice = null
    }

    fun dismissBookingManagementError() {
        bookingManagementError = null
    }

    fun getActiveDiningTabForCurrentUser(): DiningTab? {
        val currentUserId = profile?.id ?: return null
        return sqlDb.getActiveDiningTabForUser(currentUserId)
    }

    fun getActiveDiningTabForTable(tableNumber: String): DiningTab? {
        return sqlDb.getActiveDiningTabForTable(tableNumber)
    }

    fun getActiveDiningTabForBooking(bookingId: String): DiningTab? {
        return sqlDb.getActiveDiningTabForBooking(bookingId)
    }

    fun revokeSession(reason: String = "Your session has expired. Please sign in again.") {
        session.save(null)
        session.saveProfile(null)
        api.token = null
        profile = null
        otpSent = false
        otpTarget = ""
        otpPreviewCode = null
        otpNotificationBanner = null
        cart = Cart()
        quote = null
        addresses = emptyList()
        orders = emptyList()
        favorites = emptyList()
        userTableBookings = emptyList()
        tableTabObservationJob?.cancel()
        tableTabObservationJob = null
        observedTableNumber = null
        activeTableTab = null
        walkInTableError = null
        lastDineInPaymentResult = null
        showDineInReceipt = false
        selectedBookingForDetails = null
        cancellingBookingId = null
        bookingCancellationNotice = null
        bookingManagementError = null
        route = "Home"
        sessionExpiredBanner = reason
    }

    fun dismissSessionExpiredBanner() {
        sessionExpiredBanner = null
    }

    fun applyCoupon(code: String) = run {
        val clean = code.trim().uppercase()
        if (clean.isBlank()) {
            try {
                cart = api.call("/cart", "PUT", cart.copy(coupon = ""))
            } catch (_: Exception) {
                cart = cart.copy(coupon = "")
            }
            couponError = null
            couponMessage = null
            computeQuote()
            resetCheckoutKey()
            return@run
        }

        val subtotal = cart.lines.sumOf { line ->
            val dish = catalog.dishes.find { it.id == line.dishId }
            val dishPrice = dish?.price ?: 0L
            val addonsPrice = dish?.addons?.filter { it.id in line.addons }?.sumOf { it.price } ?: 0L
            (dishPrice + addonsPrice) * line.quantity
        }

        val (isValid, errorReason) = CouponRules.validateCoupon(clean, subtotal)
        if (!isValid) {
            couponError = errorReason
            couponMessage = null
            try {
                cart = api.call("/cart", "PUT", cart.copy(coupon = ""))
            } catch (_: Exception) {
                cart = cart.copy(coupon = "")
            }
            computeQuote()
            resetCheckoutKey()
            return@run
        }

        try {
            cart = api.call("/cart", "PUT", cart.copy(coupon = clean))
        } catch (_: Exception) {
            cart = cart.copy(coupon = clean)
        }
        val q = computeQuote()
        couponError = null
        couponMessage = if (clean == "FREEDEL") {
            "FREEDEL applied! Free delivery on this order."
        } else {
            "$clean applied! You saved ${money(q.discount)}."
        }
        resetCheckoutKey()
    }

    init {
        api.onSessionRevoked = { reason ->
            viewModelScope.launch {
                revokeSession(reason)
            }
        }
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000L)
                if (profile != null && session.isSessionExpired()) {
                    revokeSession("Your session has expired. Please sign in again.")
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val isExpired = session.isSessionExpired()
            if (isExpired) {
                withContext(Dispatchers.Main) {
                    revokeSession("Your session has expired. Please sign in again.")
                }
            } else {
                api.token = session.load()
                val savedProfile = session.loadProfile()
                if (savedProfile != null) {
                    withContext(Dispatchers.Main) {
                        profile = savedProfile
                    }
                }
            }

            val loadedCatalog = try {
                val remoteCatalog: Catalog = api.call("/catalog")
                if (remoteCatalog.restaurants.isNotEmpty()) remoteCatalog else backend.getCatalog()
            } catch (_: Exception) {
                backend.getCatalog()
            }

            val storedAddresses = sqlDb.getAllAddresses()
            val storedOrders = sqlDb.getAllOrders()
            val currentUserId = profile?.id ?: "guest"
            val storedBookings = sqlDb.getTableBookingsForUser(currentUserId)
            val openTab = profile?.id?.let { sqlDb.getOpenTabForUser(it) }

            withContext(Dispatchers.Main) {
                catalog = loadedCatalog
                relocateRestaurantsAround(currentLatitude, currentLongitude, currentLandmark)
                if (storedAddresses.isNotEmpty()) addresses = storedAddresses
                if (storedOrders.isNotEmpty()) orders = storedOrders
                if (storedBookings.isNotEmpty()) userTableBookings = storedBookings
                activeTableTab = openTab
                observeActiveTableTab()
                starting = false
            }

            detectAndApplyLiveGps()

            // Real-time Cloud Firestore synchronization listeners
            cloudEngine.observeOrdersRealTime(currentUserId) { cloudOrders ->
                viewModelScope.launch(Dispatchers.Main) {
                    if (cloudOrders.isNotEmpty()) {
                        orders = cloudOrders
                    }
                }
            }
            cloudEngine.observeTableBookingsRealTime(currentUserId) { cloudBookings ->
                viewModelScope.launch(Dispatchers.Main) {
                    if (cloudBookings.isNotEmpty()) {
                        userTableBookings = cloudBookings
                    }
                }
            }

            if (api.token != null) refreshAccount()
        }
    }

    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is ApiFailure && e.status == 409 && route == "Payment") {
                    runCatching {
                        cart = api.call("/cart")
                        orders = api.call("/orders")
                        if (cart.lines.isEmpty()) {
                            quote = null
                            route = "Orders"
                        } else {
                            quote = api.call("/quote")
                            route = "Checkout"
                        }
                    }
                }
                if (e is ApiFailure && e.status == 401) {
                    revokeSession("Your session has expired. Please sign in again.")
                }
                error =
                    if (e is ApiFailure) e.message
                    else "Working in offline mode. Connection to remote server optional."
            } finally {
                busy = false
                starting = false
            }
        }
    }

    private suspend fun refreshAccount() {
        try {
            profile = api.call("/profile")
            cart = api.call("/cart")
            addresses = api.call("/addresses")
            favorites = api.call("/favorites")
            orders = api.call("/orders")
            val currentUserId = profile?.id ?: "guest"
            userTableBookings = sqlDb.getTableBookingsForUser(currentUserId)
            profile?.id?.let { activeTableTab = sqlDb.getOpenTabForUser(it) }
            observeActiveTableTab()
        } catch (_: Exception) {
            if (profile == null) {
                profile = session.loadProfile()
            }
            val currentUserId = profile?.id ?: "guest"
            userTableBookings = sqlDb.getTableBookingsForUser(currentUserId)
            profile?.id?.let { activeTableTab = sqlDb.getOpenTabForUser(it) }
            observeActiveTableTab()
        }
    }

    fun refresh() = run {
        try {
            val remoteCatalog: Catalog = api.call("/catalog")
            if (remoteCatalog.restaurants.isNotEmpty()) {
                catalog = remoteCatalog
            }
        } catch (_: Exception) {
            catalog = MockCatalog.sample
        }
        profile?.id?.let { activeTableTab = sqlDb.getOpenTabForUser(it) }
        observeActiveTableTab()
        if (api.token != null) refreshAccount()
    }

    var payrollRecords by mutableStateOf<List<StaffPayrollRecord>>(emptyList())
    var forgotPasswordSuccessMessage by mutableStateOf<String?>(null)

    fun loadPayroll() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = sqlDb.getAllPayrollRecords()
            withContext(Dispatchers.Main) {
                payrollRecords = list
            }
        }
    }

    fun disburseSalary(payrollId: String, method: String = "UPI / Bank Transfer") {
        viewModelScope.launch(Dispatchers.IO) {
            val txRef = "SAL-TXN-" + UUID.randomUUID().toString().take(8).uppercase()
            sqlDb.disburseSalary(payrollId, method, txRef)
            loadPayroll()
        }
    }

    fun requestPasswordReset(target: String) {
        val clean = target.trim()
        if (clean.isBlank()) {
            error = "Please enter your registered email or phone"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val tempPass = sqlDb.generateAndSetTemporaryPassword(clean)
            withContext(Dispatchers.Main) {
                forgotPasswordSuccessMessage = "Temporary password dispatched to $clean: $tempPass. Please log in and update your password in Profile."
                notificationHelper.notifyOtpDispatched(clean, tempPass)
            }
        }
    }

    fun changePassword(oldPass: String, newPass: String, onResult: (Boolean, String) -> Unit) {
        val p = profile
        if (p == null) {
            onResult(false, "Please log in to change your password")
            return
        }
        val target = p.email.ifBlank { p.phone }
        if (newPass.length < 6) {
            onResult(false, "New password must be at least 6 characters")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val isOldValid = sqlDb.verifyUserPassword(target, oldPass)
            if (!isOldValid) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Current password does not match")
                }
                return@launch
            }
            val ok = sqlDb.updateUserPassword(target, newPass)
            withContext(Dispatchers.Main) {
                if (ok) onResult(true, "Password updated successfully!")
                else onResult(false, "Failed to update password")
            }
        }
    }

    fun auth(
        email: String,
        password: String,
        name: String,
        register: Boolean,
        role: UserRole = UserRole.CUSTOMER,
        phone: String = "",
    ) = run {
        val target = email.trim()
        val isEmailTarget = target.contains("@")
        val cleanEmail = if (isEmailTarget) target else if (target.isNotBlank()) "${target}@phone.restro.app" else "user@restro.app"
        val cleanPhone = if (!isEmailTarget && target.isNotBlank()) {
            if (target.startsWith("+91")) target else "+91 $target"
        } else if (phone.isNotBlank()) {
            if (phone.startsWith("+91")) phone.trim() else "+91 ${phone.trim()}"
        } else "+91 98765 43210"

        try {
            val response: AuthResult = api.call(
                if (register) "/auth/register" else "/auth/login",
                "POST",
                Credentials(target, password, name, role.name, phone = cleanPhone),
            )
            api.token = response.token
            session.save(response.token)
            profile = response.profile
            session.saveProfile(response.profile)
            sessionExpiredBanner = null
            refreshAccount()
        } catch (e: Exception) {
            error = e.message ?: "Authentication failed. Please verify credentials."
        }
    }

    fun sendOtp(target: String, isPhone: Boolean = true) = run {
        val cleanTarget = target.trim()
        if (cleanTarget.isBlank()) {
            error = "Please enter a valid phone number or email"
            return@run
        }
        if (isPhone) {
            if (!RestroValidationUtils.isValidPhone(cleanTarget)) {
                error = RestroValidationUtils.getPhoneValidationMessage(cleanTarget) ?: "Please enter a valid 10-digit mobile number"
                return@run
            }
        } else {
            if (!RestroValidationUtils.isValidEmail(cleanTarget)) {
                error = RestroValidationUtils.getEmailValidationMessage(cleanTarget) ?: "Please enter a valid email address (e.g. you@example.com)"
                return@run
            }
            FirebasePhoneAuthManager.storedVerificationId = null
        }
        error = null
        val resolvedTarget = if (isPhone) RestroValidationUtils.sanitizePhone(cleanTarget) else cleanTarget
        otpTarget = resolvedTarget

        if (isPhone && currentActivity != null) {
            val act = currentActivity!!
            FirebasePhoneAuthManager.sendPhoneOtp(
                activity = act,
                phoneNumber = resolvedTarget,
                onCodeSent = { _ ->
                    viewModelScope.launch(Dispatchers.Main) {
                        otpSent = true
                        otpPreviewCode = null
                        otpNotificationBanner = "Verification code sent via SMS to $resolvedTarget"
                    }
                },
                onAutoVerified = { cred ->
                    viewModelScope.launch(Dispatchers.Main) {
                        cred.smsCode?.let { verifyOtp(it) }
                    }
                },
                onFailure = { errorMsg ->
                    viewModelScope.launch(Dispatchers.Main) {
                        otpSent = false
                        otpPreviewCode = null
                        otpNotificationBanner = null
                        error = "SMS dispatch failed: $errorMsg. Please check the number or verify network connection."
                    }
                }
            )
            return@run
        }

        try {
            val response: OtpSendResponse = api.call(
                "/auth/otp/send",
                "POST",
                OtpSendRequest(target = resolvedTarget, type = if (isPhone) "phone" else "email")
            )
            otpSent = true
            otpPreviewCode = null
            otpNotificationBanner = "Verification code dispatched to $resolvedTarget"
        } catch (e: Exception) {
            error = e.message ?: "Failed to send verification code"
        }
    }

    fun verifyOtp(code: String, name: String = "", role: UserRole = UserRole.CUSTOMER) = run {
        val cleanCode = code.trim()
        if (cleanCode.length != 6) {
            error = "Please enter the 6-digit verification code"
            return@run
        }

        if (FirebasePhoneAuthManager.storedVerificationId != null) {
            FirebasePhoneAuthManager.verifyCode(
                code = cleanCode,
                onSuccess = { credential ->
                    try {
                        FirebaseAuth.getInstance().signInWithCredential(credential)
                            .addOnSuccessListener { authResult ->
                                viewModelScope.launch(Dispatchers.Main) {
                                    val fbUser = authResult.user
                                    if (fbUser != null) {
                                        val result = backend.provisionFirebasePhoneUser(
                                            firebaseUid = fbUser.uid,
                                            phoneNumber = fbUser.phoneNumber ?: otpTarget,
                                            name = name
                                        )
                                        api.token = result.token
                                        session.save(result.token)
                                        profile = result.profile
                                        session.saveProfile(result.profile)
                                        sessionExpiredBanner = null
                                        otpSent = false
                                        otpPreviewCode = null
                                        otpNotificationBanner = null
                                        FirebasePhoneAuthManager.storedVerificationId = null
                                        refreshAccount()
                                        route = "Home"
                                    } else {
                                        error = "Firebase sign-in failed. Please try again."
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                viewModelScope.launch(Dispatchers.Main) {
                                    error = e.localizedMessage ?: "Invalid verification code. Please try again."
                                }
                            }
                    } catch (e: Exception) {
                        error = e.localizedMessage ?: "Verification error"
                    }
                },
                onError = { err ->
                    error = err
                }
            )
            return@run
        }

        try {
            val response: AuthResult = api.call(
                "/auth/otp/verify",
                "POST",
                OtpVerifyRequest(target = otpTarget, code = cleanCode, name = name, role = role.name)
            )
            api.token = response.token
            session.save(response.token)
            profile = response.profile
            session.saveProfile(response.profile)
            sessionExpiredBanner = null
            otpSent = false
            otpPreviewCode = null
            otpNotificationBanner = null
            refreshAccount()
            route = "Home"
        } catch (e: Exception) {
            error = e.message ?: "Invalid verification code. Please try again."
        }
    }

    fun dismissOtpBanner() {
        otpNotificationBanner = null
    }

    fun resetOtp() {
        otpSent = false
        otpPreviewCode = null
        otpNotificationBanner = null
        FirebasePhoneAuthManager.storedVerificationId = null
        error = null
    }

    fun verifyStaffPinAndPromoteRole(pin: String): UserRole? {
        val verifiedRole = backend.verifyStaffPin(pin) ?: return null
        val current = profile
        if (current != null) {
            val updated = current.copy(role = verifiedRole)
            profile = updated
            session.saveProfile(updated)
            sqlDb.saveUser(updated)
        }
        return verifiedRole
    }

    fun logout() = run {
        try {
            api.call<Unit>("/auth/logout", "POST", null)
        } catch (_: Exception) {}
        session.save(null)
        session.saveProfile(null)
        api.token = null
        profile = null
        otpSent = false
        otpTarget = ""
        otpPreviewCode = null
        otpNotificationBanner = null
        FirebasePhoneAuthManager.storedVerificationId = null
        try { FirebaseAuth.getInstance().signOut() } catch (_: Exception) {}
        cart = Cart()
        quote = null
        addresses = emptyList()
        orders = emptyList()
        favorites = emptyList()
        route = "Home"
    }

    fun openRestaurant(id: String) {
        restaurantId = id
        category = ""
        query = ""
        route = "Restaurant"
        reviews = listOf(
            Review(
                orderId = "ord-1",
                restaurantId = id,
                author = "Priya N.",
                rating = 5,
                text = "Exquisite flavours and authentic spices! Arrived piping hot in eco-friendly packaging."
            ),
            Review(
                orderId = "ord-2",
                restaurantId = id,
                author = "Karthik R.",
                rating = 5,
                text = "Generous portions, mouth-watering aroma. One of the best in Mumbai!"
            ),
            Review(
                orderId = "ord-3",
                restaurantId = id,
                author = "Ananya M.",
                rating = 4,
                text = "Super quick delivery and tasty dishes. Loved the packaging and attention to detail."
            )
        )
        if (api.token != null) {
            viewModelScope.launch {
                try {
                    val remoteReviews: List<Review> = api.call("/reviews/$id")
                    if (remoteReviews.isNotEmpty()) reviews = remoteReviews
                } catch (_: Exception) {}
            }
        }
    }

    fun changeCart(lines: List<Line>, after: String? = null) = run {
        try {
            cart = api.call("/cart", "PUT", cart.copy(lines = lines))
        } catch (_: Exception) {
            cart = cart.copy(lines = lines)
        }

        // Validate applied coupon against new subtotal
        if (cart.coupon.isNotBlank()) {
            val subtotal = cart.lines.sumOf { line ->
                val dish = catalog.dishes.find { it.id == line.dishId }
                val dishPrice = dish?.price ?: 0L
                val addonsPrice = dish?.addons?.filter { it.id in line.addons }?.sumOf { it.price } ?: 0L
                (dishPrice + addonsPrice) * line.quantity
            }
            val activeCoupon = cart.coupon.trim().uppercase()
            if ((activeCoupon == "FEAST" || activeCoupon == "FEAST100") && subtotal < 20000L) {
                couponError = "Coupon '$activeCoupon' was removed because cart subtotal fell below ₹200."
                couponMessage = null
                cart = cart.copy(coupon = "")
                try { api.call("/cart", "PUT", cart) } catch (_: Exception) {}
            }
        }

        computeQuote()
        resetCheckoutKey()
        if (after != null) route = after
    }

    fun getCartRestaurantId(): String? {
        val firstLine = cart.lines.firstOrNull() ?: return null
        return catalog.dishes.find { it.id == firstLine.dishId }?.restaurantId
    }

    fun getCartRestaurantName(): String {
        val rId = getCartRestaurantId() ?: return ""
        return catalog.restaurants.find { it.id == rId }?.name ?: "Previous Restaurant"
    }

    fun getDishQuantityInCart(dishId: String): Int {
        return cart.lines.filter { it.dishId == dishId }.sumOf { it.quantity }
    }

    fun getCartTotalItems(): Int {
        return cart.lines.sumOf { it.quantity }
    }

    fun getCartTotalAmount(): Long {
        return cart.lines.sumOf { line ->
            val dish = catalog.dishes.find { it.id == line.dishId }
            val basePrice = dish?.price ?: 0L
            val addonsPrice = dish?.addons?.filter { it.id in line.addons }?.sumOf { it.price } ?: 0L
            (basePrice + addonsPrice) * line.quantity
        }
    }

    fun tryAddDish(dish: Dish, addons: List<String> = emptyList(), quantity: Int = 1, redirectToCart: Boolean = false) {
        val currentRestId = getCartRestaurantId()
        if (currentRestId != null && currentRestId != dish.restaurantId && cart.lines.isNotEmpty()) {
            pendingDishToAdd = dish
            pendingAddonsToAdd = addons
            pendingQuantityToAdd = quantity
            showMultiRestaurantDialog = true
            return
        }

        applyAddDish(dish, addons, quantity, if (redirectToCart) "Cart" else null)
    }

    fun confirmDiscardAndAdd() {
        val dish = pendingDishToAdd ?: return
        val addons = pendingAddonsToAdd
        val quantity = pendingQuantityToAdd

        showMultiRestaurantDialog = false
        pendingDishToAdd = null
        pendingAddonsToAdd = emptyList()
        pendingQuantityToAdd = 1

        val newLines = listOf(Line(dish.id, quantity, addons))
        restaurantId = dish.restaurantId
        changeCart(newLines, null)
    }

    fun dismissMultiRestaurantDialog() {
        showMultiRestaurantDialog = false
        pendingDishToAdd = null
        pendingAddonsToAdd = emptyList()
        pendingQuantityToAdd = 1
    }

    private fun applyAddDish(dish: Dish, addons: List<String>, quantity: Int, after: String? = null) {
        val match = cart.lines.indexOfFirst { it.dishId == dish.id && it.addons.toSet() == addons.toSet() }
        val lines = cart.lines.toMutableList()
        if (match >= 0) {
            lines[match] = lines[match].copy(quantity = (lines[match].quantity + quantity).coerceAtMost(20))
        } else {
            lines.add(Line(dish.id, quantity, addons))
        }
        restaurantId = dish.restaurantId
        changeCart(lines, after)
    }

    fun incrementDish(dish: Dish) {
        tryAddDish(dish, emptyList(), 1, redirectToCart = false)
    }

    fun decrementDish(dish: Dish) {
        val lines = cart.lines.toMutableList()
        val index = lines.indexOfLast { it.dishId == dish.id }
        if (index >= 0) {
            val currentLine = lines[index]
            if (currentLine.quantity > 1) {
                lines[index] = currentLine.copy(quantity = currentLine.quantity - 1)
            } else {
                lines.removeAt(index)
            }
            changeCart(lines, null)
        }
    }

    fun add(dish: Dish, addons: List<String>, quantity: Int) {
        tryAddDish(dish, addons, quantity, redirectToCart = false)
    }

    // Proximity & Landmark Calculations
    fun setDeliveryLandmark(landmark: String, lat: Double, lng: Double) {
        currentLandmark = landmark
        currentLatitude = lat
        currentLongitude = lng
        relocateRestaurantsAround(lat, lng, landmark)

        // Ensure the active delivery address automatically matches the live location to guarantee in-range delivery
        val liveAddr = Address(
            id = "addr-live-location",
            label = "Current Location (Live GPS)",
            line = landmark,
            city = landmark.split(",").getOrNull(1)?.trim() ?: "Local Area",
            postcode = "400062",
            phone = profile?.phone?.ifBlank { "+91 98765 43210" } ?: "+91 98765 43210",
            latitude = lat,
            longitude = lng
        )
        addresses = listOf(liveAddr) + addresses.filterNot { it.id == "addr-live-location" }
        sqlDb.saveAddress(liveAddr)
    }

    fun relocateRestaurantsAround(lat: Double, lng: Double, landmark: String) {
        val cleanLandmark = landmark.split(",").firstOrNull()?.trim() ?: landmark
        val cityOrArea = landmark.split(",").getOrNull(1)?.trim() ?: ""
        val areaLabel = if (cityOrArea.isNotBlank()) "$cleanLandmark, $cityOrArea" else cleanLandmark

        val offsets = listOf(
            Triple(0.0075, 0.0055, "100ft Road, $areaLabel"),
            Triple(-0.0090, 0.0080, "Main Avenue, $areaLabel"),
            Triple(0.0130, -0.0070, "Heritage Circle, $areaLabel"),
            Triple(-0.0150, -0.0110, "Civic Center Road, $areaLabel"),
            Triple(0.0040, -0.0035, "Green Garden Arcade, $areaLabel")
        )
        val updated = catalog.restaurants.mapIndexed { idx, rest ->
            val offset = offsets.getOrElse(idx) { Triple(0.005 * (idx + 1), 0.005 * (idx + 1), "Near $areaLabel") }
            val restLat = Math.round((lat + offset.first) * 10000.0) / 10000.0
            val restLng = Math.round((lng + offset.second) * 10000.0) / 10000.0
            val dist = calculateDistanceKmExplicit(lat, lng, restLat, restLng)
            val mins = (dist * 5.0 + 12).toInt().coerceIn(15, 40)
            rest.copy(
                latitude = restLat,
                longitude = restLng,
                address = offset.third,
                deliveryMinutes = mins,
                open = true
            )
        }
        catalog = catalog.copy(restaurants = updated)
        try {
            updated.forEach { r ->
                sqlDb?.updateRestaurantLocation(r.id, r.address, r.latitude, r.longitude, r.deliveryMinutes)
            }
        } catch (_: Exception) {}
    }

    private fun calculateDistanceKmExplicit(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return Math.round(earthRadius * c * 10.0) / 10.0
    }

    fun detectAndApplyLiveGps(force: Boolean = false) {
        val app = getApplication<Application>()
        val hasFine = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val locationManager = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@launch
                val providers = listOf(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER
                )
                var bestLoc: Location? = null
                for (provider in providers) {
                    try {
                        val loc = locationManager.getLastKnownLocation(provider) ?: continue
                        if (bestLoc == null || loc.time > bestLoc.time || (loc.hasAccuracy() && loc.accuracy < bestLoc.accuracy)) {
                            bestLoc = loc
                        }
                    } catch (_: Exception) {}
                }

                if (bestLoc != null) {
                    resolveAndApplyLocation(bestLoc, app)
                }

                withContext(Dispatchers.Main) {
                    // Register single update listener for freshest live fix
                    for (provider in providers) {
                        try {
                            if (locationManager.isProviderEnabled(provider)) {
                                locationManager.requestSingleUpdate(
                                    provider,
                                    object : LocationListener {
                                        override fun onLocationChanged(location: Location) {
                                            viewModelScope.launch(Dispatchers.IO) {
                                                resolveAndApplyLocation(location, app)
                                            }
                                        }
                                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                                        override fun onProviderEnabled(provider: String) {}
                                        override fun onProviderDisabled(provider: String) {}
                                    },
                                    Looper.getMainLooper()
                                )
                                break
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun resolveAndApplyLocation(loc: Location, context: Context) = withContext(Dispatchers.IO) {
        val lat = Math.round(loc.latitude * 10000.0) / 10000.0
        val lng = Math.round(loc.longitude * 10000.0) / 10000.0
        val accuracyM = if (loc.hasAccuracy()) " (±${loc.accuracy.toInt()}m)" else ""
        var addressName = "Live Hardware Location$accuracyM"
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val subLoc = addr.subLocality ?: addr.locality ?: addr.featureName ?: "Local Area"
                val thoroughfare = addr.thoroughfare ?: ""
                val adminArea = addr.adminArea ?: ""
                addressName = when {
                    thoroughfare.isNotBlank() && subLoc.isNotBlank() -> "$thoroughfare, $subLoc"
                    subLoc.isNotBlank() && adminArea.isNotBlank() -> "$subLoc, $adminArea"
                    subLoc.isNotBlank() -> subLoc
                    else -> "Live Location$accuracyM"
                }
            }
        } catch (_: Exception) {}

        withContext(Dispatchers.Main) {
            setDeliveryLandmark(addressName, lat, lng)
        }
    }

    fun getDineInRestaurants(
        partySizeFilter: Int? = null,
        vibeFilter: String = "All Vibes",
        cuisineFilter: String = "All Cuisines",
        searchQuery: String = ""
    ): List<Restaurant> {
        return catalog.restaurants.filter { rest ->
            val matchesPartySize = partySizeFilter == null || rest.maxPartySize >= partySizeFilter
            val matchesVibe = vibeFilter == "All Vibes" || rest.vibes.any { it.contains(vibeFilter, ignoreCase = true) }
            val matchesCuisine = cuisineFilter == "All Cuisines" || rest.cuisine.contains(cuisineFilter, ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() ||
                    rest.name.contains(searchQuery, ignoreCase = true) ||
                    rest.cuisine.contains(searchQuery, ignoreCase = true) ||
                    rest.vibes.any { it.contains(searchQuery, ignoreCase = true) } ||
                    rest.amenities.any { it.contains(searchQuery, ignoreCase = true) }
            matchesPartySize && matchesVibe && matchesCuisine && matchesQuery
        }.sortedBy { getRestaurantDistanceKm(it) }
    }

    fun calculateDistanceKm(destLat: Double, destLng: Double): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(destLat - currentLatitude)
        val dLng = Math.toRadians(destLng - currentLongitude)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(currentLatitude)) * Math.cos(Math.toRadians(destLat)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return Math.round(earthRadius * c * 10.0) / 10.0
    }

    fun getRestaurantDistanceKm(restaurant: Restaurant): Double {
        return calculateDistanceKm(restaurant.latitude, restaurant.longitude)
    }

    fun isRestaurantInRange(restaurant: Restaurant): Boolean {
        return getRestaurantDistanceKm(restaurant) <= maxDeliveryRadiusKm
    }

    fun getRestaurantDistanceInfo(restaurant: Restaurant): String {
        val dist = getRestaurantDistanceKm(restaurant)
        val estMins = (dist * 6.0 + 12).toInt()
        return String.format(java.util.Locale.US, "%.1f km · %d mins", dist, estMins)
    }

    fun getNearestRestaurants(): List<Restaurant> {
        return catalog.restaurants.sortedBy { getRestaurantDistanceKm(it) }
    }

    fun getFrequentlyOrderedDishes(): List<Pair<Dish, String>> {
        val fromOrders = orders.flatMap { order ->
            order.quote.lines.mapNotNull { ql ->
                val dish = catalog.dishes.find { it.id == ql.dishId }
                if (dish != null) Pair(dish, order.restaurantName) else null
            }
        }.distinctBy { it.first.id }

        if (fromOrders.isNotEmpty()) return fromOrders.take(6)

        return catalog.dishes.take(5).map { dish ->
            val restName = catalog.restaurants.find { it.id == dish.restaurantId }?.name ?: "Bistro Lumière"
            Pair(dish, restName)
        }
    }

    fun validateCardLuhn(number: String): Boolean {
        val clean = number.filter { it.isDigit() }
        if (clean.length < 13 || clean.length > 19) return false
        var sum = 0
        var alternate = false
        for (i in clean.length - 1 downTo 0) {
            var n = clean[i] - '0'
            if (alternate) {
                n *= 2
                if (n > 9) n = (n % 10) + 1
            }
            sum += n
            alternate = !alternate
        }
        return (sum % 10 == 0)
    }

    var liveRiderStatus by mutableStateOf("Preparing your food")
    var liveRiderEtaMins by mutableIntStateOf(18)
    var liveRiderDistanceKm by mutableDoubleStateOf(3.6)

    fun syncLiveRiderProgress(orderId: String, distanceKm: Double, etaMinutes: Int, progress: Float) {
        liveRiderDistanceKm = distanceKm
        liveRiderEtaMins = etaMinutes

        val statusText = when {
            progress >= 1.0f -> "Delivered"
            distanceKm <= 0.5 -> "Ramesh is at your gate"
            progress > 0.6f -> "Out for delivery"
            progress > 0.2f -> "Ramesh picked up order"
            else -> "Preparing"
        }
        liveRiderStatus = statusText

        orders = orders.map {
            if (it.id == orderId) it.copy(status = if (statusText.contains("Delivered")) "Delivered" else "Out for delivery") else it
        }

        viewModelScope.launch(Dispatchers.IO) {
            sqlDb.updateOrderStatus(orderId, if (statusText.contains("Delivered")) "Delivered" else "Out for delivery")
        }
    }

    fun notifyProximityAlert(orderId: String, riderName: String = "Ramesh Kumar") {
        notificationHelper.notifyRiderNearby(orderId, riderName)
        liveRiderStatus = "$riderName is at your gate"
        orders = orders.map {
            if (it.id == orderId) it.copy(status = "Out for delivery") else it
        }
        viewModelScope.launch(Dispatchers.IO) {
            sqlDb.updateOrderStatus(orderId, "Out for delivery")
            sqlDb.recordAuditLog("PROXIMITY_ALERT", riderName, "RIDER", orderId, "Rider is within 500m of customer gate")
        }
    }

    fun notifyOrderDelivered(orderId: String) {
        notificationHelper.notifyOrderDelivered(orderId)
        liveRiderStatus = "Delivered"
        orders = orders.map { if (it.id == orderId) it.copy(status = "Delivered") else it }
        viewModelScope.launch(Dispatchers.IO) {
            sqlDb.updateOrderStatus(orderId, "Delivered")
            sqlDb.recordAuditLog("ORDER_DELIVERED", "Ramesh Kumar", "RIDER", orderId, "Order successfully delivered to customer")
        }
    }

    fun markOrderDelivered(orderId: String) {
        orders = orders.map { if (it.id == orderId) it.copy(status = "Delivered") else it }
        viewModelScope.launch(Dispatchers.IO) {
            sqlDb.updateOrderStatus(orderId, "Delivered")
        }
    }

    fun computeQuote(): Quote {
        return try {
            val q = backend.calculateQuote(cart)
            quote = q
            q
        } catch (_: Exception) {
            val subtotal = cart.lines.sumOf { line ->
                val dish = catalog.dishes.find { it.id == line.dishId }
                val dishPrice = dish?.price ?: 0L
                val addonsPrice = dish?.addons?.filter { it.id in line.addons }?.sumOf { it.price } ?: 0L
                (dishPrice + addonsPrice) * line.quantity
            }
            val delivery = 4000L
            val tax = (subtotal * 0.05).toLong()
            val discount = CouponRules.calculateDiscount(cart.coupon, subtotal, delivery)
            val fallbackRestId = cart.lines.firstOrNull()?.let { line ->
                catalog.dishes.find { it.id == line.dishId }?.restaurantId
            } ?: "rest-1"
            val activeRestaurantId = if (restaurantId.isNotBlank()) restaurantId else fallbackRestId
            val q = Quote(
                restaurantId = activeRestaurantId,
                lines = cart.lines.map { line ->
                    val dish = catalog.dishes.find { it.id == line.dishId }
                    QuoteLine(
                        dishId = line.dishId,
                        name = dish?.name ?: "Item",
                        quantity = line.quantity,
                        addons = line.addons,
                        unitPrice = dish?.price ?: 0L,
                        total = (dish?.price ?: 0L) * line.quantity
                    )
                },
                subtotal = subtotal,
                tax = tax,
                delivery = delivery,
                discount = discount,
                total = (subtotal + delivery + tax - discount).coerceAtLeast(0L)
            )
            quote = q
            q
        }
    }

    fun loadQuote() = run {
        computeQuote()

        val liveAddr = Address(
            id = "addr-live-location",
            label = "Current Location (Live GPS)",
            line = currentLandmark,
            city = currentLandmark.split(",").getOrNull(1)?.trim() ?: "Local Area",
            postcode = "400062",
            phone = profile?.phone?.ifBlank { "+91 98765 43210" } ?: "+91 98765 43210",
            latitude = currentLatitude,
            longitude = currentLongitude
        )
        val hasNearbyAddress = addresses.any {
            val aLat = it.latitude ?: 0.0
            val aLng = it.longitude ?: 0.0
            calculateDistanceKmExplicit(currentLatitude, currentLongitude, aLat, aLng) <= 15.0
        }
        if (addresses.isEmpty() || !hasNearbyAddress) {
            addresses = listOf(liveAddr) + addresses.filterNot { it.id == "addr-live-location" }
            sqlDb.saveAddress(liveAddr)
        }

        if (api.token != null) {
            try {
                quote = api.call("/quote")
                val remoteAddresses: List<Address> = api.call("/addresses")
                if (remoteAddresses.isNotEmpty()) {
                    addresses = remoteAddresses
                }
            } catch (_: Exception) {}
        }
        route = "Checkout"
    }

    fun checkout(addressId: String, paymentMethod: String = "Cash on delivery") = run {
        val currentQuote: Quote = quote ?: computeQuote()
        val total = currentQuote.total
        val liveFallbackAddr = Address(
            id = "addr-live-location",
            label = "Current Location (Live GPS)",
            line = currentLandmark,
            city = currentLandmark.split(",").getOrNull(1)?.trim() ?: "Local Area",
            postcode = "400062",
            phone = profile?.phone?.ifBlank { "+91 98765 43210" } ?: "+91 98765 43210",
            latitude = currentLatitude,
            longitude = currentLongitude
        )
        val selectedAddr = addresses.find { it.id == addressId }
            ?: addresses.firstOrNull {
                val aLat = it.latitude ?: 0.0
                val aLng = it.longitude ?: 0.0
                calculateDistanceKmExplicit(currentLatitude, currentLongitude, aLat, aLng) <= 15.0
            }
            ?: liveFallbackAddr.also {
                addresses = listOf(it) + addresses.filterNot { a -> a.id == it.id }
                sqlDb.saveAddress(it)
            }

        val checkoutObj = Checkout(selectedAddr.id, paymentMethod, checkoutKey, total)
        try {
            val confirmedOrder = backend.placeOrder(checkoutObj, cart, selectedAddr, profile?.id ?: "")
            // Real-time Cloud Firestore sync
            cloudEngine.saveOrderToCloud(confirmedOrder)
            orders = listOf(confirmedOrder) + orders.filterNot { it.id == confirmedOrder.id }
            orderId = confirmedOrder.id
            cart = Cart()
            quote = null
            resetCheckoutKey()
            route = "Confirmation"
            notificationHelper.notifyOrderPlaced(
                orderId = confirmedOrder.id,
                restaurantName = confirmedOrder.restaurantName,
                etaMinutes = 28
            )
        } catch (e: Exception) {
            error = e.message ?: "Failed to place order."
        }
    }

    fun submitOrderReview(orderId: String, rating: Int, reviewText: String, tipAmount: Long = 0L) {
        viewModelScope.launch(Dispatchers.IO) {
            val user = profile?.name?.ifBlank { "Happy Diner" } ?: "Happy Diner"
            sqlDb.recordAuditLog(
                "ORDER_REVIEW_SUBMITTED",
                user,
                "CUSTOMER",
                orderId,
                "Customer rated $rating stars. Tip: Rs.$tipAmount. Note: ${reviewText.ifBlank { "Tasty food & great delivery" }}"
            )
        }
    }

    fun cancelOrder(id: String) {
        orders = orders.map { if (it.id == id) it.copy(status = "Cancelled") else it }
        viewModelScope.launch(Dispatchers.IO) {
            sqlDb.updateOrderStatus(id, "Cancelled")
            sqlDb.recordAuditLog("ORDER_CANCELLED", profile?.email ?: "Customer", profile?.role?.name ?: "CUSTOMER", id, "Delivery order cancelled by customer")
        }
    }

    var inventoryItems by mutableStateOf<List<InventoryItem>>(emptyList())
    var auditLogs by mutableStateOf<List<AuditLogEntry>>(emptyList())

    fun loadInventory() {
        viewModelScope.launch(Dispatchers.IO) {
            val items = sqlDb.getAllInventoryItems()
            withContext(Dispatchers.Main) {
                inventoryItems = items
            }
        }
    }

    fun updateStock(itemId: String, delta: Double, reason: String) {
        viewModelScope.launch(Dispatchers.IO) {
            sqlDb.recordStockMovement(itemId, delta, reason, actor = profile?.name ?: "Manager")
            loadInventory()
        }
    }

    fun loadAuditLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            val logs = sqlDb.getAllAuditLogs()
            withContext(Dispatchers.Main) {
                auditLogs = logs
            }
        }
    }

    fun updateDishPriceAndAvailability(dishId: String, newPrice: Long, isAvailable: Boolean, onDone: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            sqlDb.updateDishPriceAndAvailability(dishId, newPrice, isAvailable)
            sqlDb.recordAuditLog("MENU_UPDATED", profile?.email ?: "admin@restro.app", "ADMIN", dishId, "Price changed to ₹${newPrice / 100}, available=$isAvailable")
            refresh()
            withContext(Dispatchers.Main) {
                onDone()
            }
        }
    }

    fun reorder(order: DeliveryOrder) {
        val newLines = order.quote.lines.map { Line(it.dishId, it.quantity, it.addons) }
        changeCart(newLines, "Cart")
    }

    fun track() = run {
        try {
            val updated: DeliveryOrder = api.call("/orders/$orderId")
            orders = orders.map { if (it.id == updated.id) updated else it }
        } catch (_: Exception) {
            val current = orders.find { it.id == orderId }
            if (current != null) {
                val nextStatus = when (current.status) {
                    "Confirmed" -> "Preparing"
                    "Preparing" -> "Out for delivery"
                    "Out for delivery" -> "Delivered"
                    else -> "Delivered"
                }
                val updated = current.copy(status = nextStatus)
                orders = orders.map { if (it.id == updated.id) updated else it }
            }
        }
    }

    fun saveAddress(a: Address) = run {
        try {
            api.call<Address>("/addresses", "PUT", a)
            addresses = api.call("/addresses")
        } catch (_: Exception) {
            addresses = addresses.filterNot { it.id == a.id } + a
        }
        route = "Addresses"
    }

    fun deleteAddress(id: String) = run {
        try {
            api.call<Unit>("/addresses/$id", "DELETE", null)
            addresses = api.call("/addresses")
        } catch (_: Exception) {
            addresses = addresses.filterNot { it.id == id }
        }
    }

    fun favorite(id: String) = run {
        try {
            favorites = api.call("/favorites/$id", "PUT", mapOf("selected" to !favorites.contains(id)))
        } catch (_: Exception) {
            favorites = if (favorites.contains(id)) favorites - id else favorites + id
        }
    }

    fun saveProfile(p: Profile) = run {
        try {
            profile = api.call("/profile", "PUT", p)
        } catch (_: Exception) {
            profile = p
        }
        if (route != "Settings") route = "Profile"
    }

    fun review(rating: Int, text: String) = run {
        try {
            api.call<Review>(
                "/reviews/$orderId",
                "POST",
                mapOf("rating" to rating.toString(), "text" to text),
            )
        } catch (_: Exception) {
            reviews = reviews + Review(
                orderId = orderId,
                restaurantId = restaurantId,
                author = profile?.name ?: "Guest Diner",
                rating = rating,
                text = text
            )
        }
        route = "Orders"
    }

    fun back() {
        error = null
        route =
            when (route) {
                "Map" -> "Home"
                "Food",
                "Reviews" -> if (restaurantId.isNotBlank()) "Restaurant" else "Home"
                "BookTable" -> "DineIn"
                "BookTableReview" -> "BookTable"
                "BookTableSuccess" -> {
                    confirmedBooking = null
                    if (restaurantId.isNotBlank()) "Restaurant" else "Home"
                }
                "Payment" -> "Checkout"
                "Checkout" -> "Cart"
                "Tracking",
                "Confirmation",
                "Review" -> "Orders"
                "Edit address" -> "Addresses"
                "Addresses",
                "Settings",
                "Edit profile",
                "MyBookings" -> "Profile"
                else -> "Home"
            }
    }
}
