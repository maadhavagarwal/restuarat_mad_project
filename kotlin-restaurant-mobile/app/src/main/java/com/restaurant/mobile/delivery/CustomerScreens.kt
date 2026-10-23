package com.restaurant.mobile.delivery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 1. Food Customization Screen
@Composable
fun FoodScreen(vm: DeliveryViewModel) {
    val dish = vm.catalog.dishes.find { it.id == vm.dishId }
    if (dish == null) {
        Page {
            Heading("Dish not found", "The selected dish could not be loaded.")
            Action("Back to menu") { vm.back() }
        }
        return
    }

    var quantity by rememberSaveable { mutableStateOf(1) }
    val selectedAddons = remember { mutableStateListOf<String>() }
    val addonsPrice = dish.addons.filter { it.id in selectedAddons }.sumOf { it.price }
    val totalPrice = (dish.price + addonsPrice) * quantity

    Page {
        Photo(
            dish.image,
            dish.name,
            Modifier.fillMaxWidth().height(240.dp).clip(RestroTokens.shape)
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (dish.vegetarian) "● VEGETARIAN" else "NON-VEGETARIAN",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (dish.vegetarian) RestroTokens.green else RestroTokens.muted
            )
            Text(money(dish.price), fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }

        Heading(dish.name, dish.description)

        if (dish.addons.isNotEmpty()) {
            Text("Customize with Add-ons", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            dish.addons.forEach { addon ->
                val checked = addon.id in selectedAddons
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (checked) selectedAddons.remove(addon.id)
                            else selectedAddons.add(addon.id)
                        }
                        .background(RestroTokens.surface.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                if (it) selectedAddons.add(addon.id)
                                else selectedAddons.remove(addon.id)
                            }
                        )
                        Text(addon.name, fontSize = 14.sp)
                    }
                    Text("+${money(addon.price)}", fontSize = 13.sp, color = RestroTokens.coral)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Quantity", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Quantity(
                q = quantity,
                minus = { if (quantity > 1) quantity-- },
                plus = { if (quantity < 20) quantity++ }
            )
        }

        Spacer(Modifier.height(8.dp))

        Action(
            label = "Add to Cart · ${money(totalPrice)}",
            enabled = dish.available && !vm.busy
        ) {
            vm.add(dish, selectedAddons.toList(), quantity)
            vm.back()
        }
    }
}

