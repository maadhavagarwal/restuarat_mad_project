package com.restaurant.mobile.delivery

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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

@Composable
fun AuthScreen(vm: DeliveryViewModel) {
    var register by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    Page {
        vm.catalog.dishes.firstOrNull()?.let {
            Photo(
                it.image,
                it.name,
                Modifier.fillMaxWidth().height(220.dp).clip(RestroTokens.shape),
            )
        }
        Heading(
            if (register) "Your next favourite\nstarts here." else "Something good\nis on its way.",
            if (register) "Create your Restro account." else "Sign in to discover, save and order.",
        )
        if (register) Field("Your name", name, { name = it })
        Field("Email", email, { email = it })
        Field("Password · at least 10 characters", password, { password = it }, true)
        Action(
            if (register) "Create account" else "Sign in",
            !vm.busy &&
                email.isNotBlank() &&
                password.length >= 10 &&
                (!register || name.trim().length >= 2),
        ) {
            vm.auth(email, password, name, register)
        }
        TextButton(onClick = { register = !register }) {
            Text(
                if (register) "Already have an account? Sign in"
                else "New to Restro? Create an account"
            )
        }
    }
}

@Composable
fun Discovery(vm: DeliveryViewModel) {
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
    val restaurants =
        vm.catalog.restaurants.filter {
            (vm.route != "Favorites" || it.id in vm.favorites) &&
                (vm.query.isBlank() ||
                    it.name.contains(vm.query, true) ||
                    it.cuisine.contains(vm.query, true) ||
                    vm.catalog.dishes.any { d ->
                        d.restaurantId == it.id && d.name.contains(vm.query, true)
                    })
        }
    LazyColumn(
        Modifier.fillMaxSize().testTag("discovery-list"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Heading(
                if (detail) restaurant?.name ?: "Restaurant"
                else if (vm.route == "Favorites") "Your favourites" else "What sounds\ngood today?",
                if (detail) restaurant?.description else "Discover a delicious reason to stay in.",
            )
        }
        if (detail && restaurant != null)
            item {
                RestaurantCard(restaurant, vm, false)
                Row {
                    TextButton(onClick = { vm.route = "Reviews" }) {
                        Text("Read reviews (${vm.reviews.size})")
                    }
                    val context = LocalContext.current
                    TextButton(
                        onClick = {
                            val uri =
                                Uri.parse(
                                    "geo:${restaurant.latitude},${restaurant.longitude}?q=${Uri.encode(restaurant.address)}"
                                )
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                                .onFailure { vm.error = "No map application is installed." }
                        }
                    ) {
                        Text("View on map")
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
                items(vm.catalog.dishes.map { it.category }.distinct()) { category ->
                    FilterChip(
                        selected = vm.category == category,
                        onClick = { vm.category = category },
                        label = { Text(category) },
                    )
                }
            }
        }
        if (!detail) {
            items(restaurants, key = { "restaurant-${it.id}" }) { RestaurantCard(it, vm, true) }
            if (restaurants.isEmpty())
                item {
                    Text(
                        if (vm.route == "Favorites")
                            "Save a restaurant using its heart button to find it here."
                        else "No restaurants found. Try another search.",
                        color = RestroTokens.muted,
                    )
                }
        }
        if (vm.route != "Favorites") {
            item {
                Text(
                    if (detail) "On the menu" else "Find your next favourite",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            items(dishes, key = { "dish-${it.id}" }) { dish ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable {
                            vm.dishId = dish.id
                            vm.restaurantId = dish.restaurantId
                            vm.route = "Food"
                        }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (dish.vegetarian) "● VEGETARIAN" else "NON-VEGETARIAN",
                            fontSize = 10.sp,
                            color = if (dish.vegetarian) RestroTokens.green else RestroTokens.muted,
                        )
                        Text(dish.name, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Text(
                            dish.description,
                            color = RestroTokens.muted,
                            maxLines = 2,
                            fontSize = 13.sp,
                        )
                        Text(money(dish.price), fontWeight = FontWeight.Bold)
                        Text(
                            if (dish.available) "Customize +" else "Unavailable",
                            color = RestroTokens.coral,
                        )
                    }
                    Photo(dish.image, dish.name, Modifier.size(116.dp).clip(RestroTokens.shape))
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
}

@Composable
fun RestaurantCard(r: Restaurant, vm: DeliveryViewModel, clickable: Boolean) {
    val layer = rememberGraphicsLayer()
    val backdrop = remember(layer) { Backdrop(layer) }
    Box(
        Modifier.fillMaxWidth()
            .height(268.dp)
            .clip(RestroTokens.shape)
            .then(if (clickable) Modifier.clickable { vm.openRestaurant(r.id) } else Modifier)
    ) {
        Photo(r.image, r.name, Modifier.fillMaxSize().capture(backdrop))
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
        Glass(
            Modifier.align(Alignment.BottomCenter).padding(10.dp).fillMaxWidth(),
            backdrop,
            vm.profile?.reducedGlass == true,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(r.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(r.cuisine, fontSize = 13.sp)
                Text(
                    if (r.open)
                        "${r.deliveryMinutes}–${r.deliveryMinutes+10} min · ${money(r.deliveryFee)} delivery"
                    else "Currently closed",
                    color = RestroTokens.muted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

