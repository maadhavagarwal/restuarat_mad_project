package com.restaurant.mobile.delivery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Walk-In Pay Bill Bottom Sheet (Prompt 11).
 *
 * Allows a walk-in dine-in customer to identify their table so the app
 * can look up the active tab and present the bill for payment.
 *
 * Features:
 * 1. Grid of preset tables T1 – T12.
 * 2. Manual table-number text input for tables beyond the grid.
 * 3. Selection state tracking for the chosen table.
 * 4. Input validation — table number must be non-blank and alphanumeric.
 * 5. "Look Up Table Bill" CTA that invokes [onTableSelected] with the
 *    validated table number.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkInPayBillSheet(
    onTableSelected: (tableNumber: String) -> Unit,
    onDismiss: () -> Unit
) {
    // Preset table labels
    val presetTables = (1..12).map { "T$it" }

    // Selection state: either a preset or manually entered
    var selectedPreset by rememberSaveable { mutableStateOf<String?>(null) }
    var manualInput by rememberSaveable { mutableStateOf("") }
    var useManualInput by rememberSaveable { mutableStateOf(false) }

    // Derived: the effective table number the user has chosen
    val effectiveTable = if (useManualInput) manualInput.trim() else selectedPreset

    // Validation for manual input: non-blank, alphanumeric (letters, digits, hyphens allowed)
    val manualInputValid = remember(manualInput) {
        val trimmed = manualInput.trim()
        trimmed.isNotBlank() && trimmed.length <= 10 &&
                trimmed.all { it.isLetterOrDigit() || it == '-' }
    }

    // Overall form validity
    val isValid = if (useManualInput) manualInputValid else selectedPreset != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = RestroTokens.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = RestroTokens.muted) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
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
                        Icon(
                            Icons.Default.TableBar,
                            contentDescription = null,
                            tint = RestroTokens.coral,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "Pay Your Table Bill",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }
                    Text(
                        "Select your table to view the bill",
                        fontSize = 12.sp,
                        color = RestroTokens.muted
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Dismiss")
                }
            }

            // Table Grid — 4 columns × 3 rows
            Text(
                "Select your table",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = RestroTokens.text
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                presetTables.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { table ->
                            val isSelected = !useManualInput && selectedPreset == table
                            Surface(
                                onClick = {
                                    selectedPreset = table
                                    useManualInput = false
                                },
                                color = if (isSelected) RestroTokens.coral.copy(alpha = 0.15f)
                                else Color(0xFF161920),
                                shape = RoundedCornerShape(12.dp),
                                border = if (isSelected) BorderStroke(1.5.dp, RestroTokens.coral)
                                else BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            table,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = if (isSelected) RestroTokens.coral
                                            else RestroTokens.text
                                        )
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = RestroTokens.coral,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Divider with "or"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color.White.copy(alpha = 0.08f)
                )
                Text("or", fontSize = 12.sp, color = RestroTokens.muted)
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color.White.copy(alpha = 0.08f)
                )
            }

            // Manual input
            Text(
                "Enter table number manually",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = RestroTokens.text
            )

            OutlinedTextField(
                value = manualInput,
                onValueChange = {
                    manualInput = it
                    if (it.isNotBlank()) {
                        useManualInput = true
                        selectedPreset = null
                    }
                },
                placeholder = { Text("e.g. T15, VIP-1, Patio-3") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        tint = if (useManualInput) RestroTokens.coral else RestroTokens.muted,
                        modifier = Modifier.size(18.dp)
                    )
                },
                isError = useManualInput && manualInput.isNotBlank() && !manualInputValid,
                supportingText = {
                    if (useManualInput && manualInput.isNotBlank() && !manualInputValid) {
                        Text(
                            "Table number must be 1–10 alphanumeric characters",
                            color = Color(0xFFEF4444),
                            fontSize = 11.sp
                        )
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RestroTokens.coral,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    cursorColor = RestroTokens.coral
                )
            )

            // Selected table summary
            if (isValid && effectiveTable != null) {
                Surface(
                    color = RestroTokens.coral.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(RestroTokens.coral.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.TableBar,
                                contentDescription = null,
                                tint = RestroTokens.coral,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Table $effectiveTable",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = RestroTokens.text
                            )
                            Text(
                                "We'll look up the active bill for this table",
                                fontSize = 11.sp,
                                color = RestroTokens.muted
                            )
                        }
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = RestroTokens.coral,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // CTA Button
            Button(
                onClick = {
                    effectiveTable?.let { onTableSelected(it) }
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RestroTokens.coral,
                    disabledContainerColor = RestroTokens.coral.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    Icons.Default.Receipt,
                    contentDescription = null,
                    tint = RestroTokens.canvas,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Look Up Table Bill",
                    color = RestroTokens.canvas,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
