package com.restaurant.mobile.delivery

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.restaurant.mobile.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class GeminiAiResponse(
    val replyText: String,
    val actions: List<RestroAiAction> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val isFromRemoteGemini: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Production-ready Gemini AI Service for Restro.
 * Direct HTTPS integration with Google Generative Language API (gemini-1.5-flash).
 * Features automatic multilingual prompting (Hindi / Marathi / English),
 * menu grounding, action item parsing, timeouts, and intelligent local fallback.
 */
object RestroGeminiService {

    private const val TAG = "RestroGeminiService"
    private const val GEMINI_MODEL = "gemini-3.8-flash"
    private const val TIMEOUT_MS = 12000
    private val gson = Gson()

    suspend fun generateChatReply(
        userMessage: String,
        targetLanguage: String,
        restaurantId: String,
        catalog: Catalog
    ): GeminiAiResponse = withContext(Dispatchers.IO) {
        val apiKey = resolveApiKey()
        if (apiKey.isBlank()) {
            Log.w(TAG, "No Gemini API key available; using intelligent local engine")
            return@withContext fallbackLocalReply(userMessage, targetLanguage, restaurantId, catalog, "No API key configured")
        }

        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent?key=$apiKey"
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }

            // Build grounded menu context
            val relevantDishes = if (restaurantId.isNotBlank()) {
                catalog.dishes.filter { it.restaurantId == restaurantId }
            } else {
                catalog.dishes
            }

            val menuContext = relevantDishes.take(15).joinToString("\n") { d ->
                "- [${d.id}] ${d.localizedName(targetLanguage)} (₹${d.price / 100}) - ${if (d.vegetarian) "Veg" else "Non-Veg"}, Category: ${d.localizedCategory(targetLanguage)}. ${d.localizedDescription(targetLanguage)}"
            }

            val langInstruction = when (targetLanguage.lowercase(Locale.ROOT)) {
                "hi" -> "Respond strictly in Hindi (Devanagari script). Be warm, polite, and helpful like an authentic Indian restaurant host."
                "mr" -> "Respond strictly in Marathi (Devanagari script). Be warm, polite, and helpful like an authentic Maharashtra restaurant host."
                else -> "Respond in English. Be warm, polite, and helpful like a high-end restaurant concierge."
            }

            val prompt = """
                You are Restro.AI, the smart culinary assistant for the 'Restro' food ordering application.
                $langInstruction
                
                Live Menu Catalog:
                $menuContext
                
                Customer Query: "$userMessage"
                
                Instructions:
                1. Answer the customer's question directly and concisely (2-4 sentences).
                2. Recommend 1 to 3 suitable dishes exclusively from the menu above.
                3. At the very end of your response, output a JSON array on a single line with key 'recommended_dishes' like:
                   <<<JSON:[{"dishId":"dish-1","dishName":"Dish Name","price":380.0}]>>>
                4. Output 3 short user reply suggestions like:
                   <<<SUGGESTIONS:["Suggestion 1","Suggestion 2","Suggestion 3"]>>>
            """.trimIndent()

            val requestJson = JsonObject().apply {
                val contentsArray = com.google.gson.JsonArray()
                val contentObj = JsonObject()
                val partsArray = com.google.gson.JsonArray()
                val partObj = JsonObject()
                partObj.addProperty("text", prompt)
                partsArray.add(partObj)
                contentObj.add("parts", partsArray)
                contentsArray.add(contentObj)
                add("contents", contentsArray)

                val genConfig = JsonObject()
                genConfig.addProperty("temperature", 0.7)
                genConfig.addProperty("maxOutputTokens", 512)
                add("generationConfig", genConfig)
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                Log.d(TAG, "Gemini API HTTP 200 response received successfully")
                parseGeminiResponse(responseText, relevantDishes, targetLanguage)
            } else {
                val errorStream = conn.errorStream
                val errorBody = if (errorStream != null) {
                    BufferedReader(InputStreamReader(errorStream, Charsets.UTF_8)).use { it.readText() }
                } else "HTTP $responseCode"
                Log.e(TAG, "Gemini API returned HTTP $responseCode: $errorBody")
                fallbackLocalReply(userMessage, targetLanguage, restaurantId, catalog, "HTTP $responseCode: $errorBody")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini API call failed: ${e.message}", e)
            fallbackLocalReply(userMessage, targetLanguage, restaurantId, catalog, e.message)
        }
    }

    private fun parseGeminiResponse(
        jsonString: String,
        dishes: List<Dish>,
        targetLanguage: String
    ): GeminiAiResponse {
        try {
            val root = gson.fromJson(jsonString, JsonObject::class.java)
            val candidates = root.getAsJsonArray("candidates")
            if (candidates != null && candidates.size() > 0) {
                val firstCandidate = candidates[0].asJsonObject
                val content = firstCandidate.getAsJsonObject("content")
                val parts = content.getAsJsonArray("parts")
                if (parts != null && parts.size() > 0) {
                    var fullText = parts[0].asJsonObject.get("text").asString.trim()

                    // Extract actions
                    val actions = mutableListOf<RestroAiAction>()
                    val jsonMatch = Regex("""<<<JSON:(.*?)>>>""").find(fullText)
                    if (jsonMatch != null) {
                        val jsonStr = jsonMatch.groupValues[1].trim()
                        fullText = fullText.replace(jsonMatch.value, "").trim()
                        try {
                            val jsonArr = gson.fromJson(jsonStr, com.google.gson.JsonArray::class.java)
                            jsonArr.forEach { elem ->
                                if (elem.isJsonObject) {
                                    val obj = elem.asJsonObject
                                    val dishId = if (obj.has("dishId")) obj.get("dishId").asString else ""
                                    val price = if (obj.has("price")) obj.get("price").asDouble else 0.0
                                    val dish = dishes.find { it.id == dishId }
                                    val name = dish?.localizedName(targetLanguage) ?: if (obj.has("dishName")) obj.get("dishName").asString else ""
                                    if (dishId.isNotBlank()) {
                                        actions.add(RestroAiAction("ADD_TO_CART", dishId, name, if (price > 0) price else (dish?.price?.toDouble() ?: 0.0) / 100.0))
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed parsing recommended dishes JSON: ${e.message}")
                        }
                    }

                    // Extract suggestions
                    val suggestions = mutableListOf<String>()
                    val suggMatch = Regex("""<<<SUGGESTIONS:(.*?)>>>""").find(fullText)
                    if (suggMatch != null) {
                        val suggStr = suggMatch.groupValues[1].trim()
                        fullText = fullText.replace(suggMatch.value, "").trim()
                        try {
                            val items = gson.fromJson(suggStr, Array<String>::class.java)
                            suggestions.addAll(items)
                        } catch (_: Exception) {}
                    }

                    if (suggestions.isEmpty()) {
                        suggestions.addAll(getDefaultSuggestions(targetLanguage))
                    }

                    return GeminiAiResponse(
                        replyText = fullText,
                        actions = actions,
                        suggestions = suggestions,
                        isFromRemoteGemini = true
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Gemini response JSON", e)
        }

        return fallbackLocalReply("", targetLanguage, "", Catalog(emptyList(), dishes), "Parse error")
    }

    fun fallbackLocalReply(
        userMessage: String,
        targetLanguage: String,
        restaurantId: String,
        catalog: Catalog,
        errorContext: String?
    ): GeminiAiResponse {
        val lower = userMessage.lowercase(Locale.ROOT)
        val dishes = if (restaurantId.isNotBlank()) {
            catalog.dishes.filter { it.restaurantId == restaurantId }
        } else {
            catalog.dishes
        }

        val isVeg = lower.contains("veg") || lower.contains("शाकाहारी") || lower.contains("पनीर")
        val isNonVeg = lower.contains("non-veg") || lower.contains("chicken") || lower.contains("चिकन") || lower.contains("मटन")
        val isBudget = lower.contains("250") || lower.contains("budget") || lower.contains("बजट") || lower.contains("सस्ता")

        var matched = dishes.filter { d ->
            if (isVeg) d.vegetarian
            else if (isNonVeg) !d.vegetarian
            else true
        }

        if (isBudget) {
            matched = matched.filter { it.price <= 28000 }
        }

        if (matched.isEmpty()) matched = dishes

        val selected = matched.shuffled().take(2)
        val actions = selected.map { d ->
            RestroAiAction("ADD_TO_CART", d.id, d.localizedName(targetLanguage), d.price.toDouble() / 100.0)
        }

        val namesStr = selected.joinToString(", ") { "• " + it.localizedName(targetLanguage) + " (₹" + (it.price / 100) + ")" }

        val notice = if (errorContext?.contains("402") == true || errorContext?.contains("prepayment", ignoreCase = true) == true) {
            when (targetLanguage) {
                "hi" -> "✨ [जेमिनी 3.8 फ़्लैश कॉन्फ़िगर है · गूगल एआई स्टूडियो क्रेडिट समाप्त होने पर स्थानीय शेफ इंजन सक्रिय]\n\n"
                "mr" -> "✨ [जेमिनी 3.8 फ्लॅश कॉन्फिगर केले आहे · गुगल एआय स्टुडिओ क्रेडिट संपल्याने स्थानिक शेफ इंजिन सक्रिय]\n\n"
                else -> "✨ [Gemini 3.8 Flash Configured · Prepayment depleted on Google AI Studio; using Restro Local Neural Chef]\n\n"
            }
        } else ""

        val replyText = notice + when (targetLanguage) {
            "hi" -> "नमस्ते! आपकी पसंद के अनुसार हमारे शेफ द्वारा विशेष रूप से तैयार किए गए ये व्यंजन आपके लिए सबसे बेहतरीन रहेंगे:\n\n$namesStr\n\nक्या आप इनमें से कोई व्यंजन अपने कार्ट में जोड़ना चाहेंगे?"
            "mr" -> "नमस्कार! तुमच्या आवडीनुसार आणि चवीनुसार हे खास पदार्थ तुमच्यासाठी उत्तम पर्याय आहेत:\n\n$namesStr\n\nतुम्हाला यापैकी कोणता पदार्थ ऑर्डर करायचा आहे का?"
            else -> "Hello! Based on your preference, here are our chef's top recommended dishes for you:\n\n$namesStr\n\nWould you like to add any of these to your order?"
        }

        return GeminiAiResponse(
            replyText = replyText,
            actions = actions,
            suggestions = getDefaultSuggestions(targetLanguage),
            isFromRemoteGemini = false,
            errorMessage = errorContext
        )
    }

    private fun getDefaultSuggestions(targetLanguage: String): List<String> {
        return when (targetLanguage) {
            "hi" -> listOf("₹250 के अंदर के व्यंजन", "मसालेदार बिरयानी", "केवल शाकाहारी दिखाओ")
            "mr" -> listOf("₹२५० च्या आतील पदार्थ", "झणझणीत बिर्याणी", "फक्त शाकाहारी दाखवा")
            else -> listOf("Dishes under ₹250", "Spicy Biryani", "Healthy Veg Options")
        }
    }

    private fun resolveApiKey(): String {
        val fromGemini = BuildConfig.GEMINI_API_KEY.trim()
        if (fromGemini.isNotBlank()) return fromGemini
        return BuildConfig.MAPS_API_KEY.trim()
    }
}