// 2. Cart Screen
@Composable
fun CartScreen(vm: DeliveryViewModel) {
    if (vm.cart.lines.isEmpty()) {
        Page {
            Column(
                Modifier.fillMaxWidth().padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.ShoppingBag,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = RestroTokens.muted
                )
                Heading("Your cart is empty", "Explore menus and add your favourite dishes.")
                Action("Browse restaurants") { vm.route = "Home" }
            }
        }
        return
    }

    var couponInput by rememberSaveable { mutableStateOf(vm.cart.coupon) }

    Page {
        Heading("Your Cart", "${vm.cart.lines.sumOf { it.quantity }} items in your bag")

        vm.cart.lines.forEachIndexed { index, line ->
            val dish = vm.catalog.dishes.find { it.id == line.dishId }
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(RestroTokens.surface)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Photo(dish?.image ?: "", dish?.name ?: "Item", Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(dish?.name ?: "Dish", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    val lineTotal = (dish?.price ?: 0L) * line.quantity
                    Text(money(lineTotal), color = RestroTokens.coral, fontSize = 13.sp)
                    if (line.addons.isNotEmpty()) {
                        Text("${line.addons.size} addons", fontSize = 11.sp, color = RestroTokens.muted)
                    }
                }
                Quantity(
                    q = line.quantity,
                    minus = {
                        val newLines = vm.cart.lines.toMutableList()
                        if (line.quantity > 1) {
                            newLines[index] = line.copy(quantity = line.quantity - 1)
                        } else {
                            newLines.removeAt(index)
                        }
                        vm.changeCart(newLines)
                    },
                    plus = {
                        val newLines = vm.cart.lines.toMutableList()
                        newLines[index] = line.copy(quantity = (line.quantity + 1).coerceAtMost(20))
                        vm.changeCart(newLines)
                    }
                )
            }
        }

        // Coupon Code Row
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = couponInput,
                onValueChange = { couponInput = it.uppercase() },
                placeholder = { Text("Coupon code (e.g. WELCOME50)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
            Button(
                onClick = { vm.applyCoupon(couponInput.trim()) },
                enabled = couponInput.isNotBlank() && !vm.busy,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(54.dp)
            ) {
                Text("Apply")
            }
        }

        // Available Coupons
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val coupons = listOf(
                "WELCOME50" to "₹50 OFF on first order",
                "FEAST" to "₹50 OFF on orders above ₹200",
                "FREEDEL" to "Free delivery on orders"
            )
            coupons.forEach { (code, desc) ->
                SuggestionChip(
                    onClick = {
                        couponInput = code
                        vm.applyCoupon(code)
                    },
                    label = { Text("$code · $desc", fontSize = 11.sp) },
                    icon = { Icon(Icons.Default.LocalOffer, null, modifier = Modifier.size(14.dp), tint = RestroTokens.coral) }
                )
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        // Bill Breakdown
        Text("Bill Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        val subtotal = vm.cart.lines.sumOf { line ->
            val dishPrice = vm.catalog.dishes.find { it.id == line.dishId }?.price ?: 0L
            dishPrice * line.quantity
        }
        val deliveryFee = 4000L // Rs 40.00
        val tax = (subtotal * 0.05).toLong()
        val discount = if (vm.cart.coupon.isNotBlank()) 5000L.coerceAtMost(subtotal) else 0L
        val total = (subtotal + deliveryFee + tax - discount).coerceAtLeast(0L)

        BillRow("Item Subtotal", subtotal)
        BillRow("Delivery Partner Fee", deliveryFee)
        BillRow("Taxes & Charges (5% GST)", tax)
        if (vm.cart.coupon.isNotBlank()) {
            BillRow("Coupon Discount", -discount)
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        BillRow("To Pay", vm.quote?.total ?: total)

        Spacer(Modifier.height(8.dp))

        Action("Proceed to Checkout", enabled = !vm.busy) {
            vm.loadQuote()
        }
    }
}

// 3. Checkout Screen
@Composable
fun CheckoutScreen(vm: DeliveryViewModel) {
    var selectedAddressId by rememberSaveable {
        mutableStateOf(vm.addresses.firstOrNull()?.id ?: "addr-default")
    }
    var selectedPaymentMethod by rememberSaveable {
        mutableStateOf("Cash on delivery")
    }

    var showPaymentSheet by rememberSaveable { mutableStateOf(false) }

    if (vm.addresses.isNotEmpty() && (selectedAddressId.isBlank() || vm.addresses.none { it.id == selectedAddressId })) {
        selectedAddressId = vm.addresses.first().id
    }

    Page {
        Heading("Checkout", "Confirm delivery address and payment")

        Text("Delivery Address", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        if (vm.addresses.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Default Delivery Location", fontWeight = FontWeight.Bold)
                            Text("Flat 402, SV Road, Goregaon West, Mumbai", color = RestroTokens.muted, fontSize = 13.sp)
                        }
                        Icon(Icons.Default.CheckCircle, null, tint = RestroTokens.green)
                    }
                    TextButton(onClick = { vm.route = "Edit address" }) {
                        Icon(Icons.Default.AddLocationAlt, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add custom address")
                    }
                }
            }
        } else {
            vm.addresses.forEach { addr ->
                val isSelected = addr.id == selectedAddressId
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { selectedAddressId = addr.id }
                        .background(if (isSelected) RestroTokens.coral.copy(alpha = 0.15f) else RestroTokens.surface)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { selectedAddressId = addr.id },
                        colors = RadioButtonDefaults.colors(selectedColor = RestroTokens.coral)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(addr.label, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("${addr.line}, ${addr.city} ${addr.postcode}", color = RestroTokens.muted, fontSize = 13.sp)
                        Text("Phone: ${addr.phone}", color = RestroTokens.muted, fontSize = 12.sp)
                    }
                }
            }

            TextButton(onClick = { vm.route = "Edit address" }) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(4.dp))
                Text("Add another address")
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        Text("Select Payment Method", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        val paymentOptions = listOf(
            Triple("Cash on delivery", "Cash or UPI on Delivery", Icons.Default.Payments),
            Triple("UPI", "Google Pay / PhonePe / Paytm / QR", Icons.Default.QrCodeScanner),
            Triple("Card", "Credit / Debit Card (Visa, RuPay, MC)", Icons.Default.CreditCard),
            Triple("Netbanking", "NetBanking (HDFC, ICICI, SBI, Axis)", Icons.Default.AccountBalance)
        )

        paymentOptions.forEach { (methodKey, methodTitle, icon) ->
            val isChosen = selectedPaymentMethod == methodKey
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { selectedPaymentMethod = methodKey }
                    .background(if (isChosen) RestroTokens.coral.copy(alpha = 0.15f) else RestroTokens.surface)
                .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(icon, null, tint = if (isChosen) RestroTokens.coral else RestroTokens.muted)
                Column(Modifier.weight(1f)) {
                    Text(methodTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        if (methodKey == "Cash on delivery") "Pay when driver reaches your doorstep"
                        else "Instant secure instant checkout",
                        fontSize = 12.sp,
                        color = RestroTokens.muted
                    )
                }
                RadioButton(
                    selected = isChosen,
                    onClick = { selectedPaymentMethod = methodKey },
                    colors = RadioButtonDefaults.colors(selectedColor = RestroTokens.coral)
                )
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        val quoteTotal = vm.quote?.total ?: 0L
        BillRow("Grand Total", quoteTotal)

        val isOnlinePayment = selectedPaymentMethod in listOf("UPI", "Card", "Netbanking")
        Action(
            label = if (isOnlinePayment) "Proceed to Pay · ${money(quoteTotal)}"
            else if (quoteTotal > 0) "Place Order · ${money(quoteTotal)}" else "Place Order",
            enabled = !vm.busy
        ) {
            val addrId = selectedAddressId.ifBlank { vm.addresses.firstOrNull()?.id ?: "addr-default" }
            if (isOnlinePayment) {
                showPaymentSheet = true
            } else {
                vm.checkout(addrId, selectedPaymentMethod)
            }
        }
    }

    if (showPaymentSheet) {
        val addrId = selectedAddressId.ifBlank { vm.addresses.firstOrNull()?.id ?: "addr-default" }
        PaymentGatewayBottomSheet(
            vm = vm,
            totalAmount = vm.quote?.total ?: 0L,
            selectedAddressId = addrId,
            initialMethod = selectedPaymentMethod,
            onDismiss = { showPaymentSheet = false }
        )
    }
}

// 4. Orders, Confirmation & Tracking Screen
@Composable
fun OrdersScreen(vm: DeliveryViewModel) {
    when (vm.route) {
        "Confirmation" -> {
            Page {
                Column(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier.size(76.dp).clip(CircleShape).background(RestroTokens.green.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(44.dp), tint = RestroTokens.green)
                    }
                    Heading("Order Confirmed!", "Thank you for ordering with Restro.")
                    Text("Order ID: #${vm.orderId}", fontWeight = FontWeight.Bold, color = RestroTokens.coral, fontSize = 17.sp)

                    Card(
                        colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Estimated Arrival", color = RestroTokens.muted, fontSize = 13.sp)
                                Text("25 - 35 mins", fontWeight = FontWeight.Bold, color = RestroTokens.green)
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Email Confirmation", color = RestroTokens.muted, fontSize = 13.sp)
                                Text(vm.profile?.email ?: "aarav@example.com", fontSize = 12.sp, color = RestroTokens.coral)
                            }
                            Text(
                                "An itemized bill and invoice notification has been dispatched to your email address.",
                                fontSize = 12.sp,
                                color = RestroTokens.muted
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Action("Track Live Order on Map") {
                        vm.route = "Tracking"
                        vm.track()
                    }
                    TextButton(onClick = { vm.route = "Home" }) {
                        Text("Continue Discovering Dishes")
                    }
                }
            }
        }
        "Tracking" -> {
            val order = vm.orders.find { it.id == vm.orderId } ?: vm.orders.firstOrNull()
            Page {
                Heading("Live Order Tracking", "Order #${order?.id ?: vm.orderId}")

                // Interactive Animated Route Map with 10s Heartbeat & OSM integration
                OrderTrackingRouteMap(order = order, vm = vm)

                Card(
                    colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(order?.restaurantName ?: "Restaurant", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Surface(
                                color = RestroTokens.coral.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    order?.status ?: "In Progress",
                                    color = RestroTokens.coral,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Status Steps
                        val statuses = listOf("Confirmed", "Preparing", "Out for delivery", "Delivered")
                        val currentStatusIndex = statuses.indexOf(order?.status).coerceAtLeast(0)

                        statuses.forEachIndexed { i, s ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(
                                    if (i <= currentStatusIndex) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    null,
                                    tint = if (i <= currentStatusIndex) RestroTokens.green else RestroTokens.muted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    s,
                                    fontWeight = if (i == currentStatusIndex) FontWeight.Bold else FontWeight.Normal,
                                    color = if (i <= currentStatusIndex) RestroTokens.text else RestroTokens.muted,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                // Delivery Partner Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(46.dp).clip(CircleShape).background(RestroTokens.coral.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.TwoWheeler, null, tint = RestroTokens.coral)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Ramesh Kumar", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Delivery Partner · KA-03-EM-8821", fontSize = 12.sp, color = RestroTokens.muted)
                            Text("Vaccinated & temperature verified", fontSize = 11.sp, color = RestroTokens.green)
                        }
                        val context = androidx.compose.ui.platform.LocalContext.current
                        IconButton(onClick = {
                            try {
                                val dialIntent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                                    data = android.net.Uri.parse("tel:+919811234567")
                                }
                                context.startActivity(dialIntent)
                            } catch (_: Exception) {}
                        }) {
                            Icon(Icons.Default.Phone, "Call Rider", tint = RestroTokens.green)
                        }
                    }
                }

                if (order != null) {
                    Text("Delivery Destination", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${order.address.label}: ${order.address.line}, ${order.address.city}", color = RestroTokens.muted, fontSize = 13.sp)
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.track() },
                        enabled = !vm.busy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh ETA")
                    }
                    if (order?.status != "Delivered" && order?.status != "Cancelled") {
                        OutlinedButton(
                            onClick = { if (order != null) vm.cancelOrder(order.id) },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Cancel", color = Color.Red.copy(alpha = 0.8f))
                        }
                    }
                }

                TextButton(onClick = { vm.route = "Orders" }) {
                    Text("View All Past Orders")
                }
            }
        }
        else -> { // "Orders" list
            Page {
                Heading("Order History", "Your past orders and deliveries")

                if (vm.orders.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.ReceiptLong, null, modifier = Modifier.size(48.dp), tint = RestroTokens.muted)
                        Text("No orders placed yet", color = RestroTokens.muted)
                        Action("Explore Restaurants") { vm.route = "Home" }
                    }
                } else {
                    vm.orders.forEach { order ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(order.restaurantName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Surface(
                                        color = if (order.status == "Delivered") RestroTokens.green.copy(alpha = 0.2f)
                                        else if (order.status == "Cancelled") Color.Red.copy(alpha = 0.2f)
                                        else RestroTokens.coral.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            order.status,
                                            color = if (order.status == "Delivered") RestroTokens.green
                                            else if (order.status == "Cancelled") Color.Red
                                            else RestroTokens.coral,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text("Order #${order.id} · ${money(order.quote.total)} · ${order.quote.lines.size} items", color = RestroTokens.muted, fontSize = 13.sp)

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            vm.orderId = order.id
                                            vm.route = "Tracking"
                                            vm.track()
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Track Map")
                                    }
                                    Button(
                                        onClick = { vm.reorder(order) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.surface)
                                    ) {
                                        Icon(Icons.Default.Replay, null, modifier = Modifier.size(14.dp), tint = RestroTokens.coral)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Reorder", color = RestroTokens.text)
                                    }
                                    Button(
                                        onClick = {
                                            vm.orderId = order.id
                                            vm.route = "Review"
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Review")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 5. Addresses Screen
@Composable
fun AddressesScreen(vm: DeliveryViewModel) {
    if (vm.route == "Edit address") {
        var label by rememberSaveable { mutableStateOf("Home") }
        var line by rememberSaveable { mutableStateOf("") }
        var city by rememberSaveable { mutableStateOf("Mumbai") }
        var postcode by rememberSaveable { mutableStateOf("560103") }
        var phone by rememberSaveable { mutableStateOf(vm.profile?.phone ?: "+91 98765 43210") }

        Page {
            Heading("Save Delivery Address", "Where should we deliver your food?")

            // Interactive Mini Map Pin Selector
            Text("Tap to Pin Exact Location", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            AddressPickerMiniMap(
                onLocationSelected = { lat, lng, landmark ->
                    line = if (line.isBlank()) "Near $landmark" else "$line, $landmark"
                }
            )

            Field("Label (e.g. Home, Office, Gym)", label, { label = it })
            Field("House / Flat / Street address", line, { line = it })
            Field("City", city, { city = it })
            Field("PIN / Postal Code", postcode, { postcode = it })
            Field("Contact Phone Number", phone, { phone = it })

            Action(
                label = "Save Address",
                enabled = line.isNotBlank() && postcode.isNotBlank() && phone.isNotBlank() && !vm.busy
            ) {
                vm.saveAddress(
                    Address(
                        label = label.ifBlank { "Home" },
                        line = line,
                        city = city,
                        postcode = postcode,
                        phone = phone
                    )
                )
            }
        }
        return
    }

    Page {
        Heading("Saved Addresses", "Manage your delivery locations")

        if (vm.addresses.isEmpty()) {
            Text("No saved addresses yet.", color = RestroTokens.muted)
        } else {
            vm.addresses.forEach { addr ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(RestroTokens.surface)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.LocationOn, null, tint = RestroTokens.coral)
                    Column(Modifier.weight(1f)) {
                        Text(addr.label, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("${addr.line}, ${addr.city} ${addr.postcode}", color = RestroTokens.muted, fontSize = 13.sp)
                        Text("Ph: ${addr.phone}", color = RestroTokens.muted, fontSize = 12.sp)
                    }
                    IconButton(onClick = { vm.deleteAddress(addr.id) }) {
                        Icon(Icons.Default.Delete, "Delete", tint = Color.Red.copy(alpha = 0.7f))
                    }
                }
            }
        }

        Action("Add New Address") {
            vm.route = "Edit address"
        }
    }
}

// 6. Reviews Screen
@Composable
fun ReviewsScreen(vm: DeliveryViewModel) {
    if (vm.route == "Review") {
        var rating by rememberSaveable { mutableStateOf(5) }
        var reviewText by rememberSaveable { mutableStateOf("") }

        Page {
            Heading("Write a Review", "How was your food and delivery?")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { star ->
                    IconButton(onClick = { rating = star }) {
                        Icon(
                            if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "$star stars",
                            tint = if (star <= rating) Color(0xFFFFB800) else RestroTokens.muted,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = reviewText,
                onValueChange = { reviewText = it },
                label = { Text("Your comments (e.g. delicious food, quick delivery)") },
                modifier = Modifier.fillMaxWidth().height(140.dp),
                shape = RoundedCornerShape(16.dp)
            )

            Action(
                label = "Submit Review",
                enabled = reviewText.isNotBlank() && !vm.busy
            ) {
                vm.review(rating, reviewText)
            }
        }
        return
    }

    Page {
        Heading("Customer Reviews", "${vm.reviews.size} reviews from real diners")

        if (vm.reviews.isEmpty()) {
            Text("No reviews yet. Be the first to leave one!", color = RestroTokens.muted)
        } else {
            vm.reviews.forEach { r ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(r.author, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Row {
                                (1..r.rating).forEach {
                                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFB800), modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                        Text(r.text, fontSize = 13.sp, color = RestroTokens.text)
                    }
                }
            }
        }
    }
}

// 7. Profile Screen
@Composable
fun ProfileScreen(vm: DeliveryViewModel, onOpenStaffPortal: (() -> Unit)? = null) {
    val p = vm.profile
    var showChangePasswordDialog by rememberSaveable { mutableStateOf(false) }
    var oldPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordChangeMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isPasswordChangeSuccess by rememberSaveable { mutableStateOf(false) }

    Page {
        Heading("Your Profile", "Manage preferences, addresses and RBAC role")

        Card(
            colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(p?.name ?: "Valued Diner", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Surface(
                        color = if (p?.role?.isStaff == true) RestroTokens.green.copy(alpha = 0.2f)
                        else RestroTokens.coral.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = p?.role?.label?.uppercase() ?: "CUSTOMER",
                            color = if (p?.role?.isStaff == true) RestroTokens.green else RestroTokens.coral,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Text(p?.email ?: "", color = RestroTokens.muted, fontSize = 14.sp)
                if (!p?.phone.isNullOrBlank()) {
                    Text("Phone: ${p?.phone}", color = RestroTokens.muted, fontSize = 13.sp)
                }
            }
        }

        Text("Shortcuts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    if (p?.role?.isStaff == true && onOpenStaffPortal != null) {
                        onOpenStaffPortal()
                    } else {
                        vm.route = "ForbiddenStaff"
                    }
                }
                .background(RestroTokens.surface)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storefront, null, tint = RestroTokens.coral)
                Column {
                    Text("Staff Operations Portal (POS / KDS / Admin)", fontWeight = FontWeight.Medium)
                    Text(
                        if (p?.role?.isStaff == true) "Authorized as ${p.role.label}"
                        else "Requires Waiter, Kitchen Chef, or Admin role",
                        fontSize = 11.sp,
                        color = RestroTokens.muted
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = RestroTokens.muted)
        }

        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { vm.route = "Addresses" }
                .background(RestroTokens.surface)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, tint = RestroTokens.coral)
                Text("Saved Delivery Addresses", fontWeight = FontWeight.Medium)
            }
            Icon(Icons.Default.ChevronRight, null, tint = RestroTokens.muted)
        }

        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { vm.route = "Orders" }
                .background(RestroTokens.surface)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ReceiptLong, null, tint = RestroTokens.coral)
                Text("Past Orders & Invoices", fontWeight = FontWeight.Medium)
            }
            Icon(Icons.Default.ChevronRight, null, tint = RestroTokens.muted)
        }

        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    vm.loadUserTableBookings()
                    vm.route = "MyBookings"
                }
                .background(RestroTokens.surface)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EventSeat, null, tint = RestroTokens.coral)
                Column {
                    Text("My Table Bookings", fontWeight = FontWeight.Medium)
                    Text("View, inspect and cancel dine-in reservations", fontSize = 11.sp, color = RestroTokens.muted)
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = RestroTokens.muted)
        }

        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    oldPassword = ""
                    newPassword = ""
                    confirmPassword = ""
                    passwordChangeMessage = null
                    showChangePasswordDialog = true
                }
                .background(RestroTokens.surface)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LockReset, null, tint = RestroTokens.coral)
                Column {
                    Text("Change & Update Password", fontWeight = FontWeight.Medium)
                    Text("Update your account security password", fontSize = 11.sp, color = RestroTokens.muted)
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = RestroTokens.muted)
        }

        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { vm.route = "Favorites" }
                .background(RestroTokens.surface)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Favorite, null, tint = RestroTokens.coral)
                Text("Saved Favourite Restaurants", fontWeight = FontWeight.Medium)
            }
            Icon(Icons.Default.ChevronRight, null, tint = RestroTokens.muted)
        }

        // Accessibility setting
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(RestroTokens.surface)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("High Contrast UI", fontWeight = FontWeight.Medium)
                Text("Reduces transparent glass effects", fontSize = 12.sp, color = RestroTokens.muted)
            }
            Switch(
                checked = p?.reducedGlass == true,
                onCheckedChange = { checked ->
                    if (p != null) {
                        vm.saveProfile(p.copy(reducedGlass = checked))
                    }
                }
            )
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = vm::logout,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Icon(Icons.Default.Logout, null)
            Spacer(Modifier.width(8.dp))
            Text("Sign Out", fontWeight = FontWeight.Bold)
        }

        if (showChangePasswordDialog) {
            AlertDialog(
                onDismissRequest = { showChangePasswordDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.LockReset, null, tint = RestroTokens.coral)
                        Text("Change Password", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = oldPassword,
                            onValueChange = { oldPassword = it },
                            label = { Text("Current Password") },
                            placeholder = { Text("••••••••") },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("New Password (min 6 chars)") },
                            placeholder = { Text("••••••••") },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Confirm New Password") },
                            placeholder = { Text("••••••••") },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        passwordChangeMessage?.let { msg ->
                            Text(
                                msg,
                                color = if (isPasswordChangeSuccess) RestroTokens.green else Color(0xFFEF4444),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newPassword != confirmPassword) {
                                passwordChangeMessage = "New passwords do not match"
                                isPasswordChangeSuccess = false
                                return@Button
                            }
                            vm.changePassword(oldPassword, newPassword) { ok, msg ->
                                isPasswordChangeSuccess = ok
                                passwordChangeMessage = msg
                                if (ok) {
                                    oldPassword = ""
                                    newPassword = ""
                                    confirmPassword = ""
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Update Password")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showChangePasswordDialog = false }) {
                        Text("Cancel")
                    }
                },
                containerColor = RestroTokens.surface,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

// 8. 403 Forbidden: Staff Access Required Screen
@Composable
fun ForbiddenStaffScreen(vm: DeliveryViewModel, onOpenStaffPortal: (() -> Unit)? = null) {
    Page {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFDC2626).copy(alpha = 0.14f)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.GppBad,
                    contentDescription = "403 Forbidden",
                    tint = Color(0xFFF87171),
                    modifier = Modifier.size(54.dp)
                )
                Text(
                    "403 Forbidden: Staff Access Required",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = Color(0xFFF87171)
                )
                Text(
                    "Your account (${vm.profile?.email ?: "Customer"}) is currently authenticated with the '${vm.profile?.role?.label ?: "Customer"}' role. Access to the POS Cashier, Kitchen Display System (KDS), and Admin Analytics is strictly restricted to authorized restaurant staff.",
                    fontSize = 13.sp,
                    color = RestroTokens.text
                )
            }
        }

        if (onOpenStaffPortal != null) {
            Action("Unlock with 4-Digit Staff PIN Vault") {
                onOpenStaffPortal()
            }
        }

        Action("Return to Customer Home") {
            vm.route = "Home"
        }

        OutlinedButton(
            onClick = vm::logout,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.SwitchAccount, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Sign Out & Switch to Staff Account", fontWeight = FontWeight.Bold)
        }
    }
}

