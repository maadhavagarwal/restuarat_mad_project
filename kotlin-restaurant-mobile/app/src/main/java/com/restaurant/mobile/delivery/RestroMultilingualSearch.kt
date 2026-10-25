package com.restaurant.mobile.delivery

import java.text.Normalizer
import java.util.Locale

object RestroMultilingualSearch {

    // Bidirectional Devanagari <-> Latin Food Dictionary
    private val devanagariToLatinMap = mapOf(
        "पनीर" to "paneer",
        "पनीर टिक्का" to "paneer tikka",
        "पनीर बटर मसाला" to "paneer butter masala",
        "बिरयानी" to "biryani",
        "बिर्याणी" to "biryani",
        "चिकन" to "chicken",
        "मटन" to "mutton",
        "दाल" to "dal",
        "दाल मखनी" to "dal makhani",
        "रोटी" to "roti",
        "नान" to "naan",
        "चावल" to "rice",
        "भात" to "rice",
        "जीरा राइस" to "jeera rice",
        "पिज्जा" to "pizza",
        "पिझ्झा" to "pizza",
        "बर्गर" to "burger",
        "सैंडविच" to "sandwich",
        "पाव भाजी" to "pav bhaji",
        "मिसळ पाव" to "misal pav",
        "डोसा" to "dosa",
        "इडली" to "idli",
        "वड़ा" to "vada",
        "वडा पाव" to "vada pav",
        "समोसा" to "samosa",
        "गुलाब जामुन" to "gulab jamun",
        "रसगुल्ला" to "rasgulla",
        "आइसक्रीम" to "ice cream",
        "कॉफी" to "coffee",
        "चाय" to "tea",
        "चहा" to "tea",
        "सूप" to "soup",
        "सलाद" to "salad",
        "रोल" to "roll",
        "फ्राइज" to "fries",
        "मोमोज" to "momos",
        "नूडल्स" to "noodles",
        "पास्ता" to "pasta",
        "मंचूरियन" to "manchurian"
    )

    // Latin Transliteration Synonyms
    private val latinSynonyms = mapOf(
        "biriyani" to "biryani",
        "briyani" to "biryani",
        "panir" to "paneer",
        "panner" to "paneer",
        "pijja" to "pizza",
        "pizzas" to "pizza",
        "kabab" to "kebab",
        "kababs" to "kebab",
        "kebabs" to "kebab",
        "dosa" to "dosa",
        "dhosa" to "dosa",
        "idly" to "idli",
        "noodles" to "noodles",
        "nudles" to "noodles",
        "roties" to "roti",
        "makhni" to "makhani"
    )

    // Dietary & Flavor Intents
    private val mildSpicePhrases = listOf(
        "कम मसाले वाला", "कमी तिखट", "कम तीखा", "less spicy", "not spicy", "mild", "mild spicy", "कमी मसाला"
    )

    private val highSpicePhrases = listOf(
        "तीखा", "तिखट", "ज्यादा तीखा", "spicy", "extra spicy", "very spicy", "झणझणीत"
    )

    private val vegPhrases = listOf(
        "शाकाहारी", "शुद्ध शाकाहारी", "veg", "vegetarian", "pure veg"
    )

    data class SearchQueryIntent(
        val normalizedKeywords: List<String>,
        val isMildRequested: Boolean = false,
        val isSpicyRequested: Boolean = false,
        val isVegRequested: Boolean = false,
        val maxBudget: Long? = null
    )

    fun parseQueryIntent(rawQuery: String): SearchQueryIntent {
        val normalized = Normalizer.normalize(rawQuery.trim().lowercase(Locale.ROOT), Normalizer.Form.NFC)
        
        var isMild = false
        for (phrase in mildSpicePhrases) {
            if (normalized.contains(phrase)) {
                isMild = true
                break
            }
        }

        var isSpicy = false
        for (phrase in highSpicePhrases) {
            if (normalized.contains(phrase)) {
                isSpicy = true
                break
            }
        }

        var isVeg = false
        for (phrase in vegPhrases) {
            if (normalized.contains(phrase)) {
                isVeg = true
                break
            }
        }

        // Budget parser e.g. "under 250", "₹300", "200 रु"
        var budget: Long? = null
        val budgetRegex = Regex("""(?:under|below|कमी|के अंदर|<|₹|\b)\s*(\d{2,4})\s*(?:rupees|rs|रु|paise|\b)""")
        val match = budgetRegex.find(normalized)
        if (match != null) {
            val amount = match.groupValues[1].toLongOrNull()
            if (amount != null && amount in 50..5000) {
                budget = amount * 100 // Convert to paise
            }
        }

        // Split tokens and expand cross-language words
        val tokens = normalized.split(Regex("""[\s,]+""")).filter { it.isNotBlank() }
        val expandedKeywords = mutableListOf<String>()

        for (token in tokens) {
            expandedKeywords.add(token)

            // 1. Devanagari to Latin match
            devanagariToLatinMap[token]?.let { expandedKeywords.add(it) }

            // Reverse Latin to Devanagari match
            for ((dev, lat) in devanagariToLatinMap) {
                if (lat.equals(token, ignoreCase = true)) {
                    expandedKeywords.add(dev)
                }
            }

            // 2. Latin spelling variations
            latinSynonyms[token]?.let { expandedKeywords.add(it) }
        }

        return SearchQueryIntent(
            normalizedKeywords = expandedKeywords.distinct(),
            isMildRequested = isMild,
            isSpicyRequested = isSpicy,
            isVegRequested = isVeg,
            maxBudget = budget
        )
    }

    /**
     * Cross-Language matching for a Dish
     */
    fun matchesDish(dish: Dish, query: String): Boolean {
        if (query.isBlank()) return true
        val intent = parseQueryIntent(query)

        // Dietary check if explicit in query
        if (intent.isVegRequested && !dish.vegetarian) {
            return false
        }

        // Budget check
        if (intent.maxBudget != null && dish.price > intent.maxBudget) {
            return false
        }

        val dishText = "${dish.name} ${dish.category} ${dish.description}".lowercase(Locale.ROOT)
        val normalizedDishText = Normalizer.normalize(dishText, Normalizer.Form.NFC)

        // Check if any keyword matches
        for (kw in intent.normalizedKeywords) {
            if (normalizedDishText.contains(kw)) {
                return true
            }
        }

        // Partial/Fuzzy substring matching
        val queryLower = query.trim().lowercase(Locale.ROOT)
        if (normalizedDishText.contains(queryLower)) {
            return true
        }

        return false
    }

    /**
     * Cross-Language matching for a Restaurant
     */
    fun matchesRestaurant(restaurant: Restaurant, query: String): Boolean {
        if (query.isBlank()) return true
        val intent = parseQueryIntent(query)

        val restText = "${restaurant.name} ${restaurant.cuisine} ${restaurant.vibes.joinToString(" ")} ${restaurant.address}".lowercase(Locale.ROOT)
        val normalizedRestText = Normalizer.normalize(restText, Normalizer.Form.NFC)

        for (kw in intent.normalizedKeywords) {
            if (normalizedRestText.contains(kw)) {
                return true
            }
        }

        return normalizedRestText.contains(query.trim().lowercase(Locale.ROOT))
    }
}
