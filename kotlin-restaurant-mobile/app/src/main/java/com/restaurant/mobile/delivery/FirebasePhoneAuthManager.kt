package com.restaurant.mobile.delivery

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

object FirebasePhoneAuthManager {
    var storedVerificationId: String? = null
    var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    fun sendPhoneOtp(
        activity: Activity,
        phoneNumber: String,
        onCodeSent: (verificationId: String) -> Unit,
        onAutoVerified: (PhoneAuthCredential) -> Unit,
        onFailure: (String) -> Unit
    ) {
        try {
            val auth = FirebaseAuth.getInstance()
            val cleanDigits = phoneNumber.filter { it.isDigit() }
            val formattedPhone = if (cleanDigits.length == 10) "+91$cleanDigits" else if (cleanDigits.startsWith("91") && cleanDigits.length == 12) "+$cleanDigits" else if (phoneNumber.startsWith("+")) phoneNumber else "+91$cleanDigits"

            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(formattedPhone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                        onAutoVerified(credential)
                    }

                    override fun onVerificationFailed(e: FirebaseException) {
                        val rawMsg = e.localizedMessage ?: "Firebase verification failed"
                        val actionableMsg = if (rawMsg.contains("not allowed", ignoreCase = true) || rawMsg.contains("disabled", ignoreCase = true)) {
                            "Firebase Phone Provider is disabled in Firebase Console (restro-42877). Please go to Firebase Console -> Authentication -> Sign-in method -> Enable 'Phone' and add India (+91) in Settings -> SMS Region Policy. Or add +91 9833978177 under 'Phone numbers for testing'."
                        } else if (rawMsg.contains("quota", ignoreCase = true) || rawMsg.contains("too many", ignoreCase = true) || rawMsg.contains("blocked", ignoreCase = true)) {
                            "SMS limit or carrier block reached. Add your number (+91 9833978177) under 'Phone numbers for testing' in Firebase Console with OTP 123456 to test instantly."
                        } else if (rawMsg.contains("app check", ignoreCase = true) || rawMsg.contains("play integrity", ignoreCase = true) || rawMsg.contains("safety-net", ignoreCase = true)) {
                            "Play Integrity check requires SHA-1 (81:84:42:B2:58:13:D1:B0:F2:1F:27:B4:FF:E0:62:13:60:1C:2C:28) in Firebase Project Settings."
                        } else {
                            rawMsg
                        }
                        onFailure(actionableMsg)
                    }

                    override fun onCodeSent(
                        verificationId: String,
                        token: PhoneAuthProvider.ForceResendingToken
                    ) {
                        storedVerificationId = verificationId
                        resendToken = token
                        onCodeSent(verificationId)
                    }
                })
                .build()

            PhoneAuthProvider.verifyPhoneNumber(options)
        } catch (e: Exception) {
            onFailure(e.localizedMessage ?: "Could not initiate Firebase Phone Auth")
        }
    }

    fun verifyCode(
        code: String,
        onSuccess: (PhoneAuthCredential) -> Unit,
        onError: (String) -> Unit
    ) {
        val vid = storedVerificationId
        if (vid == null) {
            onError("No active verification session. Please request OTP first.")
            return
        }
        try {
            val credential = PhoneAuthProvider.getCredential(vid, code)
            onSuccess(credential)
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Invalid verification code")
        }
    }
}
