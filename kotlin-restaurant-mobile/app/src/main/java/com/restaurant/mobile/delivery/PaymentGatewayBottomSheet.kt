package com.restaurant.mobile.delivery

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

enum class PaymentProcessingState {
    INPUT,
    VERIFYING,
    AUTHORIZED,
    FAILED
}

/**
 * Official Razorpay Standard Checkout Bottom Sheet.
 * Direct bridge to Razorpay SDK Activity with server-side HMAC-SHA256 verification.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentGatewayBottomSheet(
    vm: DeliveryViewModel,
    totalAmount: Long = 0L,
    selectedAddressId: String = "",
    initialMethod: String = "UPI",
    diningTab: DiningTab? = null,
    razorpayCallback: RazorpayPaymentCallback? = null,
    onPaymentSuccess: ((paymentMethod: String, transactionId: String) -> Unit)? = null,
    onPaymentFailure: ((errorCode: String, errorMessage: String) -> Unit)? = null,
    onCounterBillRequested: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val effectiveDiningTab = diningTab ?: if (selectedAddressId.startsWith("DINE_IN")) vm.activeTableTab else null
    val isDineIn = effectiveDiningTab != null || selectedAddressId.startsWith("DINE_IN")
    val effectiveTotal = if (isDineIn && effectiveDiningTab != null) effectiveDiningTab.total
        else if (totalAmount > 0L) totalAmount
        else (vm.quote?.total ?: 0L)

    var processingState by remember { mutableStateOf(PaymentProcessingState.INPUT) }
    var statusMessage by remember { mutableStateOf("Preparing Razorpay Checkout...") }
    var verificationErrorMessage by remember { mutableStateOf("") }
    var transactionId by remember { mutableStateOf(razorpayCallback?.razorpay_payment_id ?: "") }
    var hasAutoLaunched by rememberSaveable { mutableStateOf(false) }

    val targetRestaurant = vm.catalog.restaurants.find {
        it.id == effectiveDiningTab?.restaurantId?.ifBlank { vm.restaurantId } ?: vm.restaurantId
    }

    val restaurantName = targetRestaurant?.name ?: "Restro Food Delivery"

    // Core function to launch official Razorpay Checkout SDK
    fun startRazorpayCheckout() {
        val activity = context.findActivity()
        if (activity == null) {
            verificationErrorMessage = "Could not find host activity to open Razorpay checkout."
            processingState = PaymentProcessingState.FAILED
            onPaymentFailure?.invoke("NO_ACTIVITY", verificationErrorMessage)
            return
        }

        // 1. Enforce restaurant.acceptsDiningPayment for Dine-In
        if (isDineIn && !DineInPaymentRules.isPaymentEligible(targetRestaurant, isDineIn = true)) {
            verificationErrorMessage = "Online dining payment is unavailable for this restaurant. Please request a counter bill."
            processingState = PaymentProcessingState.FAILED
            onPaymentFailure?.invoke("DINING_PAYMENT_DISABLED", verificationErrorMessage)
            return
        }

        processingState = PaymentProcessingState.VERIFYING
        statusMessage = "Opening Razorpay Secure Checkout..."

        coroutineScope.launch(Dispatchers.IO) {
            try {
                // 2. Authoritative server order creation
                val orderResp: Map<String, Any> = vm.api.call(
                    "/payments/razorpay/create-order",
                    "POST",
                    mapOf(
                        "amount" to effectiveTotal,
                        "currency" to "INR",
                        "receipt" to "rcpt_${System.currentTimeMillis()}",
                        "orderType" to if (isDineIn) "DINE_IN" else "DELIVERY",
                        "restaurantId" to (targetRestaurant?.id ?: vm.restaurantId),
                        "tabId" to effectiveDiningTab?.id,
                        "tableNumber" to effectiveDiningTab?.tableNumber
                    )
                )

                val orderObj = orderResp["order"] as? Map<*, *>
                val createdOrderId = orderObj?.get("id")?.toString().orEmpty()
                val serverKeyId = orderResp["key_id"]?.toString().orEmpty().ifBlank {
                    com.restaurant.mobile.BuildConfig.RAZORPAY_KEY_ID
                }

                withContext(Dispatchers.Main) {
                    statusMessage = "Awaiting payment in Razorpay window..."

                    RazorpayCheckoutLauncher.openCheckout(
                        activity = activity,
                        keyId = serverKeyId,
                        orderId = createdOrderId,
                        amountInPaise = effectiveTotal,
                        restaurantName = restaurantName,
                        description = if (isDineIn) "Table ${effectiveDiningTab?.tableNumber ?: ""} Bill Settlement" else "Food Order Payment",
                        customerEmail = vm.profile?.email.orEmpty(),
                        customerPhone = vm.profile?.phone.orEmpty(),
                        onSuccess = { payId, paymentData ->
                            val ordId = (paymentData?.orderId?.takeIf { it.isNotBlank() } ?: createdOrderId).ifBlank {
                                (orderObj?.get("local_order_id") as? String).orEmpty().ifBlank { "order_$payId" }
                            }
                            val sig = paymentData?.signature?.takeIf { it.isNotBlank() } ?: "direct_pay_sig_$payId"

                            statusMessage = "Verifying payment signature with server..."

                            coroutineScope.launch(Dispatchers.IO) {
                                val callback = RazorpayPaymentCallback(
                                    razorpay_order_id = ordId,
                                    razorpay_payment_id = payId,
                                    razorpay_signature = sig
                                )

                                val verifyResult = vm.verifyAndProcessRazorpayPayment(
                                    callback = callback,
                                    isDineIn = isDineIn,
                                    diningTab = effectiveDiningTab,
                                    addressId = selectedAddressId,
                                    paymentMethod = "Razorpay"
                                )

                                withContext(Dispatchers.Main) {
                                    if (verifyResult.verified && verifyResult.success) {
                                        transactionId = payId
                                        processingState = PaymentProcessingState.AUTHORIZED
                                        delay(1000L)
                                        onPaymentSuccess?.invoke("Razorpay", payId)
                                        onDismiss()
                                    } else {
                                        verificationErrorMessage = verifyResult.message.ifBlank { "Server cryptographic verification failed." }
                                        processingState = PaymentProcessingState.FAILED
                                        onPaymentFailure?.invoke(verifyResult.errorCode ?: "VERIFICATION_FAILED", verificationErrorMessage)
                                    }
                                }
                            }
                        },
                        onError = { code, response, _ ->
                            val msg = when (code) {
                                com.razorpay.Checkout.PAYMENT_CANCELED -> "Payment was cancelled."
                                com.razorpay.Checkout.NETWORK_ERROR -> "Network error occurred during payment. Please check your internet connection."
                                else -> response ?: "Razorpay payment encountered an error (Code: $code)"
                            }
                            verificationErrorMessage = msg
                            processingState = PaymentProcessingState.FAILED
                            onPaymentFailure?.invoke("RAZORPAY_ERROR_$code", msg)
                        }
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    verificationErrorMessage = e.message ?: "Failed to initiate payment with server."
                    processingState = PaymentProcessingState.FAILED
                    onPaymentFailure?.invoke("CREATE_ORDER_FAILED", verificationErrorMessage)
                }
            }
        }
    }

    // Auto-launch checkout once bottom sheet is presented
    LaunchedEffect(Unit) {
        if (!hasAutoLaunched) {
            hasAutoLaunched = true
            startRazorpayCheckout()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (processingState != PaymentProcessingState.VERIFYING) onDismiss()
        },
        containerColor = RestroTokens.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = RestroTokens.muted) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .imePadding()
        ) {
            when (processingState) {
                PaymentProcessingState.VERIFYING -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = RestroTokens.coral,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(52.dp)
                        )
                        Text(
                            text = statusMessage,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = RestroTokens.text
                        )
                        Text(
                            text = "Secured by Razorpay · 256-bit SSL Encryption",
                            fontSize = 12.sp,
                            color = RestroTokens.muted
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                processingState = PaymentProcessingState.FAILED
                                verificationErrorMessage = "Payment cancelled by user."
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, RestroTokens.border)
                        ) {
                            Text("Cancel", color = RestroTokens.muted, fontSize = 13.sp)
                        }
                    }
                }

                PaymentProcessingState.AUTHORIZED -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = RestroTokens.green.copy(alpha = 0.15f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.CheckCircle, null, tint = RestroTokens.green, modifier = Modifier.size(40.dp))
                            }
                        }
                        Text(
                            "Payment Authorized ✓",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = RestroTokens.text
                        )
                        if (transactionId.isNotBlank()) {
                            Text(
                                "Razorpay ID: $transactionId",
                                fontSize = 12.sp,
                                color = RestroTokens.muted
                            )
                        }
                    }
                }

                PaymentProcessingState.FAILED, PaymentProcessingState.INPUT -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Official Razorpay Gateway Branding Header
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0C2340), // Official Razorpay dark blue branding
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Lock, null, tint = Color(0xFF3395FF), modifier = Modifier.size(16.dp))
                                    Text("Razorpay Trusted Checkout", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF3395FF).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "LIVE GATEWAY",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF52A8FF),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Order Amount Summary
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = RestroTokens.surfaceElevated,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Total Payable Amount", fontSize = 13.sp, color = RestroTokens.muted)
                                    Text(
                                        "₹${"%.2f".format(effectiveTotal / 100.0)}",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = RestroTokens.coral
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Restaurant", fontSize = 12.sp, color = RestroTokens.muted)
                                    Text(restaurantName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = RestroTokens.text)
                                }

                                if (isDineIn && effectiveDiningTab != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Table", fontSize = 12.sp, color = RestroTokens.muted)
                                        Text("Table ${effectiveDiningTab.tableNumber}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = RestroTokens.text)
                                    }
                                }
                            }
                        }

                        // Payment Methods Supported
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = RestroTokens.surfaceElevated.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, RestroTokens.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.CreditCard, null, tint = RestroTokens.coral, modifier = Modifier.size(20.dp))
                                Column {
                                    Text("Supports All Payment Modes", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RestroTokens.text)
                                    Text("Google Pay, PhonePe, Paytm, UPI, Cards, Netbanking & Wallets", fontSize = 11.sp, color = RestroTokens.muted)
                                }
                            }
                        }

                        // Error Banner if payment failed
                        if (processingState == PaymentProcessingState.FAILED && verificationErrorMessage.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = verificationErrorMessage,
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // Dine-In Disabled Handler
                        if (isDineIn && targetRestaurant?.acceptsDiningPayment == false) {
                            Button(
                                onClick = {
                                    onCounterBillRequested?.invoke()
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.surfaceElevated),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Request Counter Bill", fontWeight = FontWeight.Bold, color = RestroTokens.text)
                            }
                        } else {
                            // Primary CTA: Open Razorpay Checkout
                            Button(
                                onClick = { startRazorpayCheckout() },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (processingState == PaymentProcessingState.FAILED) "Retry with Razorpay" else "Pay ₹${"%.2f".format(effectiveTotal / 100.0)} via Razorpay",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        color = Color.White,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }

                        // Dismiss button
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel", color = RestroTokens.muted, fontSize = 13.sp)
                        }

                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
