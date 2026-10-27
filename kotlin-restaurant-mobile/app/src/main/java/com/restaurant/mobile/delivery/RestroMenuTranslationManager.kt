package com.restaurant.mobile.delivery

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * RestroMenuTranslationManager
 * Manages comprehensive culinary translations for menu dishes, categories,
 * and restaurants into Hindi and Marathi.
 * Features in-memory caching, offline culinary lexicon fallback, and seamless Compose reactivity.
 */
object RestroMenuTranslationManager {

    // Key format: "$lang:$dishId" or "$lang:$dishName"
    private val memoryCache = ConcurrentHashMap<String, DishTranslation>()

    val restaurantLexiconHindi = mapOf(
        "Bistro Lumière & Biryani" to Pair("बिस्ट्रो लुमिएर और बिरयानी", "अवधी · मुग़लई · बिरयानी"),
        "Trattoria Milano" to Pair("त्रातोरिया मिलानो", "इटैलियन · वुडफायर्ड पिज़्ज़ा · पास्ता"),
        "Dakshin Delights" to Pair("दक्षिण डिलाइट्स", "दक्षिण भारतीय · चेट्टीनाड · डोसा"),
        "Tokyo Zen & Bao" to Pair("टोक्यो ज़ेन और बाओ", "पैन-एशियाई · रामेन · डिम सम"),
        "The Green Bowl & Bakery" to Pair("द ग्रीन बाउल और बेकरी", "स्वस्थ आहार · सलाद · स्मूदी")
    )

    val restaurantLexiconMarathi = mapOf(
        "Bistro Lumière & Biryani" to Pair("बिस्ट्रो लुमिएर आणि बिर्याणी", "अवधी · मुघलाई · बिर्याणी"),
        "Trattoria Milano" to Pair("त्रातोरिया मिलानो", "इटालियन · वुडफायर्ड पिझ्झा · पास्ता"),
        "Dakshin Delights" to Pair("दक्षिण डिलाइट्स", "दक्षिण भारतीय · चेट्टीनाड · डोसा"),
        "Tokyo Zen & Bao" to Pair("टोकियो झेन आणि बाओ", "पॅन-आशियाई · रामेन · डिम सम"),
        "The Green Bowl & Bakery" to Pair("द ग्रीन बाऊल आणि बेकरी", "पौष्टिक आहार · सॅलड · स्मूदी")
    )

    val categoryLexiconHindi = mapOf(
        "Biryani" to "बिरयानी",
        "Starters" to "शुरुआती व्यंजन",
        "Curries" to "करी और दाल",
        "Breads" to "रोटी और नान",
        "Desserts" to "मिठाइयाँ",
        "Pizza" to "पिज़्ज़ा",
        "Pasta" to "पास्ता",
        "Dosa" to "डोसा",
        "Tiffin" to "टिफिन और नाश्ता",
        "Mains" to "मुख्य भोजन",
        "Beverages" to "पेय पदार्थ",
        "Ramen" to "रामेन",
        "Dim Sum" to "डिम सम",
        "Bao" to "बाओ बन",
        "Bowls" to "हेल्दी बाउल",
        "Toast" to "सॉरडो टोस्ट",
        "Smoothies" to "स्मूदी बाउल"
    )

    val categoryLexiconMarathi = mapOf(
        "Biryani" to "बिर्याणी",
        "Starters" to "स्टार्टर्स",
        "Curries" to "रस्सा आणि डाळ",
        "Breads" to "रोटी आणि नान",
        "Desserts" to "गोड पदार्थ",
        "Pizza" to "पिझ्झा",
        "Pasta" to "पास्ता",
        "Dosa" to "डोसा",
        "Tiffin" to "नाश्ता",
        "Mains" to "मुख्य जेवण",
        "Beverages" to "पेये",
        "Ramen" to "रामेन",
        "Dim Sum" to "डिम सम",
        "Bao" to "बाओ",
        "Bowls" to "पौष्टिक बाऊल",
        "Toast" to "टोस्ट",
        "Smoothies" to "स्मूदी"
    )

