package com.restaurant.mobile.delivery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Dedicated Dine-In Discovery & Restaurant Explorer Screen.
 * Allows customers to browse restaurants tailored for dining out, filter by party size /
 * number of guests, ambiance/vibe, and cuisine requirements, and book tables with instant confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DineInScreen(
    vm: DeliveryViewModel,
    onSelectRestaurantToBook: (Restaurant, Int) -> Unit,
    onOpenMenu: (String) -> Unit,
    onOpenLocationPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPartySize by rememberSaveable { mutableStateOf<Int?>(2) }
    var selectedVibe by rememberSaveable { mutableStateOf("All Vibes") }
    var selectedCuisine by rememberSaveable { mutableStateOf("All Cuisines") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showWalkInSheet by rememberSaveable { mutableStateOf(false) }
    var showTabPaymentSheet by rememberSaveable { mutableStateOf(false) }

    val partySizeOptions = listOf(
        Pair(null, "Any Size"),
        Pair(1, "1 Guest"),
        Pair(2, "2 Guests"),
        Pair(3, "3 Guests"),
        Pair(4, "4 Guests"),
        Pair(6, "6 Guests"),
        Pair(8, "8 Guests"),
        Pair(10, "10 Guests"),
        Pair(12, "12 Guests"),
        Pair(16, "16+ Guests")
    )

    val vibeOptions = listOf(
        "All Vibes",
        "Fine Dining & Luxury",
        "Rooftop & City View",
        "Outdoor Garden Seating",
        "Romantic Candlelight",
        "Family & Kids Friendly",
        "Casual Bistro & Cafe",
        "Live Music & Bar"
    )

    val cuisineOptions = listOf(
        "All Cuisines",
        "Biryani",
        "Italian",
        "South Indian",
        "Pan-Asian",
        "Healthy"
    )

    val restaurants = remember(selectedPartySize, selectedVibe, selectedCuisine, searchQuery, vm.catalog.restaurants) {
        vm.getDineInRestaurants(
            partySizeFilter = selectedPartySize,
            vibeFilter = selectedVibe,
            cuisineFilter = selectedCuisine,
            searchQuery = searchQuery
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
    ) {
        // 1. Header with Active GPS Location Pill
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            RestroI18n.t("dine_in_title"),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = RestroTokens.text
                        )
                        Text(
                            RestroI18n.t("dine_in_subtitle"),
                            fontSize = 13.sp,
                            color = RestroTokens.muted
                        )
                    }
                    // Locality Chip
                    Surface(
                        onClick = onOpenLocationPicker,
                        color = RestroTokens.surface,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, RestroTokens.coral.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.MyLocation, null, tint = RestroTokens.coral, modifier = Modifier.size(14.dp))
                            Text(
                                vm.currentLandmark.split(",").firstOrNull()?.trim() ?: "Current Location",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = RestroTokens.text
                            )
                            Icon(Icons.Default.KeyboardArrowDown, null, tint = RestroTokens.muted, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        // 2. Active Dining Session or Walk-In Pay Table Bill Card
        item {
            if (vm.activeTableTab != null) {
                val activeRestAcceptsPay = vm.catalog.restaurants.find { it.id == vm.restaurantId }?.acceptsDiningPayment ?: true
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, RestroTokens.green)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(RestroTokens.green.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.TableRestaurant, null, tint = RestroTokens.green, modifier = Modifier.size(22.dp))
                            }
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Seated at Table ${vm.activeTableTab?.tableNumber}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Surface(color = RestroTokens.green, shape = RoundedCornerShape(6.dp)) {
                                        Text("DINING ACTIVE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                                Text(
                                    if (activeRestAcceptsPay) "Tab: ${money(vm.activeTableTab?.total ?: 0L)} · ${vm.activeTableTab?.items?.size ?: 0} items"
                                    else "Tab: ${money(vm.activeTableTab?.total ?: 0L)} · ${RestroI18n.t("counter_pay_only")}",
                                    fontSize = 12.sp,
                                    color = RestroTokens.muted
                                )
                            }
                        }
                        if (activeRestAcceptsPay) {
                            Button(
                                onClick = { showTabPaymentSheet = true },
                                colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Pay Bill", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        } else {
                            Surface(
                                color = RestroTokens.coral.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, RestroTokens.coral.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    RestroI18n.t("counter_pay_only"),
                                    color = RestroTokens.coral,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    onClick = { showWalkInSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    color = RestroTokens.surface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(RestroTokens.coral.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ReceiptLong, null, tint = RestroTokens.coral, modifier = Modifier.size(18.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Dining in right now?", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Select your table to view rounds & pay bill directly", fontSize = 11.sp, color = RestroTokens.muted)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = RestroTokens.muted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // 3. Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by restaurant name, cuisine or vibe...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = RestroTokens.muted) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, null, tint = RestroTokens.muted)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RestroTokens.coral,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedContainerColor = RestroTokens.surface,
                    unfocusedContainerColor = RestroTokens.surface
                )
            )
        }

        // 4. Requirement 1: Party Size / Number of Guests
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Groups, null, tint = RestroTokens.coral, modifier = Modifier.size(18.dp))
                        Text(
                            "Party Size / Dining Guests",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = RestroTokens.text
                        )
                    }
                    if (selectedPartySize != null) {
                        Text(
                            "${selectedPartySize} Guests Selected",
                            fontSize = 11.sp,
                            color = RestroTokens.coral,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(partySizeOptions) { (size, label) ->
                        val isSelected = selectedPartySize == size
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedPartySize = size },
                            label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (size == null) Icons.Default.AllInclusive else Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RestroTokens.coral,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        // 5. Requirement 2: Dining Vibe & Occasion
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Celebration, null, tint = RestroTokens.coral, modifier = Modifier.size(18.dp))
                    Text(
                        "Dining Requirements & Vibe",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = RestroTokens.text
                    )
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vibeOptions) { vibe ->
                        val isSelected = selectedVibe == vibe
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedVibe = vibe },
                            label = { Text(vibe, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RestroTokens.coral.copy(alpha = 0.9f),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        // 6. Requirement 3: Cuisine Filters
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.RestaurantMenu, null, tint = RestroTokens.coral, modifier = Modifier.size(18.dp))
                    Text(
                        "Cuisine",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = RestroTokens.text
                    )
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(cuisineOptions) { cuisine ->
                        val isSelected = selectedCuisine == cuisine
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCuisine = cuisine },
                            label = { Text(cuisine, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF3B82F6),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        // Results Count Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Restaurants (${restaurants.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = RestroTokens.muted
                )
                if (selectedPartySize != null || selectedVibe != "All Vibes" || selectedCuisine != "All Cuisines" || searchQuery.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            selectedPartySize = null
                            selectedVibe = "All Vibes"
                            selectedCuisine = "All Cuisines"
                            searchQuery = ""
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Reset Filters", fontSize = 12.sp, color = RestroTokens.coral)
                    }
                }
            }
        }

        // 7. Dine-In Restaurant Cards
        if (restaurants.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.SearchOff, null, tint = RestroTokens.muted, modifier = Modifier.size(40.dp))
                        Text("No restaurants match your selected dining requirements", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Try adjusting party size or selecting 'All Vibes'.", fontSize = 12.sp, color = RestroTokens.muted)
                        Button(
                            onClick = {
                                selectedPartySize = null
                                selectedVibe = "All Vibes"
                                selectedCuisine = "All Cuisines"
                                searchQuery = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Clear All Filters")
                        }
                    }
                }
            }
        } else {
            items(restaurants, key = { it.id }) { restaurant ->
                DineInRestaurantCard(
                    restaurant = restaurant,
                    vm = vm,
                    chosenPartySize = selectedPartySize ?: 2,
                    onBookTable = { onSelectRestaurantToBook(restaurant, selectedPartySize ?: 2) },
                    onExploreMenu = { onOpenMenu(restaurant.id) }
                )
            }
        }
    }

    if (showWalkInSheet) {
        val targetRestId = vm.restaurantId.ifBlank { "rest-1" }
        val restTables = RestaurantTableDefaults.getTablesForRestaurant(targetRestId).map { it.tableNumber }
        val acceptsPay = vm.catalog.restaurants.find { it.id == targetRestId }?.acceptsDiningPayment ?: true
        WalkInPayBillSheet(
            onTableSelected = { tableNumber ->
                val tab = vm.lookupWalkInTableBill(tableNumber)
                if (tab != null) {
                    showWalkInSheet = false
                }
            },
            onDismiss = { showWalkInSheet = false },
            errorMessage = vm.walkInTableError,
            availableTables = restTables,
            acceptsDiningPayment = acceptsPay
        )
    }

    if (showTabPaymentSheet && vm.activeTableTab != null) {
        val tab = vm.activeTableTab!!
        PaymentGatewayBottomSheet(
            vm = vm,
            totalAmount = tab.total,
            selectedAddressId = "DINE_IN_${tab.tableNumber}",
            diningTab = tab,
            initialMethod = "UPI",
            onPaymentSuccess = { paymentMethod, transactionId ->
                vm.settleDineInTab(tab, paymentMethod, transactionId)
                showTabPaymentSheet = false
            },
            onDismiss = { showTabPaymentSheet = false }
        )
    }
}

