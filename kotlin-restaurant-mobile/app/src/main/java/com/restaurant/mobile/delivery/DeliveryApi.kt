package com.restaurant.mobile.delivery

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.restaurant.mobile.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("restro-session", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("restro-session", null) as? SecretKey)
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(
                    KeyGenParameterSpec.Builder(
                            "restro-session",
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
                generateKey()
            }
    }

    companion object {
        const val DEFAULT_SESSION_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    fun save(token: String?, ttlMs: Long = DEFAULT_SESSION_TTL_MS) {
        if (token == null) {
            prefs.edit().clear().commit()
            return
        }
        val cipher =
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        prefs
            .edit()
            .putString(
                "token",
                Base64.encodeToString(
                    cipher.iv + cipher.doFinal(token.toByteArray()),
                    Base64.NO_WRAP,
                ),
            )
            .putLong("expires_at", System.currentTimeMillis() + ttlMs)
            .commit()
    }

    fun getExpiresAt(): Long = prefs.getLong("expires_at", 0L)

    fun isSessionExpired(): Boolean {
        if (!prefs.contains("token")) return false
        val expiresAt = getExpiresAt()
        return expiresAt in 1 until System.currentTimeMillis()
    }

    fun extendSession(ttlMs: Long = DEFAULT_SESSION_TTL_MS) {
        if (prefs.contains("token")) {
            prefs.edit().putLong("expires_at", System.currentTimeMillis() + ttlMs).commit()
        }
    }

    fun load(): String? =
        try {
            if (isSessionExpired()) {
                save(null)
                null
            } else {
                prefs.getString("token", null)?.let {
                    val bytes = Base64.decode(it, Base64.NO_WRAP)
                    val cipher =
                        Cipher.getInstance("AES/GCM/NoPadding").apply {
                            init(
                                Cipher.DECRYPT_MODE,
                                key(),
                                GCMParameterSpec(128, bytes.copyOfRange(0, 12)),
                            )
                        }
                    String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)))
                }
            }
        } catch (_: Exception) {
            save(null)
            null
        }

    fun saveProfile(profile: Profile?) {
        if (profile == null) {
            prefs.edit().remove("profile_json").commit()
            return
        }
        val json = Gson().toJson(profile)
        prefs.edit().putString("profile_json", json).commit()
    }

    fun loadProfile(): Profile? =
        try {
            if (isSessionExpired()) {
                save(null)
                null
            } else {
                prefs.getString("profile_json", null)?.let {
                    Gson().fromJson(it, Profile::class.java)
                }
            }
        } catch (_: Exception) {
            null
        }
}

class ApiFailure(val status: Int, message: String) : Exception(message)

class DeliveryApi {
    var token: String? = null
    var engine: RestroFirebaseBackendEngine? = null
    var onSessionRevoked: ((String) -> Unit)? = null
    val gson = Gson()

    suspend inline fun <reified T> call(
        path: String,
        method: String = "GET",
        body: Any? = null,
    ): T {
        // Pure Kotlin Firebase & SQL Backend execution
        engine?.let { eng ->
            val localResult = dispatchToKotlinEngine(eng, path, method, body)
            if (localResult != null) {
                return gson.fromJson(gson.toJson(localResult), object : TypeToken<T>() {}.type)
            }
        }

        return gson.fromJson(request(path, method, body), object : TypeToken<T>() {}.type)
    }

    fun dispatchToKotlinEngine(eng: RestroFirebaseBackendEngine, path: String, method: String, body: Any?): Any? {
        val cleanPath = path.substringBefore("?")
        return when {
            cleanPath == "/catalog" -> eng.getCatalog()
            cleanPath == "/auth/otp/send" -> {
                val req = if (body is OtpSendRequest) body else gson.fromJson(gson.toJson(body), OtpSendRequest::class.java)
                eng.sendOtp(req)
            }
            cleanPath == "/auth/otp/verify" -> {
                val req = if (body is OtpVerifyRequest) body else gson.fromJson(gson.toJson(body), OtpVerifyRequest::class.java)
                eng.verifyOtp(req)
            }
            cleanPath == "/orders" && method == "GET" -> eng.sqlDb.getAllOrders()
            cleanPath == "/addresses" && method == "GET" -> eng.sqlDb.getAllAddresses()
            cleanPath == "/addresses" && method == "POST" -> {
                val addr = if (body is Address) body else gson.fromJson(gson.toJson(body), Address::class.java)
                eng.sqlDb.saveAddress(addr)
                addr
            }
            cleanPath.startsWith("/orders/") && cleanPath.endsWith("/tracking") -> {
                val orderId = cleanPath.removePrefix("/orders/").removeSuffix("/tracking")
                val orders = eng.sqlDb.getAllOrders()
                val order = orders.find { it.id == orderId } ?: orders.firstOrNull()
                TrackingResponse(
                    orderId = orderId,
                    restaurantName = order?.restaurantName ?: "Restaurant",
                    status = "On the way",
                    etaMinutes = 18,
                    distanceRemainingKm = 2.4,
                    isNearby = false,
                    rider = RiderTracking("Vikram Kumar", "+91 98112 34567", "KA-01-EQ-9872", LocationPoint(12.9716, 77.5946))
                )
            }
            cleanPath == "/cart" -> Cart()
            cleanPath == "/favorites" -> emptyList<String>()
            cleanPath == "/analytics" -> eng.getAnalyticsData()
            else -> null
        }
    }

    suspend fun request(path: String, method: String, body: Any?): String =
        withContext(Dispatchers.IO) {
            val connection = URL(BuildConfig.API_URL + path).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 2000
            connection.readTimeout = 3000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            try {
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.bufferedWriter().use { it.write(gson.toJson(body)) }
                }
                val status = connection.responseCode
                val response =
                    (if (status in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()
                if (status == 401 && !path.startsWith("/auth")) {
                    onSessionRevoked?.invoke("Your session has expired. Please sign in again.")
                }
                if (status !in 200..299) {
                    val message =
                        runCatching {
                                gson.fromJson(response, Map::class.java)["message"] as? String
                            }
                            .getOrNull()
                    throw ApiFailure(
                        status,
                        message ?: "Unable to complete this request. Try again.",
                    )
                }
                response.ifBlank { "{}" }
            } finally {
                connection.disconnect()
            }
        }
}
