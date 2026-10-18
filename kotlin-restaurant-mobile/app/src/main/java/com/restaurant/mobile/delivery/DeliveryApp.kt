package com.restaurant.mobile.delivery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryApp(vm: DeliveryViewModel = viewModel(), onOpenStaffPortal: (() -> Unit)? = null) {
    var showLandmarkPicker by remember { mutableStateOf(false) }

    RestroTheme {
        val layer = rememberGraphicsLayer()
        val backdrop = remember(layer) { Backdrop(layer) }
        BackHandler(vm.route != "Home") { vm.back() }
        Surface(Modifier.fillMaxSize(), color = RestroTokens.canvas) {
            if (vm.starting)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("restro", style = MaterialTheme.typography.headlineLarge)
                        CircularProgressIndicator(Modifier.padding(24.dp))
                    }
                }
            else if (vm.profile == null)
                Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("restro", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = RestroTokens.coral.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        "AUTH LOCKED",
                                        color = RestroTokens.coral,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Authentication Required",
                                tint = RestroTokens.coral,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        vm.error?.let { message ->
                            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                                Row(
                                    Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        message,
                                        Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    IconButton(onClick = { vm.error = null }) {
                                        Icon(Icons.Default.Close, "Dismiss error")
                                    }
                                }
                            }
                        }
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Box(Modifier.widthIn(max = 560.dp).fillMaxSize()) {
                                AuthScreen(vm)
                            }
                        }
                    }
                }
            else
                Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                    Column(Modifier.fillMaxSize().capture(backdrop)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (vm.route != "Home")
                                IconButton(onClick = vm::back) {
                                    Icon(Icons.Default.ArrowBack, "Back")
                                }
                            Column(
                                Modifier.weight(1f).clickable {
                                    showLandmarkPicker = true
                                }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("restro", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        color = RestroTokens.coral.copy(alpha = 0.15f),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            "LIVE GPS",
                                            color = RestroTokens.coral,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = RestroTokens.coral,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = vm.currentLandmark,
                                        color = RestroTokens.text,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Change landmark",
                                        tint = RestroTokens.muted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            if (onOpenStaffPortal != null && vm.profile?.role?.isStaff == true) {
                                IconButton(onClick = onOpenStaffPortal) {
                                    Icon(Icons.Default.Storefront, "Staff POS / KDS", tint = RestroTokens.coral)
                                }
                            }
                            IconButton(onClick = { vm.route = "Cart" }) {
                                BadgedBox(
                                    badge = {
                                        if (vm.cart.lines.isNotEmpty())
                                            Badge {
                                                Text(
                                                    vm.cart.lines
                                                        .sumOf { it.quantity }
                                                        .toString()
                                                )
                                            }
                                    }
                                ) {
                                    Icon(Icons.Default.ShoppingBag, "Open cart")
                                }
                            }
                        }
                        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        vm.error?.let { message ->
                            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                                Row(
                                    Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        message,
                                        Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    TextButton(onClick = vm::refresh, enabled = !vm.busy) {
                                        Text("Retry")
                                    }
                                    IconButton(onClick = { vm.error = null }) {
                                        Icon(Icons.Default.Close, "Dismiss error")
                                    }
                                }
                            }
                        }
                        PullToRefreshBox(
                            isRefreshing = vm.busy,
                            onRefresh = vm::refresh,
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                Modifier.fillMaxSize().padding(bottom = 88.dp),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                Box(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                                    when (vm.route) {
                                        "Home",
                                        "Search",
                                        "Favorites",
                                        "Restaurant" -> Discovery(vm)
                                        "Map" -> DeliveryInteractiveMap(vm, onClose = { vm.route = "Home" })
                                        "Food" -> FoodScreen(vm)
                                        "Cart" -> CartScreen(vm)
                                        "Checkout",
                                        "Payment" -> CheckoutScreen(vm)
                                        "Orders",
                                        "Confirmation",
                                        "Tracking" -> OrdersScreen(vm)
                                        "Addresses",
                                        "Edit address" -> AddressesScreen(vm)
                                        "Reviews",
                                        "Review" -> ReviewsScreen(vm)
                                        "ForbiddenStaff" -> ForbiddenStaffScreen(vm, onOpenStaffPortal)
                                        "BookTable" -> BookTableScreen(
                                            vm = vm,
                                            onProceedToReview = { validatedDraft ->
                                                vm.tableBookingDraft = validatedDraft
                                                vm.route = "BookTableReview"
                                            }
                                        )
                                        "BookTableReview" -> TableBookingReviewScreen(
                                            vm = vm,
                                            onBack = { vm.back() },
                                            onConfirm = {
                                                vm.confirmTableBooking {
                                                    vm.route = "BookTableSuccess"
                                                }
                                            }
                                        )
                                        "BookTableSuccess" -> TableBookingSuccessScreen(
                                            vm = vm,
                                            onDone = {
                                                vm.confirmedBooking = null
                                                vm.resetTableBookingDraft()
                                                vm.route = "Home"
                                            },
                                            onBookAnother = {
                                                val currentRest = vm.catalog.restaurants.find { it.id == vm.restaurantId }
                                                vm.confirmedBooking = null
                                                vm.resetTableBookingDraft(currentRest?.id ?: "", currentRest?.name ?: "")
                                                vm.route = "BookTable"
                                            },
                                            onViewMyBookings = {
                                                vm.confirmedBooking = null
                                                vm.loadUserTableBookings()
                                                vm.route = "MyBookings"
                                            }
                                        )
                                        "MyBookings" -> MyTableBookingsScreen(
                                            vm = vm,
                                            onBack = { vm.back() },
                                            onBookNewTable = { vm.route = "Home" }
                                        )
                                        else -> ProfileScreen(vm, onOpenStaffPortal)
                                    }
                                }
                            }
                        }
                    }
                    // In-Menu Sticky Cart Pill (Floating above navigation bar)
                    if (vm.cart.lines.isNotEmpty() && vm.route in listOf("Home", "Search", "Favorites", "Restaurant")) {
                        Surface(
                            onClick = { vm.route = "Cart" },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 76.dp, start = 16.dp, end = 16.dp)
                                .widthIn(max = 560.dp)
                                .fillMaxWidth()
                                .shadow(14.dp, RoundedCornerShape(20.dp)),
                            color = RestroTokens.coral,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.25f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.ShoppingBag,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            "${vm.getCartTotalItems()} items · ${money(vm.getCartTotalAmount())}",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            "From ${vm.getCartRestaurantName()}",
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "View Cart",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Multi-Restaurant Guard Modal Prompt
                    if (vm.showMultiRestaurantDialog) {
                        val currentRestName = vm.getCartRestaurantName()
                        val newRestName = vm.pendingDishToAdd?.let {
                            vm.catalog.restaurants.find { r -> r.id == it.restaurantId }?.name
                        } ?: "another restaurant"

                        AlertDialog(
                            onDismissRequest = { vm.dismissMultiRestaurantDialog() },
                            title = {
                                Text("Replace cart items?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            },
                            text = {
                                Text(
                                    "Your cart contains items from $currentRestName. Discard items from $currentRestName and start an order from $newRestName?",
                                    color = RestroTokens.muted,
                                    fontSize = 14.sp
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = { vm.confirmDiscardAndAdd() },
                                    colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Discard & Start New", fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { vm.dismissMultiRestaurantDialog() }) {
                                    Text("Cancel", color = RestroTokens.muted)
                                }
                            },
                            containerColor = RestroTokens.surface,
                            titleContentColor = RestroTokens.text,
                            textContentColor = RestroTokens.text,
                            shape = RoundedCornerShape(20.dp)
                        )
                    }

                    if (showLandmarkPicker) {
                        LandmarkPickerSheet(
                            vm = vm,
                            onDismiss = { showLandmarkPicker = false }
                        )
                    }

                    if (vm.profile != null)
                        Glass(
                            Modifier.align(Alignment.BottomCenter)
                                .padding(12.dp)
                                .widthIn(max = 560.dp)
                                .fillMaxWidth(),
                            backdrop,
                            vm.profile?.reducedGlass == true,
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                listOf(
                                        "Home" to Icons.Default.Home,
                                        "Map" to Icons.Default.Map,
                                        "Favorites" to Icons.Default.FavoriteBorder,
                                        "Orders" to Icons.Default.ReceiptLong,
                                        "Profile" to Icons.Default.PersonOutline,
                                    )
                                    .forEach { (name, icon) ->
                                        TextButton(
                                            onClick = {
                                                vm.route = name
                                                vm.error = null
                                            },
                                            contentPadding = PaddingValues(6.dp),
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Icon(
                                                    icon,
                                                    name,
                                                    tint =
                                                        if (vm.route == name) RestroTokens.coral
                                                        else RestroTokens.muted,
                                                )
                                                Text(
                                                    name,
                                                    fontSize = 11.sp,
                                                    color =
                                                        if (vm.route == name) RestroTokens.coral
                                                        else RestroTokens.muted,
                                                )
                                            }
                                        }
                                    }
                            }
                        }
                }
        }
    }
}