    private val lexiconHindi = mapOf(
        "Butter Chicken" to DishTranslation("बटर चिकन", "मखमली टमाटर और मक्खन की समृद्ध ग्रेवी में रसीला चिकन।"),
        "Dal Makhani" to DishTranslation("दाल मखनी", "काली दाल और मक्खन की मखमली धीमी आंच पर पकी दाल।"),
        "Misal Pav" to DishTranslation("मिसल पाव", "तीखा मसालेदार मोठ बीन करी और कुरकुरे फरसाण के साथ पाव।"),
        "Ghee Roast Dosa" to DishTranslation("घी रोस्ट डोसा", "शुद्ध देसी घी में भुना हुआ कुरकुरा डोसा।"),
        // Bistro Lumière & Biryani
        "Hyderabadi Dum Gosht Biryani" to DishTranslation("हैदराबादी दम गोश्त बिरयानी", "धीमी आंच पर पका हुआ रसीला गोश्त, केसरिया बासमती चावल, भुने प्याज और शाही मसालों की परतें।"),
        "Subz Paneer Dum Biryani" to DishTranslation("सब्ज पनीर दम बिरयानी", "सुगंधित बासमती चावल, मसालेदार पनीर के टुकड़े, ताजी सब्जियां और पुदीने की महक।"),
        "Melt-in-Mouth Galouti Kebab" to DishTranslation("गलौटी कबाब (मुलायम कबाब)", "कच्चे पपीते और 32 सुगंधित जड़ी-बूटियों के साथ तैयार किए गए बेहद नरम कबाब।"),
        "Tandoori Paneer Angara" to DishTranslation("तंदूरी पनीर अंगारा", "कश्मीरी मिर्च और मसालों में मैरीनेट किया हुआ, भट्टी में भुना रसदार पनीर।"),
        "Royal Murgh Makhani" to DishTranslation("शाही मुर्ग मखनी", "तंदूरी चिकन, मखमली टमाटर-काजू की ग्रेवी और कस्तूरी मेथी मक्खन में पकाया गया।"),
        "Dal Bukhara Slow-Simmered" to DishTranslation("दाल बुखारा (धीमी आंच पर पकी)", "रात भर धीमी आंच पर मक्खन और ताजे टमाटरों के साथ पकाई गई काली उड़द दाल।"),
        "Butter Garlic Naan" to DishTranslation("बटर गार्लिक नान", "तंदूर में सिका हुआ लहसुन, जड़ी-बूटियों और मक्खन से महकता गरमा-गरम नान।"),
        "Shahi Tukda with Kesari Rabri" to DishTranslation("शाही टुकड़ा केसरिया रबड़ी के साथ", "देसी घी में कुरकुरी तली ब्रेड, इलायची चाशनी और गाढ़ी केसरिया रबड़ी।"),

        // Trattoria Milano
        "Woodfired Margherita Pizza" to DishTranslation("वुडफायर्ड मार्गरिटा पिज्जा", "सैन मार्जानो टमाटर सॉस, ताजा बफेलो मोज़ेरेला चीज और तुलसी के पत्तों से सिका पिज्जा।"),
        "Quattro Formaggi Bianca" to DishTranslation("क्वात्रो फॉर्मैगी चीज पिज्जा", "चार खास इटैलियन चीज (गॉरगॉनज़ोला, फॉन्टीना, प्रोवोलोन, परमेसन) का स्वादिष्ट मेल।"),
        "Diavola Spicy Salami Pizza" to DishTranslation("डियावोला स्पाइसी सलामी पिज्जा", "स्मोक्ड मोज़ेरेला, तीखी सलामी और मिर्च-शहद के अनूठे स्वाद वाला पिज्जा।"),
        "Handmade Truffle Tagliatelle" to DishTranslation("हैंडमेड ट्रफल टालियातेले पास्ता", "हाथ से बनी पास्ता पट्टियां, ब्लैक ट्रफल क्रीम, परमेसन चीज और जंगली मशरूम।"),
        "Bruschetta al Pomodoro" to DishTranslation("ब्रुशेटा अल पोमोडोरो", "सिकी हुई क्रिस्पी सियबाटा ब्रेड, पके टमाटर, लहसुन, ऑलिव ऑयल और तुलसी।"),
        "Classic Espresso Tiramisu" to DishTranslation("क्लासिक एस्प्रेसो तिरामिसु", "डार्क एस्प्रेसो में डूबे लेडीफिंगर बिस्कुट, मस्कार्पोन क्रीम और कोको पाउडर।"),

        // Dakshin Delights
        "Ghee Roast Masala Dosa" to DishTranslation("घी रोस्ट मसाला डोसा", "शुद्ध देसी घी में सिका कुरकुरा डोसा, आलू मसाला भरा हुआ, 3 चटनी और सांभर के साथ।"),
        "Mysore Masala Dosa" to DishTranslation("मैसूर मसाला डोसा", "तीखी लाल लहसुनी चटनी की परत, मक्खन और स्वादिष्ट आलू मसाले से भरा कुरकुरा डोसा।"),
        "Steamed Thatte Idli with Podi" to DishTranslation("स्टीम्ड तट्टे इडली पोड़ी के साथ", "नरम और फूली हुई बड़ी इडली, गर्म देसी घी और मसालेदार गनपाउडर पोड़ी के साथ।"),
        "Crispy Medu Vada (2 pcs)" to DishTranslation("कुरकुरा मेदू वड़ा (2 नग)", "उड़द दाल के कुरकुरे गरमा-गरम वड़े, नारियल चटनी और तीखे सांभर के साथ।"),
        "Malabar Parotta with Veg Kurma" to DishTranslation("मालाबार परोटा वेज कुरमा के साथ", "केरल शैली का लच्छेदार परतदार परोटा और नारियल-सब्जियों का सुगंधित कुरमा।"),
        "Chettinad Pepper Chicken" to DishTranslation("चेट्टीनाड काली मिर्च चिकन", "ताजा पिसी काली मिर्च, छोटे प्याज और कढ़ी पत्ते में भुना मसालेदार चिकन।"),
        "Mysore Pure Filter Coffee" to DishTranslation("मैसूर प्योर फिल्टर कॉफी", "ताजा डिकॉक्शन और गाढ़े झागदार गर्म दूध से बनी पारंपरिक दक्षिण भारतीय कॉफी।"),

        // Tokyo Zen & Bao
        "Tokyo Shoyu Chashu Ramen" to DishTranslation("टोक्यो शोयु चाशू रामेन", "पारंपरिक सोया शोरबा, हाथ से बने नूडल्स, चाशू, अंडा और नोरी समुद्री घास।"),
        "Creamy Miso Veggie Ramen" to DishTranslation("क्रीमी मिसो वेजी रामेन", "सोयाबीन मिसो शोरबा, बोक चोय, स्वीट कॉर्न, मशरूम और तिल-चिली ऑयल।"),
        "Truffle Edamame Dim Sum (4 pcs)" to DishTranslation("ट्रफल एडामेमे डिम सम (4 नग)", "भाप में पके पारदर्शी डिम सम, जिनमें एडामेमे और व्हाइट ट्रफल ऑयल का मिश्रण है।"),
        "Crispy Karaage Chicken Bao (2 pcs)" to DishTranslation("क्रिस्पी कारागे चिकन बाओ (2 नग)", "नरम स्टीम्ड बाओ बन, कुरकुरा जापानी फ्राइड चिकन और स्पाइसी मेयो।"),

        // The Green Bowl & Bakery
        "Beet Hummus & Falafel Buddha Bowl" to DishTranslation("बीट हमस और फलाफेल बुद्धा बाउल", "क्विनोआ, कुरकुरे छोले, एवोकाडो, पालक, चुकंदर हमस और सिट्रस ड्रेसिंग।"),
        "Sourdough Avocado & Poached Egg" to DishTranslation("सॉरडो एवोकाडो और पोच्ड एग", "कुरकुरी सॉरडो ब्रेड, मैश किया एवोकाडो, पोच्ड अंडा और माइक्रोग्रीन्स।"),
        "Wild Berry Acai Smoothie Bowl" to DishTranslation("वाइल्ड बेरी अकाई स्मूदी बाउल", "ब्लूबेरी और बादाम दूध की जमी हुई स्मूदी, घर का बना ग्रेनोला और ताजे केले।")
    )

