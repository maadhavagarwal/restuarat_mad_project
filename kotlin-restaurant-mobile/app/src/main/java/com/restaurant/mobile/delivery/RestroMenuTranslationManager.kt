package com.restaurant.mobile.delivery

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * RestroMenuTranslationManager
 * Manages Gemini-powered and authentic culinary translations for menu dishes into Hindi and Marathi.
 * Features in-memory caching, offline culinary lexicon fallback, and seamless Compose reactivity.
 */
object RestroMenuTranslationManager {

    // Key format: "$lang:$dishId" or "$lang:$dishName"
    private val memoryCache = ConcurrentHashMap<String, DishTranslation>()

    private val lexiconHindi = mapOf(
        "Butter Chicken" to DishTranslation("बटर चिकन", "मलाईदार मक्खन और टमाटर की समृद्ध ग्रेवी में पका हुआ तंदूरी चिकन।"),
        "Garlic Naan" to DishTranslation("गार्लिक नान", "तंदूर में सिका हुआ लहसुन और मक्खन से महकता गरमा-गरम नान।"),
        "Paneer Tikka" to DishTranslation("पनीर टिक्का", "तंदूर में भुने हुए मसालेदार और रसदार ताजे पनीर के टुकड़े।"),
        "Dal Makhani" to DishTranslation("दाल मखनी", "धीमी आंच पर रात भर पकी हुई मक्खन और क्रीम वाली काली दाल।"),
        "Awadhi Dum Biryani" to DishTranslation("अवधी दम बिरयानी", "धीमी आंच पर पकाई गई सुगंधित मसालों से भरपूर शाही बिरयानी।"),
        "Truffle Fettuccine" to DishTranslation("ट्रफल फेटुचिनी", "हाथ से बनी पास्ता पट्टी, मशरूम और परमेसन चीज की मलाईदार सॉस में।"),
        "Woodfired Margherita Pizza" to DishTranslation("वुडफायर्ड मार्गरिटा पिज्जा", "लकड़ी की भट्टी में सिका पारंपरिक इटैलियन पिज्जा और मोज़ेरेला चीज।"),
        "Ghee Roast Dosa" to DishTranslation("घी रोस्ट डोसा", "शुद्ध देसी घी में सिका हुआ कुरकुरा डोसा, नारियल की चटनी और सांभर के साथ।"),
        "Misal Pav" to DishTranslation("मिसल पाव", "तीखी उसल, कुरकुरे फरसाण, नींबू और ताजे पाव के साथ।"),
        "Pav Bhaji" to DishTranslation("पाव भाजी", "मक्खन में मैश की गई तीखी सब्जियां और सिके हुए पाव।"),
        "Gulab Jamun" to DishTranslation("गुलाब जामुन", "इलायची और केसर की चाशनी में डूबे हुए गरमा-गरम नरम मावे के जामुन।"),
        "Tokyo Shoyu Ramen" to DishTranslation("टोक्यो शोयु रामेन", "पारंपरिक जापानी सोया शोरबा, नूडल्स और ताजी सब्जियों का कटोरा।"),
        "Tandoori Roti" to DishTranslation("तंदूरी रोटी", "पारंपरिक तंदूर में सिकी हुई कुरकुरी गेहूं की रोटी।"),
        "Jeera Rice" to DishTranslation("जीरा राइस", "साबुत जीरे और देसी घी से छौंक लगे सुगंधित बासमती चावल।"),
        "Filter Coffee" to DishTranslation("फिल्टर कॉफी", "दक्षिण भारतीय पारंपरिक डिकॉक्शन और झागदार गर्म दूध वाली कॉफी।"),
        "Masala Chai" to DishTranslation("मसाला चाय", "अदरक, इलायची और दालचीनी के तड़के वाली ताजा कुल्हड़ चाय।")
    )

