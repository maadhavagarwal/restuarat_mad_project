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

    fun save(token: String?) {
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
            .commit()
    }

    fun load(): String? =
        try {
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
        } catch (_: Exception) {
            save(null)
            null
        }
}

class ApiFailure(val status: Int, message: String) : Exception(message)

class DeliveryApi {
    var token: String? = null
    val gson = Gson()

    suspend inline fun <reified T> call(
        path: String,
        method: String = "GET",
        body: Any? = null,
    ): T = gson.fromJson(request(path, method, body), object : TypeToken<T>() {}.type)

    suspend fun request(path: String, method: String, body: Any?): String =
        withContext(Dispatchers.IO) {
            val connection = URL(BuildConfig.API_URL + path).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
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
