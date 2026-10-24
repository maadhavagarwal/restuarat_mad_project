package com.restaurant.mobile.delivery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.UUID

/**
 * Production Administrator Menu Management Interface.
 * Allows authorized administrators to:
 * 1. Inspect the real-time menu from Cloud Firestore.
 * 2. Add new dishes with price and dietary metadata.
 * 3. Edit existing dishes and prices.
 * 4. Instantly enable or disable (mark sold-out) dishes.
 * 5. Delete dishes with explicit confirmation and safety warnings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMenuManagementDialog(
    restaurantId: String,
    restaurantName: String,
    dishes: List<Dish>,
    onAddDish: (Dish) -> Unit,
    onUpdateDish: (Dish) -> Unit,
    onToggleAvailability: (Dish, Boolean) -> Unit,
    onDeleteDish: (Dish) -> Unit,
    onDismiss: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingDish by remember { mutableStateOf<Dish?>(null) }
    var dishToDelete by remember { mutableStateOf<Dish?>(null) }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }

    val categories = remember(dishes) {
        listOf("All") + dishes.map { it.category }.distinct()
    }
    val filteredDishes = remember(dishes, selectedCategoryFilter) {
        if (selectedCategoryFilter == null || selectedCategoryFilter == "All") dishes
        else dishes.filter { it.category == selectedCategoryFilter }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E2128),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Menu Management",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                color = RestroTokens.coral.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "FIRESTORE LIVE",
                                    color = RestroTokens.coral,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            "$restaurantName • ${dishes.size} items",
                            color = RestroTokens.muted,
                            fontSize = 13.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Actions Bar: Category Filter & Add Dish Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Category Chips (Horizontal scroll)
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories) { cat ->
                            val isSelected = (selectedCategoryFilter == null && cat == "All") || selectedCategoryFilter == cat
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) RestroTokens.coral else Color(0xFF2A2E37),
                                modifier = Modifier.clickable {
                                    selectedCategoryFilter = if (cat == "All") null else cat
                                }
                            ) {
                                Text(
                                    cat,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Dish", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Dishes List
                if (filteredDishes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No dishes found in this category.", color = RestroTokens.muted)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredDishes, key = { it.id }) { dish ->
                            AdminDishRow(
                                dish = dish,
                                onToggleAvailability = { onToggleAvailability(dish, !dish.available) },
                                onEdit = { editingDish = dish },
                                onDelete = { dishToDelete = dish }
                            )
                        }
                    }
                }
            }
        }
    }

    // Sub-Dialogs
    if (showAddDialog) {
        DishEditDialog(
            title = "Add New Dish to Firestore",
            initialDish = Dish(
                id = "dish-" + UUID.randomUUID().toString().take(8),
                restaurantId = restaurantId,
                category = selectedCategoryFilter ?: "Main Course",
                name = "",
                description = "",
                price = 25000L, // ₹250
                image = "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=800&auto=format&fit=crop",
                vegetarian = true,
                available = true
            ),
            onSave = { newDish ->
                onAddDish(newDish)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    editingDish?.let { targetDish ->
        DishEditDialog(
            title = "Edit Dish & Pricing",
            initialDish = targetDish,
            onSave = { updatedDish ->
                onUpdateDish(updatedDish)
                editingDish = null
            },
            onDismiss = { editingDish = null }
        )
    }

    dishToDelete?.let { targetDish ->
        AlertDialog(
            onDismissRequest = { dishToDelete = null },
            title = { Text("Delete Dish?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to delete '${targetDish.name}'? Existing past orders and historical invoices referencing this dish will be preserved in their order snapshots, but it will be removed permanently from the active menu.",
                    color = RestroTokens.muted,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteDish(targetDish)
                        dishToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Delete Dish", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { dishToDelete = null }) {
                    Text("Cancel", color = RestroTokens.muted)
                }
            },
            containerColor = Color(0xFF252932)
        )
    }
}

@Composable
private fun AdminDishRow(
    dish: Dish,
    onToggleAvailability: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF262A34),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (dish.available) Color.White.copy(alpha = 0.08f) else Color(0xFFDC2626).copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (dish.vegetarian) "● VEG" else "▲ NON-VEG",
                        color = if (dish.vegetarian) RestroTokens.green else Color(0xFFEF4444),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        dish.category,
                        color = RestroTokens.muted,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    dish.name,
                    color = if (dish.available) Color.White else Color.White.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    money(dish.price),
                    color = RestroTokens.coral,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Availability Toggle Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (dish.available) Color(0xFF16A34A).copy(alpha = 0.2f) else Color(0xFFDC2626).copy(alpha = 0.2f),
                    modifier = Modifier.clickable { onToggleAvailability() }
                ) {
                    Text(
                        if (dish.available) "AVAILABLE" else "DISABLED",
                        color = if (dish.available) Color(0xFF22C55E) else Color(0xFFEF4444),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }

                // Edit Button
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Dish", tint = Color.White, modifier = Modifier.size(16.dp))
                }

                // Delete Button
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Dish", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun DishEditDialog(
    title: String,
    initialDish: Dish,
    onSave: (Dish) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialDish.name) }
    var category by remember { mutableStateOf(initialDish.category) }
    var description by remember { mutableStateOf(initialDish.description) }
    // Price in Rupees for input (converted to paise on save)
    var priceRupees by remember { mutableStateOf((initialDish.price / 100).toString()) }
    var vegetarian by remember { mutableStateOf(initialDish.vegetarian) }
    var available by remember { mutableStateOf(initialDish.available) }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Dish Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (e.g. Biryani, Starters, Pizza)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = priceRupees,
                    onValueChange = { priceRupees = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Price in Rupees (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Vegetarian Dish", color = Color.White, fontSize = 14.sp)
                    Switch(checked = vegetarian, onCheckedChange = { vegetarian = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Available to Order", color = Color.White, fontSize = 14.sp)
                    Switch(checked = available, onCheckedChange = { available = it })
                }

                validationError?.let {
                    Text(it, color = Color(0xFFEF4444), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        validationError = "Dish name cannot be empty"
                        return@Button
                    }
                    val priceVal = priceRupees.toLongOrNull()
                    if (priceVal == null || priceVal <= 0) {
                        validationError = "Please enter a valid price in rupees"
                        return@Button
                    }
                    val updated = initialDish.copy(
                        name = name.trim(),
                        category = category.trim().ifBlank { "Main Course" },
                        description = description.trim(),
                        price = priceVal * 100L, // Stored authoritatively in paise
                        vegetarian = vegetarian,
                        available = available
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral)
            ) {
                Text("Save to Cloud", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = RestroTokens.muted)
            }
        },
        containerColor = Color(0xFF252932)
    )
}
