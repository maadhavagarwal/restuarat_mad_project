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

    LaunchedEffect(vm.profile?.isDarkMode) {
        vm.profile?.let {
            RestroTokens.isDark = it.isDarkMode
        }
    }

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
            else if (!vm.hasSelectedLanguage)
                LanguageSelectionScreen(
                    onLanguageSelected = { lang ->
                        vm.selectLanguage(lang)
                    }
                )
            else if (vm.profile == null && !vm.isGuestMode)
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
                                        RestroI18n.t("bistro_dining"),
                                        color = RestroTokens.coral,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AppLanguage.entries.forEach { lang ->
                                    val isSel = RestroI18n.currentLanguage == lang.code
                                    Surface(
                                        onClick = { vm.selectLanguage(lang.code) },
                                        color = if (isSel) RestroTokens.coral else RestroTokens.surface,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            lang.nativeName,
                                            color = if (isSel) Color.White else RestroTokens.muted,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
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
                            val isRootTab = vm.route in listOf("Home", "DineIn", "Orders", "Bookings", "Profile")
                            if (!isRootTab) {
                                IconButton(onClick = vm::back) {
                                    Icon(Icons.Default.ArrowBack, "Back")
                                }
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
                                        "DineIn" -> DineInScreen(
                                            vm = vm,
                                            onSelectRestaurantToBook = { selectedRest, partySize ->
                                                vm.restaurantId = selectedRest.id
                                                vm.resetTableBookingDraft(selectedRest.id, selectedRest.name)
                                                vm.tableBookingDraft = vm.tableBookingDraft.copy(partySize = partySize)
                                                vm.route = "BookTable"
                                            },
                                            onOpenMenu = { restId ->
                                                vm.openRestaurant(restId)
                                            },
                                            onOpenLocationPicker = {
                                                showLandmarkPicker = true
                                            }
                                        )
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
                                                vm.route = "DineIn"
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
                                        "Bookings",
                                        "MyBookings" -> MyTableBookingsScreen(
                                            vm = vm,
                                            onBack = { vm.route = "Home" },
                                            onBookNewTable = { vm.route = "DineIn" }
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

                    if (vm.showRestroAiSheet) {
                        RestroAiBottomSheet(
                            vm = vm,
                            onDismiss = { vm.showRestroAiSheet = false }
                        )
                    }

                    if (vm.isVoiceListening) {
                        VoiceListeningDialog(
                            language = RestroI18n.currentLanguage,
                            onDismiss = { vm.isVoiceListening = false },
                            onSimulateVoiceResult = { spokenText ->
                                vm.handleVoiceResult(spokenText)
                            }
                        )
                    }

                    if (vm.isTourActive) {
                        InteractiveAppTourOverlay(
                            currentStepIndex = vm.currentTourStep,
                            onNext = vm::nextTourStep,
                            onBack = vm::prevTourStep,
                            onSkip = vm::finishAppTour,
                            onFinish = vm::finishAppTour
                        )
                    }

                    if (vm.showLanguageSelector) {
                        androidx.compose.ui.window.Dialog(
                            onDismissRequest = { vm.showLanguageSelector = false },
                            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                        ) {
                            LanguageSelectionScreen(
                                onLanguageSelected = {
                                    vm.selectLanguage(it)
                                    vm.showLanguageSelector = false
                                }
                            )
                        }
                    }

                    if (vm.profile != null || vm.isGuestMode)
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
                                        Triple("Home", RestroI18n.t("tab_delivery"), Icons.Default.DeliveryDining),
                                        Triple("DineIn", RestroI18n.t("tab_dine_in"), Icons.Default.TableBar),
                                        Triple("Orders", RestroI18n.t("tab_orders"), Icons.Default.ReceiptLong),
                                        Triple("Bookings", RestroI18n.t("tab_bookings"), Icons.Default.EventSeat),
                                        Triple("Profile", RestroI18n.t("tab_profile"), Icons.Default.PersonOutline),
                                    )
                                    .forEach { (route, label, icon) ->
                                        val isSelected = when (route) {
                                            "Home" -> vm.route in listOf("Home", "Search", "Favorites", "Restaurant", "Map", "Food")
                                            "DineIn" -> vm.route in listOf("DineIn", "BookTable", "BookTableReview", "BookTableSuccess")
                                            "Orders" -> vm.route in listOf("Orders", "Confirmation", "Tracking")
                                            "Bookings" -> vm.route in listOf("Bookings", "MyBookings")
                                            "Profile" -> vm.route in listOf("Profile", "Addresses", "Edit address", "Reviews", "Review")
                                            else -> vm.route == route
                                        }
                                        TextButton(
                                            onClick = {
                                                if (route == "Bookings") {
                                                    vm.loadUserTableBookings()
                                                }
                                                vm.route = route
                                                vm.error = null
                                            },
                                            contentPadding = PaddingValues(4.dp),
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Icon(
                                                    icon,
                                                    label,
                                                    tint = if (isSelected) RestroTokens.coral else RestroTokens.muted,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Text(
                                                    label,
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    maxLines = 1,
                                                    color = if (isSelected) RestroTokens.coral else RestroTokens.muted,
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