    private val lexiconMarathi = mapOf(
        "Butter Chicken" to DishTranslation("बटर चिकन", "मखमली टोमॅटो आणि बटरच्या ग्रेव्हीतील रसरशीत चिकन."),
        "Dal Makhani" to DishTranslation("दाल मखनी", "काळी डाळ आणि लोणी घातलेली मखमली डाळ."),
        "Misal Pav" to DishTranslation("झणझणीत मिसळ पाव", "झणझणीत कट, मटकी उसळ आणि कुरकुरीत फरसाणसह पाव."),
        "Ghee Roast Dosa" to DishTranslation("तूप रोस्ट डोसा", "तुपात भाजलेला कुरकुरीत डोसा."),
        // Bistro Lumière & Biryani
        "Hyderabadi Dum Gosht Biryani" to DishTranslation("हैद्राबादी दम गोश्त बिर्याणी", "मंद आचेवर शिजवलेले रसरशीत मटण, केशरयुक्त बासुमती भात आणि सुगंधी मसाल्यांची बिर्याणी."),
        "Subz Paneer Dum Biryani" to DishTranslation("सब्ज पनीर दम बिर्याणी", "सुगंधी बासुमती भात, मसालेदार ताजे पनीरचे तुकडे, भाज्या आणि पुदिन्याचा सुगंध."),
        "Melt-in-Mouth Galouti Kebab" to DishTranslation("मऊ गलौटी कबाब", "तोंडा विरघळणारे मऊ कबाब, कच्ची पपई आणि ३२ सुगंधी औषधी वनस्पतींचा अर्क."),
        "Tandoori Paneer Angara" to DishTranslation("तंदूरी पनीर अंगारा", "काश्मिरी मिरची आणि मसाल्यात घोळवून भट्टीत भाजलेले चविष्ट पनीर."),
        "Royal Murgh Makhani" to DishTranslation("शाही मुर्ग मखनी", "तंदूरी चिकन, मखमली टोमॅटो-काजूची घट्ट ग्रेव्ही आणि कस्तुरी मेथी बटर."),
        "Dal Bukhara Slow-Simmered" to DishTranslation("दाल बुखारा", "रात्रभर मंद आचेवर लोणी आणि ताज्या टोमॅटोमध्ये शिजवलेली काळी डाळ."),
        "Butter Garlic Naan" to DishTranslation("बटर गार्लिक नान", "तंदूरमध्ये भाजलेले लसूण आणि लोणी लावलेले गरमागरम मऊ नान."),
        "Shahi Tukda with Kesari Rabri" to DishTranslation("शाही तुकडा केशर रबडीसह", "साजूक तुपात तळलेला कुरकुरीत पाव, वेलची पाक आणि दाट केशर रबडी."),

        // Trattoria Milano
        "Woodfired Margherita Pizza" to DishTranslation("वुडफायर्ड मार्गेरिटा पिझ्झा", "सॅन मार्झानो टोमॅटो सॉस, ताजे मोझरेला चीज आणि तुळशीच्या पानांचा पिझ्झा."),
        "Quattro Formaggi Bianca" to DishTranslation("क्वात्रो फॉर्मॅगी पिझ्झा", "चार प्रसिद्ध इटालियन चीजचा अप्रतिम संगम असलेला पिझ्झा."),
        "Diavola Spicy Salami Pizza" to DishTranslation("डियाव्होला स्पायसी सलामी पिझ्झा", "मसालेदार इटालियन सलामी, स्मोक्ड चीज आणि मिरची-मधाची चव."),
        "Handmade Truffle Tagliatelle" to DishTranslation("ट्रफल तालियातेले पास्ता", "हाताने बनवलेला पास्ता, ब्लॅक ट्रफल क्रीम, पारमेसन चीज आणि मशरूम."),
        "Bruschetta al Pomodoro" to DishTranslation("ब्रुशेटा अल पोमोडोरो", "कुरकुरीत भाजलेला पाव, ताजे टोमॅटो, लसूण, ऑलिव्ह तेल आणि तुळस."),
        "Classic Espresso Tiramisu" to DishTranslation("क्लासिक एस्प्रेसो तिरामिसू", "एस्प्रेसो कॉफीमध्ये भिजवलेले बिस्किटे, मस्कार्पोन क्रीम आणि कोको."),

        // Dakshin Delights
        "Ghee Roast Masala Dosa" to DishTranslation("तूप रोस्ट मसाला डोसा", "शुद्ध साजूक तुपात भाजलेला कुरकुरीत डोसा, बटाटा भाजी, ३ चटण्या आणि सांबारसह."),
        "Mysore Masala Dosa" to DishTranslation("म्हैसूर मसाला डोसा", "तिखट लाल चटणी, लोणी आणि बटाट्याच्या मसाल्याने भरलेला कुरकुरीत डोसा."),
        "Steamed Thatte Idli with Podi" to DishTranslation("तट्टे इडली पोडीसह", "मऊ आणि टम्म फुगलेली मोठी इडली, साजूक तूप आणि मसालेदार गनपावडर."),
        "Crispy Medu Vada (2 pcs)" to DishTranslation("कुरकुरीत मेदू वडा (२ नग)", "उडीद डाळीचे गरमागरम कुरकुरीत वडे, खोबऱ्याची चटणी आणि सांबार."),
        "Malabar Parotta with Veg Kurma" to DishTranslation("मलबार परोटा व्हेज कुर्मासह", "केरळ पद्धतीचा पदर असलेला मऊ परोटा आणि नारळाचा घट्ट भाज्यांचा कुर्मा."),
        "Chettinad Pepper Chicken" to DishTranslation("चेट्टीनाड मिरी चिकन", "ताज्या काळ्या मिऱ्या, कांदा आणि कढीपत्त्यात परतलेले मसालेदार चिकन."),
        "Mysore Pure Filter Coffee" to DishTranslation("म्हैसूर प्युअर फिल्टर कॉफी", "ताजी कडक डिकॉक्शन आणि वाफाळलेल्या दुधाची पारंपरिक कॉफी."),

        // Tokyo Zen & Bao
        "Tokyo Shoyu Chashu Ramen" to DishTranslation("टोकियो शोयु चाशू रामेन", "पारंपरिक जपानी सोया सूप, नूडल्स, चिकन, उकडलेले अंडे आणि सीवीड."),
        "Creamy Miso Veggie Ramen" to DishTranslation("क्रीमी मिसो व्हेजी रामेन", "सोयाबीन मिसो सूप, ताज्या भाज्या, कॉर्न आणि तिळाचे तेल."),
        "Truffle Edamame Dim Sum (4 pcs)" to DishTranslation("ट्रफल एडामेमे डिम सम (४ नग)", "वाफेवर शिजवलेले पारदर्शक डिम सम, एडामेमे आणि ट्रफल ऑईलसह."),
        "Crispy Karaage Chicken Bao (2 pcs)" to DishTranslation("क्रिस्पी कारागे चिकन बाओ (२ नग)", "मऊ स्टीम्ड बाओ बन, कुरकुरीत जपानी तळलेले चिकन आणि मेयोनेज."),

        // The Green Bowl & Bakery
        "Beet Hummus & Falafel Buddha Bowl" to DishTranslation("बीट हमस आणि फलाफेल बाऊल", "क्विनोआ, कुरकुरीत काबुली चणे, पालक, एवोकॅडो आणि बीटरूट हमस."),
        "Sourdough Avocado & Poached Egg" to DishTranslation("सॉरडो एवोकॅडो आणि पोच्ड एग", "कुरकुरीत सॉरडो ब्रेड, एवोकॅडो आणि पोच्ड अंडे."),
        "Wild Berry Acai Smoothie Bowl" to DishTranslation("बेरी अकाई स्मूदी बाऊल", "ब्लूबेरी आणि बदामाच्या दुधाची स्मूदी, ग्रॅनोला आणि ताज्या केळीच्या चकत्या.")
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

        // Fuzzy matching against known lexicon
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

    fun translateCategory(categoryName: String, lang: String = RestroI18n.currentLanguage): String {
        val targetLang = lang.lowercase(Locale.ROOT).trim()
        if (targetLang == "en") return categoryName
        val map = if (targetLang == "hi") categoryLexiconHindi
                  else if (targetLang == "mr") categoryLexiconMarathi
                  else emptyMap()
        return map[categoryName] ?: map.entries.firstOrNull { it.key.contains(categoryName, ignoreCase = true) || categoryName.contains(it.key, ignoreCase = true) }?.value ?: categoryName
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

fun Dish.localizedCategory(lang: String = RestroI18n.currentLanguage): String {
    return this.category.localizedCategory(lang)
}

fun String.localizedCategory(lang: String = RestroI18n.currentLanguage): String {
    val targetLang = lang.lowercase(Locale.ROOT).trim()
    if (targetLang == "en") return this

    val map = if (targetLang == "hi") RestroMenuTranslationManager.categoryLexiconHindi
              else if (targetLang == "mr") RestroMenuTranslationManager.categoryLexiconMarathi
              else emptyMap()

    return map[this] ?: map.entries.firstOrNull { it.key.contains(this, ignoreCase = true) || this.contains(it.key, ignoreCase = true) }?.value ?: this
}

fun Restaurant.localizedName(lang: String = RestroI18n.currentLanguage): String {
    val targetLang = lang.lowercase(Locale.ROOT).trim()
    if (targetLang == "en") return this.name

    val map = if (targetLang == "hi") RestroMenuTranslationManager.restaurantLexiconHindi
              else if (targetLang == "mr") RestroMenuTranslationManager.restaurantLexiconMarathi
              else emptyMap()

    return map[this.name]?.first ?: map.entries.firstOrNull { it.key.contains(this.name, ignoreCase = true) || this.name.contains(it.key, ignoreCase = true) }?.value?.first ?: this.name
}

fun Restaurant.localizedCuisine(lang: String = RestroI18n.currentLanguage): String {
    val targetLang = lang.lowercase(Locale.ROOT).trim()
    if (targetLang == "en") return this.cuisine

    val map = if (targetLang == "hi") RestroMenuTranslationManager.restaurantLexiconHindi
              else if (targetLang == "mr") RestroMenuTranslationManager.restaurantLexiconMarathi
              else emptyMap()

    return map[this.name]?.second ?: this.cuisine
}

fun Restaurant.localizedAddress(lang: String = RestroI18n.currentLanguage): String {
    val targetLang = lang.lowercase(Locale.ROOT).trim()
    if (targetLang == "en") return this.address

    // Translate common area names
    return if (targetLang == "hi") {
        this.address.replace("Goregaon West", "गोरेगांव पश्चिम")
            .replace("Andheri West", "अंधेरी पश्चिम")
            .replace("Bandra West", "बांद्रा पश्चिम")
            .replace("Juhu Beach", "जुहू बीच")
            .replace("Powai", "पवई")
            .replace("Mumbai", "मुंबई")
            .replace("Near Station", "स्टेशन के पास")
            .replace("Road", "रोड")
    } else if (targetLang == "mr") {
        this.address.replace("Goregaon West", "गोरेगाव पश्चिम")
            .replace("Andheri West", "अंधेरी पश्चिम")
            .replace("Bandra West", "वांद्रे पश्चिम")
            .replace("Juhu Beach", "जुहू चौपाटी")
            .replace("Powai", "पवई")
            .replace("Mumbai", "मुंबई")
            .replace("Near Station", "रेल्वे स्थानकाजवळ")
            .replace("Road", "रस्ता")
    } else this.address
}

fun String.localizedRestaurantName(lang: String = RestroI18n.currentLanguage): String {
    val targetLang = lang.lowercase(Locale.ROOT).trim()
    if (targetLang == "en") return this

    val map = if (targetLang == "hi") RestroMenuTranslationManager.restaurantLexiconHindi
              else if (targetLang == "mr") RestroMenuTranslationManager.restaurantLexiconMarathi
              else emptyMap()

    return map[this]?.first ?: map.entries.firstOrNull { it.key.contains(this, ignoreCase = true) || this.contains(it.key, ignoreCase = true) }?.value?.first ?: this
}

fun String.localizedAddress(lang: String = RestroI18n.currentLanguage): String {
    val targetLang = lang.lowercase(Locale.ROOT).trim()
    if (targetLang == "en") return this

    return if (targetLang == "hi") {
        this.replace("Swami Vivekanand Road", "स्वामी विवेकानंद रोड")
            .replace("Swami Vivekanand", "स्वामी विवेकानंद")
            .replace("Goregaon West", "गोरेगांव पश्चिम")
            .replace("Andheri West", "अंधेरी पश्चिम")
            .replace("Bandra West", "बांद्रा पश्चिम")
            .replace("Juhu Beach", "जुहू बीच")
            .replace("Hill Road", "हिल रोड")
            .replace("SV Road", "एसवी रोड")
            .replace("Linking Road", "लिंकिंग रोड")
            .replace("Tara Road", "तारा रोड")
            .replace("Hiranandani Gardens", "हीरानंदानी गार्डन्स")
            .replace("Amphitheatre Parkway", "एम्फीथिएटर पार्कवे")
            .replace("Mountain View", "माउंटेन व्यू")
            .replace("Powai", "पवई")
            .replace("Mumbai", "मुंबई")
            .replace("Near Station", "स्टेशन के पास")
            .replace("Lokhandwala", "लोखंडवाला")
            .replace("Road", "रोड")
    } else if (targetLang == "mr") {
        this.replace("Swami Vivekanand Road", "स्वामी विवेकानंद रस्ता")
            .replace("Swami Vivekanand", "स्वामी विवेकानंद")
            .replace("Goregaon West", "गोरेगाव पश्चिम")
            .replace("Andheri West", "अंधेरी पश्चिम")
            .replace("Bandra West", "वांद्रे पश्चिम")
            .replace("Juhu Beach", "जुहू चौपाटी")
            .replace("Hill Road", "हिल रस्ता")
            .replace("SV Road", "एसव्ही रोड")
            .replace("Linking Road", "लिंकिंग रोड")
            .replace("Tara Road", "तारा रस्ता")
            .replace("Hiranandani Gardens", "हिरानंदानी गार्डन्स")
            .replace("Amphitheatre Parkway", "ॲम्फीथिएटर पार्कवे")
            .replace("Mountain View", "माउंटेन व्ह्यू")
            .replace("Powai", "पवई")
            .replace("Mumbai", "मुंबई")
            .replace("Near Station", "रेल्वे स्थानकाजवळ")
            .replace("Lokhandwala", "लोखंडवाला")
            .replace("Road", "रस्ता")
    } else this
}

fun com.restaurant.mobile.model.MenuItem.localizedName(lang: String = RestroI18n.currentLanguage): String {
    val targetLang = lang.lowercase(Locale.ROOT).trim()
    if (targetLang == "en") return this.name
    return RestroMenuTranslationManager.getTranslation(id.toString(), name, "", targetLang).name
}
