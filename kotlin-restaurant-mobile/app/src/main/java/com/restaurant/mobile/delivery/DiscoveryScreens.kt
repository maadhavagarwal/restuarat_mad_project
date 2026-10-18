package com.restaurant.mobile.delivery

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun AuthScreen(vm: DeliveryViewModel) {
    var authMode by rememberSaveable { mutableStateOf(0) } // 0: Fast OTP, 1: Password
    var otpType by rememberSaveable { mutableStateOf(0) } // 0: Phone, 1: Email
    var phoneInput by rememberSaveable { mutableStateOf("") }
    var emailOtpInput by rememberSaveable { mutableStateOf("") }
    var otpCode by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }

    var register by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedRoleName by rememberSaveable { mutableStateOf(UserRole.CUSTOMER.name) }
    val selectedRole = UserRole.fromString(selectedRoleName)

    Page {
        vm.catalog.dishes.firstOrNull()?.let {
            Photo(
                it.image,
                it.name,
                Modifier.fillMaxWidth().height(180.dp).clip(RestroTokens.shape),
            )
        }

        vm.sessionExpiredBanner?.let { expiredMsg ->
            Surface(
                color = Color(0xFFDC2626).copy(alpha = 0.18f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.WarningAmber,
                        contentDescription = "Session Expired",
                        tint = Color(0xFFF87171),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = expiredMsg,
                        color = Color(0xFFF87171),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { vm.dismissSessionExpiredBanner() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Heading(
            if (authMode == 0) "Fast & Secure\nSign In."
            else if (register) "Your next favourite\nstarts here."
            else "Something good\nis on its way.",
            if (authMode == 0) "Verify via instant 6-digit OTP to mobile or email."
            else if (register) "Create your Restro account."
            else "Sign in to discover, save and order.",
        )

        // Role-Based Access Control (RBAC) Selector
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Access Role (RBAC)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = RestroTokens.muted
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(UserRole.entries) { role ->
                    FilterChip(
                        selected = selectedRole == role,
                        onClick = { selectedRoleName = role.name },
                        label = {
                            Text(
                                role.label,
                                fontSize = 12.sp,
                                fontWeight = if (selectedRole == role) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = when (role) {
                                    UserRole.CUSTOMER -> Icons.Default.Person
                                    UserRole.WAITER -> Icons.Default.PointOfSale
                                    UserRole.CHEF_KDS -> Icons.Default.SoupKitchen
                                    UserRole.ADMIN -> Icons.Default.AdminPanelSettings
                                },
                                contentDescription = role.label,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }

        // Auth Mode Switcher Tab
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(RestroTokens.surface)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = {
                    authMode = 0
                    vm.resetOtp()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (authMode == 0) RestroTokens.coral else Color.Transparent,
                    contentColor = if (authMode == 0) Color.White else RestroTokens.muted
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Phone / OTP", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Button(
                onClick = { authMode = 1 },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (authMode == 1) RestroTokens.coral else Color.Transparent,
                    contentColor = if (authMode == 1) Color.White else RestroTokens.muted
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Password", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        if (authMode == 0) {
            // OTP Authentication Flow
            if (!vm.otpSent) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = otpType == 0,
                        onClick = { otpType = 0 },
                        label = { Text("Mobile Phone") },
                        leadingIcon = { Icon(Icons.Default.Phone, null, modifier = Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = otpType == 1,
                        onClick = { otpType = 1 },
                        label = { Text("Email Address") },
                        leadingIcon = { Icon(Icons.Default.Email, null, modifier = Modifier.size(16.dp)) }
                    )
                }

                if (otpType == 0) {
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Phone Number") },
                        placeholder = { Text("+91 98765 43210") },
                        leadingIcon = { Icon(Icons.Default.Call, null, tint = RestroTokens.coral) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                } else {
                    OutlinedTextField(
                        value = emailOtpInput,
                        onValueChange = { emailOtpInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Email Address") },
                        placeholder = { Text("you@example.com") },
                        leadingIcon = { Icon(Icons.Default.Email, null, tint = RestroTokens.coral) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Your Name (Optional)") },
                    placeholder = { Text("Aarav Sharma") },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )

                Action(
                    label = "Send Verification OTP",
                    enabled = !vm.busy && if (otpType == 0) phoneInput.trim().length >= 8 else emailOtpInput.contains("@")
                ) {
                    val target = if (otpType == 0) phoneInput else emailOtpInput
                    vm.sendOtp(target, isPhone = (otpType == 0))
                }
            } else {
                // OTP Sent View
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = RestroTokens.green)
                            Text(
                                "OTP sent to ${vm.otpTarget}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        vm.otpNotificationBanner?.let { banner ->
                            Surface(
                                color = RestroTokens.coral.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Notifications, null, tint = RestroTokens.coral, modifier = Modifier.size(18.dp))
                                    Text(
                                        banner,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (vm.otpPreviewCode != null) {
                                        TextButton(
                                            onClick = { otpCode = vm.otpPreviewCode ?: "" },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) {
                                            Text("Autofill", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RestroTokens.coral)
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { if (it.length <= 6) otpCode = it.filter { ch -> ch.isDigit() } },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Enter 6-Digit Code") },
                            placeholder = { Text("• • • • • •") },
                            leadingIcon = { Icon(Icons.Default.Lock, null, tint = RestroTokens.coral) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )

                        Action(
                            label = "Verify & Continue (${selectedRole.label})",
                            enabled = !vm.busy && otpCode.length == 6
                        ) {
                            vm.verifyOtp(otpCode, name, selectedRole)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                vm.resetOtp()
                                otpCode = ""
                            }) {
                                Text("Change Target", fontSize = 12.sp)
                            }
                            TextButton(onClick = {
                                otpCode = ""
                                vm.sendOtp(vm.otpTarget, isPhone = (otpType == 0))
                            }) {
                                Text("Resend OTP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            // Password Authentication Flow
            if (register) Field("Your name", name, { name = it })
            Field("Email", email, { email = it })
            Field("Password · at least 10 characters", password, { password = it }, true)
            Action(
                if (register) "Create account (${selectedRole.label})" else "Sign in (${selectedRole.label})",
                !vm.busy &&
                    email.isNotBlank() &&
                    password.length >= 10 &&
                    (!register || name.trim().length >= 2),
            ) {
                vm.auth(email, password, name, register, selectedRole)
            }
            TextButton(onClick = { register = !register }) {
                Text(
                    if (register) "Already have an account? Sign in"
                    else "New to Restro? Create an account"
                )
            }
        }

        HorizontalDivider(
            color = Color.White.copy(alpha = 0.1f),
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Surface(
            color = RestroTokens.surface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = RestroTokens.green,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        "Mandatory Auth Gatekeeper Active",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        "All ordering, table reservations, and staff portals require verified OTP or password authentication.",
                        fontSize = 11.sp,
                        color = RestroTokens.muted
                    )
                }
            }
        }
    }
}

@Composable
fun Discovery(vm: DeliveryViewModel) {
    var showLandmarkSheet by rememberSaveable { mutableStateOf(false) }
    val restaurant = vm.catalog.restaurants.find { it.id == vm.restaurantId }
    val detail = vm.route == "Restaurant"
    val dishes =
        vm.catalog.dishes
            .filter {
                (!detail || it.restaurantId == vm.restaurantId) &&
                    (!vm.vegetarian || it.vegetarian) &&
                    (vm.category.isBlank() || it.category == vm.category) &&
                    (vm.query.isBlank() || it.name.contains(vm.query, true))
            }
            .let { if (vm.sortPrice) it.sortedBy { d -> d.price } else it }

    val nearestRestaurants =
        vm.getNearestRestaurants().filter {
            (vm.route != "Favorites" || it.id in vm.favorites) &&
                (vm.query.isBlank() ||
                    it.name.contains(vm.query, true) ||
                    it.cuisine.contains(vm.query, true) ||
                    vm.catalog.dishes.any { d ->
                        d.restaurantId == it.id && d.name.contains(vm.query, true)
                    })
        }

    var showTabPaymentSheet by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().testTag("discovery-list"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Live Dine-In Tab Card (Prompt 10)
        vm.activeTableTab?.let { tab ->
            item(key = "live-dine-in-tab") {
                LiveDineInTabCard(tab = tab, onPayClick = { showTabPaymentSheet = true })
            }
        }

        item {
            Heading(
                if (detail) restaurant?.name ?: "Restaurant"
                else if (vm.route == "Favorites") "Your favourites" else "What sounds\ngood today?",
                if (detail) restaurant?.description else "Discover restaurants delivering near you.",
            )
        }

        // 1. Dynamic Delivery Landmark Header Pill
        if (!detail && vm.route != "Favorites") {
            item {
                Surface(
                    onClick = { showLandmarkSheet = true },
                    color = RestroTokens.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(RestroTokens.coral.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.LocationOn, null, tint = RestroTokens.coral, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Delivering to:", fontSize = 11.sp, color = RestroTokens.muted)
                                Surface(
                                    color = RestroTokens.coral.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "LIVE GPS",
                                        color = RestroTokens.coral,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = vm.currentLandmark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = RestroTokens.text,
                                maxLines = 1
                            )
                        }
                        Icon(Icons.Default.KeyboardArrowDown, "Change Landmark", tint = RestroTokens.muted)
                    }
                }
            }
        }

        if (detail && restaurant != null)
            item {
                RestaurantCard(restaurant, vm, false)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { vm.route = "Reviews" },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFB800), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reviews (${vm.reviews.size})")
                    }
                    Button(
                        onClick = { vm.route = "Map" },
                        colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.surface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Map, null, tint = RestroTokens.coral, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("View on Map", color = RestroTokens.text)
                    }
                }
                Button(
                    onClick = {
                        vm.resetTableBookingDraft(restaurant.id, restaurant.name)
                        vm.route = "BookTable"
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = RestroTokens.canvas,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Book a Table",
                        color = RestroTokens.canvas,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

        // 2. Frequently Ordered / Quick Reorder Carousel
        if (!detail && vm.route != "Favorites" && vm.query.isBlank()) {
            val frequentlyOrdered = vm.getFrequentlyOrderedDishes()
            if (frequentlyOrdered.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Frequently Ordered / Quick Reorder", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Tap to reorder your cravings with 1 tap", fontSize = 12.sp, color = RestroTokens.muted)
                            }
                            Icon(Icons.Default.Bolt, null, tint = RestroTokens.coral, modifier = Modifier.size(20.dp))
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            items(frequentlyOrdered) { (dish, restName) ->
                                QuickReorderCard(dish = dish, restaurantName = restName, vm = vm)
                            }
                        }
                    }
                }
            }
        }

        if (!detail && vm.route != "Favorites") {
            item {
                Surface(
                    onClick = { vm.route = "Map" },
                    color = RestroTokens.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(RestroTokens.coral.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Explore, null, tint = RestroTokens.coral, modifier = Modifier.size(22.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Interactive City Food Map", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Explore restaurants & delivery radius live on map", fontSize = 12.sp, color = RestroTokens.muted)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = RestroTokens.muted)
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = vm.query,
                onValueChange = { vm.query = it },
                placeholder = {
                    Text(if (detail) "Search this menu" else "Restaurants, dishes, cravings")
                },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (vm.query.isNotEmpty())
                        IconButton(onClick = { vm.query = "" }) {
                            Icon(Icons.Default.Close, "Clear search")
                        }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
            )
        }

        item {
            val availableCategories = if (detail) {
                vm.catalog.dishes.filter { it.restaurantId == vm.restaurantId }.map { it.category }.distinct()
            } else {
                vm.catalog.dishes.map { it.category }.distinct()
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = vm.vegetarian,
                        onClick = { vm.vegetarian = !vm.vegetarian },
                        label = { Text("Vegetarian") },
                    )
                }
                item {
                    FilterChip(
                        selected = vm.sortPrice,
                        onClick = { vm.sortPrice = !vm.sortPrice },
                        label = { Text("Price: low to high") },
                    )
                }
                item {
                    FilterChip(
                        selected = vm.category.isEmpty(),
                        onClick = { vm.category = "" },
                        label = { Text("All") },
                    )
                }
                items(availableCategories) { category ->
                    FilterChip(
                        selected = vm.category == category,
                        onClick = { vm.category = category },
                        label = { Text(category) },
                    )
                }
            }
        }

        // 3. Nearest Restaurants Section Sorted by Distance
        if (!detail) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            if (vm.route == "Favorites") "Your Saved Favorites" else "Nearest Restaurants",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Sorted by real-time distance from ${vm.currentLandmark.substringBefore(",")}",
                            fontSize = 12.sp,
                            color = RestroTokens.muted
                        )
                    }
                    Surface(
                        color = RestroTokens.surface,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "${nearestRestaurants.size} places",
                            fontSize = 11.sp,
                            color = RestroTokens.muted,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            items(nearestRestaurants, key = { "restaurant-${it.id}" }) { RestaurantCard(it, vm, true) }
            if (nearestRestaurants.isEmpty())
                item {
                    Text(
                        if (vm.route == "Favorites")
                            "Save a restaurant using its heart button to find it here."
                        else "No restaurants found. Try another search.",
                        color = RestroTokens.muted,
                    )
                }
        }

        // 4. In-Menu Dish List with Inline Quantity Stepper (No Jarring Redirect!)
        if (detail) {
            item {
                Text(
                    "On the menu",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            items(dishes, key = { "dish-${it.id}" }) { dish ->
                val inCartQty = vm.getDishQuantityInCart(dish.id)

                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (dish.vegetarian) "● VEGETARIAN" else "NON-VEGETARIAN",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (dish.vegetarian) RestroTokens.green else RestroTokens.muted,
                            )
                            if (dish.addons.isNotEmpty()) {
                                Text("· Customisable", fontSize = 10.sp, color = RestroTokens.coral)
                            }
                        }
                        Text(
                            dish.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            modifier = Modifier.clickable {
                                vm.dishId = dish.id
                                vm.restaurantId = dish.restaurantId
                                vm.route = "Food"
                            }
                        )
                        Text(
                            dish.description,
                            color = RestroTokens.muted,
                            maxLines = 2,
                            fontSize = 12.sp,
                        )
                        Text(money(dish.price), fontWeight = FontWeight.Bold, fontSize = 15.sp)

                        if (dish.addons.isNotEmpty()) {
                            Text(
                                "Customize Add-ons +",
                                color = RestroTokens.coral,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    vm.dishId = dish.id
                                    vm.restaurantId = dish.restaurantId
                                    vm.route = "Food"
                                }
                            )
                        }
                    }

                    // Image + Inline Stepper
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Photo(
                            dish.image,
                            dish.name,
                            Modifier.size(100.dp)
                                .clip(RestroTokens.shape)
                                .clickable {
                                    vm.dishId = dish.id
                                    vm.restaurantId = dish.restaurantId
                                    vm.route = "Food"
                                }
                        )

                        if (!dish.available) {
                            Surface(
                                color = Color(0xFF262A32),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "Unavailable",
                                    fontSize = 11.sp,
                                    color = RestroTokens.muted,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else if (inCartQty == 0) {
                            // Inline "ADD +" Button (No jarring redirect!)
                            Button(
                                onClick = {
                                    vm.tryAddDish(dish, emptyList(), 1, redirectToCart = false)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("ADD +", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = RestroTokens.canvas)
                            }
                        } else {
                            // Inline Quantity Stepper [-]  qty  [+]
                            Surface(
                                color = RestroTokens.surface,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, RestroTokens.coral)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    IconButton(
                                        onClick = { vm.decrementDish(dish) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, null, tint = RestroTokens.coral, modifier = Modifier.size(15.dp))
                                    }
                                    Text(
                                        inCartQty.toString(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = RestroTokens.text
                                    )
                                    IconButton(
                                        onClick = { vm.incrementDish(dish) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Add, null, tint = RestroTokens.coral, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = .08f))
            }
            if (dishes.isEmpty())
                item {
                    Text("No dishes match these filters.")
                    TextButton(
                        onClick = {
                            vm.query = ""
                            vm.category = ""
                            vm.vegetarian = false
                        }
                    ) {
                        Text("Clear filters")
                    }
                }
        }
    }

    if (showLandmarkSheet) {
        LandmarkPickerSheet(
            vm = vm,
            onDismiss = { showLandmarkSheet = false }
        )
    }

    if (showTabPaymentSheet) {
        vm.activeTableTab?.let { tab ->
            PaymentGatewayBottomSheet(
                vm = vm,
                totalAmount = tab.total,
                selectedAddressId = "DINE_IN_${tab.tableNumber}",
                initialMethod = "UPI",
                onDismiss = { showTabPaymentSheet = false }
            )
        }
    }
}

/**
 * Live Dine-In Tab card displayed at the top of the Discovery screen
 * when the user has an active dining session. Shows real-time bill
 * with pulsing status indicator, expandable item list, and pay CTA.
 */
@Composable
fun LiveDineInTabCard(tab: DiningTab, onPayClick: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse-alpha"
    )

    Surface(
        color = RestroTokens.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row: Table badge + pulsing status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Table number badge
                    Surface(
                        color = RestroTokens.coral.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "TABLE ${tab.tableNumber}",
                            color = RestroTokens.coral,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    // Pulsing green dot + "Dining in Session"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E).copy(alpha = pulseAlpha))
                        )
                        Text(
                            "Dining in Session",
                            color = Color(0xFF22C55E),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Title
            Text(
                "Live Dine-In Tab \u2014 Table ${tab.tableNumber}",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = RestroTokens.text
            )

            // Expandable items list
            Surface(
                onClick = { expanded = !expanded },
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${tab.items.size} item${if (tab.items.size != 1) "s" else ""} ordered",
                        fontSize = 13.sp,
                        color = RestroTokens.muted
                    )
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse items" else "Expand items",
                        tint = RestroTokens.muted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                    "x${item.quantity}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RestroTokens.coral
                                )
                                Text(
                                    item.name,
                                    fontSize = 13.sp,
                                    color = RestroTokens.text,
                                    maxLines = 1
                                )
                            }
                            Text(
                                money(item.total),
                                fontSize = 13.sp,
                                color = RestroTokens.text
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            // Bill breakdown
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    Text("GST (5%)", fontSize = 13.sp, color = RestroTokens.muted)
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
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Grand Total", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RestroTokens.text)
                    Text(money(tab.total), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RestroTokens.coral)
                }
            }

            // CTA Button
            Button(
                onClick = onPayClick,
                colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    Icons.Default.Payment,
                    contentDescription = null,
                    tint = RestroTokens.canvas,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Review & Pay Table Bill (${money(tab.total)})",
                    color = RestroTokens.canvas,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * Quick Reorder Card used in the Frequently Ordered Feed.
 */
@Composable
fun QuickReorderCard(
    dish: Dish,
    restaurantName: String,
    vm: DeliveryViewModel
) {
    Card(
        modifier = Modifier
            .width(180.dp)
            .height(210.dp),
        colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                Photo(dish.image, dish.name, Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)))
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        if (dish.vegetarian) "● VEG" else "NON-VEG",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (dish.vegetarian) RestroTokens.green else Color(0xFFFFB088),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(dish.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                    Text(restaurantName, fontSize = 11.sp, color = RestroTokens.muted, maxLines = 1)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(money(dish.price), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Button(
                        onClick = {
                            vm.tryAddDish(dish, emptyList(), 1, redirectToCart = false)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Reorder +", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RestroTokens.canvas)
                    }
                }
            }
        }
    }
}

/**
 * Restaurant card with distance badge, delivery ETA, and out-of-range visual indicator.
 */
@Composable
fun RestaurantCard(r: Restaurant, vm: DeliveryViewModel, clickable: Boolean) {
    val layer = rememberGraphicsLayer()
    val backdrop = remember(layer) { Backdrop(layer) }
    val inRange = vm.isRestaurantInRange(r)
    val distanceInfo = vm.getRestaurantDistanceInfo(r)
    val distKm = vm.getRestaurantDistanceKm(r)

    Box(
        Modifier.fillMaxWidth()
            .height(270.dp)
            .clip(RestroTokens.shape)
            .then(
                if (clickable && inRange) Modifier.clickable { vm.openRestaurant(r.id) }
                else Modifier
            )
    ) {
        Photo(r.image, r.name, Modifier.fillMaxSize().capture(backdrop))

        // Out-of-Range Dimmed Visual Overlay
        if (!inRange) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
            )
        }

        // Top Distance & Out-of-Range Indicator Badge
        Surface(
            color = if (inRange) Color.Black.copy(alpha = 0.72f) else Color(0xFFDC2626).copy(alpha = 0.9f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    if (inRange) Icons.Default.NearMe else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (inRange) RestroTokens.coral else Color.White,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (inRange) distanceInfo else "Out of Range ($distKm km)",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Favorite Heart Button
        FilledIconButton(
            onClick = { vm.favorite(r.id) },
            enabled = !vm.busy,
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = RestroTokens.canvas.copy(alpha = .7f)
                ),
        ) {
            Icon(
                if (r.id in vm.favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                if (r.id in vm.favorites) "Remove favourite" else "Save restaurant",
                tint = RestroTokens.coral,
            )
        }

        // Frosted Glass Info Caption
        Glass(
            Modifier.align(Alignment.BottomCenter).padding(10.dp).fillMaxWidth(),
            backdrop,
            vm.profile?.reducedGlass == true,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(r.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(r.cuisine, fontSize = 12.sp, color = RestroTokens.muted)
                    Text(
                        if (!inRange) "Outside ${vm.maxDeliveryRadiusKm} km delivery zone"
                        else if (r.open) "$distanceInfo · ${money(r.deliveryFee)} delivery"
                        else "Currently closed",
                        color = if (!inRange) Color(0xFFF87171) else RestroTokens.muted,
                        fontSize = 12.sp,
                        fontWeight = if (!inRange) FontWeight.SemiBold else FontWeight.Normal
                    )
                }

                if (!inRange) {
                    // Out of Range Visual Indicator with Disabled Button
                    Surface(
                        color = Color(0xFFEF4444).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444))
                    ) {
                        Text(
                            "Out of Range",
                            color = Color(0xFFF87171),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