    private val lexiconMarathi = mapOf(
        "Butter Chicken" to DishTranslation("बटर चिकन", "लोणी आणि टोमॅटोच्या मलाईदार रश्श्यात शिजवलेले चिकन."),
        "Garlic Naan" to DishTranslation("लसूण नान", "तंदूरमध्ये भाजलेले लसूण आणि लोणी लावलेले गरमागरम नान."),
        "Paneer Tikka" to DishTranslation("पनीर टिक्का", "तंदूरमध्ये भाजलेले मसालेदार आणि चविष्ट ताजे पनीरचे तुकडे."),
        "Dal Makhani" to DishTranslation("दाल मखनी", "मंद आचेवर रात्रभर शिजवलेली लोणी आणि मलईयुक्त काळी डाळ."),
        "Awadhi Dum Biryani" to DishTranslation("अवधी दम बिर्याणी", "मंद आचेवर शिजवलेली सुगंधी मसाल्यांची शाही बिर्याणी."),
        "Truffle Fettuccine" to DishTranslation("ट्रफल फेटुचिनी", "मशरूम आणि पारमेसन चीजच्या मलाईदार सॉसमध्ये हाताने बनवलेला पास्ता."),
        "Woodfired Margherita Pizza" to DishTranslation("वुडफायर्ड मार्गेरिटा पिझ्झा", "लाकडाच्या भट्टीत भाजलेला पारंपरिक इटालियन पिझ्झा आणि मोझरेला चीज."),
        "Ghee Roast Dosa" to DishTranslation("तूप रोस्ट डोसा", "शुद्ध गावरान तुपात भाजलेला कुरकुरीत डोसा, खोबऱ्याची चटणी आणि सांबारसह."),
        "Misal Pav" to DishTranslation("झणझणीत मिसळ पाव", "झणझणीत कट, चवदार उसळ, फरसाण, लिंबू आणि ताजे लादी पाव."),
        "Pav Bhaji" to DishTranslation("पाव भाजी", "लोण्यामध्ये शिजवलेली चवदार भाजी आणि भाजलेले मऊ पाव."),
        "Gulab Jamun" to DishTranslation("गुलाब जामुन", "वेलची आणि केशरच्या पाकात भिजवलेले गरमागरम मऊ खव्याचे गुलाबजाम."),
        "Tokyo Shoyu Ramen" to DishTranslation("टोकियो शोयु रामेन", "पारंपरिक जपानी सोया सूप, नूडल्स आणि ताज्या भाज्यांचा स्वादिष्ट बाऊल."),
        "Tandoori Roti" to DishTranslation("तंदूरी रोटी", "तंदूरमध्ये भाजलेली गरमागरम आणि कुरकुरीत गव्हाची रोटी."),
        "Jeera Rice" to DishTranslation("जिरा भात", "जिऱ्याची फोडणी दिलेला मऊ आणि सुगंधी बासमती भात."),
        "Filter Coffee" to DishTranslation("फिल्टर कॉफी", "पारंपरिक दक्षिण भारतीय पद्धतीची वाफाळलेली कडक फिल्टर कॉफी."),
        "Masala Chai" to DishTranslation("मसाला चहा", "आले आणि वेलची घालून उकळलेला गरमागरम मसालेदार चहा.")
    )

    fun getTranslation(dishId: String, dishName: String, dishDesc: String, lang: String): DishTranslation {
        val targetLang = lang.lowercase(Locale.ROOT).trim()
        if (targetLang == "en") {
            return DishTranslation(dishName, dishDesc)
        }

        // 1. Check in-memory cache
        val keyById = "$targetLang:$dishId"
        memoryCache[keyById]?.let { return it }

        val keyByName = "$targetLang:$dishName"
        memoryCache[keyByName]?.let { return it }

        // 2. Check culinary lexicon
        val lexicon = if (targetLang == "hi") lexiconHindi else if (targetLang == "mr") lexiconMarathi else emptyMap()
        lexicon[dishName]?.let {
            memoryCache[keyById] = it
            memoryCache[keyByName] = it
            return it
        }

        // Fuzzy/partial matching against known lexicon
        for ((key, translation) in lexicon) {
            if (dishName.contains(key, ignoreCase = true) || key.contains(dishName, ignoreCase = true)) {
                memoryCache[keyById] = translation
                memoryCache[keyByName] = translation
                return translation
            }
        }

        // 3. Fallback to original text gracefully
        val fallback = DishTranslation(dishName, dishDesc)
        memoryCache[keyById] = fallback
        return fallback
    }

    fun cacheTranslation(lang: String, dishId: String, dishName: String, translation: DishTranslation) {
        val targetLang = lang.lowercase(Locale.ROOT).trim()
        memoryCache["$targetLang:$dishId"] = translation
        memoryCache["$targetLang:$dishName"] = translation
    }

    fun isCached(lang: String, dishId: String, dishName: String): Boolean {
        val targetLang = lang.lowercase(Locale.ROOT).trim()
        if (targetLang == "en") return true
        return memoryCache.containsKey("$targetLang:$dishId") || memoryCache.containsKey("$targetLang:$dishName")
    }

    fun clearCache() {
        memoryCache.clear()
    }
}

/**
 * Extension properties for seamless Compose and UI rendering
 */
fun Dish.localizedName(lang: String = RestroI18n.currentLanguage): String {
    val targetLang = lang.lowercase(Locale.ROOT).trim()
    if (targetLang == "en") return this.name

    val direct = this.translations[targetLang]
    if (direct != null && direct.name.isNotBlank()) {
        return direct.name
    }

    return RestroMenuTranslationManager.getTranslation(id, name, description, targetLang).name
}

fun Dish.localizedDescription(lang: String = RestroI18n.currentLanguage): String {
    val targetLang = lang.lowercase(Locale.ROOT).trim()
    if (targetLang == "en") return this.description

    val direct = this.translations[targetLang]
    if (direct != null && direct.description.isNotBlank()) {
        return direct.description
    }

    return RestroMenuTranslationManager.getTranslation(id, name, description, targetLang).description
}
