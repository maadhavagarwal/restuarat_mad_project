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
        )
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
