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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryApp(vm: DeliveryViewModel = viewModel()) {
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
                            Column(Modifier.weight(1f)) {
                                Text("restro", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    if (vm.profile == null) "Good food. A little closer."
                                    else
                                        vm.addresses.firstOrNull()?.let {
                                            "${it.label} · ${it.city}"
                                        } ?: "Add your delivery address",
                                    color = RestroTokens.muted,
                                    fontSize = 12.sp,
                                )
                            }
                            if (vm.profile != null)
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
                        if (vm.profile == null) AuthScreen(vm)
                        else
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
                                            else -> ProfileScreen(vm)
                                        }
                                    }
                                }
                            }
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
