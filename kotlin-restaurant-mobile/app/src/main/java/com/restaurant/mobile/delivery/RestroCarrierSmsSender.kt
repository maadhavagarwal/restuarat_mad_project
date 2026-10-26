package com.restaurant.mobile.delivery

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log

object RestroCarrierSmsSender {

    private const val TAG = "RestroCarrierSms"

    fun sendCarrierSms(context: Context, destinationPhone: String, message: String): Boolean {
        val cleanNumber = destinationPhone.filter { it.isDigit() || it == '+' }
        if (cleanNumber.isBlank()) return false

        try {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.SEND_SMS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                Log.d(TAG, "Dispatching carrier SMS to $cleanNumber via SmsManager")
                val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                val parts = smsManager.divideMessage(message)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(cleanNumber, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(cleanNumber, null, message, null, null)
                }
                return true
            } else {
                Log.d(TAG, "SEND_SMS permission not granted; delivering OTP code via in-app banner and status bar notification")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending carrier SMS: ${e.message}", e)
            return false
        }
    }
}
