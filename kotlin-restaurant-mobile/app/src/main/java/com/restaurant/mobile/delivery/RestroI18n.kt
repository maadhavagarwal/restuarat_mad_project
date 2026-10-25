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

    fun setLanguage(code: String) {
        currentLanguage = when (code.lowercase()) {
            "hi" -> "hi"
            "mr" -> "mr"
            else -> "en"
        }
    }

    fun isHindi(): Boolean = currentLanguage == "hi"
    fun isMarathi(): Boolean = currentLanguage == "mr"
    fun isEnglish(): Boolean = currentLanguage == "en"

    private val translations = mapOf(
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
        "analytics_tab" to mapOf("en" to "Analytics", "hi" to "एनालिटिक्स", "mr" to "ॲनालिटिक्स")
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
