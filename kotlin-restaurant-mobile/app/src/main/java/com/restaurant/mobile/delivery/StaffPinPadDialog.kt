package com.restaurant.mobile.delivery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Staff Quick-Access PIN Pad Vault Dialog (Prompt 3).
 * Verifies 4-digit staff PINs against SHA-256 hashes in RestroSqlDatabase:
 *  - 1234 -> WAITER (POS Cashier & Tables)
 *  - 5678 -> CHEF_KDS (Kitchen KDS & Tables)
 *  - 9999 -> ADMIN (POS, KDS, Tables & Analytics)
 */
@Composable
fun StaffPinPadDialog(
    title: String = "Staff PIN Pad Vault",
    subtitle: String = "Enter your 4-digit shift PIN to unlock POS / KDS / Admin operations",
    onVerifyPin: (String) -> UserRole?,
    onSuccess: (UserRole) -> Unit,
    onDismiss: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun handleDigit(digit: String) {
        if (enteredPin.length < 4) {
            val next = enteredPin + digit
            enteredPin = next
            errorMessage = null
            if (next.length == 4) {
                val role = onVerifyPin(next)
                if (role != null) {
                    onSuccess(role)
                } else {
                    errorMessage = "Invalid Staff PIN. Access Denied."
                    enteredPin = ""
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF181512)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFFF6B35).copy(alpha = 0.35f), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFFFF6B35),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = title,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close PIN Pad", tint = Color(0xFFA8A29E))
                    }
                }

                Text(
                    text = subtitle,
                    color = Color(0xFFA8A29E),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                // 4-Dot PIN Indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    repeat(4) { index ->
                        val filled = index < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (filled) Color(0xFFFF6B35)
                                    else Color.White.copy(alpha = 0.12f)
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = if (filled) Color(0xFFFF6B35) else Color.White.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                        )
                    }
                }

                errorMessage?.let { msg ->
                    Surface(
                        color = Color(0xFFDC2626).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFFF87171),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = msg,
                                color = Color(0xFFF87171),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 0-9 Numeric Keypad Grid
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "BACK")
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rows.forEach { rowKeys ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowKeys.forEach { key ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            when (key) {
                                                "C" -> Color(0xFFDC2626).copy(alpha = 0.18f)
                                                "BACK" -> Color.White.copy(alpha = 0.08f)
                                                else -> Color(0xFF26221E)
                                            }
                                        )
                                        .clickable {
                                            when (key) {
                                                "C" -> {
                                                    enteredPin = ""
                                                    errorMessage = null
                                                }
                                                "BACK" -> {
                                                    if (enteredPin.isNotEmpty()) {
                                                        enteredPin = enteredPin.dropLast(1)
                                                    }
                                                }
                                                else -> handleDigit(key)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (key == "BACK") {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Backspace,
                                            contentDescription = "Backspace",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Text(
                                            text = key,
                                            color = if (key == "C") Color(0xFFF87171) else Color.White,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Shift PIN Security Footer
                Surface(
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Secure Shift Authentication • Contact Manager for Assigned PIN",
                        color = Color(0xFFA8A29E),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}
