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
                        onFailure(e.localizedMessage ?: "Firebase verification failed")
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
