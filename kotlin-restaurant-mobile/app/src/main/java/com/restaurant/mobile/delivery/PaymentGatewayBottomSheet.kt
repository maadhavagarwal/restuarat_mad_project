package com.restaurant.mobile.delivery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.UUID

enum class PaymentProcessingState {
    INPUT,
    VERIFYING,
    AUTHORIZED
}

/**
 * Interactive Payment Gateway Bottom Sheet.
 * Features:
 * 1. UPI Apps (Google Pay, PhonePe, Paytm) + instant UPI ID validation regex.
 * 2. Card Checkout with formatted inputs, card brand detection, and Luhn algorithm validation.
 * 3. 1.5-second simulated verification animation ("Verifying with Bank..." -> "Payment Authorized ✓").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentGatewayBottomSheet(
    vm: DeliveryViewModel,
    totalAmount: Long = 0L,
    selectedAddressId: String = "",
    initialMethod: String = "UPI",
    diningTab: DiningTab? = null,
    onPaymentSuccess: ((paymentMethod: String, transactionId: String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val effectiveDiningTab = diningTab ?: if (selectedAddressId.startsWith("DINE_IN")) vm.activeTableTab else null
    val isDineIn = effectiveDiningTab != null || selectedAddressId.startsWith("DINE_IN")
    val effectiveTotal = if (isDineIn && effectiveDiningTab != null) effectiveDiningTab.total
        else if (totalAmount > 0L) totalAmount
        else (vm.quote?.total ?: 0L)

    var selectedTab by rememberSaveable {
        mutableStateOf(
            when (initialMethod.trim().lowercase()) {
                "card" -> 1
                "netbanking" -> 2
                else -> 0
            }
        )
    } // 0: UPI, 1: Card, 2: Netbanking
    var processingState by remember { mutableStateOf(PaymentProcessingState.INPUT) }
    var transactionId by remember { mutableStateOf("TXN-${UUID.randomUUID().toString().take(8).uppercase()}") }

    // UPI State
    var selectedUpiApp by rememberSaveable { mutableStateOf("Google Pay") }
    var customUpiId by rememberSaveable { mutableStateOf("") }
    val isCustomUpi = selectedUpiApp == "Custom"
    val upiRegex = remember { Regex("^[a-zA-Z0-9.\\-_]{2,49}@[a-zA-Z]{2,49}$") }
    val isUpiValid = remember(customUpiId, selectedUpiApp) {
        if (!isCustomUpi) true
        else customUpiId.isNotBlank() && upiRegex.matches(customUpiId.trim())
    }

    // Card State
    var cardNumber by rememberSaveable { mutableStateOf("") }
    var cardExpiry by rememberSaveable { mutableStateOf("") }
    var cardCvv by rememberSaveable { mutableStateOf("") }
    var cardName by rememberSaveable { mutableStateOf("") }

    val cleanCardNumber = cardNumber.filter { it.isDigit() }
    val isLuhnValid = remember(cleanCardNumber) {
        if (cleanCardNumber.length >= 13) vm.validateCardLuhn(cleanCardNumber) else false
    }
    val cardIssuer = remember(cleanCardNumber) {
        when {
            cleanCardNumber.startsWith("4") -> "Visa"
            cleanCardNumber.matches(Regex("^(5[1-5]|2[2-7]).*")) -> "Mastercard"
            cleanCardNumber.matches(Regex("^(60|65|81|82).*")) -> "RuPay"
            cleanCardNumber.matches(Regex("^(34|37).*")) -> "Amex"
            else -> "Card"
        }
    }
    val isExpiryValid = remember(cardExpiry) {
        cardExpiry.length == 5 && cardExpiry.contains("/") &&
                cardExpiry.take(2).toIntOrNull()?.let { it in 1..12 } == true
    }
    val isCvvValid = remember(cardCvv) {
        cardCvv.length in 3..4
    }
    val isCardFormValid = cleanCardNumber.length in 15..16 && isLuhnValid && isExpiryValid && isCvvValid

    // Netbanking State
    var selectedBank by rememberSaveable { mutableStateOf("HDFC Bank") }

    // 1.5-second simulated verification animation
    LaunchedEffect(processingState) {
        if (processingState == PaymentProcessingState.VERIFYING) {
            delay(1500L) // Exact 1.5s simulated bank verification animation
            processingState = PaymentProcessingState.AUTHORIZED
            delay(700L)
            val methodLabel = when (selectedTab) {
                0 -> "UPI ($selectedUpiApp)"
                1 -> "Card ($cardIssuer)"
                2 -> "Netbanking ($selectedBank)"
                else -> "UPI"
            }
            if (isDineIn) {
                effectiveDiningTab?.let { tab ->
                    vm.recordDineInPaymentAuthorized(tab, methodLabel, transactionId)
                    vm.settleDineInTab(tab, methodLabel, transactionId)
                }
                onPaymentSuccess?.invoke(methodLabel, transactionId)
            } else {
                vm.checkout(selectedAddressId, methodLabel)
                onPaymentSuccess?.invoke(methodLabel, transactionId)
            }
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (processingState == PaymentProcessingState.INPUT) onDismiss()
        },
        containerColor = RestroTokens.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = RestroTokens.muted) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .imePadding()
        ) {
            when (processingState) {
                PaymentProcessingState.INPUT -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Lock, null, tint = RestroTokens.green, modifier = Modifier.size(16.dp))
                                    Text(
                                        if (isDineIn && effectiveDiningTab != null) "Table ${effectiveDiningTab.tableNumber} Bill"
                                        else "Secure Payment Gateway",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                }
                                Text(
                                    if (isDineIn && effectiveDiningTab != null) "Dine-In Settlement · 256-bit Encrypted"
                                    else "256-bit Bank Grade Encrypted",
                                    fontSize = 11.sp,
                                    color = RestroTokens.muted
                                )
                            }
                            Text(
                                money(effectiveTotal),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = RestroTokens.coral
                            )
                        }

                        // Tab Switcher (UPI vs Card vs Netbanking)
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = Color(0xFF16191F),
                            contentColor = RestroTokens.coral,
                            divider = {}
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(16.dp))
                                        Text("UPI", fontWeight = FontWeight.Bold)
                                    }
                                }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.CreditCard, null, modifier = Modifier.size(16.dp))
                                        Text("Card", fontWeight = FontWeight.Bold)
                                    }
                                }
                            )
                            Tab(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.AccountBalance, null, modifier = Modifier.size(16.dp))
                                        Text("Netbanking", fontWeight = FontWeight.Bold)
                                    }
                                }
                            )
                        }

                        // Tab 0: UPI
                        if (selectedTab == 0) {
                            Text("Fast UPI Checkout", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

                            // Quick UPI Apps
                            val upiApps = listOf(
                                "Google Pay" to "⚡ 1-Tap GPay",
                                "PhonePe" to "📱 PhonePe Direct",
                                "Paytm" to "💰 Paytm Wallet/UPI",
                                "Custom" to "✍️ Other UPI ID"
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                upiApps.forEach { (appName, appDesc) ->
                                    val isSelected = selectedUpiApp == appName
                                    Surface(
                                        onClick = { selectedUpiApp = appName },
                                        color = if (isSelected) RestroTokens.coral.copy(alpha = 0.15f) else Color(0xFF1A1D24),
                                        shape = RoundedCornerShape(14.dp),
                                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, RestroTokens.coral) else null,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { selectedUpiApp = appName },
                                                colors = RadioButtonDefaults.colors(selectedColor = RestroTokens.coral)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(appName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text(appDesc, fontSize = 12.sp, color = RestroTokens.muted)
                                            }
                                            if (appName != "Custom") {
                                                Surface(
                                                    color = RestroTokens.green.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        "INSTANT",
                                                        fontSize = 10.sp,
                                                        color = RestroTokens.green,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Custom UPI Input Field with Real-Time Validation
                            if (isCustomUpi) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    OutlinedTextField(
                                        value = customUpiId,
                                        onValueChange = { customUpiId = it.trim().lowercase() },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Enter UPI ID (VPA)") },
                                        placeholder = { Text("e.g. mobile@upi, name@okhdfcbank") },
                                        leadingIcon = { Icon(Icons.Default.AlternateEmail, null, tint = RestroTokens.coral) },
                                        trailingIcon = {
                                            if (customUpiId.isNotBlank()) {
                                                if (isUpiValid) {
                                                    Icon(Icons.Default.CheckCircle, "Valid UPI", tint = RestroTokens.green)
                                                } else {
                                                    Icon(Icons.Default.ErrorOutline, "Invalid UPI", tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp)
                                    )

                                    if (customUpiId.isNotBlank()) {
                                        Text(
                                            text = if (isUpiValid) "✓ Valid UPI VPA address" else "Enter a valid UPI ID (e.g. user@okhdfcbank or 9876543210@paytm)",
                                            fontSize = 11.sp,
                                            color = if (isUpiValid) RestroTokens.green else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(start = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Tab 1: Card Checkout with Luhn Validation
                        if (selectedTab == 1) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // Card Number Field
                                OutlinedTextField(
                                    value = cardNumber,
                                    onValueChange = { input ->
                                        val digits = input.filter { it.isDigit() }.take(16)
                                        // Auto-format into 4-digit groups (XXXX XXXX XXXX XXXX)
                                        val formatted = digits.chunked(4).joinToString(" ")
                                        cardNumber = formatted
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Card Number") },
                                    placeholder = { Text("4111 2222 3333 4444") },
                                    leadingIcon = { Icon(Icons.Default.CreditCard, null, tint = RestroTokens.coral) },
                                    trailingIcon = {
                                        Surface(
                                            color = RestroTokens.surface,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                cardIssuer,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = RestroTokens.coral,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp)
                                )

                                // Real-Time Luhn Validation Feedback
                                if (cleanCardNumber.length >= 13) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(start = 4.dp)
                                    ) {
                                        Icon(
                                            if (isLuhnValid) Icons.Default.CheckCircle else Icons.Default.Warning,
                                            null,
                                            tint = if (isLuhnValid) RestroTokens.green else Color(0xFFEF4444),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (isLuhnValid) "Luhn Algorithm Verified: Valid Card Number" else "Invalid Card Number: Failed Luhn Checksum",
                                            fontSize = 11.sp,
                                            color = if (isLuhnValid) RestroTokens.green else Color(0xFFEF4444),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                // Expiry and CVV Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = cardExpiry,
                                        onValueChange = { input ->
                                            val digits = input.filter { it.isDigit() }.take(4)
                                            cardExpiry = if (digits.length >= 3) "${digits.take(2)}/${digits.drop(2)}" else digits
                                        },
                                        modifier = Modifier.weight(1f),
                                        label = { Text("Valid Thru") },
                                        placeholder = { Text("MM/YY") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp)
                                    )

                                    OutlinedTextField(
                                        value = cardCvv,
                                        onValueChange = { input ->
                                            cardCvv = input.filter { it.isDigit() }.take(4)
                                        },
                                        modifier = Modifier.weight(1f),
                                        label = { Text("CVV") },
                                        placeholder = { Text("•••") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                }

                                OutlinedTextField(
                                    value = cardName,
                                    onValueChange = { cardName = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Name on Card") },
                                    placeholder = { Text("Aarav Sharma") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp)
                                )
                            }
                        }

                        // Tab 2: Netbanking
                        if (selectedTab == 2) {
                            Text("Select Netbanking Bank", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

                            val banks = listOf(
                                "HDFC Bank" to "Popular · Instant authorization",
                                "ICICI Bank" to "iMobile & Retail Internet Banking",
                                "State Bank of India" to "SBI YONO & Retail Banking",
                                "Axis Bank" to "Axis NetBanking Direct",
                                "Kotak Mahindra Bank" to "Kotak 811 & Retail Banking",
                                "Punjab National Bank" to "PNB One & Retail Banking"
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                banks.forEach { (bankName, bankDesc) ->
                                    val isSelected = selectedBank == bankName
                                    Surface(
                                        onClick = { selectedBank = bankName },
                                        color = if (isSelected) RestroTokens.coral.copy(alpha = 0.15f) else Color(0xFF1A1D24),
                                        shape = RoundedCornerShape(14.dp),
                                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, RestroTokens.coral) else null,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { selectedBank = bankName },
                                                colors = RadioButtonDefaults.colors(selectedColor = RestroTokens.coral)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(bankName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text(bankDesc, fontSize = 12.sp, color = RestroTokens.muted)
                                            }
                                            if (bankName in listOf("HDFC Bank", "ICICI Bank", "State Bank of India")) {
                                                Surface(
                                                    color = RestroTokens.green.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        "INSTANT",
                                                        fontSize = 10.sp,
                                                        color = RestroTokens.green,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        // Pay Button
                        val isActionEnabled = when (selectedTab) {
                            0 -> isUpiValid
                            1 -> isCardFormValid
                            2 -> selectedBank.isNotBlank()
                            else -> false
                        }

                        Button(
                            onClick = {
                                processingState = PaymentProcessingState.VERIFYING
                            },
                            enabled = isActionEnabled && !vm.busy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Security, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Pay ${money(effectiveTotal)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        Text(
                            "By paying, you authorize Restro to initiate standard PCI-DSS bank debit protocols.",
                            fontSize = 11.sp,
                            color = RestroTokens.muted,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }

                // 1.5-Second Simulated Bank Verification Animation
                PaymentProcessingState.VERIFYING -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        CircularProgressIndicator(
                            color = RestroTokens.coral,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            if (selectedTab == 2) "Connecting to $selectedBank..."
                            else "Verifying with Bank...",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = RestroTokens.text
                        )
                        Text(
                            "Authorizing 256-bit secure gateway tokens with issuing bank...",
                            fontSize = 12.sp,
                            color = RestroTokens.muted
                        )
                        Surface(
                            color = Color(0xFF1B222E),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                "Please do not press back or close the app",
                                fontSize = 11.sp,
                                color = RestroTokens.coral,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Payment Authorized ✓ Screen
                PaymentProcessingState.AUTHORIZED -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(RestroTokens.green.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = RestroTokens.green,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        Text(
                            "Payment Authorized ✓",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = RestroTokens.green
                        )
                        Text(
                            "Reference: $transactionId",
                            fontSize = 12.sp,
                            color = RestroTokens.muted
                        )
                        Text(
                            if (isDineIn && effectiveDiningTab != null) "Payment authorized for Table ${effectiveDiningTab.tableNumber}..."
                            else "Placing your order with restaurant...",
                            fontSize = 13.sp,
                            color = RestroTokens.text
                        )
                    }
                }
            }
        }
    }
}
