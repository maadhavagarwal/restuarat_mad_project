package com.restaurant.mobile.delivery

import android.app.Activity
import android.util.Log
import com.razorpay.Checkout
import com.razorpay.PaymentData
import org.json.JSONObject

/**
 * Official Razorpay Standard Checkout SDK Bridge.
 * Launches the native Razorpay payment sheet and dispatches callbacks.
 */
object RazorpayCheckoutLauncher {
    private const val TAG = "RazorpayLauncher"

    private var pendingSuccess: ((paymentId: String, data: PaymentData?) -> Unit)? = null
    private var pendingError: ((Int, String?, PaymentData?) -> Unit)? = null

    fun openCheckout(
        activity: Activity,
        keyId: String,
        orderId: String,
        amountInPaise: Long,
        restaurantName: String = "Restro Food Delivery",
        description: String = "Food Order Payment",
        customerEmail: String = "",
        customerPhone: String = "",
        onSuccess: (paymentId: String, data: PaymentData?) -> Unit,
        onError: (Int, String?, PaymentData?) -> Unit
    ) {
        pendingSuccess = onSuccess
        pendingError = onError

        try {
            val checkout = Checkout()
            checkout.setKeyID(keyId.ifBlank { "rzp_test_TsoUycZ6YiDeDi" })

            val options = JSONObject().apply {
                put("name", restaurantName.ifBlank { "Restro Food Delivery" })
                put("description", description.ifBlank { "Order Payment" })
                put("currency", "INR")
                put("amount", amountInPaise)
                if (orderId.isNotBlank()) {
                    put("order_id", orderId)
                }

                val prefill = JSONObject().apply {
                    if (customerEmail.isNotBlank()) put("email", customerEmail)
                    if (customerPhone.isNotBlank()) put("contact", customerPhone)
                }
                put("prefill", prefill)

                val theme = JSONObject().apply {
                    put("color", "#FF5252") // Restro coral brand color
                    put("backdrop_color", "#1A1A1A")
                }
                put("theme", theme)

                val retryObj = JSONObject().apply {
                    put("enabled", true)
                    put("max_count", 4)
                }
                put("retry", retryObj)

                val modal = JSONObject().apply {
                    put("confirm_close", true)
                }
                put("modal", modal)
            }

            Log.i(TAG, "Opening official Razorpay Checkout for order '$orderId', amount ₹${amountInPaise / 100.0}")
            checkout.open(activity, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Razorpay Checkout", e)
            onError(Checkout.PAYMENT_CANCELED, e.message ?: "Failed to open Razorpay checkout", null)
        }
    }

    fun handleSuccess(paymentId: String?, data: PaymentData?) {
        Log.i(TAG, "Razorpay payment success received: paymentId=$paymentId, orderId=${data?.orderId}")
        val cb = pendingSuccess
        pendingSuccess = null
        pendingError = null
        cb?.invoke(paymentId.orEmpty(), data)
    }

    fun handleError(code: Int, response: String?, data: PaymentData?) {
        Log.w(TAG, "Razorpay payment error/cancellation received: code=$code, response=$response")
        val cb = pendingError
        pendingSuccess = null
        pendingError = null
        cb?.invoke(code, response, data)
    }
}
