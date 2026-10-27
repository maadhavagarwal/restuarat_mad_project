package com.restaurant.mobile.delivery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
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
                if (dish.vegetarian) RestroI18n.t("dish_veg_tag") else RestroI18n.t("dish_non_veg_tag"),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (dish.vegetarian) RestroTokens.green else RestroTokens.muted
            )
            Text(money(dish.price), fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }

        Heading(dish.localizedName(), dish.localizedDescription())

        if (dish.addons.isNotEmpty()) {
            Text(RestroI18n.t("customize_addons"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
            Text(RestroI18n.t("quantity_label"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Quantity(
                q = quantity,
                minus = { if (quantity > 1) quantity-- },
                plus = { if (quantity < 20) quantity++ }
            )
        }

        Spacer(Modifier.height(8.dp))

        Action(
            label = RestroI18n.t("add_to_cart_btn") + " · " + money(totalPrice),
            enabled = dish.available && !vm.busy
        ) {
            vm.add(dish, selectedAddons.toList(), quantity)
            vm.back()
        }

        Spacer(Modifier.height(16.dp))
        DishRatingsAndReviewsSection(dish = dish, vm = vm)
    }
}

@Composable
fun DishRatingsAndReviewsSection(dish: Dish, vm: DeliveryViewModel) {
    var showWriteDialog by rememberSaveable { mutableStateOf(false) }
    var userRating by rememberSaveable { mutableIntStateOf(5) }
    var reviewComment by rememberSaveable { mutableStateOf("") }
    var selectedSort by rememberSaveable { mutableStateOf("newest") }

    val dishReviews = remember(dish.id, vm.reviews.size) {
        val existing = vm.reviews.filter { it.dishId == dish.id || (it.dishName.isNotBlank() && it.dishName.equals(dish.name, ignoreCase = true)) }
        if (existing.isNotEmpty()) existing
        else {
            listOf(
                Review(
                    orderId = "ORD-SEED-1",
                    restaurantId = dish.restaurantId,
                    author = "Priya Sharma",
                    rating = 5,
                    text = if (RestroI18n.currentLanguage == "hi") "बेहद स्वादिष्ट और ताज़ा! स्वाद बिल्कुल लाजवाब था।" else if (RestroI18n.currentLanguage == "mr") "खूपच चवदार आणि ताजे अन्न! अप्रतिम अनुभव." else "Incredible taste and perfectly balanced authentic spices. Highly recommended!",
                    at = "Yesterday",
                    dishId = dish.id,
                    dishName = dish.name,
                    verifiedPurchase = true
                ),
                Review(
                    orderId = "ORD-SEED-2",
                    restaurantId = dish.restaurantId,
                    author = "Rohan Kulkarni",
                    rating = 5,
                    text = if (RestroI18n.currentLanguage == "hi") "गरमागरम और समय पर डिलीवरी। पैकेजिंग भी बहुत अच्छी थी।" else if (RestroI18n.currentLanguage == "mr") "गरमागरम आणि वेळेवर डिलिव्हरी मिळाली. उत्तम पॅकेजिंग." else "Delivered piping hot with secure leak-proof packaging. Top quality!",
                    at = "3 days ago",
                    dishId = dish.id,
                    dishName = dish.name,
                    verifiedPurchase = true
                )
            )
        }
    }

    val sortedReviews = remember(dishReviews, selectedSort) {
        when (selectedSort) {
            "highest" -> dishReviews.sortedByDescending { it.rating }
            "lowest" -> dishReviews.sortedBy { it.rating }
            else -> dishReviews
        }
    }

    val avgRating = remember(dishReviews) {
        if (dishReviews.isEmpty()) 4.8 else (dishReviews.map { it.rating }.average() * 10).toInt() / 10.0
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, RestroTokens.border)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        RestroI18n.t("reviews_section_title"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = RestroTokens.text
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFB800), modifier = Modifier.size(16.dp))
                        Text(
                            "$avgRating / 5.0",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = RestroTokens.text
                        )
                        Text(
                            "(${dishReviews.size} ${RestroI18n.t("customer_reviews_count")})",
                            fontSize = 12.sp,
                            color = RestroTokens.muted
                        )
                    }
                }

                Button(
                    onClick = { showWriteDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(RestroI18n.t("write_review_btn"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RestroTokens.canvas)
                }
            }

            HorizontalDivider(color = RestroTokens.border)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                sortedReviews.forEach { rev ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(RestroTokens.surfaceElevated)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(rev.author, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RestroTokens.text)
                                if (rev.verifiedPurchase) {
                                    Surface(
                                        color = RestroTokens.green.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            "✓ " + RestroI18n.t("verified_purchase_tag"),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = RestroTokens.green,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(rev.at, fontSize = 10.sp, color = RestroTokens.muted)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            (1..5).forEach { star ->
                                Icon(
                                    if (star <= rev.rating) Icons.Default.Star else Icons.Default.StarBorder,
                                    null,
                                    tint = if (star <= rev.rating) Color(0xFFFFB800) else RestroTokens.muted,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        Text(rev.text, fontSize = 12.sp, color = RestroTokens.text.copy(alpha = 0.9f))
                    }
                }
            }
        }
    }

    if (showWriteDialog) {
        AlertDialog(
            onDismissRequest = { showWriteDialog = false },
            title = {
                Text(RestroI18n.t("write_review_btn"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = RestroTokens.text)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(dish.localizedName(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = RestroTokens.coral)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..5).forEach { star ->
                            IconButton(onClick = { userRating = star }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    if (star <= userRating) Icons.Default.Star else Icons.Default.StarBorder,
                                    null,
                                    tint = if (star <= userRating) Color(0xFFFFB800) else RestroTokens.muted,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = reviewComment,
                        onValueChange = { reviewComment = it },
                        placeholder = { Text(RestroI18n.t("review_input_hint")) },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (reviewComment.isNotBlank()) {
                            val newRev = Review(
                                orderId = "ORD-${System.currentTimeMillis() % 10000}",
                                restaurantId = dish.restaurantId,
                                author = vm.profile?.name ?: "Priya Sharma",
                                rating = userRating,
                                text = reviewComment,
                                at = "Just now",
                                dishId = dish.id,
                                dishName = dish.name,
                                verifiedPurchase = true
                            )
                            vm.addReview(newRev)
                            reviewComment = ""
                            showWriteDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(RestroI18n.t("submit_btn"), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWriteDialog = false }) {
                    Text(RestroI18n.t("cancel_btn"), color = RestroTokens.muted)
                }
            },
            containerColor = RestroTokens.surface,
            shape = RoundedCornerShape(18.dp)
        )
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
                Heading(RestroI18n.t("cart_empty_title"), RestroI18n.t("cart_empty_sub"))
                Action(RestroI18n.t("browse_restaurants")) { vm.route = "Home" }
            }
        }
        return
    }

    var couponInput by rememberSaveable { mutableStateOf(vm.cart.coupon) }

    Page {
        Heading(RestroI18n.t("cart_title"), "${vm.cart.lines.sumOf { it.quantity }} ${RestroI18n.t("cart_subtitle")}")

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
                    Text(dish?.localizedName() ?: "Dish", fontWeight = FontWeight.Bold, fontSize = 15.sp)
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

        // Coupon Feedback Messages
        if (vm.couponError != null) {
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Text(vm.couponError ?: "", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        } else if (vm.couponMessage != null) {
            Surface(
                color = RestroTokens.green.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = RestroTokens.green, modifier = Modifier.size(16.dp))
                    Text(vm.couponMessage ?: "", color = RestroTokens.green, fontSize = 12.sp)
                }
            }
        }

        // Applied Coupon Indicator
        if (vm.cart.coupon.isNotBlank()) {
            val q = vm.quote ?: vm.computeQuote()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(RestroTokens.coral.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = RestroTokens.coral, modifier = Modifier.size(16.dp))
                    Column {
                        Text(
                            "Coupon '${vm.cart.coupon}' applied",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = RestroTokens.coral
                        )
                        if (q.discount > 0L) {
                            Text(
                                "Saved ${money(q.discount)} on this order",
                                fontSize = 11.sp,
                                color = RestroTokens.muted
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        couponInput = ""
                        vm.removeCoupon()
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Remove", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Available Coupons
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val coupons = listOf(
                "WELCOME50" to "50% OFF up to ₹50",
                "FEAST" to "₹50 OFF on ₹200+",
                "FEAST100" to "₹100 OFF on ₹400+",
                "FREEDEL" to "Free delivery"
            )
            coupons.forEach { (code, desc) ->
                val isSelected = vm.cart.coupon.equals(code, ignoreCase = true)
                SuggestionChip(
                    onClick = {
                        couponInput = code
                        vm.applyCoupon(code)
                    },
                    label = {
                        Text(
                            "$code · $desc",
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) RestroTokens.coral else Color.Unspecified
                        )
                    },
                    icon = {
                        Icon(
                            if (isSelected) Icons.Default.CheckCircle else Icons.Default.LocalOffer,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = RestroTokens.coral
                        )
                    }
                )
            }
        }

        HorizontalDivider(color = RestroTokens.border)

        // Bill Breakdown
        Text(RestroI18n.t("bill_summary"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = RestroTokens.text)

        val q = vm.quote ?: vm.computeQuote()

        BillRow(RestroI18n.t("bill_subtotal"), q.subtotal)
        BillRow(RestroI18n.t("delivery_partner_fee"), q.delivery)
        BillRow(RestroI18n.t("bill_tax"), q.tax)
        if (q.discount > 0L) {
            val couponName = if (vm.cart.coupon.isNotBlank()) " (${vm.cart.coupon})" else ""
            BillRow(RestroI18n.t("bill_discount") + couponName, -q.discount)
        }
        HorizontalDivider(color = RestroTokens.border)
        BillRow(RestroI18n.t("bill_total"), q.total)

        Spacer(Modifier.height(8.dp))

        Action(RestroI18n.t("btn_checkout"), enabled = !vm.busy) {
            vm.loadQuote()
        }
    }
}

// 3. Checkout Screen
@Composable
fun CheckoutScreen(vm: DeliveryViewModel) {
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
                Heading(RestroI18n.t("empty_cart_title"), RestroI18n.t("empty_cart_desc"))
                Action(RestroI18n.t("browse_menus")) { vm.route = "Home" }
            }
        }
        return
    }

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
        Heading(RestroI18n.t("checkout_title"), RestroI18n.t("checkout_sub"))

        Text(RestroI18n.t("delivery_address"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

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
                            Text(RestroI18n.t("default_address"), fontWeight = FontWeight.Bold)
                            Text("Flat 402, SV Road, Goregaon West, Mumbai", color = RestroTokens.muted, fontSize = 13.sp)
                        }
                        Icon(Icons.Default.CheckCircle, null, tint = RestroTokens.green)
                    }
                    TextButton(onClick = { vm.route = "Edit address" }) {
                        Icon(Icons.Default.AddLocationAlt, null)
                        Spacer(Modifier.width(6.dp))
                        Text(RestroI18n.t("add_custom_address"))
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
                Text(RestroI18n.t("add_another_address"))
            }
        }

        HorizontalDivider(color = RestroTokens.border)

        Text(RestroI18n.t("select_payment_method"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = RestroTokens.text)

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

        HorizontalDivider(color = RestroTokens.border)

        // Applied Coupon Summary & Remove in Checkout
        if (vm.cart.coupon.isNotBlank()) {
            val q = vm.quote ?: vm.computeQuote()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(RestroTokens.coral.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = RestroTokens.coral, modifier = Modifier.size(16.dp))
                    Column {
                        Text(
                            "Coupon '${vm.cart.coupon}' applied",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = RestroTokens.coral
                        )
                        if (q.discount > 0L) {
                            Text(
                                "Saved ${money(q.discount)} on this order",
                                fontSize = 11.sp,
                                color = RestroTokens.muted
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        vm.removeCoupon()
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Remove", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        val effectiveQuote = vm.quote ?: vm.computeQuote()
        val quoteTotal = effectiveQuote.total
        BillRow(RestroI18n.t("bill_total"), quoteTotal)

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
            totalAmount = (vm.quote ?: vm.computeQuote()).total,
            selectedAddressId = addrId,
            initialMethod = selectedPaymentMethod,
            onPaymentSuccess = { methodLabel, txId ->
                showPaymentSheet = false
                vm.checkout(addrId, methodLabel)
            },
            onDismiss = { showPaymentSheet = false }
        )
    }
}

// 4. Orders, Confirmation & Tracking Screen (Scoped to Assigned Customer & Enhanced Post-Order Experience)
@Composable
fun OrdersScreen(vm: DeliveryViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current

    when (vm.route) {
        "Confirmation" -> {
            val confirmedOrder = vm.orders.find { it.id == vm.orderId }
                ?: vm.orders.firstOrNull { vm.profile == null || it.userId.isBlank() || it.userId == vm.profile?.id }

            Page {
                Column(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier.size(80.dp).clip(CircleShape).background(RestroTokens.green.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(52.dp), tint = RestroTokens.green)
                    }
                    Heading(RestroI18n.t("order_confirmed_title"), RestroI18n.t("order_confirmed_sub"))

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(RestroTokens.coral.copy(alpha = 0.12f))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("${RestroI18n.t("order_id_label")}: #${confirmedOrder?.id ?: vm.orderId}", fontWeight = FontWeight.Bold, color = RestroTokens.coral, fontSize = 15.sp)
                    }

                    // ETA & Restaurant Header
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Restaurant, null, tint = RestroTokens.coral, modifier = Modifier.size(18.dp))
                                    Text(confirmedOrder?.restaurantName ?: "Spice Route Mumbai", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                                Surface(
                                    color = RestroTokens.green.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        "25 - 35 mins",
                                        fontWeight = FontWeight.Bold,
                                        color = RestroTokens.green,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = RestroTokens.muted.copy(alpha = 0.2f), thickness = 0.5.dp)

                            // Delivery Address Preview
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.LocationOn, null, tint = RestroTokens.coral, modifier = Modifier.size(18.dp))
                                Column {
                                    Text(
                                        "${RestroI18n.t("deliver_to_label")}: ${confirmedOrder?.address?.label ?: RestroI18n.t("delivery_address")}",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        "${confirmedOrder?.address?.line ?: "Goregaon West"}, ${confirmedOrder?.address?.city ?: "Mumbai"}",
                                        color = RestroTokens.muted,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    // Itemized Order Summary Card
                    if (confirmedOrder?.quote != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(RestroI18n.t("order_summary"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                confirmedOrder.quote.lines.forEach { line ->
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("${line.quantity}x ${line.name}", fontSize = 13.sp, color = RestroTokens.text)
                                        Text("₹${line.total / 100}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                                HorizontalDivider(color = RestroTokens.muted.copy(alpha = 0.2f), thickness = 0.5.dp)

                                // Price Breakdown
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(RestroI18n.t("bill_subtotal"), fontSize = 12.sp, color = RestroTokens.muted)
                                    Text("₹${confirmedOrder.quote.subtotal / 100}", fontSize = 12.sp)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(RestroI18n.t("delivery_partner_fee"), fontSize = 12.sp, color = RestroTokens.muted)
                                    Text(
                                        if (confirmedOrder.quote.delivery == 0L) "FREE" else "₹${confirmedOrder.quote.delivery / 100}",
                                        fontSize = 12.sp,
                                        color = if (confirmedOrder.quote.delivery == 0L) RestroTokens.green else RestroTokens.text
                                    )
                                }
                                if (confirmedOrder.quote.discount > 0L) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(RestroI18n.t("coupon_savings"), fontSize = 12.sp, color = RestroTokens.green)
                                        Text("-₹${confirmedOrder.quote.discount / 100}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RestroTokens.green)
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(RestroI18n.t("bill_tax"), fontSize = 12.sp, color = RestroTokens.muted)
                                    Text("₹${confirmedOrder.quote.tax / 100}", fontSize = 12.sp)
                                }
                                HorizontalDivider(color = RestroTokens.muted.copy(alpha = 0.2f), thickness = 0.5.dp)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(RestroI18n.t("grand_total_paid"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("₹${confirmedOrder.quote.total / 100}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = RestroTokens.coral)
                                }
                                Text(
                                    "Payment Method: ${confirmedOrder.paymentMethod} (${confirmedOrder.paymentStatus})",
                                    fontSize = 11.sp,
                                    color = RestroTokens.muted
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Action(RestroI18n.t("track_live_order_btn")) {
                        vm.route = "Tracking"
                        vm.track(vm.orderId)
                    }
                    TextButton(onClick = { vm.route = "Home" }) {
                        Text(RestroI18n.t("continue_discovering"))
                    }
                }
            }
        }
        "Tracking" -> {
            // Strictly scope live tracking to the currently logged in user's assigned orders
            val activeOrder = vm.orders.find {
                it.id == vm.orderId && (vm.profile == null || it.userId.isBlank() || it.userId == vm.profile?.id)
            } ?: vm.orders.firstOrNull {
                vm.profile == null || it.userId.isBlank() || it.userId == vm.profile?.id
            }

            var showBillDetails by rememberSaveable { mutableStateOf(false) }
            var showSupportDialog by rememberSaveable { mutableStateOf(false) }
            var reviewSubmitted by rememberSaveable(activeOrder?.id) { mutableStateOf(false) }
            var selectedRating by rememberSaveable(activeOrder?.id) { mutableIntStateOf(5) }
            var selectedTip by rememberSaveable(activeOrder?.id) { mutableLongStateOf(30L) }
            var reviewComment by rememberSaveable(activeOrder?.id) { mutableStateOf("") }

            if (activeOrder == null) {
                Page {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Default.ReceiptLong, null, modifier = Modifier.size(56.dp), tint = RestroTokens.muted)
                        Heading("No Active Order to Track", "All your live and completed deliveries appear here.")
                        Action("Explore Restaurants & Dishes") { vm.route = "Home" }
                        TextButton(onClick = { vm.route = "Orders" }) {
                            Text("View Past Orders History")
                        }
                    }
                }
            } else {
                Page {
                    Heading(RestroI18n.t("live_order_tracking"), "Order #${activeOrder.id} · ${activeOrder.restaurantName.localizedRestaurantName()}")

                    // Live Refreshed ETA Header Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(RestroTokens.coral.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (activeOrder.status == "Delivered") Icons.Default.CheckCircle else Icons.Default.Timer,
                                        null,
                                        tint = if (activeOrder.status == "Delivered") RestroTokens.green else RestroTokens.coral,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = if (activeOrder.status == "Delivered") RestroI18n.t("order_status") else RestroI18n.t("estimated_delivery_time"),
                                        fontSize = 11.sp,
                                        color = RestroTokens.muted
                                    )
                                    Text(
                                        text = if (activeOrder.status == "Delivered") RestroI18n.t("delivered_at_doorstep") else "${vm.liveRiderEtaMins} mins (${String.format(java.util.Locale.US, "%.1f km away", vm.liveRiderDistanceKm)})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (activeOrder.status == "Delivered") RestroTokens.green else RestroTokens.text
                                    )
                                }
                            }
                            FilledTonalButton(
                                onClick = {
                                    vm.track(activeOrder.id)
                                    android.widget.Toast.makeText(
                                        context,
                                        "ETA Refreshed: ${vm.liveRiderEtaMins} mins",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(RestroI18n.t("refresh"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Proximity Alert Banner (Triggers when rider is under 500m or at gate)
                    if (activeOrder.status != "Delivered" && (vm.liveRiderDistanceKm <= 0.5 || vm.liveRiderStatus.contains("gate"))) {
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF10B981)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.TwoWheeler, null, tint = Color.White, modifier = Modifier.size(22.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Rider is at Your Gate!", fontWeight = FontWeight.Bold, color = Color(0xFF047857), fontSize = 14.sp)
                                    Text("Ramesh Kumar is within 500m. Please meet your delivery partner at the gate.", fontSize = 12.sp, color = Color(0xFF065F46))
                                }
                                IconButton(onClick = {
                                    try {
                                        val dialIntent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                                            data = android.net.Uri.parse("tel:+919811234567")
                                        }
                                        context.startActivity(dialIntent)
                                    } catch (_: Exception) {}
                                }) {
                                    Icon(Icons.Default.Phone, "Call Rider", tint = Color(0xFF047857))
                                }
                            }
                        }
                    }

                    // Interactive Animated Route Map with 10s Heartbeat & OSM integration
                    OrderTrackingRouteMap(order = activeOrder, vm = vm)

                    // Vertical Order Lifecycle Stepper
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(RestroI18n.t("delivery_progress"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Surface(
                                    color = if (activeOrder.status == "Delivered") RestroTokens.green.copy(alpha = 0.15f)
                                           else RestroTokens.coral.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        activeOrder.status,
                                        color = if (activeOrder.status == "Delivered") RestroTokens.green else RestroTokens.coral,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            val stages = listOf(
                                Triple("Placed", "Order Placed", "Order received by restaurant"),
                                Triple("Preparing", "Kitchen Preparing", "Restaurant accepted & Chef is cooking your fresh dishes"),
                                Triple("Ready", "Food Prepared", "Packed & ready for delivery partner pickup"),
                                Triple("Out for delivery", "Out for Delivery", "${activeOrder.riderName.ifBlank { "Delivery Partner" }} is on the way"),
                                Triple("Delivered", "Delivered", "Delivered at doorstep. Enjoy your meal!")
                            )
                            val normalizedStatus = activeOrder.status.trim().lowercase()
                            val activeIdx = when (normalizedStatus) {
                                "placed", "received", "pending" -> 0
                                "confirmed", "accepted_by_admin" -> 0
                                "preparing" -> 1
                                "ready" -> 2
                                "accepted", "picked up", "out for delivery" -> 3
                                "delivered" -> 4
                                "cancelled", "canceled" -> -1
                                else -> 0
                            }

                            // Dynamic Live Progress Bar
                            val progressTarget = if (activeIdx < 0) 0f else (activeIdx.toFloat() / (stages.size - 1).toFloat())
                            val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = progressTarget,
                                animationSpec = androidx.compose.animation.core.tween(durationMillis = 600)
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = if (activeOrder.status.equals("Delivered", ignoreCase = true)) RestroTokens.green else RestroTokens.coral,
                                    trackColor = RestroTokens.muted.copy(alpha = 0.2f)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Step ${(activeIdx + 1).coerceAtLeast(1)} of ${stages.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = RestroTokens.muted
                                    )
                                    Text(
                                        text = "${(animatedProgress * 100).toInt()}% Completed",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (activeOrder.status.equals("Delivered", ignoreCase = true)) RestroTokens.green else RestroTokens.coral
                                    )
                                }
                            }

                            stages.forEachIndexed { idx, (_, title, subtitle) ->
                                val isDone = idx <= activeIdx
                                val isCurrent = idx == activeIdx
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(if (isDone) RestroTokens.green else RestroTokens.muted.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (isDone) Icons.Default.Check else Icons.Default.RadioButtonUnchecked,
                                            null,
                                            tint = if (isDone) Color.White else RestroTokens.muted,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            title,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isDone) RestroTokens.text else RestroTokens.muted,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            subtitle,
                                            fontSize = 11.sp,
                                            color = if (isCurrent) RestroTokens.coral else RestroTokens.muted
                                        )
                                    }
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
                                Modifier.size(46.dp).clip(CircleShape).background(RestroTokens.coral.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.TwoWheeler, null, tint = RestroTokens.coral)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Ramesh Kumar", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Delivery Partner · KA-03-EM-8821 (Hero Splendor)", fontSize = 12.sp, color = RestroTokens.muted)
                                Text("Vaccinated · Contactless delivery verified", fontSize = 11.sp, color = RestroTokens.green)
                            }
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

                    // Delivery Destination Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, null, tint = RestroTokens.coral, modifier = Modifier.size(20.dp))
                            Column {
                                Text(activeOrder.address.label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("${activeOrder.address.line}, ${activeOrder.address.city}", color = RestroTokens.muted, fontSize = 12.sp)
                            }
                        }
                    }

                    // Expandable Itemized Bill & Receipt Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { showBillDetails = !showBillDetails },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Receipt, null, tint = RestroTokens.coral, modifier = Modifier.size(16.dp))
                                    Text("Order Bill & Receipt Details", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Text(
                                    if (showBillDetails) "Hide ▲" else "View (₹${activeOrder.quote.total / 100}) ▼",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = RestroTokens.coral
                                )
                            }

                            if (showBillDetails) {
                                HorizontalDivider(color = RestroTokens.muted.copy(alpha = 0.2f), thickness = 0.5.dp)
                                activeOrder.quote.lines.forEach { line ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${line.quantity}x ${line.name}", fontSize = 12.sp)
                                        Text("₹${line.total / 100}", fontSize = 12.sp)
                                    }
                                }
                                HorizontalDivider(color = RestroTokens.muted.copy(alpha = 0.2f), thickness = 0.5.dp)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Subtotal", fontSize = 12.sp, color = RestroTokens.muted)
                                    Text("₹${activeOrder.quote.subtotal / 100}", fontSize = 12.sp)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Delivery Partner Fee", fontSize = 12.sp, color = RestroTokens.muted)
                                    Text(if (activeOrder.quote.delivery == 0L) "FREE" else "₹${activeOrder.quote.delivery / 100}", fontSize = 12.sp)
                                }
                                if (activeOrder.quote.discount > 0L) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Coupon Savings", fontSize = 12.sp, color = RestroTokens.green)
                                        Text("-₹${activeOrder.quote.discount / 100}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RestroTokens.green)
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Taxes (5%)", fontSize = 12.sp, color = RestroTokens.muted)
                                    Text("₹${activeOrder.quote.tax / 100}", fontSize = 12.sp)
                                }
                                HorizontalDivider(color = RestroTokens.muted.copy(alpha = 0.2f), thickness = 0.5.dp)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Total Amount Paid", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("₹${activeOrder.quote.total / 100}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = RestroTokens.coral)
                                }
                            }
                        }
                    }

                    // Post-Delivery Experience: Rating & Reorder (Active once order is Delivered)
                    if (activeOrder.status == "Delivered") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.ThumbUp, null, tint = RestroTokens.green, modifier = Modifier.size(20.dp))
                                    Text("Rate Your Dining Experience", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }

                                if (!reviewSubmitted) {
                                    Text("How was the food taste and delivery speed?", fontSize = 12.sp, color = RestroTokens.muted)

                                    // Star Rating Row
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        for (star in 1..5) {
                                            IconButton(
                                                onClick = { selectedRating = star },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    if (star <= selectedRating) Icons.Default.Star else Icons.Default.StarBorder,
                                                    contentDescription = "$star Stars",
                                                    tint = Color(0xFFFFB800),
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Quick Tip Rider Selection
                                    Text("Add Tip for Delivery Partner Ramesh:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(0L to "No Tip", 20L to "₹20", 30L to "₹30", 50L to "₹50").forEach { (tipAmt, tipLabel) ->
                                            FilterChip(
                                                selected = selectedTip == tipAmt,
                                                onClick = { selectedTip = tipAmt },
                                                label = { Text(tipLabel, fontSize = 11.sp) }
                                            )
                                        }
                                    }

                                    OutlinedTextField(
                                        value = reviewComment,
                                        onValueChange = { reviewComment = it },
                                        placeholder = { Text("Write a quick review (optional)", fontSize = 12.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp)
                                    )

                                    Button(
                                        onClick = {
                                            reviewSubmitted = true
                                            vm.submitOrderReview(activeOrder.id, selectedRating, reviewComment, selectedTip)
                                            android.widget.Toast.makeText(context, "Thank you for your feedback! ⭐", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.fillMaxWidth().height(44.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.green)
                                    ) {
                                        Text("Submit Feedback & Rating", fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Surface(
                                        color = RestroTokens.green.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "Feedback recorded (${selectedRating}★). Thank you for helping us improve!",
                                            fontSize = 12.sp,
                                            color = RestroTokens.green,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }

                                // Quick One-Click Reorder Button
                                OutlinedButton(
                                    onClick = {
                                        vm.reorder(activeOrder)
                                        android.widget.Toast.makeText(context, "Items added to cart!", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Reorder These Dishes Again", fontWeight = FontWeight.Bold, color = RestroTokens.coral)
                                }
                            }
                        }
                    }

                    // Action Controls
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                vm.track(activeOrder.id)
                                android.widget.Toast.makeText(
                                    context,
                                    "ETA refreshed: ${vm.liveRiderEtaMins} mins remaining",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            enabled = !vm.busy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Refresh ETA")
                        }

                        if (activeOrder.status != "Delivered" && activeOrder.status != "Cancelled") {
                            OutlinedButton(
                                onClick = {
                                    if (activeOrder.status == "Confirmed") {
                                        vm.cancelOrder(activeOrder.id)
                                    } else {
                                        showSupportDialog = true
                                    }
                                },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Cancel", color = Color.Red.copy(alpha = 0.8f))
                            }
                        }
                    }

                    TextButton(onClick = { vm.route = "Orders" }) {
                        Text("View All Past Orders")
                    }

                    // Cancellation Support Guard Modal Dialog
                    if (showSupportDialog) {
                        AlertDialog(
                            onDismissRequest = { showSupportDialog = false },
                            icon = { Icon(Icons.Default.SupportAgent, null, tint = RestroTokens.coral, modifier = Modifier.size(36.dp)) },
                            title = { Text("Cancellation Policy", fontWeight = FontWeight.Bold) },
                            text = {
                                Text(
                                    "Your order is already ${activeOrder.status.lowercase()}. Fresh ingredients have been prepared by the kitchen.\n\nTo cancel or modify, please contact restaurant support directly.",
                                    fontSize = 13.sp,
                                    color = RestroTokens.muted
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showSupportDialog = false
                                        try {
                                            val dialIntent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                                                data = android.net.Uri.parse("tel:+919811234567")
                                            }
                                            context.startActivity(dialIntent)
                                        } catch (_: Exception) {}
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral)
                                ) {
                                    Icon(Icons.Default.Phone, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Call Support")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showSupportDialog = false }) {
                                    Text("Close")
                                }
                            }
                        )
                    }
                }
            }
        }
        else -> { // "Orders" list
            Page {
                Heading(RestroI18n.t("orders_title"), RestroI18n.t("orders_subtitle"))

                val currentUid = vm.profile?.id.orEmpty()
                val userScopedOrders = if (vm.profile?.role?.isStaff == true) {
                    vm.orders.filterNot { it.id.startsWith("ORD-SEED") }
                } else if (currentUid.isNotBlank()) {
                    vm.orders.filter { it.userId == currentUid && !it.id.startsWith("ORD-SEED") }
                } else {
                    emptyList()
                }

                if (userScopedOrders.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.ReceiptLong, null, modifier = Modifier.size(48.dp), tint = RestroTokens.muted)
                        Text(if (vm.profile == null) "Sign in to view synced past orders" else "No orders placed yet", color = RestroTokens.muted)
                        if (vm.profile == null) {
                            OutlinedButton(
                                onClick = { vm.isGuestMode = false },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Sign In to Account", color = RestroTokens.coral, fontWeight = FontWeight.Bold)
                            }
                        }
                        Action("Explore Restaurants") { vm.route = "Home" }
                    }
                } else {
                    userScopedOrders.forEach { order ->
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
        val startLat = if (vm.currentLatitude != 0.0) vm.currentLatitude else 19.0596
        val startLng = if (vm.currentLongitude != 0.0) vm.currentLongitude else 72.8295
        var latitude by rememberSaveable { mutableStateOf<Double?>(startLat) }
        var longitude by rememberSaveable { mutableStateOf<Double?>(startLng) }

        Page {
            Heading(RestroI18n.t("save_delivery_address_title"), RestroI18n.t("save_delivery_address_subtitle"))

            // Interactive Mini Map Pin Selector
            Text(RestroI18n.t("tap_to_pin_location"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            AddressPickerMiniMap(
                initialLat = latitude ?: 19.0596,
                initialLng = longitude ?: 72.8295,
                onLocationSelected = { lat, lng, landmark ->
                    latitude = lat
                    longitude = lng
                    line = landmark
                    if (landmark.contains("Mumbai", ignoreCase = true)) {
                        city = "Mumbai"
                    }
                    val pinMatch = Regex("[1-9][0-9]{5}").find(landmark)
                    if (pinMatch != null) {
                        postcode = pinMatch.value
                    }
                }
            )

            // Pinned Coordinates Confirmation Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = RestroTokens.surface,
                border = BorderStroke(1.dp, RestroTokens.coral.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Place, null, tint = RestroTokens.coral, modifier = Modifier.size(22.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            RestroI18n.t("pinned_map_coordinates"),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = RestroTokens.coral
                        )
                        Text(
                            if (latitude != null && longitude != null)
                                "Lat: " + String.format(java.util.Locale.US, "%.5f", latitude) + ", Lng: " + String.format(java.util.Locale.US, "%.5f", longitude)
                            else RestroI18n.t("no_pin_selected"),
                            fontSize = 11.sp,
                            color = RestroTokens.muted
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val lat = if (vm.currentLatitude != 0.0) vm.currentLatitude else 19.0596
                            val lng = if (vm.currentLongitude != 0.0) vm.currentLongitude else 72.8295
                            latitude = lat
                            longitude = lng
                            line = vm.currentLandmark.ifBlank { "Current GPS Location, Mumbai" }
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(RestroI18n.t("my_gps_btn"), fontSize = 11.sp, color = RestroTokens.coral)
                    }
                }
            }

            Field(RestroI18n.t("label_field_hint"), label, { label = it })
            Field(RestroI18n.t("address_line_hint"), line, { line = it })
            Field(RestroI18n.t("city_field_hint"), city, { city = it })
            Field(RestroI18n.t("postcode_field_hint"), postcode, { postcode = it })
            Field(RestroI18n.t("auth_phone_number"), phone, { if (it.length <= 10) phone = it.filter { ch -> ch.isDigit() } })

            Action(
                label = RestroI18n.t("save_address_btn"),
                enabled = line.isNotBlank() && postcode.isNotBlank() && phone.isNotBlank() && !vm.busy
            ) {
                vm.saveAddress(
                    Address(
                        label = label.ifBlank { "Home" },
                        line = line,
                        city = city,
                        postcode = postcode,
                        phone = phone,
                        latitude = latitude,
                        longitude = longitude
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
                        if (addr.latitude != null && addr.longitude != null) {
                            Text("📍 Pin: ${"%.4f".format(addr.latitude)}, ${"%.4f".format(addr.longitude)}", color = RestroTokens.coral, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
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

// 7. Profile Screen (Revamped Modern Account Experience)
@Composable
fun ProfileScreen(vm: DeliveryViewModel, onOpenStaffPortal: (() -> Unit)? = null) {
    val p = vm.profile
    var showEditProfileDialog by rememberSaveable { mutableStateOf(false) }
    var editName by rememberSaveable { mutableStateOf(p?.name ?: "") }
    var editPhone by rememberSaveable { mutableStateOf(p?.phone ?: "") }

    var showChangePasswordDialog by rememberSaveable { mutableStateOf(false) }
    var oldPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordChangeMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isPasswordChangeSuccess by rememberSaveable { mutableStateOf(false) }

    var showLogoutConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showHelpSupportDialog by rememberSaveable { mutableStateOf(false) }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }

    val isDark = RestroTokens.isDark

    Page {
        // 1. Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    RestroI18n.t("my_account"),
                    style = MaterialTheme.typography.headlineLarge,
                    color = RestroTokens.text,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    RestroI18n.t("manage_profile_sub"),
                    fontSize = 13.sp,
                    color = RestroTokens.muted
                )
            }

            if (p?.role?.isStaff == true && onOpenStaffPortal != null) {
                Button(
                    onClick = onOpenStaffPortal,
                    colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Storefront,
                        contentDescription = "Open Staff Portal",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if ((p?.role ?: UserRole.CUSTOMER) == UserRole.KIOSK) "Kiosk POS" else "Staff Portal",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // 2. User Hero Profile Card
        Card(
            colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, RestroTokens.border, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Profile Avatar Circle with Gradient & Initials
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(RestroTokens.coral, Color(0xFFE11D48))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val initial = (p?.name?.firstOrNull() ?: if (p == null) 'G' else 'U').uppercaseChar()
                        Text(
                            text = initial.toString(),
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (p != null) p.name.ifBlank { "Valued Diner" } else "Guest Diner",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = RestroTokens.text,
                            maxLines = 1
                        )

                        Spacer(Modifier.height(4.dp))

                        Surface(
                            color = if (p == null) RestroTokens.muted.copy(alpha = 0.2f)
                            else if (p?.role?.isStaff == true) RestroTokens.green.copy(alpha = 0.2f)
                            else RestroTokens.coral.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = (p?.role?.label ?: "GUEST MODE").uppercase(),
                                color = if (p == null) RestroTokens.muted
                                else if (p?.role?.isStaff == true) RestroTokens.green
                                else RestroTokens.coral,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (p != null) {
                        IconButton(
                            onClick = {
                                editName = p.name
                                editPhone = p.phone
                                showEditProfileDialog = true
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(RestroTokens.surfaceElevated)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Profile",
                                tint = RestroTokens.coral,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = RestroTokens.border)

                // Contact Details or Guest Prompt
                if (p != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (p.email.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.Email,
                                    contentDescription = null,
                                    tint = RestroTokens.muted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = p.email,
                                    fontSize = 13.sp,
                                    color = RestroTokens.text
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = null,
                                tint = RestroTokens.muted,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (p.phone.isNotBlank()) p.phone else "Add mobile number for delivery tracking",
                                fontSize = 13.sp,
                                color = if (p.phone.isNotBlank()) RestroTokens.text else RestroTokens.muted
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "You are currently browsing as a guest. Sign in to save addresses, track live deliveries, and collect dining rewards.",
                            fontSize = 12.sp,
                            color = RestroTokens.muted
                        )
                        Button(
                            onClick = { vm.isGuestMode = false },
                            colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sign In or Register", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // 3. Quick Account Stats Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, RestroTokens.border, RoundedCornerShape(16.dp))
                    .clickable { vm.route = "Orders" }
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "${vm.orders.size}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = RestroTokens.coral
                    )
                    Text(
                        RestroI18n.t("orders_count_stat"),
                        fontSize = 11.sp,
                        color = RestroTokens.muted
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, RestroTokens.border, RoundedCornerShape(16.dp))
                    .clickable {
                        vm.loadUserTableBookings()
                        vm.route = "MyBookings"
                    }
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "${vm.userTableBookings.size}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3B82F6)
                    )
                    Text(
                        RestroI18n.t("bookings_count_stat"),
                        fontSize = 11.sp,
                        color = RestroTokens.muted
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, RestroTokens.border, RoundedCornerShape(16.dp))
                    .clickable { vm.route = "Addresses" }
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "${vm.addresses.size}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = RestroTokens.green
                    )
                    Text(
                        RestroI18n.t("addresses_count_stat"),
                        fontSize = 11.sp,
                        color = RestroTokens.muted
                    )
                }
            }
        }

        // 4. APPEARANCE & THEME (Light & Dark Mode)
        Text(
            RestroI18n.t("profile_sec_appearance"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = RestroTokens.text
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, RestroTokens.border, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            if (isDark) Icons.Default.Nightlight else Icons.Default.WbSunny,
                            contentDescription = "Theme Mode",
                            tint = RestroTokens.coral,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                RestroI18n.t("theme_mode"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = RestroTokens.text
                            )
                            Text(
                                if (isDark) RestroI18n.t("dark_mode_sub") else RestroI18n.t("light_mode_sub"),
                                fontSize = 12.sp,
                                color = RestroTokens.muted
                            )
                        }
                    }

                    Switch(
                        checked = isDark,
                        onCheckedChange = { checked ->
                            RestroTokens.isDark = checked
                            if (p != null) {
                                vm.saveProfile(p.copy(isDarkMode = checked))
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = RestroTokens.coral,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFCBD5E1)
                        )
                    )
                }

                HorizontalDivider(color = RestroTokens.border)

                // App Language Selector (Clean 1-Tap Switcher)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Translate,
                                contentDescription = "Language",
                                tint = RestroTokens.coral,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                RestroI18n.t("language_settings_title"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = RestroTokens.text
                            )
                        }
                        Text(
                            "${RestroI18n.currentLanguage.uppercase()} · ${AppLanguage.fromCode(RestroI18n.currentLanguage).nativeName}",
                            fontSize = 12.sp,
                            color = RestroTokens.coral,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Instant 1-Tap Language Switcher Pills in Settings
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppLanguage.entries.forEach { lang ->
                            val selected = RestroI18n.currentLanguage == lang.code
                            Surface(
                                onClick = { vm.selectLanguage(lang.code, isInitialOnboarding = false) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) RestroTokens.coral else RestroTokens.surfaceElevated,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) RestroTokens.coral else RestroTokens.border
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        lang.nativeName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (selected) Color.White else RestroTokens.text
                                    )
                                    Text(
                                        lang.englishName,
                                        fontSize = 10.sp,
                                        color = if (selected) Color.White.copy(alpha = 0.85f) else RestroTokens.muted
                                    )
                                }
                            }
                        }
                    }
                }

                // Guided App Tour Replay
                Surface(
                    onClick = {
                        vm.route = "Home"
                        vm.startAppTour()
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = RestroTokens.surfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Explore,
                                contentDescription = "Tour",
                                tint = RestroTokens.coral,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    RestroI18n.t("replay_tour_title"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = RestroTokens.text
                                )
                                Text(
                                    RestroI18n.t("guided_tour_sub"),
                                    fontSize = 11.sp,
                                    color = RestroTokens.muted
                                )
                            }
                        }
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Start tour",
                            tint = RestroTokens.coral,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 5. Orders & Dining Section
        Text(
            RestroI18n.t("profile_sec_orders"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = RestroTokens.text
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, RestroTokens.border, RoundedCornerShape(20.dp))
        ) {
            Column {
                AccountOptionRow(
                    icon = Icons.Default.ReceiptLong,
                    title = RestroI18n.t("past_orders_title"),
                    subtitle = RestroI18n.t("past_orders_sub"),
                    onClick = { vm.route = "Orders" }
                )
                HorizontalDivider(color = RestroTokens.border, modifier = Modifier.padding(horizontal = 16.dp))
                AccountOptionRow(
                    icon = Icons.Default.EventSeat,
                    title = RestroI18n.t("dine_in_bookings_title"),
                    subtitle = RestroI18n.t("dine_in_bookings_sub"),
                    onClick = {
                        vm.loadUserTableBookings()
                        vm.route = "MyBookings"
                    }
                )
                HorizontalDivider(color = RestroTokens.border, modifier = Modifier.padding(horizontal = 16.dp))
                AccountOptionRow(
                    icon = Icons.Default.Favorite,
                    title = RestroI18n.t("fav_restaurants_title"),
                    subtitle = RestroI18n.t("fav_restaurants_sub"),
                    onClick = { vm.route = "Favorites" }
                )
            }
        }

        // 6. Security & Addresses Section
        Text(
            RestroI18n.t("profile_sec_security"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = RestroTokens.text
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, RestroTokens.border, RoundedCornerShape(20.dp))
        ) {
            Column {
                AccountOptionRow(
                    icon = Icons.Default.LocationOn,
                    title = RestroI18n.t("saved_addresses_title"),
                    subtitle = RestroI18n.t("saved_addresses_sub"),
                    onClick = { vm.route = "Addresses" }
                )
                HorizontalDivider(color = RestroTokens.border, modifier = Modifier.padding(horizontal = 16.dp))
                AccountOptionRow(
                    icon = Icons.Default.LockReset,
                    title = RestroI18n.t("change_password_title"),
                    subtitle = RestroI18n.t("change_password_sub"),
                    onClick = {
                        oldPassword = ""
                        newPassword = ""
                        confirmPassword = ""
                        passwordChangeMessage = null
                        showChangePasswordDialog = true
                    }
                )
            }
        }

        // 7. Support & About
        Text(
            RestroI18n.t("profile_sec_help"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = RestroTokens.text
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, RestroTokens.border, RoundedCornerShape(20.dp))
        ) {
            Column {
                AccountOptionRow(
                    icon = Icons.Default.SupportAgent,
                    title = RestroI18n.t("customer_support_title"),
                    subtitle = RestroI18n.t("customer_support_sub"),
                    onClick = { showHelpSupportDialog = true }
                )
                HorizontalDivider(color = RestroTokens.border, modifier = Modifier.padding(horizontal = 16.dp))
                AccountOptionRow(
                    icon = Icons.Default.Info,
                    title = RestroI18n.t("about_enterprise_title"),
                    subtitle = RestroI18n.t("about_enterprise_sub"),
                    onClick = { showAboutDialog = true }
                )
            }
        }

        // 8. Staff Portal Link (if staff or staff portal available)
        if (p?.role?.isStaff == true || onOpenStaffPortal != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = RestroTokens.green.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, RestroTokens.green.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                    .clickable { onOpenStaffPortal?.invoke() }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        Icons.Default.RestaurantMenu,
                        contentDescription = null,
                        tint = RestroTokens.green,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            RestroI18n.t("staff_portal_title"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = RestroTokens.text
                        )
                        Text(
                            RestroI18n.t("staff_portal_sub"),
                            fontSize = 12.sp,
                            color = RestroTokens.muted
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = RestroTokens.green)
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // 9. Sign Out / Sign In Button
        if (p != null) {
            OutlinedButton(
                onClick = { showLogoutConfirmDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFEF4444)
                ),
                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Logout, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(RestroI18n.t("sign_out_btn"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        } else {
            Button(
                onClick = { vm.isGuestMode = false },
                colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Login, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(RestroI18n.t("sign_in_register_btn"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        // Space for floating bottom navigation bar
        Spacer(Modifier.height(84.dp))

        // --- Dialogs ---

        // Edit Profile Dialog
        if (showEditProfileDialog) {
            AlertDialog(
                onDismissRequest = { showEditProfileDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Edit, null, tint = RestroTokens.coral)
                        Text(RestroI18n.t("edit_profile_title"), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = RestroTokens.text)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text(RestroI18n.t("full_name_label")) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editPhone,
                            onValueChange = { if (it.length <= 10) editPhone = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Mobile Phone Number (10 Digits)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (p != null) {
                                vm.saveProfile(p.copy(name = editName.trim(), phone = editPhone.trim()))
                            }
                            showEditProfileDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(RestroI18n.t("save_changes_btn"), color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditProfileDialog = false }) {
                        Text("Cancel", color = RestroTokens.muted)
                    }
                },
                containerColor = RestroTokens.surface,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Change Password Dialog
        if (showChangePasswordDialog) {
            AlertDialog(
                onDismissRequest = { showChangePasswordDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.LockReset, null, tint = RestroTokens.coral)
                        Text(RestroI18n.t("change_password_title"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = RestroTokens.text)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = oldPassword,
                            onValueChange = { oldPassword = it },
                            label = { Text(RestroI18n.t("current_password_label")) },
                            placeholder = { Text("••••••••") },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text(RestroI18n.t("new_password_label")) },
                            placeholder = { Text("••••••••") },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text(RestroI18n.t("confirm_password_label")) },
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
                        Text(RestroI18n.t("update_password_btn"), color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showChangePasswordDialog = false }) {
                        Text("Cancel", color = RestroTokens.muted)
                    }
                },
                containerColor = RestroTokens.surface,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Logout Confirmation Dialog
        if (showLogoutConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutConfirmDialog = false },
                title = {
                    Text(RestroI18n.t("logout_dialog_title"), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = RestroTokens.text)
                },
                text = {
                    Text(
                        RestroI18n.t("logout_dialog_desc"),
                        fontSize = 13.sp,
                        color = RestroTokens.muted
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showLogoutConfirmDialog = false
                            vm.logout()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(RestroI18n.t("logout_dialog_title"), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutConfirmDialog = false }) {
                        Text(RestroI18n.t("stay_signed_in"), color = RestroTokens.text)
                    }
                },
                containerColor = RestroTokens.surface,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Customer Support Dialog
        if (showHelpSupportDialog) {
            AlertDialog(
                onDismissRequest = { showHelpSupportDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.SupportAgent, null, tint = RestroTokens.coral)
                        Text("24x7 Customer Care", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = RestroTokens.text)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Our customer concierge team is available round-the-clock for real-time delivery tracking help, meal quality inquiries, and reservation assistance.",
                            fontSize = 13.sp,
                            color = RestroTokens.text
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = RestroTokens.surfaceElevated),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("📞 Toll-Free Helpline: 1800-RESTRO-CARE", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = RestroTokens.coral)
                                Text("✉️ Email Support: care@restro.app", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = RestroTokens.text)
                                Text("⚡ Instant Response: < 3 mins avg. wait time", fontSize = 11.sp, color = RestroTokens.green)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showHelpSupportDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Done", color = Color.White)
                    }
                },
                containerColor = RestroTokens.surface,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // About Dialog
        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                title = {
                    Text("About Restro", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = RestroTokens.text)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Restro Enterprise Restaurant Management Suite", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = RestroTokens.text)
                        Text("Version: 2.4.0 (Production Rescue Release)", fontSize = 12.sp, color = RestroTokens.muted)
                        Text("Crafted with Jetpack Compose & Express Backend with zero-simulation security and real-time live order dispatching.", fontSize = 12.sp, color = RestroTokens.muted)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showAboutDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Close", color = Color.White)
                    }
                },
                containerColor = RestroTokens.surface,
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

@Composable
private fun AccountOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(RestroTokens.coral.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = RestroTokens.coral,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = RestroTokens.text
            )
            Text(
                subtitle,
                fontSize = 11.sp,
                color = RestroTokens.muted,
                maxLines = 1
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = RestroTokens.muted,
            modifier = Modifier.size(18.dp)
        )
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