/**
 * Dedicated Card for Dine-In Display with Table Capacity, Distance, Vibes, and Direct Booking CTA.
 */
@Composable
fun DineInRestaurantCard(
    restaurant: Restaurant,
    vm: DeliveryViewModel,
    chosenPartySize: Int,
    onBookTable: () -> Unit,
    onExploreMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val distKm = vm.getRestaurantDistanceKm(restaurant)
    val driveMins = (distKm * 4.0 + 6).toInt().coerceAtLeast(5)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column {
            // Photo & Badges Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clickable { onExploreMenu() }
            ) {
                Photo(restaurant.image, restaurant.name, Modifier.fillMaxSize())

                // Gradient Vignette for readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.75f)
                                )
                            )
                        )
                )

                // Top-Left Rating Badge
                Surface(
                    color = Color(0xFF16A34A),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(Icons.Default.Star, null, tint = Color.White, modifier = Modifier.size(13.dp))
                        Text("${restaurant.rating}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("(${restaurant.reviewCount})", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp)
                    }
                }

                // Top-Right Distance Badge
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.NearMe, null, tint = RestroTokens.coral, modifier = Modifier.size(12.dp))
                        Text(
                            "$distKm km · $driveMins min drive",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                // Bottom Overlay Bar (Instant Confirmation Guarantee)
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Bolt, null, tint = Color(0xFFFACC15), modifier = Modifier.size(14.dp))
                        Text(
                            "Instant Table Confirmation · No Wait Times",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Info & Attributes Body
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            restaurant.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = RestroTokens.text
                        )
                        Text(
                            "${restaurant.cuisine} · ₹${restaurant.costForTwo} for two",
                            fontSize = 12.sp,
                            color = RestroTokens.muted
                        )
                    }
                }

                Text(
                    restaurant.address,
                    fontSize = 12.sp,
                    color = RestroTokens.muted,
                    maxLines = 1
                )

                // Vibes & Amenities Flow
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    restaurant.vibes.take(2).forEach { vibe ->
                        Surface(
                            color = RestroTokens.coral.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, RestroTokens.coral.copy(alpha = 0.3f))
                        ) {
                            Text(
                                vibe,
                                color = RestroTokens.coral,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    restaurant.amenities.take(2).forEach { amenity ->
                        Surface(
                            color = Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                amenity,
                                color = RestroTokens.muted,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Table Capacity & Dining Payment Policy Notice
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.TableBar, null, tint = RestroTokens.green, modifier = Modifier.size(15.dp))
                    Text(
                        "Tables accommodating 1 to ${restaurant.maxPartySize} guests · ${if (restaurant.acceptsDiningPayment) "Online Table Pay" else RestroI18n.t("counter_pay_only")}",
                        fontSize = 11.sp,
                        color = RestroTokens.green,
                        fontWeight = FontWeight.Medium
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // Actions: Book Table vs Explore Menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onExploreMenu,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RestroTokens.text),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                    ) {
                        Text("View Menu", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onBookTable,
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                        contentPadding = PaddingValues(vertical = 11.dp)
                    ) {
                        Icon(Icons.Default.EventSeat, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Book Table ($chosenPartySize Guests)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
