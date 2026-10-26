package com.restaurant.mobile.delivery

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun RiderPortalScreenView(
    riderId: String,
    riderName: String,
    database: RestroSqlDatabase,
    cloudEngine: FirebaseCloudEngine? = null,
    deliveryVm: DeliveryViewModel? = null,
    onShowToast: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val currentLang = RestroI18n.currentLanguage // Observe language state for live recomposition

    var completedRides by remember { mutableStateOf<List<RiderRideRecord>>(emptyList()) }
    var payouts by remember { mutableStateOf<List<RiderPayoutRecord>>(emptyList()) }
    var availableOffers by remember { mutableStateOf<List<RiderRideOffer>>(emptyList()) }
    var partnerVerification by remember { mutableStateOf<PartnerVerificationRecord?>(null) }

    var payoutMethod by remember { mutableStateOf("UPI") } // "UPI" or "IMPS"
    var bankOrUpiInput by remember { mutableStateOf("ramesh.rider@okaxis") }
    var customWithdrawAmount by remember { mutableStateOf("") }
    var isSubmittingPayout by remember { mutableStateOf(false) }
    var selectedPayoutReceipt by remember { mutableStateOf<RiderPayoutReceipt?>(null) }

    fun refreshRiderData() {
        coroutineScope.launch(Dispatchers.IO) {
            val rides = database.getRiderRides(riderId)
            val payoutList = database.getRiderPayouts(riderId)
            val offers = database.getAvailableRideOffers(riderId)
            val verification = database.getPartnerVerification(riderId)
            withContext(Dispatchers.Main) {
                completedRides = rides
                payouts = payoutList
                availableOffers = offers
                partnerVerification = verification
            }
        }
    }

    LaunchedEffect(riderId, currentLang) {
        refreshRiderData()
    }

    DisposableEffect(riderId, cloudEngine) {
        val regRides = cloudEngine?.observeRiderRidesRealTime(riderId = riderId, isAdmin = false) { updatedRides ->
            completedRides = updatedRides
        }
        val regPayouts = cloudEngine?.observeRiderPayoutsRealTime(riderId = riderId, isAdmin = false) { updatedPayouts ->
            payouts = updatedPayouts
        }
        val regRejected = cloudEngine?.observeRiderRejectedOffersRealTime(riderId = riderId) {
            refreshRiderData()
        }
        onDispose {
            regRides?.remove()
            regPayouts?.remove()
            regRejected?.remove()
        }
    }

    val now = System.currentTimeMillis()
    val startOfToday = remember(now) {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val sevenDaysAgo = now - (7L * 24L * 3600_000L)
    val thirtyDaysAgo = now - (30L * 24L * 3600_000L)

    // Day-wise (Today)
    val dayRides = completedRides.filter { it.completedAtMs >= startOfToday }
    val dayIncome = dayRides.sumOf { it.incomeRupees }
    val dayCount = dayRides.size

    // Week-wise (Last 7 Days)
    val weekRides = completedRides.filter { it.completedAtMs >= sevenDaysAgo }
    val weekIncome = weekRides.sumOf { it.incomeRupees }
    val weekCount = weekRides.size

    // Month-wise (Last 30 Days)
    val monthRides = completedRides.filter { it.completedAtMs >= thirtyDaysAgo }
    val monthIncome = monthRides.sumOf { it.incomeRupees }
    val monthCount = monthRides.size

    // All-time & Average per Ride
    val totalIncome = completedRides.sumOf { it.incomeRupees }
    val totalRidesCount = completedRides.size
    val avgIncomePerRide = if (totalRidesCount > 0) (totalIncome / totalRidesCount) else 0L

    // Net Withdrawable Balance (only active/settled payouts deducted; failed/reversed restored)
    val totalWithdrawn = payouts
        .filter { it.status == "PROCESSED" || it.status == "PROCESSING" || it.status == "PENDING" }
        .sumOf { it.amountRupees }
    val availableBalance = (totalIncome - totalWithdrawn).coerceAtLeast(0L)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Rider Header & Verified Licence Badge
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFC2410C)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.DeliveryDining, null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            Column {
                                Text(
                                    text = "$riderName · ${RestroI18n.t("rider_portal_title")}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Vehicle: ${partnerVerification?.vehicleNumber?.ifBlank { "MH-02-EM-8821" } ?: "MH-02-EM-8821"} · Active On-Duty",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = RestroI18n.t("doc_verified_badge"),
                                color = Color(0xFF34D399),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (!partnerVerification?.extractedSummary.isNullOrBlank()) {
                        Text(
                            text = partnerVerification!!.extractedSummary,
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // 2. Earnings & Ride Counts Grid (Day-wise, Week-wise, Month-wise, Average Income per Ride)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RiderStatMetricCard(
                        title = RestroI18n.t("rider_income_day"),
                        incomeRupees = dayIncome,
                        subtitle = "$dayCount ${RestroI18n.t("rider_rides_done")}",
                        accentColor = Color(0xFF059669),
                        modifier = Modifier.weight(1f)
                    )
                    RiderStatMetricCard(
                        title = RestroI18n.t("rider_income_week"),
                        incomeRupees = weekIncome,
                        subtitle = "$weekCount ${RestroI18n.t("rider_rides_done")}",
                        accentColor = Color(0xFF2563EB),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RiderStatMetricCard(
                        title = RestroI18n.t("rider_income_month"),
                        incomeRupees = monthIncome,
                        subtitle = "$monthCount ${RestroI18n.t("rider_rides_done")}",
                        accentColor = Color(0xFF7C3AED),
                        modifier = Modifier.weight(1f)
                    )
                    RiderStatMetricCard(
                        title = RestroI18n.t("rider_avg_per_ride"),
                        incomeRupees = avgIncomePerRide,
                        subtitle = "Across $totalRidesCount total rides",
                        accentColor = Color(0xFFC2410C),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. Instant Payout / Take Money in Account Whenever He Wants
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = RestroI18n.t("rider_available_balance"),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF475569)
                            )
                            Text(
                                text = "₹$availableBalance",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF059669)
                            )
                        }
                        Surface(
                            color = Color(0xFFDCFCE7),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "RazorpayX 24x7 Payouts",
                                color = Color(0xFF166534),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Payout Method Selector: UPI vs IMPS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = payoutMethod == "UPI",
                            onClick = {
                                payoutMethod = "UPI"
                                if (bankOrUpiInput.contains("••") || bankOrUpiInput.all { it.isDigit() }) {
                                    bankOrUpiInput = "ramesh.rider@okaxis"
                                }
                            },
                            label = { Text("UPI (VPA)") },
                            leadingIcon = {
                                if (payoutMethod == "UPI") Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = payoutMethod == "IMPS",
                            onClick = {
                                payoutMethod = "IMPS"
                                if (bankOrUpiInput.contains("@")) {
                                    bankOrUpiInput = "50100429381948"
                                }
                            },
                            label = { Text("IMPS (Bank)") },
                            leadingIcon = {
                                if (payoutMethod == "IMPS") Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = bankOrUpiInput,
                            onValueChange = { bankOrUpiInput = it },
                            label = { Text(if (payoutMethod == "UPI") "UPI ID (VPA)" else "Bank Account Number") },
                            placeholder = { Text(if (payoutMethod == "UPI") "e.g. name@okaxis" else "e.g. 50100234567890") },
                            singleLine = true,
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = customWithdrawAmount,
                            onValueChange = { customWithdrawAmount = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Amount (₹)") },
                            placeholder = { Text("$availableBalance") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.9f),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Button(
                        onClick = {
                            if (isSubmittingPayout) return@Button
                            val amountToWithdraw = customWithdrawAmount.toLongOrNull() ?: availableBalance
                            val amountValidation = RiderPayoutRules.validatePayoutAmount(amountToWithdraw)
                            if (!amountValidation.isValid) {
                                onShowToast(amountValidation.errorMessage ?: "Enter a valid amount")
                                return@Button
                            }
                            if (amountToWithdraw > availableBalance) {
                                onShowToast("Amount cannot exceed available balance of ₹$availableBalance")
                                return@Button
                            }
                            if (payoutMethod == "UPI") {
                                val vpaValidation = RiderPayoutRules.validateVpa(bankOrUpiInput)
                                if (!vpaValidation.isValid) {
                                    onShowToast(vpaValidation.errorMessage ?: "Invalid UPI ID format")
                                    return@Button
                                }
                            } else {
                                if (bankOrUpiInput.trim().length < 9) {
                                    onShowToast("Enter a valid bank account number (at least 9 digits)")
                                    return@Button
                                }
                            }

                            isSubmittingPayout = true
                            val idempotencyKey = "idemp_${riderId}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"

                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    if (deliveryVm != null) {
                                        val result = deliveryVm.requestRiderPayout(
                                            riderId = riderId,
                                            amountRupees = amountToWithdraw,
                                            destination = bankOrUpiInput,
                                            method = payoutMethod,
                                            idempotencyKey = idempotencyKey
                                        )
                                        result.onSuccess { payoutRecord ->
                                            withContext(Dispatchers.Main) {
                                                customWithdrawAmount = ""
                                                isSubmittingPayout = false
                                                refreshRiderData()
                                                onShowToast("₹${payoutRecord.amountRupees} payout requested (${payoutRecord.status})")
                                            }
                                        }.onFailure { err ->
                                            withContext(Dispatchers.Main) {
                                                isSubmittingPayout = false
                                                onShowToast(err.message ?: "Payout failed. Please retry.")
                                            }
                                        }
                                    } else {
                                        val payoutId = "pout_${System.currentTimeMillis()}"
                                        val refId = "rpz_pout_${UUID.randomUUID().toString().replace("-", "").take(12)}"
                                        val utr = "UTR${System.currentTimeMillis().toString().takeLast(10)}99"
                                        val receiptNo = "REC-POUT-${System.currentTimeMillis().toString().takeLast(6)}"
                                        val payoutRecord = RiderPayoutRecord(
                                            id = payoutId,
                                            riderId = riderId,
                                            amountRupees = amountToWithdraw,
                                            accountDestination = RiderPayoutRules.maskDestination(bankOrUpiInput),
                                            payoutMethod = payoutMethod,
                                            status = "PROCESSED",
                                            referenceId = refId,
                                            utr = utr,
                                            failureReason = "",
                                            idempotencyKey = idempotencyKey,
                                            receiptNumber = receiptNo,
                                            timestampMs = System.currentTimeMillis()
                                        )
                                        database.recordRiderPayout(payoutRecord)
                                        cloudEngine?.saveRiderPayoutToCloud(payoutRecord)
                                        withContext(Dispatchers.Main) {
                                            customWithdrawAmount = ""
                                            isSubmittingPayout = false
                                            refreshRiderData()
                                            onShowToast("₹$amountToWithdraw payout processed successfully!")
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isSubmittingPayout = false
                                        onShowToast(e.message ?: "Payout request failed")
                                    }
                                }
                            }
                        },
                        enabled = availableBalance >= 10L && !isSubmittingPayout,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSubmittingPayout) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Processing RazorpayX Payout...", fontWeight = FontWeight.Bold, color = Color.White)
                        } else {
                            Icon(Icons.Default.AccountBalanceWallet, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            val targetAmt = customWithdrawAmount.toLongOrNull() ?: availableBalance
                            Text(
                                text = "${RestroI18n.t("rider_withdraw_btn")} (₹$targetAmt)",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    if (payouts.isNotEmpty()) {
                        HorizontalDivider(color = Color(0xFFE2E8F0))
                        Text(
                            text = "Recent RazorpayX Withdrawals (${payouts.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )
                        payouts.take(5).forEach { p ->
                            val dateStr = remember(p.timestampMs) {
                                SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(Date(p.timestampMs))
                            }
                            val (badgeBg, badgeText, statusLabel) = when (p.status.uppercase()) {
                                "PROCESSED" -> Triple(Color(0xFFDCFCE7), Color(0xFF166534), "Processed")
                                "PROCESSING" -> Triple(Color(0xFFDBEAFE), Color(0xFF1E40AF), "Processing")
                                "PENDING" -> Triple(Color(0xFFFEF3C7), Color(0xFF92400E), "Pending")
                                "FAILED" -> Triple(Color(0xFFFEE2E2), Color(0xFF991B1B), "Failed")
                                "REVERSED" -> Triple(Color(0xFFF3E8FF), Color(0xFF6B21A8), "Reversed")
                                else -> Triple(Color(0xFFF1F5F9), Color(0xFF475569), p.status)
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPayoutReceipt = RiderPayoutReceipt(
                                            receiptNumber = p.receiptNumber.ifBlank { "REC-${p.id}" },
                                            payoutId = p.id,
                                            riderId = p.riderId,
                                            amountRupees = p.amountRupees,
                                            payoutMethod = p.payoutMethod,
                                            destinationMasked = p.accountDestination,
                                            status = p.status,
                                            referenceId = p.referenceId,
                                            utr = p.utr,
                                            timestampMs = p.timestampMs,
                                            receiptText = RiderPayoutRules.generateReceiptText(p)
                                        )
                                    },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "-₹${p.amountRupees}",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (p.status == "FAILED" || p.status == "REVERSED") Color(0xFF991B1B) else Color(0xFF059669)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                color = Color(0xFFE2E8F0),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = p.payoutMethod.ifBlank { "UPI" },
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF334155),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Surface(
                                            color = badgeBg,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = statusLabel,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = badgeText,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${p.accountDestination} · $dateStr",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                    if (p.referenceId.isNotBlank() || p.utr.isNotBlank()) {
                                        Text(
                                            text = buildString {
                                                if (p.referenceId.isNotBlank()) append("Ref: ${p.referenceId} ")
                                                if (p.utr.isNotBlank()) append("· UTR: ${p.utr}")
                                            },
                                            fontSize = 10.sp,
                                            color = Color(0xFF475569)
                                        )
                                    }
                                    if (p.failureReason.isNotBlank()) {
                                        Text(
                                            text = "Reason: ${p.failureReason}",
                                            fontSize = 10.sp,
                                            color = Color(0xFFDC2626)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Choose Ride Offers Feed (Shows Ride km, Estimated Time, Income; Reject hides offer permanently)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${RestroI18n.t("rider_choose_ride")} (${availableOffers.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "Rejected offers never reappear",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }
        }

        if (availableOffers.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.CheckCircleOutline, null, tint = Color(0xFF059669), modifier = Modifier.size(32.dp))
                        Text(
                            text = RestroI18n.t("rider_no_offers"),
                            fontSize = 13.sp,
                            color = Color(0xFF475569)
                        )
                    }
                }
            }
        } else {
            items(availableOffers, key = { it.id }) { offer ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = offer.restaurantName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "Pickup: ${offer.pickupAddress} → Drop: ${offer.dropAddress}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF475569)
                                )
                            }
                            Surface(
                                color = Color(0xFFFEF3C7),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "₹${offer.riderIncomeRupees}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    color = Color(0xFFB45309),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        // Ride Distance (km), Estimated Time (min), and Income chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = Color(0xFFF1F5F9),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Route, null, tint = Color(0xFF0F172A), modifier = Modifier.size(14.dp))
                                    Text(
                                        text = "${offer.distanceKm} km",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                }
                            }
                            Surface(
                                color = Color(0xFFF1F5F9),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Schedule, null, tint = Color(0xFF0F172A), modifier = Modifier.size(14.dp))
                                    Text(
                                        text = "${offer.estimatedMinutes} mins",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                }
                            }
                            Surface(
                                color = Color(0xFFDCFCE7),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Payments, null, tint = Color(0xFF166534), modifier = Modifier.size(14.dp))
                                    Text(
                                        text = "Earn ₹${offer.riderIncomeRupees}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF166534)
                                    )
                                }
                            }
                        }

                        // Accept vs Reject Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        database.rejectRideOffer(riderId, offer.id)
                                        cloudEngine?.saveRiderRejectedOfferToCloud(riderId, offer.id)
                                        withContext(Dispatchers.Main) {
                                            refreshRiderData()
                                            onShowToast("Offer ${offer.id} rejected. You will not see this offer again.")
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFFEF4444))
                            ) {
                                Icon(Icons.Default.Close, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = RestroI18n.t("rider_reject_ride"),
                                    color = Color(0xFFEF4444),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Button(
                                onClick = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        database.markRideOfferCompleted(riderId, offer)
                                        withContext(Dispatchers.Main) {
                                            refreshRiderData()
                                            onShowToast("Ride completed! +₹${offer.riderIncomeRupees} added to your wallet.")
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1.4f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC2410C)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = RestroI18n.t("rider_accept_ride"),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedPayoutReceipt?.let { receipt ->
        val context = LocalContext.current
        val clipboardManager = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { selectedPayoutReceipt = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Color(0xFF0F172A))
                    Spacer(Modifier.width(8.dp))
                    Text("RazorpayX Receipt", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Receipt: ${receipt.receiptNumber}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Payout ID: ${receipt.payoutId}", fontSize = 12.sp, color = Color(0xFF475569))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Amount", fontSize = 13.sp, color = Color(0xFF64748B))
                        Text("₹${receipt.amountRupees}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF059669))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Status", fontSize = 13.sp, color = Color(0xFF64748B))
                        Text(receipt.status, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Method", fontSize = 13.sp, color = Color(0xFF64748B))
                        Text(receipt.payoutMethod, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Destination", fontSize = 13.sp, color = Color(0xFF64748B))
                        Text(receipt.destinationMasked, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }

                    if (receipt.referenceId.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Reference ID", fontSize = 12.sp, color = Color(0xFF64748B))
                            Text(receipt.referenceId, fontSize = 12.sp, color = Color(0xFF334155))
                        }
                    }

                    if (receipt.utr.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Bank UTR", fontSize = 12.sp, color = Color(0xFF64748B))
                            Text(receipt.utr, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))
                        }
                    }

                    val dateFormatted = remember(receipt.timestampMs) {
                        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(receipt.timestampMs))
                    }
                    Text("Date: $dateFormatted", fontSize = 11.sp, color = Color(0xFF94A3B8))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, receipt.receiptText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share RazorpayX Payout Receipt"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share")
                }
            },
            dismissButton = {
                Row {
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(receipt.receiptText))
                            onShowToast("Receipt copied to clipboard")
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy")
                    }
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = { selectedPayoutReceipt = null }) {
                        Text("Close")
                    }
                }
            }
        )
    }
}

@Composable
private fun RiderStatMetricCard(
    title: String,
    incomeRupees: Long,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF64748B)
            )
            Text(
                text = "₹$incomeRupees",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF334155)
            )
        }
    }
}
