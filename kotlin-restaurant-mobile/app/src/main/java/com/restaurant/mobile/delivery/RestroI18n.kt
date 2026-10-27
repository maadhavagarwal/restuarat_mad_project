package com.restaurant.mobile.delivery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppLanguage(val code: String, val nativeName: String, val englishName: String) {
    EN("en", "English", "English"),
    HI("hi", "हिंदी", "Hindi"),
    MR("mr", "मराठी", "Marathi");

    companion object {
        fun fromCode(code: String): AppLanguage =
            values().find { it.code == code.lowercase() } ?: EN
    }
}

object RestroI18n {
    var currentLanguage by mutableStateOf("en")
    private var appContext: android.content.Context? = null

    fun init(context: android.content.Context) {
        appContext = context.applicationContext
        val prefs = appContext?.getSharedPreferences("restro_prefs", android.content.Context.MODE_PRIVATE)
        val savedLang = prefs?.getString("app_language", null)
        if (!savedLang.isNullOrBlank()) {
            currentLanguage = when (savedLang.lowercase()) {
                "hi" -> "hi"
                "mr" -> "mr"
                else -> "en"
            }
        }
    }

    fun setLanguage(code: String, context: android.content.Context? = null) {
        val target = when (code.lowercase()) {
            "hi" -> "hi"
            "mr" -> "mr"
            else -> "en"
        }
        currentLanguage = target
        val ctx = context?.applicationContext ?: appContext
        try {
            ctx?.getSharedPreferences("restro_prefs", android.content.Context.MODE_PRIVATE)
                ?.edit()
                ?.putString("app_language", target)
                ?.apply()
        } catch (_: Exception) {}
    }

    fun isHindi(): Boolean = currentLanguage == "hi"
    fun isMarathi(): Boolean = currentLanguage == "mr"

    fun roleLabel(role: String): String {
        val key = when (role.trim().lowercase()) {
            "customer", "ग्राहक" -> "role_customer"
            "manager", "admin", "प्रबंधक", "व्यवस्थापक" -> "role_admin"
            "master admin", "मास्टर एडमिन" -> "role_master_admin"
            "chef", "chef kds", "शेफ" -> "role_chef"
            "waiter", "waiter pos", "वेटर" -> "role_waiter"
            "rider", "delivery rider", "राइडर" -> "role_rider"
            "kiosk", "कियोस्क" -> "role_kiosk"
            else -> return role
        }
        return t(key)
    }
    fun isEnglish(): Boolean = currentLanguage == "en"

