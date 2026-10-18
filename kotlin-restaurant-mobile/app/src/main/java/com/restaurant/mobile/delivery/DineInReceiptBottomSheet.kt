package com.restaurant.mobile.delivery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Digital GST Receipt Bottom Sheet (Prompt 12).
 *
 * Displays a digital GST tax receipt after successful dine-in settlement, containing:
 * - Table number
 * - Itemized dishes and quantities
 * - Subtotal
 * - 5% GST
 * - Applicable service charge (if present)
 * - Total paid
 * - Payment method
 * - Transaction ID (where available)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DineInReceiptBottomSheet(
    receipt: DineInPaymentResult,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = RestroTokens.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = RestroTokens.muted) },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        DineInReceiptContent(
            receipt = receipt,
            onDismiss = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        )
    }
}

/**
 * Reusable Digital GST Receipt content card.
 */
@Composable
fun DineInReceiptContent(
    receipt: DineInPaymentResult,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val tab = receipt.tab

    Column(
        modifier = modifier.verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Success Icon & Header
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(Color(0xFF22C55E).copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Payment Verified",
                tint = Color(0xFF22C55E),
                modifier = Modifier.size(40.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Bill Settled Successfully!",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = RestroTokens.text,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Digital GST Tax Invoice",
                fontSize = 13.sp,
                color = RestroTokens.muted,
                textAlign = TextAlign.Center
            )
        }

        // Receipt Card Container
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = RestroTokens.canvas),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(18.dp)
                )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Table Number & Invoice Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = RestroTokens.coral,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "TABLE ${tab.tableNumber}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = RestroTokens.coral
                        )
                    }

                    Surface(
                        color = Color(0xFF22C55E).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "PAID",
                            color = Color(0xFF22C55E),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // Payment Details (Method & Transaction ID)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Payment Method", fontSize = 12.sp, color = RestroTokens.muted)
                    Text(receipt.paymentMethod, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = RestroTokens.text)
                }

                if (receipt.transactionId.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Transaction ID", fontSize = 12.sp, color = RestroTokens.muted)
                        Text(
                            receipt.transactionId,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = RestroTokens.text
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // Itemized Dishes & Quantities
                Text(
                    text = "Itemized Order (${tab.items.size} item${if (tab.items.size != 1) "s" else ""})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = RestroTokens.muted
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tab.items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "x${item.quantity}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RestroTokens.coral
                                )
                                Text(
                                    text = item.name,
                                    fontSize = 13.sp,
                                    color = RestroTokens.text,
                                    maxLines = 1
                                )
                            }
                            Text(
                                text = money(item.total),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = RestroTokens.text
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // Financial Breakdown (Subtotal, 5% GST, Service Charge, Total Paid)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Subtotal", fontSize = 13.sp, color = RestroTokens.muted)
                    Text(money(tab.subtotal), fontSize = 13.sp, color = RestroTokens.text)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("5% GST", fontSize = 13.sp, color = RestroTokens.muted)
                    Text(money(tab.tax), fontSize = 13.sp, color = RestroTokens.text)
                }

                if (tab.serviceCharge > 0L) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Service Charge", fontSize = 13.sp, color = RestroTokens.muted)
                        Text(money(tab.serviceCharge), fontSize = 13.sp, color = RestroTokens.text)
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total Paid",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = RestroTokens.text
                    )
                    Text(
                        text = money(tab.total),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF22C55E)
                    )
                }
            }
        }

        // Thank You Note
        Text(
            text = "Thank you for dining with us! A verified digital GST receipt has been recorded.",
            fontSize = 12.sp,
            color = RestroTokens.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Close CTA Button
        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(
                text = "Done",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = RestroTokens.canvas
            )
        }
    }
}