    private val translations = mapOf(
        "delivery_label" to mapOf("en" to "delivery", "hi" to "डिलीवरी", "mr" to "डिलिव्हरी"),
        "out_of_range" to mapOf("en" to "Out of Range", "hi" to "डिलीवरी सीमा से बाहर", "mr" to "वितरण क्षेत्राबाहेर"),
        "password_label" to mapOf("en" to "Pass", "hi" to "पास", "mr" to "पास"),
        // Navigation & Root Tabs
        "tab_delivery" to mapOf("en" to "Delivery", "hi" to "डिलीवरी", "mr" to "डिलिव्हरी"),
        "tab_dine_in" to mapOf("en" to "Dine-In", "hi" to "डाइन-इन", "mr" to "डायन-इन"),
        "tab_orders" to mapOf("en" to "Orders", "hi" to "ऑर्डर्स", "mr" to "ऑर्डर्स"),
        "tab_bookings" to mapOf("en" to "Bookings", "hi" to "बुकिंग्स", "mr" to "बुकिंग्ज"),
        "tab_profile" to mapOf("en" to "Profile", "hi" to "प्रोफाइल", "mr" to "प्रोफाइल"),
        
        // Header & Search
        "search_placeholder" to mapOf(
            "en" to "Search dishes, restaurants, cuisines...",
            "hi" to "व्यंजन, रेस्तरां, भोजन खोजें...",
            "mr" to "पदार्थ, हॉटेल्स, खाद्यपदार्थ शोधा..."
        ),
        "voice_search_tooltip" to mapOf(
            "en" to "Voice Search",
            "hi" to "आवाज से खोजें",
            "mr" to "आवाजाने शोधा"
        ),
        "live_gps" to mapOf("en" to "LIVE GPS", "hi" to "लाइव जीपीएस", "mr" to "थेट जीपीएस"),
        "bistro_dining" to mapOf("en" to "BISTRO & DINING", "hi" to "बिस्ट्रो और डाइनिंग", "mr" to "बिस्ट्रो आणि डायनिंग"),

        // Filter Chips
        "filter_veg_only" to mapOf("en" to "Veg Only 🌱", "hi" to "केवल शाकाहारी 🌱", "mr" to "फक्त शाकाहारी 🌱"),
        "filter_price_low_high" to mapOf("en" to "Price: Low to High", "hi" to "कीमत: कम से ज्यादा", "mr" to "किंमत: कमी ते जास्त"),
        "filter_price_high_low" to mapOf("en" to "Price: High to Low", "hi" to "कीमत: ज्यादा से कम", "mr" to "किंमत: जास्त ते कमी"),
        "filter_clear_all" to mapOf("en" to "✕ Clear All", "hi" to "✕ सभी हटाएं", "mr" to "✕ सर्व साफ करा"),
        "filter_active" to mapOf("en" to "Active Filters", "hi" to "सक्रिय फ़िल्टर", "mr" to "सक्रिय फिल्टर्स"),

        // Discovery Sections
        "section_matching_dishes" to mapOf("en" to "Dishes Matching Search", "hi" to "खोज से मेल खाते व्यंजन", "mr" to "शोधाशी जुळणारे पदार्थ"),
        "section_nearest_restaurants" to mapOf("en" to "Restaurants Near You", "hi" to "आपके आस-पास के रेस्तरां", "mr" to "तुमच्या जवळील हॉटेल्स"),
        "empty_dishes_title" to mapOf("en" to "No dishes match your filter", "hi" to "कोई व्यंजन मेल नहीं खाया", "mr" to "कोणतेही पदार्थ जुळले नाहीत"),
        "empty_dishes_desc" to mapOf(
            "en" to "Try adjusting your search query, selecting another category, or clearing dietary filters.",
            "hi" to "अपनी खोज बदलने का प्रयास करें या फ़िल्टर हटाकर देखें।",
            "mr" to "कृपया तुमचे शोध शब्द बदला किंवा फिल्टर्स काढून पहा."
        ),
        "button_clear_filters" to mapOf("en" to "Clear All Filters", "hi" to "सभी फ़िल्टर साफ़ करें", "mr" to "सर्व फिल्टर्स साफ करा"),

        // Dish & Menu Cards
        "btn_add" to mapOf("en" to "ADD", "hi" to "जोड़ें", "mr" to "जोडा"),
        "badge_customizable" to mapOf("en" to "CUSTOMIZABLE", "hi" to "अनुकूलन योग्य", "mr" to "सानुकूलित"),
        "badge_bestseller" to mapOf("en" to "BESTSELLER", "hi" to "लोकप्रिय", "mr" to "सर्वाधिक विकले जाणारे"),
        "badge_in_stock" to mapOf("en" to "In Stock", "hi" to "उपलब्ध", "mr" to "उपलब्ध"),
        "badge_out_of_stock" to mapOf("en" to "Sold Out", "hi" to "समाप्त", "mr" to "संपले"),
        "dish_rating" to mapOf("en" to "Rating", "hi" to "रेटिंग", "mr" to "रेटिंग"),
        "dish_prep_time" to mapOf("en" to "mins", "hi" to "मिनट", "mr" to "मिनिटे"),

        // Cart & Sticky Bar
        "cart_items" to mapOf("en" to "items", "hi" to "सामग्री", "mr" to "वस्तू"),
        "btn_view_cart" to mapOf("en" to "View Cart", "hi" to "कार्ट देखें", "mr" to "कार्ट पहा"),
        "btn_checkout" to mapOf("en" to "Proceed to Checkout", "hi" to "चेकआउट करें", "mr" to "चेकआऊट करा"),
        "cart_title" to mapOf("en" to "Your Cart", "hi" to "आपकी कार्ट", "mr" to "तुमची कार्ट"),
        "cart_empty_title" to mapOf("en" to "Your cart is hungry!", "hi" to "आपकी कार्ट खाली है!", "mr" to "तुमची कार्ट रिकामी आहे!"),
        "cart_empty_desc" to mapOf(
            "en" to "Explore top restaurants around you and add delicious food to satisfy your cravings.",
            "hi" to "अपने आस-पास के बेहतरीन रेस्तरां से स्वादिष्ट भोजन जोड़ें।",
            "mr" to "जवळपासच्या उत्तम हॉटेल्समधून स्वादिष्ट पदार्थ जोडा."
        ),
        "btn_browse_restaurants" to mapOf("en" to "Browse Restaurants", "hi" to "रेस्तरां देखें", "mr" to "हॉटेल्स पहा"),
        "applied_coupon" to mapOf("en" to "Applied Coupon", "hi" to "लागू कूपन", "mr" to "लागू कूपन"),
        "btn_remove" to mapOf("en" to "Remove", "hi" to "हटाएं", "mr" to "काढून टाका"),
        "bill_subtotal" to mapOf("en" to "Item Subtotal", "hi" to "सामग्री कुल", "mr" to "वस्तूंची बेरीज"),
        "bill_tax" to mapOf("en" to "Taxes & Charges (GST)", "hi" to "कर और शुल्क (जीएसटी)", "mr" to "कर आणि शुल्क (जीएसटी)"),
        "bill_delivery" to mapOf("en" to "Delivery Fee", "hi" to "डिलीवरी शुल्क", "mr" to "डिलिव्हरी फी"),
        "bill_discount" to mapOf("en" to "Coupon Discount", "hi" to "कूपन छूट", "mr" to "कूपन सवलत"),
        "bill_total" to mapOf("en" to "Grand Total", "hi" to "कुल देय राशि", "mr" to "एकूण रक्कम"),

        // Dine-In & Table Booking
        "dine_in_title" to mapOf("en" to "Table Booking & Seated Dining", "hi" to "टेबल बुकिंग और डाइनिंग", "mr" to "टेबल बुकिंग आणि डायनिंग"),
        "dine_in_subtitle" to mapOf(
            "en" to "Reserve preferred tables and order straight from your seat",
            "hi" to "मनपसंद टेबल बुक करें और सीधे अपनी सीट से ऑर्डर करें",
            "mr" to "आवडते टेबल आरक्षित करा आणि जागेवर बसून ऑर्डर द्या"
        ),
        "btn_book_table" to mapOf("en" to "Book a Table", "hi" to "टेबल बुक करें", "mr" to "टेबल बुक करा"),
        "table_selector_title" to mapOf("en" to "Select Your Table", "hi" to "अपना टेबल चुनें", "mr" to "तुमचे टेबल निवडा"),
        "table_available" to mapOf("en" to "Available", "hi" to "उपलब्ध", "mr" to "उपलब्ध"),
        "table_selected" to mapOf("en" to "Selected", "hi" to "चयनित", "mr" to "निवडलेले"),
        "table_occupied" to mapOf("en" to "Occupied", "hi" to "व्यस्त", "mr" to "भरलेले"),
        "table_reserved" to mapOf("en" to "Reserved", "hi" to "आरक्षित", "mr" to "आरक्षित"),
        "seats_count" to mapOf("en" to "Seats", "hi" to "सीटें", "mr" to "जागा"),
        "guests_label" to mapOf("en" to "Guests", "hi" to "अतिथि", "mr" to "पाहुणे"),

        // Restro.AI Assistant
        "ai_assistant_title" to mapOf("en" to "Restro.AI Assistant", "hi" to "रेस्ट्रो.एआई सहायक", "mr" to "रेस्ट्रो.एआय सहाय्यक"),
        "ai_assistant_tagline" to mapOf(
            "en" to "Smart recommendations powered by Google Gemini",
            "hi" to "गूगल जेमिनी द्वारा संचालित स्मार्ट सिफारिशें",
            "mr" to "गुगल जेमिनी समर्थित स्मार्ट शिफारसी"
        ),
        "ai_chat_placeholder" to mapOf(
            "en" to "Ask Restro.AI: 'Suggest dinner under ₹300'...",
            "hi" to "रेस्ट्रो.एआई से पूछें: '₹300 के अंदर रात का खाना बताएं'...",
            "mr" to "रेस्ट्रो.एआय ला विचारा: '₹३०० च्या आत जेवण सुचवा'..."
        ),
        "ai_suggest_budget" to mapOf("en" to "Dishes under ₹250", "hi" to "₹250 के अंदर के व्यंजन", "mr" to "₹२५० च्या आतील पदार्थ"),
        "ai_suggest_spicy" to mapOf("en" to "Spicy Biryani", "hi" to "मसालेदार बिरयानी", "mr" to "झणझणीत बिर्याणी"),
        "ai_suggest_veg" to mapOf("en" to "Healthy Veg Dinner", "hi" to "पौष्टिक शाकाहारी भोजन", "mr" to "पौष्टिक शाकाहारी जेवण"),
        "ai_suggest_popular" to mapOf("en" to "Top Best Sellers", "hi" to "शीर्ष लोकप्रिय खाद्य", "mr" to "सर्वात लोकप्रिय पदार्थ"),
        "btn_add_ai_item" to mapOf("en" to "+ Add to Cart", "hi" to "+ कार्ट में जोड़ें", "mr" to "+ कार्टमध्ये जोडा"),
        "btn_view_dish" to mapOf("en" to "View Dish", "hi" to "व्यंजन देखें", "mr" to "पदार्थ पहा"),
        "btn_clear_chat" to mapOf("en" to "Clear Chat", "hi" to "बातचीत साफ़ करें", "mr" to "संभाषण साफ करा"),

        // Voice System
        "voice_listening" to mapOf("en" to "Listening...", "hi" to "सुन रहे हैं...", "mr" to "ऐकत आहे..."),
        "voice_speak_now" to mapOf("en" to "Speak now in your chosen language", "hi" to "अपनी भाषा में बोलें", "mr" to "तुमच्या भाषेत बोला"),
        "voice_try_saying" to mapOf(
            "en" to "Try: 'Show vegetarian biryani' or 'Open my cart'",
            "hi" to "कोशिश करें: 'शाकाहारी बिरयानी दिखाओ' या 'मेरी कार्ट खोलो'",
            "mr" to "प्रयत्न करा: 'व्हेज बिर्याणी दाखवा' किंवा 'माझी कार्ट उघडा'"
        ),
        "voice_done" to mapOf("en" to "Search", "hi" to "खोजें", "mr" to "शोधा"),
        "voice_cancel" to mapOf("en" to "Cancel", "hi" to "रद्द करें", "mr" to "रद्द करा"),

        // Language & Onboarding Tour
        "lang_select_title" to mapOf("en" to "Choose Your Language", "hi" to "अपनी भाषा चुनें", "mr" to "तुमची भाषा निवडा"),
        "lang_select_subtitle" to mapOf(
            "en" to "Select the language you want to browse and order in. You can change this anytime.",
            "hi" to "वह भाषा चुनें जिसमें आप ब्राउज़ और ऑर्डर करना चाहते हैं। इसे कभी भी बदला जा सकता है।",
            "mr" to "तुम्हाला ज्या भाषेत ऑर्डर करायची आहे ती भाषा निवडा. तुम्ही हे कधीही बदलू शकता."
        ),
        "btn_continue" to mapOf("en" to "Continue", "hi" to "आगे बढ़ें", "mr" to "पुढे जा"),
        "btn_skip_tour" to mapOf("en" to "Skip Tour", "hi" to "टूर छोड़ें", "mr" to "टूर वगळा"),
        "btn_next" to mapOf("en" to "Next", "hi" to "आगे", "mr" to "पुढील"),
        "btn_back" to mapOf("en" to "Back", "hi" to "पीछे", "mr" to "मागे"),
        "btn_finish_tour" to mapOf("en" to "Get Started", "hi" to "शुरू करें", "mr" to "सुरू करा"),
        "replay_tour" to mapOf("en" to "Replay App Tour", "hi" to "ऐप टूर फिर से देखें", "mr" to "अॅप टूर पुन्हा पहा"),
        "replay_tour_subtitle" to mapOf(
            "en" to "Walk through all features, Restro.AI & Voice ordering",
            "hi" to "सभी सुविधाओं, रेस्ट्रो.एआई और वॉयस ऑर्डरिंग की जानकारी लें",
            "mr" to "सर्व वैशिष्ट्ये, रेस्ट्रो.एआय आणि व्हॉईस ऑर्डरिंग समजून घ्या"
        ),
        "settings_language" to mapOf("en" to "App Language", "hi" to "ऐप की भाषा", "mr" to "अॅपची भाषा"),
        "settings_language_desc" to mapOf(
            "en" to "Change interface, voice recognition & AI language",
            "hi" to "इंटरफ़ेस, वॉइस और एआई की भाषा बदलें",
            "mr" to "इंटरफेस, व्हॉईस आणि एआय भाषा बदला"
        ),

        // Auth & Registration Redirect
        "auth_redirected_to_register" to mapOf(
            "en" to "Account not found for this email/phone. Redirected to Create Account — please register below.",
            "hi" to "इस ईमेल/फ़ोन से कोई खाता नहीं मिला। नया खाता बनाने के लिए नीचे विवरण भरें।",
            "mr" to "या ईमेल/फोनसाठी खाते सापडले नाही. नवीन खाते तयार करण्यासाठी खालील माहिती भरा."
        ),
        "auth_fast_sign_in" to mapOf("en" to "Fast & Secure\nSign In.", "hi" to "तेज़ और सुरक्षित\nसाइन इन।", "mr" to "जलद आणि सुरक्षित\nसाईन इन."),
        "auth_create_heading" to mapOf("en" to "Create your\nRestro account.", "hi" to "अपना नया\nखाता बनाएं।", "mr" to "तुमचे नवीन\nखाते तयार करा."),
        "auth_signin_heading" to mapOf("en" to "Something good\nis on its way.", "hi" to "स्वादिष्ट भोजन\nरास्ते में है।", "mr" to "स्वादिष्ट जेवण\nतुमच्यासाठी तयार."),
        "auth_otp_sub" to mapOf("en" to "Verify via instant 6-digit OTP to mobile or email.", "hi" to "मोबाइल या ईमेल पर 6-अंकीय ओटीपी से सत्यापित करें।", "mr" to "मोबाईल किंवा ईमेलवर ६-अंकी ओटीपीने सत्यापित करा."),
        "auth_create_sub" to mapOf("en" to "Register with your name, email/mobile and password.", "hi" to "अपने नाम, ईमेल/मोबाइल और पासवर्ड के साथ पंजीकरण करें।", "mr" to "तुमचे नाव, ईमेल/मोबाईल आणि पासवर्डसह नोंदणी करा."),
        "auth_signin_sub" to mapOf("en" to "Sign in to discover, save and order.", "hi" to "खोजने, सहेजने और ऑर्डर करने के लिए साइन इन करें।", "mr" to "शोधण्यासाठी आणि ऑर्डर करण्यासाठी साईन इन करा."),
        "auth_tab_otp" to mapOf("en" to "Phone / OTP", "hi" to "फ़ोन / ओटीपी", "mr" to "फोन / ओटीपी"),
        "auth_tab_password" to mapOf("en" to "Password", "hi" to "पासवर्ड", "mr" to "पासवर्ड"),
        "auth_tab_register" to mapOf("en" to "Create Account", "hi" to "नया खाता", "mr" to "नवीन खाते"),
        "auth_mobile_phone" to mapOf("en" to "Mobile Phone", "hi" to "मोबाइल नंबर", "mr" to "मोबाईल नंबर"),
        "auth_email_address" to mapOf("en" to "Email Address", "hi" to "ईमेल पता", "mr" to "ईमेल पत्ता"),
        "auth_your_name" to mapOf("en" to "Full Name", "hi" to "पूरा नाम", "mr" to "पूर्ण नाव"),
        "auth_send_otp" to mapOf("en" to "Send Verification OTP", "hi" to "सत्यापन ओटीपी भेजें", "mr" to "ओटीपी पाठवा"),
        "auth_verify_signin" to mapOf("en" to "Verify & Sign In", "hi" to "सत्यापित करें और साइन इन करें", "mr" to "सत्यापित करा आणि साईन इन करा"),
        "auth_create_btn" to mapOf("en" to "Create Account", "hi" to "खाता बनाएं", "mr" to "खाते तयार करा"),
        "auth_signin_btn" to mapOf("en" to "Sign In", "hi" to "साइन इन करें", "mr" to "साईन इन करा"),
        "auth_guest_btn" to mapOf("en" to "Explore Menu as Guest", "hi" to "अतिथि (Guest) के रूप में मेनू देखें", "mr" to "अतिथी (Guest) म्हणून मेनू पहा"),
        "auth_already_have_account" to mapOf("en" to "Already have an account? Sign in", "hi" to "पहले से खाता है? साइन इन करें", "mr" to "आधीच खाते आहे? साईन इन करा"),
        "auth_new_create_account" to mapOf("en" to "New to Restro? Create an account", "hi" to "रेस्ट्रो पर नए हैं? खाता बनाएं", "mr" to "रेस्ट्रोवर नवीन आहात? खाते तयार करा"),
        "auth_forgot_password" to mapOf("en" to "Forgot Password?", "hi" to "पासवर्ड भूल गए?", "mr" to "पासवर्ड विसरलात?"),

        // Discovery & Home
        "delivering_to" to mapOf("en" to "Delivering to:", "hi" to "डिलीवरी स्थान:", "mr" to "डिलिव्हरी ठिकाण:"),
        "what_sounds_good" to mapOf("en" to "What sounds\ngood today?", "hi" to "आज आप क्या\nखाना चाहेंगे?", "mr" to "आज काय खायला\nआवडेल?"),
        "your_favourites" to mapOf("en" to "Your favourites", "hi" to "आपके पसंदीदा", "mr" to "तुमचे आवडते"),
        "discover_sub" to mapOf("en" to "Discover restaurants delivering near you.", "hi" to "अपने आस-पास डिलीवरी करने वाले रेस्तरां खोजें।", "mr" to "तुमच्या जवळ डिलिव्हरी करणारी हॉटेल्स शोधा."),
        "freq_ordered_title" to mapOf("en" to "Frequently Ordered / Quick Reorder", "hi" to "बार-बार ऑर्डर किए गए / तुरंत रीऑर्डर", "mr" to "वारंवार ऑर्डर केलेले / त्वरित रीऑर्डर"),
        "freq_ordered_sub" to mapOf("en" to "Tap to reorder your cravings with 1 tap", "hi" to "1 टैप में अपना पसंदीदा भोजन फिर से मंगाएं", "mr" to "१ टॅपमध्ये तुमचे आवडते पदार्थ पुन्हा मागवा"),
        "city_map_title" to mapOf("en" to "Interactive City Food Map", "hi" to "इंटरएक्टिव सिटी फ़ूड मैप", "mr" to "इंटरअॅक्टिव्ह सिटी फूड मॅप"),
        "city_map_sub" to mapOf("en" to "Explore restaurants & delivery radius live on map", "hi" to "मैप पर लाइव रेस्तरां और डिलीवरी क्षेत्र देखें", "mr" to "नकाशावर थेट हॉटेल्स आणि डिलिव्हरी अंतर पहा"),
        "nearest_rest_title" to mapOf("en" to "Nearest Restaurants", "hi" to "नज़दीकी रेस्तरां", "mr" to "जवळची हॉटेल्स"),
        "saved_fav_title" to mapOf("en" to "Your Saved Favorites", "hi" to "आपके सहेजे गए पसंदीदा", "mr" to "तुमची आवडती हॉटेल्स"),
        "on_the_menu" to mapOf("en" to "On the menu", "hi" to "मेनू में उपलब्ध", "mr" to "मेनूमधील पदार्थ"),
        "closed_for_delivery" to mapOf("en" to "Closed for Delivery", "hi" to "डिलीवरी के लिए बंद", "mr" to "डिलिव्हरीसाठी बंद"),
        "currently_closed" to mapOf("en" to "Currently closed for delivery", "hi" to "वर्तमान में डिलीवरी के लिए बंद", "mr" to "सध्या डिलिव्हरीसाठी बंद"),
        "counter_pay_only" to mapOf("en" to "Counter Bill Payment Only", "hi" to "केवल काउंटर पर बिल भुगतान", "mr" to "फक्त काउंटरवर बिल पेमेंट"),
        "dining_pay_disabled_notice" to mapOf(
            "en" to "Online dining payment is disabled by this restaurant. Please settle your bill at the restaurant counter or with your waiter.",
            "hi" to "इस रेस्तरां द्वारा ऑनलाइन डाइनिंग भुगतान बंद किया गया है। कृपया अपना बिल काउंटर या वेटर के पास जमा करें।",
            "mr" to "या हॉटेलने ऑनलाइन डायनिंग पेमेंट बंद ठेवले आहे. कृपया तुमचे बिल काउंटरवर किंवा वेटरकडे जमा करा."
        ),
        "manage_restaurant_menu" to mapOf("en" to "Manage Menu & Settings", "hi" to "मेनू और सेटिंग्स प्रबंधित करें", "mr" to "मेनू आणि सेटिंग्ज व्यवस्थापित करा"),

        // Profile & Settings
        "my_account" to mapOf("en" to "My Account", "hi" to "मेरा खाता", "mr" to "माझे खाते"),
        "manage_profile_sub" to mapOf("en" to "Manage profile, theme & language preferences", "hi" to "प्रोफ़ाइल, थीम और भाषा प्राथमिकताएं प्रबंधित करें", "mr" to "प्रोफाइल, थीम आणि भाषा सेटिंग्ज व्यवस्थापित करा"),
        "appearance_display" to mapOf("en" to "Appearance, Language & Display", "hi" to "दिखावट, भाषा और डिस्प्ले", "mr" to "दिसावट, भाषा आणि डिस्प्ले"),
        "theme_mode" to mapOf("en" to "Theme Mode", "hi" to "थीम मोड", "mr" to "थीम मोड"),
        "orders_reservations" to mapOf("en" to "Orders & Reservations", "hi" to "ऑर्डर और टेबल बुकिंग", "mr" to "ऑर्डर्स आणि टेबल बुकिंग"),
        "security_preferences" to mapOf("en" to "Security & Preferences", "hi" to "सुरक्षा और प्राथमिकताएं", "mr" to "सुरक्षा आणि प्राधान्ये"),
        "order_history_title" to mapOf("en" to "Order History", "hi" to "ऑर्डर इतिहास", "mr" to "ऑर्डर इतिहास"),
        "order_history_sub" to mapOf("en" to "Your past orders and deliveries", "hi" to "आपके पिछले ऑर्डर और डिलीवरी", "mr" to "तुमच्या मागील ऑर्डर्स आणि डिलिव्हरी"),
        "live_order_tracking" to mapOf("en" to "Live Order Tracking", "hi" to "लाइव ऑर्डर ट्रैकिंग", "mr" to "थेट ऑर्डर ट्रॅकिंग"),

        // Additional Auth & Partner Registration Keys
        "auth_enter_otp" to mapOf("en" to "6-Digit Verification OTP", "hi" to "6-अंकीय सत्यापन ओटीपी", "mr" to "६-अंकी सत्यापन ओटीपी"),
        "auth_login_id" to mapOf("en" to "Email or Mobile Number", "hi" to "ईमेल या मोबाइल नंबर", "mr" to "ईमेल किंवा मोबाईल नंबर"),
        "auth_password_min" to mapOf("en" to "Password (min 6 characters)", "hi" to "पासवर्ड (कम से कम 6 अक्षर)", "mr" to "पासवर्ड (किमान ६ अक्षरे)"),
        "auth_password" to mapOf("en" to "Password", "hi" to "पासवर्ड", "mr" to "पासवर्ड"),
        "auth_switch_to_signin" to mapOf("en" to "Already have an account? Sign in", "hi" to "पहले से खाता है? साइन इन करें", "mr" to "आधीच खाते आहे? साईन इन करा"),
        "auth_switch_to_register" to mapOf("en" to "New to Restro? Create an account", "hi" to "रेस्ट्रो पर नए हैं? खाता बनाएं", "mr" to "रेस्ट्रोवर नवीन आहात? खाते तयार करा"),
        "role_customer" to mapOf("en" to "Customer", "hi" to "ग्राहक", "mr" to "ग्राहक"),
        "role_rider" to mapOf("en" to "Delivery Rider", "hi" to "डिलीवरी राइडर", "mr" to "डिलिव्हरी रायडर"),
        "role_admin" to mapOf("en" to "Restaurant Admin", "hi" to "रेस्तरां एडमिन", "mr" to "हॉटेल ॲडमिन"),
        "vehicle_number_label" to mapOf("en" to "Vehicle Registration Number", "hi" to "वाहन पंजीकरण संख्या", "mr" to "वाहन नोंदणी क्रमांक"),
        "upload_dl_btn" to mapOf("en" to "Upload Driving Licence", "hi" to "ड्राइविंग लाइसेंस अपलोड करें", "mr" to "ड्रायव्हिंग लायसन्स अपलोड करा"),
        "fssai_number_label" to mapOf("en" to "14-Digit FSSAI License No.", "hi" to "14-अंकीय FSSAI लाइसेंस नं.", "mr" to "१४-अंकी FSSAI लायसन्स क्र."),
        "upload_fssai_btn" to mapOf("en" to "Upload FSSAI Certificate", "hi" to "FSSAI प्रमाणपत्र अपलोड करें", "mr" to "FSSAI प्रमाणपत्र अपलोड करा"),
        "gst_number_label" to mapOf("en" to "15-Char GST Number (GSTIN)", "hi" to "15-अंकीय जीएसटी नंबर (GSTIN)", "mr" to "१५-अंकी जीएसटी क्रमांक (GSTIN)"),
        "upload_gst_btn" to mapOf("en" to "Upload GST Certificate", "hi" to "जीएसटी प्रमाणपत्र अपलोड करें", "mr" to "जीएसटी प्रमाणपत्र अपलोड करा"),
        "upload_logo_btn" to mapOf("en" to "Upload Restaurant Logo", "hi" to "रेस्तरां लोगो अपलोड करें", "mr" to "हॉटेल लोगो अपलोड करा"),
        "upload_dish_image_btn" to mapOf("en" to "Upload Custom Dish Image", "hi" to "कस्टम डिश फोटो अपलोड करें", "mr" to "पदार्थाचा फोटो अपलोड करा"),
        "doc_verified_badge" to mapOf("en" to "Gemini OCR Verified ✓", "hi" to "जेमिनी ओसीआर सत्यापित ✓", "mr" to "जेमिनी ओसीआर सत्यापित ✓"),
        "verifying_doc_gemini" to mapOf("en" to "Extracting & verifying document via Gemini...", "hi" to "जेमिनी द्वारा दस्तावेज़ की जांच हो रही है...", "mr" to "जेमिनीद्वारे कागदपत्र तपासले जात आहे..."),

        // Restaurant Operational & Menu Console Keys
        "open_for_delivery_label" to mapOf("en" to "Open for Delivery", "hi" to "डिलीवरी के लिए खुला", "mr" to "डिलिव्हरीसाठी सुरू"),
        "accept_dining_payment_label" to mapOf("en" to "Accept Online Dining Payment", "hi" to "ऑनलाइन डाइनिंग भुगतान स्वीकार करें", "mr" to "ऑनलाइन डायनिंग पेमेंट स्वीकारा"),
        "prep_time_label" to mapOf("en" to "Kitchen Preparation Time", "hi" to "किचन तैयारी का समय", "mr" to "किचन तयारीचा वेळ"),
        "add_new_dish" to mapOf("en" to "Add Dish", "hi" to "नया व्यंजन जोड़ें", "mr" to "नवीन पदार्थ जोडा"),
        "update_dish" to mapOf("en" to "Update Dish", "hi" to "व्यंजन अपडेट करें", "mr" to "पदार्थ अपडेट करा"),
        "delete_dish" to mapOf("en" to "Delete Dish", "hi" to "व्यंजन हटाएं", "mr" to "पदार्थ काढून टाका"),
        "save_changes" to mapOf("en" to "Save Changes", "hi" to "परिवर्तन सहेजें", "mr" to "बदल जतन करा"),
        "cancel" to mapOf("en" to "Cancel", "hi" to "रद्द करें", "mr" to "रद्द करा"),

        // Delivery Rider Portal Keys
        "rider_portal_title" to mapOf("en" to "Delivery Partner Hub", "hi" to "डिलीवरी पार्टनर हब", "mr" to "डिलिव्हरी पार्टनर हब"),
        "rider_income_day" to mapOf("en" to "Today (Day-wise)", "hi" to "आज की आय (दिन-वार)", "mr" to "आजचे उत्पन्न (दिवसनिहाय)"),
        "rider_income_week" to mapOf("en" to "This Week (Week-wise)", "hi" to "इस सप्ताह (सप्ताह-वार)", "mr" to "या आठवड्याचे (आठवडा-निहाय)"),
        "rider_income_month" to mapOf("en" to "This Month (Month-wise)", "hi" to "इस महीने (माह-वार)", "mr" to "या महिन्याचे (महिना-निहाय)"),
        "rider_avg_per_ride" to mapOf("en" to "Avg Income / Ride", "hi" to "औसत आय / राइड", "mr" to "सरासरी उत्पन्न / राईड"),
        "rider_available_balance" to mapOf("en" to "Withdrawable Wallet Balance", "hi" to "निकासी योग्य बैलेंस", "mr" to "काढता येणारी शिल्लक रक्कम"),
        "rider_withdraw_btn" to mapOf("en" to "Withdraw to Bank / UPI Now", "hi" to "अभी बैंक / यूपीआई में पैसे निकालें", "mr" to "आताच बँक / यूपीआय मध्ये पैसे काढा"),
        "rider_choose_ride" to mapOf("en" to "Available Ride Offers Near You", "hi" to "आपके पास उपलब्ध राइड ऑफ़र", "mr" to "तुमच्या जवळील उपलब्ध राईड ऑफर्स"),
        "rider_accept_ride" to mapOf("en" to "Accept & Complete Ride", "hi" to "राइड स्वीकार करें और पूरा करें", "mr" to "राईड स्वीकारा आणि पूर्ण करा"),
        "rider_reject_ride" to mapOf("en" to "Reject Offer", "hi" to "ऑफ़र अस्वीकार करें", "mr" to "ऑफर नाकारा"),
        "rider_rides_done" to mapOf("en" to "rides done", "hi" to "राइड पूरी", "mr" to "राईड्स पूर्ण"),
        "rider_no_offers" to mapOf("en" to "All nearby ride offers processed. Rejected offers are hidden permanently.", "hi" to "सभी आस-पास के ऑफ़र पूरे हो गए। अस्वीकृत ऑफ़र दोबारा नहीं दिखाए जाएंगे।", "mr" to "जवळील सर्व ऑफर्स पूर्ण झाल्या. नाकारलेल्या ऑफर्स पुन्हा दिसणार नाहीत."),

        // Staff Portal Navigation
        "pos_cashier" to mapOf("en" to "POS Cashier", "hi" to "पीओएस कैशियर", "mr" to "पीओएस कॅशियर"),
        "kitchen_kds" to mapOf("en" to "Kitchen KDS", "hi" to "किचन केडीएस", "mr" to "किचन केडीएस"),
        "tables_floor" to mapOf("en" to "Tables", "hi" to "टेबल्स", "mr" to "टेबल्स"),
        "menu_prices" to mapOf("en" to "Menu & Prices", "hi" to "मेनू और कीमतें", "mr" to "मेनू आणि किमती"),
        "inventory_stock" to mapOf("en" to "Inventory", "hi" to "इन्वेंटरी", "mr" to "इन्व्हेंटरी"),
        "staff_payroll" to mapOf("en" to "Staff Payroll", "hi" to "स्टाफ पेरोल", "mr" to "स्टाफ पेरोल"),
        "analytics_tab" to mapOf("en" to "Analytics", "hi" to "एनालिटिक्स", "mr" to "ॲनालिटिक्स"),

        // Customer Home, Discovery & Profile UI Keys
        "home_heading_title" to mapOf("en" to "What sounds good today?", "hi" to "आज आप क्या खाना चाहेंगे?", "mr" to "आज काय खायला आवडेल?"),
        "home_heading_subtitle" to mapOf(
            "en" to "Discover restaurants, dishes, and neighborhood favorites.",
            "hi" to "आस-पास के बेहतरीन रेस्तरां और स्वादिष्ट व्यंजन खोजें।",
            "mr" to "जवळपासची उत्तम रेस्टॉरंट्स आणि चविष्ट पदार्थ शोधा."
        ),
        "frequently_ordered_title" to mapOf("en" to "Frequently Ordered by You", "hi" to "बार-बार ऑर्डर किए गए व्यंजन", "mr" to "तुम्ही वारंवार ऑर्डर केलेले"),
        "frequently_ordered_sub" to mapOf("en" to "Tap to reorder your favorites in seconds", "hi" to "1 टैप में अपना पसंदीदा भोजन फिर से मंगाएं", "mr" to "१ टॅपमध्ये तुमचे आवडते पदार्थ पुन्हा मागवा"),
        "nearest_restaurants" to mapOf("en" to "Nearest Restaurants", "hi" to "नज़दीकी रेस्तरां", "mr" to "जवळची हॉटेल्स"),
        "sorted_by_distance" to mapOf("en" to "Sorted by distance", "hi" to "दूरी के अनुसार क्रमबद्ध", "mr" to "अंतरानुसार क्रमवारी"),
        "places_count" to mapOf("en" to "places", "hi" to "स्थान", "mr" to "ठिकाणे"),
        "saved_favorites" to mapOf("en" to "Your Saved Favorites", "hi" to "आपके पसंदीदा रेस्तरां", "mr" to "तुमची आवडती हॉटेल्स"),
        "no_results" to mapOf("en" to "No matching dishes or restaurants", "hi" to "कोई मेल खाने वाला व्यंजन या रेस्तरां नहीं मिला", "mr" to "कोणतेही जुळणारे पदार्थ किंवा हॉटेल्स सापडले नाहीत"),
        "filter_all" to mapOf("en" to "All", "hi" to "सभी", "mr" to "सर्व"),
        "reviews_label" to mapOf("en" to "reviews", "hi" to "समीक्षाएं", "mr" to "रिव्ह्यू"),
        "view_on_map" to mapOf("en" to "View on Map", "hi" to "मैप पर देखें", "mr" to "नकाशावर पहा"),
        "book_a_table" to mapOf("en" to "Book a Table", "hi" to "टेबल बुक करें", "mr" to "टेबल बुक करा"),
        "add_btn" to mapOf("en" to "Add", "hi" to "जोड़ें", "mr" to "जोडा"),
        "restaurant_partner_controls" to mapOf("en" to "Restaurant Partner Controls", "hi" to "रेस्तरां पार्टनर नियंत्रण", "mr" to "हॉटेल पार्टनर नियंत्रणे"),
        "language_settings_title" to mapOf("en" to "App Language", "hi" to "ऐप की भाषा", "mr" to "अॅपची भाषा"),
        "replay_tour_title" to mapOf("en" to "Replay Interactive App Tour", "hi" to "ऐप टूर फिर से देखें", "mr" to "अॅप टूर पुन्हा पहा"),
        "orders_title" to mapOf("en" to "Your Orders", "hi" to "आपके ऑर्डर्स", "mr" to "तुमच्या ऑर्डर्स"),
        "orders_subtitle" to mapOf("en" to "Tap an order to track live or leave a review", "hi" to "लाइव ट्रैक या समीक्षा के लिए ऑर्डर पर टैप करें", "mr" to "थेट ट्रॅक किंवा रिव्ह्यूसाठी ऑर्डरवर टॅप करा"),
        "cart_subtitle" to mapOf("en" to "items in your order", "hi" to "आइटम आपके ऑर्डर में", "mr" to "पदार्थ तुमच्या ऑर्डरमध्ये"),
        "cart_empty_sub" to mapOf(
            "en" to "Browse restaurants and add delicious dishes to build your order.",
            "hi" to "अपने आस-पास के बेहतरीन रेस्तरां से स्वादिष्ट भोजन जोड़ें।",
            "mr" to "जवळपासच्या उत्तम हॉटेल्समधून स्वादिष्ट पदार्थ जोडा."
        ),
        "browse_restaurants" to mapOf("en" to "Browse Restaurants", "hi" to "रेस्तरां देखें", "mr" to "हॉटेल्स पहा"),
        
        // Checkout & Billing Keys
        "bill_summary" to mapOf("en" to "Bill Summary", "hi" to "बिल सारांश", "mr" to "बिल सारांश"),
        "delivery_address" to mapOf("en" to "Delivery Address", "hi" to "डिलीवरी का पता", "mr" to "डिलिव्हरीचा पत्ता"),
        "default_address" to mapOf("en" to "Default Delivery Location", "hi" to "डिफ़ॉल्ट डिलीवरी स्थान", "mr" to "डीफॉल्ट डिलिव्हरी ठिकाण"),
        "add_custom_address" to mapOf("en" to "Add custom address", "hi" to "नया पता जोड़ें", "mr" to "नवीन पत्ता जोडा"),
        "add_another_address" to mapOf("en" to "Add another address", "hi" to "अन्य पता जोड़ें", "mr" to "दुसरा पत्ता जोडा"),
        "select_payment_method" to mapOf("en" to "Select Payment Method", "hi" to "भुगतान विधि चुनें", "mr" to "पेमेंट पद्धत निवडा"),
        "order_summary" to mapOf("en" to "Order Summary", "hi" to "ऑर्डर सारांश", "mr" to "ऑर्डर सारांश"),
        "delivery_partner_fee" to mapOf("en" to "Delivery Partner Fee", "hi" to "डिलीवरी पार्टनर शुल्क", "mr" to "डिलिव्हरी पार्टनर फी"),
        "coupon_savings" to mapOf("en" to "Coupon Savings", "hi" to "कूपन बचत", "mr" to "कूपन सवलत"),
        "grand_total_paid" to mapOf("en" to "Grand Total Paid", "hi" to "कुल भुगतान किया गया", "mr" to "एकूण भरलेली रक्कम"),
        "view_menu" to mapOf("en" to "View Menu", "hi" to "मेनू देखें", "mr" to "मेनू पहा"),
        "full_map" to mapOf("en" to "Full Map", "hi" to "पूरा मैप", "mr" to "पूर्ण नकाशा"),
        "pay_bill" to mapOf("en" to "Pay Bill", "hi" to "बिल भरें", "mr" to "बिल भरा"),
        "reset_filters" to mapOf("en" to "Reset Filters", "hi" to "फ़िल्टर रीसेट करें", "mr" to "फिल्टर्स रीसेट करा"),
        "staff_tip" to mapOf("en" to "Staff Tip", "hi" to "स्टाफ टिप", "mr" to "कर्मचाऱ्यांना टीप"),
        "instant_access" to mapOf("en" to "Instant Access", "hi" to "त्वरित पहुंच", "mr" to "त्वरित प्रवेश"),
        "resend_otp" to mapOf("en" to "Resend OTP", "hi" to "ओटीपी पुनः भेजें", "mr" to "ओटीपी पुन्हा पाठवा"),
        "clear_search" to mapOf("en" to "Clear Search", "hi" to "खोज साफ़ करें", "mr" to "शोध साफ करा"),
        "service_charge" to mapOf("en" to "Service Charge", "hi" to "सेवा शुल्क", "mr" to "सेवा शुल्क"),
        "checkout_title" to mapOf("en" to "Checkout", "hi" to "चेकआउट", "mr" to "चेकआऊट"),
        "checkout_sub" to mapOf("en" to "Confirm delivery address and payment", "hi" to "डिलीवरी का पता और भुगतान की पुष्टि करें", "mr" to "डिलिव्हरी पत्ता आणि पेमेंट तपासा"),
        "empty_cart_title" to mapOf("en" to "Your cart is empty", "hi" to "आपकी कार्ट खाली है", "mr" to "तुमची कार्ट रिकामी आहे"),
        "empty_cart_desc" to mapOf("en" to "Add delicious dishes to your bag before checking out.", "hi" to "चेकआउट करने से पहले अपनी थाली में स्वादिष्ट व्यंजन जोड़ें।", "mr" to "चेकआऊट करण्यापूर्वी स्वादिष्ट पदार्थ जोडा."),
        "browse_menus" to mapOf("en" to "Browse Menus", "hi" to "मेनू देखें", "mr" to "मेनू पाहा"),
        "dish_veg_tag" to mapOf("en" to "● VEGETARIAN", "hi" to "● केवल शाकाहारी", "mr" to "● फक्त शाकाहारी"),
        "dish_non_veg_tag" to mapOf("en" to "● NON-VEGETARIAN", "hi" to "● मांसाहारी", "mr" to "● मांसाहारी"),
        "dish_customisable" to mapOf("en" to "· Customisable", "hi" to "· अनुकूलन योग्य", "mr" to "· बदल करता येईल"),
        "tap_to_add_directly" to mapOf("en" to "Tap to add directly", "hi" to "सीधे जोड़ने के लिए टैप करें", "mr" to "थेट जोडण्यासाठी टॅप करा"),
        "dishes_count_label" to mapOf("en" to "Dishes", "hi" to "व्यंजन", "mr" to "पदार्थ"),
        "restaurants_count_label" to mapOf("en" to "Restaurants", "hi" to "रेस्तरां", "mr" to "हॉटेल्स"),
        "no_results_for" to mapOf("en" to "No dishes or restaurants found for", "hi" to "इसके लिए कोई व्यंजन या रेस्तरां नहीं मिला", "mr" to "यासाठी कोणतेही पदार्थ किंवा हॉटेल्स आढळले नाहीत"),
        "google_sign_in_btn" to mapOf("en" to "Continue with Google", "hi" to "Google के साथ जारी रखें", "mr" to "Google सह पुढे सुरू ठेवा"),
        "google_create_account_btn" to mapOf("en" to "Sign up with Google", "hi" to "Google के साथ खाता बनाएं", "mr" to "Google सह खाते तयार करा"),
        "demo_accounts_title" to mapOf("en" to "Demo Test Accounts (1-Tap Login)", "hi" to "डेमो टेस्ट खाते (1-टैप लॉगिन)", "mr" to "डेमो टेस्ट खाती (१-टॅप लॉगिन)"),
        "demo_instant_access" to mapOf("en" to "Instant Access", "hi" to "त्वरित पहुंच", "mr" to "त्वरित प्रवेश"),
        "valid_email_hint" to mapOf("en" to "Please enter a valid email address", "hi" to "कृपया एक वैध ईमेल पता दर्ज करें", "mr" to "कृपया वैध ईमेल पत्ता प्रविष्ट करा"),
        "valid_phone_hint" to mapOf("en" to "Please enter a valid 10-digit mobile number", "hi" to "कृपया 10 अंकों का वैध मोबाइल नंबर दर्ज करें", "mr" to "कृपया १० अंकी वैध मोबाईल नंबर प्रविष्ट करा"),
        "password_min_hint" to mapOf("en" to "Password must be at least 6 characters", "hi" to "पासवर्ड कम से कम 6 अक्षरों का होना चाहिए", "mr" to "पासवर्ड किमान ६ अक्षरांचा असावा"),
        "passwords_dont_match" to mapOf("en" to "Passwords do not match", "hi" to "पासवर्ड मेल नहीं खाते", "mr" to "पासवर्ड जुळत नाहीत"),
        "confirm_password_label" to mapOf("en" to "Confirm Password", "hi" to "पासवर्ड की पुष्टि करें", "mr" to "पासवर्डची पुष्टी करा"),
        "password_strength_label" to mapOf("en" to "Password Strength", "hi" to "पासवर्ड की मजबूती", "mr" to "पासवर्डची ताकद"),
        "password_strength_weak" to mapOf("en" to "Weak", "hi" to "कमजोर", "mr" to "कमकुवत"),
        "password_strength_medium" to mapOf("en" to "Medium", "hi" to "मध्यम", "mr" to "मध्यम"),
        "password_strength_strong" to mapOf("en" to "Strong", "hi" to "मजबूत", "mr" to "मजबूत"),
        "google_signed_in_toast" to mapOf("en" to "Successfully signed in with Google!", "hi" to "Google के साथ सफलतापूर्वक साइन इन किया गया!", "mr" to "Google सह यशस्वीरीत्या साईन इन केले!"),
        "mins_unit" to mapOf("en" to "mins", "hi" to "मिनट", "mr" to "मिनिटे"),
        "reorder_btn" to mapOf("en" to "Reorder +", "hi" to "फिर ऑर्डर करें +", "mr" to "पुन्हा ऑर्डर +"),
        "search_no_results_sub" to mapOf("en" to "Try searching for Biryani, Pizza, Burger, Noodles, or Paneer", "hi" to "बिरयानी, पिज़्ज़ा, बर्गर, नूडल्स या पनीर खोज कर देखें", "mr" to "बिर्याणी, पिझ्झा, बर्गर, नूडल्स किंवा पनीर शोधून पहा"),
        "clear_search_btn" to mapOf("en" to "Clear Search", "hi" to "खोज हटाएं", "mr" to "शोध हटवा"),
        "profile_sec_appearance" to mapOf("en" to "Appearance & Display", "hi" to "दिखावट और प्रदर्शन", "mr" to "देखावा आणि प्रदर्शन"),
        "profile_sec_orders" to mapOf("en" to "Orders & Reservations", "hi" to "ऑर्डर और टेबल बुकिंग", "mr" to "ऑर्डर्स आणि आरक्षण"),
        "profile_sec_security" to mapOf("en" to "Security & Preferences", "hi" to "सुरक्षा और प्राथमिकताएं", "mr" to "सुरक्षा आणि प्राधान्ये"),
        "profile_sec_help" to mapOf("en" to "Help & Information", "hi" to "सहायता और जानकारी", "mr" to "मदत आणि माहिती"),
        "saved_addresses_title" to mapOf("en" to "Saved Delivery Addresses", "hi" to "सहेजे गए डिलीवरी पते", "mr" to "जतन केलेले डिलिव्हरी पत्ते"),
        "saved_addresses_sub" to mapOf("en" to "Manage Home, Work and GPS drop coordinates", "hi" to "घर, कार्यालय और जीपीएस स्थान प्रबंधित करें", "mr" to "घर, ऑफिस आणि जीपीएस ठिकाणे व्यवस्थापित करा"),
        "change_password_title" to mapOf("en" to "Change & Update Password", "hi" to "पासवर्ड बदलें और अपडेट करें", "mr" to "पासवर्ड बदला आणि अपडेट करा"),
        "change_password_sub" to mapOf("en" to "Manage your login credentials & account protection", "hi" to "लॉगिन क्रेडेंशियल और खाता सुरक्षा प्रबंधित करें", "mr" to "लॉगिन क्रेडेन्शियल आणि खाते सुरक्षा व्यवस्थापित करा"),
        "customer_support_title" to mapOf("en" to "24x7 Customer Support", "hi" to "24x7 ग्राहक सहायता", "mr" to "२४x७ ग्राहक मदत"),
        "customer_support_sub" to mapOf("en" to "Speak to our care team or report order issues", "hi" to "हमारी सहायता टीम से बात करें या समस्या बताएं", "mr" to "आमच्या मदत टीमशी बोला किंवा अडचण नोंदवा"),
        "about_enterprise_title" to mapOf("en" to "About Restro Enterprise", "hi" to "रेस्ट्रो एंटरप्राइज के बारे में", "mr" to "रेस्ट्रो एंटरप्राइझबद्दल"),
        "about_enterprise_sub" to mapOf("en" to "v2.4.0 Production Build • Terms & Privacy", "hi" to "v2.4.0 प्रोडक्शन बिल्ड · नियम और गोपनीयता", "mr" to "v2.4.0 प्रोडक्शन बिल्ड · अटी आणि गोपनीयता"),
        "staff_portal_title" to mapOf("en" to "Staff Management Portal", "hi" to "स्टाफ प्रबंधन पोर्टल", "mr" to "कर्मचारी व्यवस्थापन पोर्टल"),
        "staff_portal_sub" to mapOf("en" to "Access Waiter POS, KDS Kitchen Display and Admin Analytics", "hi" to "वेटर पीओएस, केडीएस किचन और एडमिन एनालिटिक्स खोलें", "mr" to "वेटर पीओएस, केडीएस किचन आणि ॲडमिन ॲनालिटिक्स उघडा"),
        "sign_out_btn" to mapOf("en" to "Sign Out of Restro", "hi" to "रेस्ट्रो से लॉग आउट करें", "mr" to "रेस्ट्रोमधून लॉग आऊट करा"),
        "sign_in_register_btn" to mapOf("en" to "Sign In or Register", "hi" to "साइन इन करें या खाता बनाएं", "mr" to "साइन इन करा किंवा खाते तयार करा"),
        "logout_dialog_title" to mapOf("en" to "Sign Out", "hi" to "लॉग आउट करें", "mr" to "लॉग आऊट करा"),
        "logout_dialog_desc" to mapOf("en" to "Are you sure you want to sign out from your Restro account? Your active cart and orders will be preserved upon re-login.", "hi" to "क्या आप वाकई अपने रेस्ट्रो खाते से लॉग आउट करना चाहते हैं? दोबारा लॉगिन करने पर आपकी सक्रिय कार्ट और ऑर्डर सुरक्षित रहेंगे।", "mr" to "तुम्हाला नक्की रेस्ट्रो खात्यातून लॉग आऊट करायचे आहे का? पुन्हा लॉगिन केल्यावर तुमची कार्ट आणि ऑर्डर्स सुरक्षित राहतील."),
        "stay_signed_in" to mapOf("en" to "Stay Signed In", "hi" to "लॉग इन रहें", "mr" to "लॉग इन राहा"),
        "edit_profile_title" to mapOf("en" to "Edit Profile Details", "hi" to "प्रोफ़ाइल विवरण संपादित करें", "mr" to "प्रोफाइल तपशील संपादित करा"),
        "full_name_label" to mapOf("en" to "Full Name", "hi" to "पूरा नाम", "mr" to "पूर्ण नाव"),
        "mobile_number_label" to mapOf("en" to "Mobile Number", "hi" to "मोबाइल नंबर", "mr" to "मोबाईल नंबर"),
        "save_changes_btn" to mapOf("en" to "Save Changes", "hi" to "परिवर्तन सहेजें", "mr" to "बदल जतन करा"),
        "cancel_btn" to mapOf("en" to "Cancel", "hi" to "रद्द करें", "mr" to "रद्द करा"),
        "current_password_label" to mapOf("en" to "Current Password", "hi" to "वर्तमान पासवर्ड", "mr" to "सध्याचा पासवर्ड"),
        "new_password_label" to mapOf("en" to "New Password (min 6 chars)", "hi" to "नया पासवर्ड (कम से कम 6 अक्षर)", "mr" to "नवीन पासवर्ड (किमान ६ अक्षरे)"),
        "update_password_btn" to mapOf("en" to "Update Password", "hi" to "पासवर्ड अपडेट करें", "mr" to "पासवर्ड अपडेट करा"),
        "select_landmark_title" to mapOf("en" to "Select Delivery Landmark", "hi" to "डिलीवरी लैंडमार्क चुनें", "mr" to "डिलिव्हरी लँडमार्क निवडा"),
        "select_landmark_sub" to mapOf("en" to "Nearest restaurants recalculate live by distance", "hi" to "दूरी के अनुसार नज़दीकी रेस्तरां तुरंत अपडेट होते हैं", "mr" to "अंतरानुसार जवळची हॉटेल्स त्वरित अपडेट होतात"),
        "use_hardware_gps" to mapOf("en" to "Use My Current Hardware GPS Location", "hi" to "मेरे वर्तमान जीपीएस स्थान का उपयोग करें", "mr" to "माझ्या सध्याच्या जीपीएस स्थानाचा वापर करा"),
        "detect_gps_sub" to mapOf("en" to "Detect live coordinates via Android LocationManager", "hi" to "एंड्रॉइड लोकेशन से लाइव निर्देशांक प्राप्त करें", "mr" to "अँड्रॉइड लोकेशनवरून थेट कोऑर्डिनेट्स मिळवा"),
        "gps_heartbeat_label" to mapOf("en" to "10S LIVE GPS HEARTBEAT", "hi" to "10 से. लाइव जीपीएस हार्टबीट", "mr" to "१० सेकंद थेट जीपीएस ट्रॅकिंग"),
        "delivery_complete_label" to mapOf("en" to "DELIVERY COMPLETE", "hi" to "डिलीवरी पूर्ण हुई", "mr" to "डिलिव्हरी पूर्ण झाली"),
        "at_your_gate_label" to mapOf("en" to "At your gate", "hi" to "आपके दरवाजे पर", "mr" to "तुमच्या दारावर"),
        "rider_gate_alert" to mapOf("en" to "🛵 Ramesh is at your gate! Please meet your delivery partner.", "hi" to "🛵 रमेश आपके दरवाजे पर पहुंच गए हैं! कृपया अपना ऑर्डर प्राप्त करें।", "mr" to "🛵 रमेश तुमच्या दारावर पोहोचले आहेत! कृपया तुमची डिलिव्हरी घ्या."),
        "gmaps_app_btn" to mapOf("en" to "Google Maps App ↗", "hi" to "गूगल मैप्स ऐप ↗", "mr" to "गुगल मॅप्स ॲप ↗"),
        "reviews_section_title" to mapOf("en" to "Ratings & Customer Reviews", "hi" to "रेटिंग और ग्राहक समीक्षाएं", "mr" to "रेटिंग आणि ग्राहक पुनरावलोकने"),
        "write_review_btn" to mapOf("en" to "Write a Review", "hi" to "समीक्षा लिखें", "mr" to "पुनरावलोकन लिहा"),
        "verified_purchase_tag" to mapOf("en" to "Verified Purchase", "hi" to "सत्यापित खरीद", "mr" to "प्रमाणित खरेदी"),
        "orders_count_stat" to mapOf("en" to "Orders", "hi" to "ऑर्डर्स", "mr" to "ऑर्डर्स"),
        "bookings_count_stat" to mapOf("en" to "Bookings", "hi" to "बुकिंग्स", "mr" to "बुकिंग्स"),
        "addresses_count_stat" to mapOf("en" to "Addresses", "hi" to "पते", "mr" to "पत्ते"),
        "theme_mode_title" to mapOf("en" to "Theme Mode", "hi" to "थीम मोड", "mr" to "थीम मोड"),
        "dark_mode_sub" to mapOf("en" to "Dark mode active (gentle on eyes)", "hi" to "डार्क मोड सक्रिय (आंखों के लिए आरामदायक)", "mr" to "डार्क मोड सक्रिय (डोळ्यांसाठी सौम्य)"),
        "light_mode_sub" to mapOf("en" to "Light mode active (high contrast & bright)", "hi" to "लाइट मोड सक्रिय (उज्ज्वल और स्पष्ट)", "mr" to "लाइट मोड सक्रिय (उज्ज्वल आणि स्पष्ट)"),
        "app_language_title" to mapOf("en" to "App Language", "hi" to "ऐप की भाषा", "mr" to "ॲपची भाषा"),
        "guided_tour_title" to mapOf("en" to "See App Tour Again", "hi" to "ऐप टूर फिर से देखें", "mr" to "ॲप टूर पुन्हा पाहा"),
        "guided_tour_sub" to mapOf("en" to "14-step guided feature walkthrough", "hi" to "14-चरणीय विस्तृत फीचर गाइड", "mr" to "१४-टप्प्यांचे मार्गदर्शक"),
        "past_orders_title" to mapOf("en" to "Past Orders & Invoices", "hi" to "पिछले ऑर्डर और बिल", "mr" to "मागील ऑर्डर्स आणि बिले"),
        "past_orders_sub" to mapOf("en" to "View itemized receipts, live tracking and 1-click reorder", "hi" to "विस्तृत रसीदें, लाइव ट्रैकिंग और 1-क्लिक रीऑर्डर", "mr" to "तपशीलवार पावत्या, थेट ट्रॅकिंग आणि १-क्लिक रीऑर्डर"),
        "dine_in_bookings_title" to mapOf("en" to "Dine-In Table Bookings", "hi" to "डाइन-इन टेबल बुकिंग", "mr" to "डाइन-इन टेबल आरक्षणे"),
        "dine_in_bookings_sub" to mapOf("en" to "Inspect reservation dates, guests and QR check-in status", "hi" to "बुकिंग तिथि, अतिथि और क्यूआर चेक-इन स्थिति", "mr" to "आरक्षण तारीख, अतिथी आणि क्यूआर चेक-इन स्थिती"),
        "fav_restaurants_title" to mapOf("en" to "Favorite Restaurants", "hi" to "पसंदीदा रेस्तरां", "mr" to "आवडती हॉटेल्स"),
        "fav_restaurants_sub" to mapOf("en" to "Quick access to your saved dining spots", "hi" to "अपने सहेजे गए रेस्तरां तक त्वरित पहुंच", "mr" to "तुमच्या जतन केलेल्या हॉटेल्समध्ये त्वरित जा"),
        "customize_addons" to mapOf("en" to "Customize with Add-ons", "hi" to "ऐड-ऑन के साथ कस्टमाइज़ करें", "mr" to "अ‍ॅड-ऑन्ससह कस्टमाइझ करा"),
        "quantity_label" to mapOf("en" to "Quantity", "hi" to "मात्रा", "mr" to "प्रमाण"),
        "add_to_cart_btn" to mapOf("en" to "Add to Cart", "hi" to "कार्ट में जोड़ें", "mr" to "कार्टमध्ये जोडा"),
        "role_customer" to mapOf("en" to "Customer", "hi" to "ग्राहक", "mr" to "ग्राहक"),
        "role_admin" to mapOf("en" to "Manager", "hi" to "मैनेजर", "mr" to "मॅनेजर"),
        "role_master_admin" to mapOf("en" to "Master Admin", "hi" to "मास्टर एडमिन", "mr" to "मास्टर ॲडमिन"),
        "role_chef" to mapOf("en" to "Chef", "hi" to "शेफ", "mr" to "शेफ"),
        "role_waiter" to mapOf("en" to "Waiter", "hi" to "वेटर", "mr" to "वेटर"),
        "role_rider" to mapOf("en" to "Rider", "hi" to "राइडर", "mr" to "रायडर"),
        "role_kiosk" to mapOf("en" to "Kiosk", "hi" to "कियोस्क", "mr" to "किऑस्क"),
        "customer_reviews_count" to mapOf("en" to "reviews", "hi" to "समीक्षाएं", "mr" to "पुनरावलोकने"),
        "review_input_hint" to mapOf("en" to "Share your dining experience...", "hi" to "अपने भोजन का अनुभव साझा करें...", "mr" to "तुमचा जेवणाचा अनुभव सांगा..."),
        "submit_btn" to mapOf("en" to "Submit", "hi" to "सबमिट करें", "mr" to "सबमिट करा"),
        // Staff Role & Portal Translations
        "pos_select_table" to mapOf("en" to "Select Table", "hi" to "टेबल चुनें", "mr" to "टेबल निवडा"),
        "pos_no_table" to mapOf("en" to "No Table Selected", "hi" to "कोई टेबल नहीं चुना", "mr" to "कोणताही टेबल निवडला नाही"),
        "pos_select_table_hint" to mapOf("en" to "Select an active table to begin taking orders", "hi" to "ऑर्डर लेने के लिए सक्रिय टेबल चुनें", "mr" to "ऑर्डर घेण्यासाठी सक्रिय टेबल निवडा"),
        "pos_live_tab" to mapOf("en" to "Live Tab", "hi" to "लाइव बिल", "mr" to "लाईव्ह बिल"),
        "pos_change_table" to mapOf("en" to "Change", "hi" to "बदलें", "mr" to "बदला"),
        "pos_search_placeholder" to mapOf("en" to "Search menu items...", "hi" to "मेनू व्यंजन खोजें...", "mr" to "मेनू आयटम शोधा..."),
        "pos_add_to_order" to mapOf("en" to "Add to Order", "hi" to "ऑर्डर में जोड़ें", "mr" to "ऑर्डरमध्ये जोडा"),
        "pos_view_cart" to mapOf("en" to "View Cart", "hi" to "कार्ट देखें", "mr" to "कार्ट पहा"),
        "pos_send_to_kitchen" to mapOf("en" to "Send to Kitchen (KOT)", "hi" to "रसोई को भेजें (KOT)", "mr" to "किचनला पाठवा (KOT)"),
        "kds_title" to mapOf("en" to "Kitchen Display System", "hi" to "किचन डिस्प्ले सिस्टम", "mr" to "किचन डिस्प्ले सिस्टीम"),
        "kds_pending" to mapOf("en" to "Pending Orders", "hi" to "लंबित ऑर्डर्स", "mr" to "प्रलंबित ऑर्डर्स"),
        "kds_in_progress" to mapOf("en" to "Preparing", "hi" to "तैयार हो रहा है", "mr" to "तयार होत आहे"),
        "kds_ready" to mapOf("en" to "Ready for Pickup", "hi" to "पिकअप के लिए तैयार", "mr" to "पिकअपसाठी तयार"),
        "kds_mark_ready" to mapOf("en" to "Mark Ready", "hi" to "तैयार मार्क करें", "mr" to "तयार मार्क करा"),
        "kds_served" to mapOf("en" to "Mark Served", "hi" to "परोसा गया", "mr" to "सर्व्ह केले"),
        "tables_title" to mapOf("en" to "Floor Tables", "hi" to "फ्लोर टेबल्स", "mr" to "फ्लोअर टेबल्स"),
        "tables_available" to mapOf("en" to "Available", "hi" to "उपलब्ध", "mr" to "उपलब्ध"),
        "tables_occupied" to mapOf("en" to "Occupied", "hi" to "व्यस्त", "mr" to "व्यापलेले"),
        "tables_billed" to mapOf("en" to "Bill Requested", "hi" to "बिल मांगा गया", "mr" to "बिल मागितले"),
        "tables_seats" to mapOf("en" to "Seats", "hi" to "सीटें", "mr" to "जागा"),
        "admin_menu_title" to mapOf("en" to "Menu & Price Management", "hi" to "मेनू और मूल्य प्रबंधन", "mr" to "मेनू आणि किंमत व्यवस्थापन"),
        "admin_add_dish" to mapOf("en" to "Add New Dish", "hi" to "नया व्यंजन जोड़ें", "mr" to "नवीन पदार्थ जोडा"),
        "admin_edit_dish" to mapOf("en" to "Edit Dish", "hi" to "व्यंजन संपादित करें", "mr" to "पदार्थ संपादित करा"),
        "admin_dish_name" to mapOf("en" to "Dish Name", "hi" to "व्यंजन का नाम", "mr" to "पदार्थाचे नाव"),
        "admin_dish_price" to mapOf("en" to "Price (₹)", "hi" to "कीमत (₹)", "mr" to "किंमत (₹)"),
        "admin_dish_category" to mapOf("en" to "Category", "hi" to "श्रेणी", "mr" to "श्रेणी"),
        "admin_upload_image" to mapOf("en" to "Upload Image", "hi" to "फोटो अपलोड करें", "mr" to "फोटो अपलोड करा"),
        "admin_save_dish" to mapOf("en" to "Save Dish", "hi" to "व्यंजन सहेजें", "mr" to "पदार्थ सेव्ह करा"),
        "inventory_title" to mapOf("en" to "Stock & Inventory Management", "hi" to "स्टॉक और इन्वेंटरी प्रबंधन", "mr" to "स्टॉक आणि इन्व्हेंटरी व्यवस्थापन"),
        "inventory_item" to mapOf("en" to "Item", "hi" to "सामग्री", "mr" to "वस्तू"),
        "inventory_stock_qty" to mapOf("en" to "In Stock", "hi" to "स्टॉक मात्रा", "mr" to "शिल्लक साठा"),
        "inventory_reorder" to mapOf("en" to "Reorder Stock", "hi" to "पुनः ऑर्डर करें", "mr" to "पुन्हा ऑर्डर करा"),
        "payroll_title" to mapOf("en" to "Staff Payroll & Wages", "hi" to "कर्मचारी वेतन और मजदूरी", "mr" to "कर्मचारी वेतन आणि पगार"),
        "payroll_staff_name" to mapOf("en" to "Staff Member", "hi" to "कर्मचारी", "mr" to "कर्मचारी"),
        "payroll_role" to mapOf("en" to "Role", "hi" to "भूमिका", "mr" to "भूमिका"),
        "payroll_salary" to mapOf("en" to "Monthly Salary", "hi" to "मासिक वेतन", "mr" to "मासिक पगार"),
        "payroll_pay_now" to mapOf("en" to "Disburse Salary", "hi" to "वेतन भुगतान करें", "mr" to "पगार अदा करा"),
        "analytics_title" to mapOf("en" to "Business Performance Analytics", "hi" to "व्यापार विश्लेषण", "mr" to "व्यवसाय विश्लेषण"),
        "analytics_revenue" to mapOf("en" to "Gross Sales", "hi" to "कुल बिक्री", "mr" to "एकूण विक्री"),
        "analytics_orders_count" to mapOf("en" to "Orders Completed", "hi" to "पूर्ण ऑर्डर्स", "mr" to "पूर्ण झालेल्या ऑर्डर्स"),
        "analytics_avg_order" to mapOf("en" to "Avg Order Value", "hi" to "औसत ऑर्डर मूल्य", "mr" to "सरासरी ऑर्डर मूल्य"),
        "rider_stage1_title" to mapOf("en" to "En Route to Restaurant", "hi" to "रेस्तरां के रास्ते में", "mr" to "रेस्टॉरंटच्या मार्गावर"),
        "rider_stage2_title" to mapOf("en" to "Delivering to Customer", "hi" to "ग्राहक को डिलीवरी", "mr" to "ग्राहकाला वितरण"),
        "rider_mark_picked_up" to mapOf("en" to "Mark Order Picked Up", "hi" to "ऑर्डर उठा लिया", "mr" to "ऑर्डर घेतली"),
        "rider_open_maps" to mapOf("en" to "Open Google Maps", "hi" to "गूगल मैप्स खोलें", "mr" to "गुगल मॅप्स उघडा"),
        // Additional Staff & Universal Role Translations
        "action_settle" to mapOf("en" to "Settle", "hi" to "निपटान करें", "mr" to "हिशोब पूर्ण करा"),
        "kiosk_pin_required" to mapOf("en" to "Kiosk Staff PIN Required", "hi" to "कियोस्क स्टाफ पिन आवश्यक", "mr" to "किऑस्क कर्मचारी पिन आवश्यक"),
        "kiosk_pin_desc" to mapOf("en" to "Enter the 4-digit Staff / Admin PIN to unlock Kiosk Lockdown and access settings or sign out.", "hi" to "कियोस्क लॉक हटाने के लिए 4 अंकों का स्टाफ / एडमिन पिन दर्ज करें।", "mr" to "किऑस्क लॉक अनलॉक करण्यासाठी 4 अंकी कर्मचारी / व्यवस्थापक पिन टाका."),
        "kiosk_pin_placeholder" to mapOf("en" to "Staff PIN (e.g. 0000 or 1234)", "hi" to "स्टाफ पिन (उदा. 0000 या 1234)", "mr" to "कर्मचारी पिन (उदा. 0000 किंवा 1234)"),
        "sign_out_kiosk" to mapOf("en" to "Sign Out Kiosk", "hi" to "कियोस्क साइन आउट", "mr" to "किऑस्क साइन आउट"),
        "customer_view" to mapOf("en" to "Customer View", "hi" to "ग्राहक दृश्य", "mr" to "ग्राहक दृश्य"),
        "action_cancel" to mapOf("en" to "Cancel", "hi" to "रद्द करें", "mr" to "रद्द करा"),
        "action_accept" to mapOf("en" to "Accept", "hi" to "स्वीकार करें", "mr" to "स्वीकारा"),
        "action_decline" to mapOf("en" to "Decline", "hi" to "अस्वीकार करें", "mr" to "नाकारा"),
        "filter_all" to mapOf("en" to "All", "hi" to "सभी", "mr" to "सर्व"),
        "enter_amount" to mapOf("en" to "Enter amount", "hi" to "राशि दर्ज करें", "mr" to "रक्कम टाका"),
        "label_subtotal" to mapOf("en" to "Subtotal", "hi" to "उप-योग", "mr" to "उप-एकूण"),
        "label_grand_total" to mapOf("en" to "Grand Total", "hi" to "कुल योग", "mr" to "एकूण बेरीज"),
        "action_close" to mapOf("en" to "Close", "hi" to "बंद करें", "mr" to "बंद करा"),
        "action_take_order" to mapOf("en" to "Take Order", "hi" to "ऑर्डर लें", "mr" to "ऑर्डर घ्या"),
        "action_seat_walkin" to mapOf("en" to "Seat Walk-in", "hi" to "ग्राहक बैठाएं", "mr" to "ग्राहक बसवा"),
        "action_seat_guests" to mapOf("en" to "Seat Guests", "hi" to "अतिथि बैठाएं", "mr" to "पाहुणे बसवा"),
        "action_bill_table" to mapOf("en" to "Bill Table", "hi" to "टेबल बिल करें", "mr" to "टेबल बिल करा"),
        "action_revert" to mapOf("en" to "Revert", "hi" to "वापस लें", "mr" to "मागे घ्या"),
        "label_gross_revenue" to mapOf("en" to "Gross Revenue", "hi" to "सकल राजस्व", "mr" to "एकूण महसूल"),
        "payment_cash" to mapOf("en" to "Cash", "hi" to "नकद", "mr" to "रोख"),
        "payment_card" to mapOf("en" to "Card", "hi" to "कार्ड", "mr" to "कार्ड"),
        "payment_upi" to mapOf("en" to "UPI", "hi" to "यूपीआई", "mr" to "यूपीआय"),
        "select_table_order" to mapOf("en" to "Select Table for Order", "hi" to "ऑर्डर के लिए टेबल चुनें", "mr" to "ऑर्डरसाठी टेबल निवडा"),
        "status_disbursed" to mapOf("en" to "Disbursed", "hi" to "भुगतान किया", "mr" to "पगार जमा"),
        "status_pending_payouts" to mapOf("en" to "Pending Payouts", "hi" to "लंबित भुगतान", "mr" to "प्रलंबित पगार"),
        "label_total_staff" to mapOf("en" to "Total Staff", "hi" to "कुल कर्मचारी", "mr" to "एकूण कर्मचारी"),
        "add_staff_member" to mapOf("en" to "Add Staff Member", "hi" to "कर्मचारी जोड़ें", "mr" to "कर्मचारी जोडा"),
        "add_staff_subtitle" to mapOf("en" to "Add Staff Member (Waiter, Kitchen, Manager)", "hi" to "नया कर्मचारी जोड़ें (वेटर, किचन, मैनेजर)", "mr" to "नवीन कर्मचारी जोडा (वेटर, किचन, व्यवस्थापक)"),
        "rzp_receipt" to mapOf("en" to "RazorpayX Receipt", "hi" to "रेज़रपे एक्स रसीद", "mr" to "रेझरपे एक्स पावती"),
        "label_amount" to mapOf("en" to "Amount", "hi" to "राशि", "mr" to "रक्कम"),
        "label_status" to mapOf("en" to "Status", "hi" to "स्थिति", "mr" to "स्थिती"),
        "label_method" to mapOf("en" to "Method", "hi" to "विधि", "mr" to "पद्धत"),
        "label_destination" to mapOf("en" to "Destination", "hi" to "गंतव्य", "mr" to "गंतव्य"),
        "label_reference_id" to mapOf("en" to "Reference ID", "hi" to "संदर्भ आईडी", "mr" to "संदर्भ आयडी"),
        "label_bank_utr" to mapOf("en" to "Bank UTR", "hi" to "बैंक यूटीआर", "mr" to "बँक यूटीआर"),
        "action_share" to mapOf("en" to "Share", "hi" to "साझा करें", "mr" to "शेअर करा"),
        "action_copy" to mapOf("en" to "Copy", "hi" to "कॉपी करें", "mr" to "कॉपी करा"),
        "rzp_processing" to mapOf("en" to "Processing RazorpayX Payout...", "hi" to "रेज़रपे एक्स भुगतान संसाधित हो रहा है...", "mr" to "रेझरपे एक्स पगार प्रक्रिया सुरू आहे..."),
        "master_admin_privileges" to mapOf("en" to "All-access privileges: Full multi-venue Mumbai telemetry, staff payroll, and system performance.", "hi" to "पूर्ण अधिकार: मुंबई रेस्टोरेंट डेटा, कर्मचारी वेतन, और सिस्टम प्रदर्शन।", "mr" to "पूर्ण अधिकार: मुंबई रेस्टॉरंट डेटा, कर्मचारी पगार आणि सिस्टीम कामगिरी."),
        "rider_complete_order" to mapOf("en" to "Complete & Collect", "hi" to "डिलीवरी पूरी करें", "mr" to "वितरण पूर्ण करा"),
        "save_delivery_address_title" to mapOf("en" to "Save Delivery Address", "hi" to "डिलीवरी पता सहेजें", "mr" to "डिलिव्हरी पत्ता सेव्ह करा"),
        "save_delivery_address_subtitle" to mapOf("en" to "Where should we deliver your food?", "hi" to "हम आपका खाना कहाँ पहुँचाएँ?", "mr" to "आम्ही तुमचे जेवण कुठे पोहोचवायचे?"),
        "tap_to_pin_location" to mapOf("en" to "Tap to Pin Exact Location", "hi" to "सटीक स्थान पिन करने के लिए टैप करें", "mr" to "अचूक स्थान पिन करण्यासाठी टॅप करा"),
        "pinned_map_coordinates" to mapOf("en" to "Pinned Map Coordinates", "hi" to "पिन किए गए मानचित्र निर्देशांक", "mr" to "पिन केलेले नकाशाचे निर्देशांक"),
        "my_gps_btn" to mapOf("en" to "My GPS", "hi" to "मेरा जीपीएस", "mr" to "माझे जीपीएस"),
        "label_field_hint" to mapOf("en" to "Label (e.g. Home, Office, Gym)", "hi" to "लेबल (उदा. घर, कार्यालय, जिम)", "mr" to "लेबल (उदा. घर, कार्यालय, जिम)"),
        "address_line_hint" to mapOf("en" to "House / Flat / Street address", "hi" to "मकान / फ्लैट / सड़क का पता", "mr" to "घर / फ्लॅट / रस्त्याचा पत्ता"),
        "city_field_hint" to mapOf("en" to "City", "hi" to "शहर", "mr" to "शहर"),
        "postcode_field_hint" to mapOf("en" to "Pin / Postal code", "hi" to "पिन कोड", "mr" to "पिन कोड"),
        "save_address_btn" to mapOf("en" to "Save Address", "hi" to "पता सहेजें", "mr" to "पत्ता सेव्ह करा"),
        "selected_location_label" to mapOf("en" to "Selected Location", "hi" to "चयनित स्थान", "mr" to "निवडलेले स्थान"),
        "quick_localities_label" to mapOf("en" to "Quick Mumbai Localities:", "hi" to "त्वरित मुंबई क्षेत्र:", "mr" to "जलद मुंबई भाग:"),
        "tap_map_pin_hint" to mapOf("en" to "Tap map or drag pin to select exact delivery spot", "hi" to "डिलीवरी स्थान चुनने के लिए मानचित्र पर टैप करें या पिन खींचें", "mr" to "वितरण स्थान निवडण्यासाठी नकाशावर टॅप करा किंवा पिन ड्रॅग करा"),
        "no_pin_selected" to mapOf("en" to "No pin coordinates selected", "hi" to "कोई पिन नहीं चुना गया", "mr" to "कोणताही पिन निवडला नाही"),
        "auth_phone_number" to mapOf("en" to "Contact Phone Number", "hi" to "संपर्क फ़ोन नंबर", "mr" to "संपर्क फोन नंबर"),
        "rider_stage_customer" to mapOf("en" to "Delivering to Customer Doorstep", "hi" to "ग्राहक के पते पर डिलीवरी जारी है", "mr" to "ग्राहकाच्या दारापर्यंत वितरण सुरू आहे"),
        "rider_stage_restaurant" to mapOf("en" to "Heading to Restaurant Pickup", "hi" to "रेस्टोरेंट से खाना लेने जा रहे हैं", "mr" to "हॉटेलमधून जेवण आणण्यासाठी जात आहोत"),
        "order_confirmed_title" to mapOf("en" to "Order Confirmed!", "hi" to "ऑर्डर की पुष्टि हो गई!", "mr" to "ऑर्डर कन्फर्म झाली!"),
        "order_confirmed_sub" to mapOf("en" to "Your order is confirmed & sent to the kitchen.", "hi" to "आपका ऑर्डर स्वीकृत हो गया है और रसोई में भेजा गया है।", "mr" to "तुमची ऑर्डर कन्फर्म झाली असून स्वयंपाकघरात पाठवली आहे."),
        "track_live_order_btn" to mapOf("en" to "Track Live Order on Map", "hi" to "मैप पर लाइव ऑर्डर ट्रैक करें", "mr" to "नकाशावर थेट ऑर्डर ट्रॅक करा"),
        "continue_discovering" to mapOf("en" to "Continue Discovering Dishes", "hi" to "और स्वादिष्ट व्यंजन खोजें", "mr" to "इतर स्वादिष्ट पदार्थ एक्सप्लोर करा"),
        "order_id_label" to mapOf("en" to "Order ID", "hi" to "ऑर्डर आईडी", "mr" to "ऑर्डर आयडी"),
        "deliver_to_label" to mapOf("en" to "Deliver to", "hi" to "डिलीवरी का पता", "mr" to "डिलिव्हरीचा पत्ता"),
        "delivery_progress" to mapOf("en" to "Delivery Progress", "hi" to "डिलीवरी प्रगति", "mr" to "डिलिव्हरी प्रगती"),
        "order_status" to mapOf("en" to "Order Status", "hi" to "ऑर्डर स्थिति", "mr" to "ऑर्डर स्थिती"),
        "estimated_delivery_time" to mapOf("en" to "Estimated Delivery Time (ETA)", "hi" to "अनुमानित डिलीवरी समय (ETA)", "mr" to "अंदाजे डिलिव्हरी वेळ (ETA)"),
        "delivered_at_doorstep" to mapOf("en" to "Delivered at Doorstep", "hi" to "दरवाजे पर डिलीवर किया गया", "mr" to "दारावर पोहोचवले"),
        "refresh" to mapOf("en" to "Refresh", "hi" to "रिफ्रेश", "mr" to "रिफ्रेश"),
        "status_order_confirmed" to mapOf("en" to "Order Placed & Confirmed", "hi" to "ऑर्डर दिया गया और पुष्टि की गई", "mr" to "ऑर्डर दिली आणि कन्फर्म झाली"),
        "stage_confirmed_sub" to mapOf("en" to "Restaurant accepted and preparing ingredients", "hi" to "रेस्तरां ने स्वीकार किया और सामग्री तैयार कर रहा है", "mr" to "रेस्टॉरंटने स्वीकारले आणि साहित्य तयार करत आहे"),
        "status_preparing" to mapOf("en" to "Kitchen Preparing", "hi" to "रसोई में तैयारी जारी", "mr" to "किचनमध्ये तयारी सुरू"),
        "stage_preparing_sub" to mapOf("en" to "Chef is cooking your fresh dishes", "hi" to "शेफ आपके ताज़ा व्यंजन तैयार कर रहे हैं", "mr" to "शेफ तुमचे ताजे पदार्थ बनवत आहेत"),
        "status_out_for_delivery" to mapOf("en" to "Out for Delivery", "hi" to "डिलीवरी के लिए रवाना", "mr" to "डिलिव्हरीसाठी निघाले"),
        "stage_out_for_delivery_sub" to mapOf("en" to "Rider is on the way with hot food", "hi" to "राइडर गरमागरम खाना लेकर आ रहा है", "mr" to "रायडर गरमागरम जेवण घेऊन येत आहे"),
        "status_delivered" to mapOf("en" to "Order Delivered", "hi" to "ऑर्डर डिलीवर हो गया", "mr" to "ऑर्डर डिलिव्हर झाली"),
        "stage_delivered_sub" to mapOf("en" to "Delivered at doorstep. Bon Appétit!", "hi" to "दरवाजे पर डिलीवर किया गया। भोजन का आनंद लें!", "mr" to "दारावर पोहोचवले. जेवणाचा आनंद घ्या!"),
        "proceed_to_pay" to mapOf("en" to "Proceed to Pay", "hi" to "भुगतान के लिए आगे बढ़ें", "mr" to "पेमेंटसाठी पुढे जा"),
        "place_order_btn" to mapOf("en" to "Place Order", "hi" to "ऑर्डर दें", "mr" to "ऑर्डर द्या"),
        "filter_all" to mapOf("en" to "All", "hi" to "सभी", "mr" to "सर्व"),
        "search_menu_dishes" to mapOf("en" to "Search menu dishes...", "hi" to "मेनू व्यंजन खोजें...", "mr" to "मेनू पदार्थ शोधा..."),
    )

    fun t(key: String): String {
        val entry = translations[key] ?: return key
        return entry[currentLanguage] ?: entry["en"] ?: key
    }

    fun t(key: String, vararg args: Any): String {
        val raw = t(key)
        return try {
            String.format(raw, *args)
        } catch (_: Exception) {
            raw
        }
    }
}
