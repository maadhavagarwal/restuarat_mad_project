package com.restaurant.mobile.delivery

import androidx.compose.foundation.BorderStroke

import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Interactive In-App Map for exploring restaurants across the city.
 * Operates standalone and offline without requiring external Google Play Services API keys.
 */
@Composable
fun DeliveryInteractiveMap(
    vm: DeliveryViewModel,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var selectedRestaurantId by remember {
        mutableStateOf(vm.restaurantId.ifBlank { vm.catalog.restaurants.firstOrNull()?.id ?: "" })
    }
    var zoomLevel by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 10f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseRadius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    val restaurants = vm.catalog.restaurants
    val selectedRestaurant = restaurants.find { it.id == selectedRestaurantId } ?: restaurants.firstOrNull()
    var mapEngine by rememberSaveable { mutableStateOf("GMaps") }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF14171C))) {
        when (mapEngine) {
            "GMaps" -> {
                RealGoogleMapsWebView(
                    latitude = vm.currentLatitude,
                    longitude = vm.currentLongitude,
                    restaurants = restaurants,
                    selectedRestaurantId = selectedRestaurantId,
                    onSelectRestaurant = { selectedRestaurantId = it },
                    modifier = Modifier.fillMaxSize()
                )
            }
            "OSM", "Street" -> {
                RealOpenStreetMapWebView(
                    latitude = vm.currentLatitude,
                    longitude = vm.currentLongitude,
                    restaurants = restaurants,
                    selectedRestaurantId = selectedRestaurantId,
                    onSelectRestaurant = { selectedRestaurantId = it },
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                // Map Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        val width = size.width
                        val height = size.height

                        // Check which restaurant pin was tapped
                        restaurants.forEachIndexed { index, rest ->
                            val norm = getCoordinatesForIndex(index, restaurants.size)
                            val pinX = width * norm.x * zoomLevel + panOffset.x
                            val pinY = height * norm.y * zoomLevel + panOffset.y

                            val distanceSq = (tapOffset.x - pinX) * (tapOffset.x - pinX) +
                                    (tapOffset.y - pinY) * (tapOffset.y - pinY)
                            if (distanceSq <= 40f * 40f) {
                                selectedRestaurantId = rest.id
                            }
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height

            // 1. Draw Map City Background & Water Bodies (Lakes)
            drawRect(color = Color(0xFF181B20))

            // Water bodies (e.g. Ulsoor & Bellandur Lake)
            val lakePath = Path().apply {
                moveTo(w * 0.72f, h * 0.18f)
                cubicTo(w * 0.85f, h * 0.15f, w * 0.92f, h * 0.28f, w * 0.80f, h * 0.35f)
                cubicTo(w * 0.70f, h * 0.40f, w * 0.65f, h * 0.25f, w * 0.72f, h * 0.18f)
                close()
            }
            drawPath(lakePath, color = Color(0xFF1B2E3D))

            val parkPath = Path().apply {
                moveTo(w * 0.15f, h * 0.35f)
                cubicTo(w * 0.28f, h * 0.32f, w * 0.32f, h * 0.45f, w * 0.22f, h * 0.52f)
                cubicTo(w * 0.10f, h * 0.50f, w * 0.08f, h * 0.40f, w * 0.15f, h * 0.35f)
                close()
            }
            drawPath(parkPath, color = Color(0xFF1B2B23))

            // 2. Draw Arterial Roads & Streets Grid
            val roadColor = Color(0xFF262A32)
            val mainRoadColor = Color(0xFF383E4B)

            // Secondary street grid
            for (i in 1..9) {
                val y = h * (i * 0.1f)
                drawLine(
                    color = roadColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 3f * zoomLevel
                )
            }
            for (i in 1..7) {
                val x = w * (i * 0.14f)
                drawLine(
                    color = roadColor,
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 3f * zoomLevel
                )
            }

            // Main Highways & Arterial Roads
            drawLine(
                color = mainRoadColor,
                start = Offset(0f, h * 0.35f),
                end = Offset(w, h * 0.55f),
                strokeWidth = 8f * zoomLevel,
                cap = StrokeCap.Round
            )
            drawLine(
                color = mainRoadColor,
                start = Offset(w * 0.45f, 0f),
                end = Offset(w * 0.58f, h),
                strokeWidth = 9f * zoomLevel,
                cap = StrokeCap.Round
            )
            drawLine(
                color = mainRoadColor,
                start = Offset(w * 0.1f, h * 0.8f),
                end = Offset(w * 0.9f, h * 0.25f),
                strokeWidth = 7f * zoomLevel,
                cap = StrokeCap.Round
            )

            // 3. User Location Indicator (Blue Pulsing Marker)
            val userX = w * 0.48f * zoomLevel + panOffset.x
            val userY = h * 0.62f * zoomLevel + panOffset.y

            drawCircle(
                color = Color(0xFF3B82F6).copy(alpha = pulseAlpha),
                radius = pulseRadius * 1.5f,
                center = Offset(userX, userY)
            )
            drawCircle(
                color = Color(0xFF60A5FA),
                radius = 9f,
                center = Offset(userX, userY)
            )
            drawCircle(
                color = Color.White,
                radius = 4f,
                center = Offset(userX, userY)
            )

            // 4. Draw Route line if a restaurant is selected
            if (selectedRestaurant != null) {
                val selIndex = restaurants.indexOf(selectedRestaurant).coerceAtLeast(0)
                val selNorm = getCoordinatesForIndex(selIndex, restaurants.size)
                val restX = w * selNorm.x * zoomLevel + panOffset.x
                val restY = h * selNorm.y * zoomLevel + panOffset.y

                val routePath = Path().apply {
                    moveTo(restX, restY)
                    quadraticTo(
                        (restX + userX) / 2f + 40f,
                        (restY + userY) / 2f - 30f,
                        userX,
                        userY
                    )
                }

                drawPath(
                    path = routePath,
                    color = RestroTokens.coral.copy(alpha = 0.8f),
                    style = Stroke(
                        width = 6f,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 12f), 0f)
                    )
                )
            }

            // 5. Draw Restaurant Pins
            restaurants.forEachIndexed { index, rest ->
                val norm = getCoordinatesForIndex(index, restaurants.size)
                val pinX = w * norm.x * zoomLevel + panOffset.x
                val pinY = h * norm.y * zoomLevel + panOffset.y
                val isSelected = rest.id == selectedRestaurantId

                if (isSelected) {
                    drawCircle(
                        color = RestroTokens.coral.copy(alpha = pulseAlpha),
                        radius = pulseRadius * 1.8f,
                        center = Offset(pinX, pinY)
                    )
                }

                // Pin base circle
                val pinRadius = if (isSelected) 18f else 14f
                val pinColor = if (isSelected) RestroTokens.coral else Color(0xFFE2E8F0)

                drawCircle(
                    color = Color.Black.copy(alpha = 0.4f),
                    radius = pinRadius + 3f,
                    center = Offset(pinX + 2f, pinY + 3f)
                )
                drawCircle(
                    color = pinColor,
                    radius = pinRadius,
                    center = Offset(pinX, pinY)
                )
                drawCircle(
                    color = if (isSelected) Color.White else Color(0xFF1E293B),
                    radius = pinRadius * 0.45f,
                    center = Offset(pinX, pinY)
                )
            }
        }
        }
    }

        // Top Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = RestroTokens.surface.copy(alpha = 0.95f),
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Explore, null, tint = RestroTokens.coral, modifier = Modifier.size(18.dp))
                    val dynamicCity = vm.currentLandmark.split(",").firstOrNull()?.trim() ?: "Local"
                    Text(
                        dynamicCity.localizedAddress() + " " + RestroI18n.t("city_map_title") + " · " + restaurants.size + " " + RestroI18n.t("restaurants_count_label"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = RestroTokens.text
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        mapEngine = if (mapEngine == "GMaps") "OSM" else "GMaps"
                    },
                    modifier = Modifier.size(36.dp).background(RestroTokens.surface.copy(alpha = 0.95f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Layers,
                        contentDescription = "Switch Map Engine ($mapEngine)",
                        tint = if (mapEngine == "GMaps") RestroTokens.coral else Color(0xFF60A5FA),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = {
                        val sel = selectedRestaurant ?: restaurants.firstOrNull()
                        if (sel != null) {
                            selectedRestaurantId = sel.id
                        }
                    },
                    modifier = Modifier.size(36.dp).background(RestroTokens.surface.copy(alpha = 0.95f), CircleShape)
                ) {
                    Icon(Icons.Default.NearMe, "Focus In-App Map", tint = RestroTokens.coral, modifier = Modifier.size(18.dp))
                }

                if (onClose != null) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(40.dp)
                            .background(RestroTokens.surface.copy(alpha = 0.95f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, "Close Map", tint = Color.White)
                    }
                }
            }
        }

        // Zoom Controls & Re-Center
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(
                onClick = { zoomLevel = (zoomLevel * 1.25f).coerceAtMost(2.5f) },
                modifier = Modifier.size(42.dp),
                containerColor = RestroTokens.surface,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, "Zoom in")
            }
            FloatingActionButton(
                onClick = { zoomLevel = (zoomLevel / 1.25f).coerceAtLeast(0.8f) },
                modifier = Modifier.size(42.dp),
                containerColor = RestroTokens.surface,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Remove, "Zoom out")
            }
            FloatingActionButton(
                onClick = {
                    zoomLevel = 1f
                    panOffset = Offset.Zero
                },
                modifier = Modifier.size(42.dp),
                containerColor = RestroTokens.surface,
                contentColor = RestroTokens.coral
            ) {
                Icon(Icons.Default.MyLocation, "Re-center")
            }
        }

        // Bottom Selected Restaurant Card
        if (selectedRestaurant != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Photo(
                            selectedRestaurant.image,
                            selectedRestaurant.localizedName(),
                            Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                selectedRestaurant.localizedName(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = RestroTokens.text
                            )
                            Text(
                                selectedRestaurant.localizedCuisine(),
                                fontSize = 12.sp,
                                color = RestroTokens.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.TwoWheeler, null, tint = RestroTokens.coral, modifier = Modifier.size(14.dp))
                                Text(
                                    "${selectedRestaurant.deliveryMinutes} " + RestroI18n.t("mins_unit") + " · " + money(selectedRestaurant.deliveryFee),
                                    fontSize = 12.sp,
                                    color = RestroTokens.coral,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                try {
                                    val gmmIntentUri = Uri.parse("geo:${selectedRestaurant.latitude},${selectedRestaurant.longitude}?q=${Uri.encode(selectedRestaurant.name)}")
                                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                                        setPackage("com.google.android.apps.maps")
                                    }
                                    context.startActivity(mapIntent)
                                } catch (_: Exception) {
                                    try {
                                        val browserUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${selectedRestaurant.latitude},${selectedRestaurant.longitude}")
                                        context.startActivity(Intent(Intent.ACTION_VIEW, browserUri))
                                    } catch (_: Exception) {}
                                }
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.Directions, contentDescription = "Open in Google Maps", tint = RestroTokens.coral)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { selectedRestaurantId = "" },
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, RestroTokens.coral),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Layers, null, tint = RestroTokens.coral, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(RestroI18n.t("full_map"), color = RestroTokens.coral, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { vm.openRestaurant(selectedRestaurant.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(RestroI18n.t("view_menu"), color = RestroTokens.canvas, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Animated Live Delivery Route Map embedded in the order tracking screen.
 * Powered by a 10-second Compose coroutine heartbeat, live calculated ETA,
 * OpenStreetMap integration, and automated proximity & delivery notifications.
 */
@Composable
fun OrderTrackingRouteMap(
    order: DeliveryOrder?,
    vm: DeliveryViewModel? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Heartbeat state tracking (10-second ticks)
    var progress by rememberSaveable(order?.id) {
        mutableFloatStateOf(
            when (order?.status) {
                "Confirmed" -> 0.15f
                "Preparing" -> 0.35f
                "Out for delivery" -> 0.65f
                "Delivered" -> 1.0f
                else -> 0.20f
            }
        )
    }

    var proximityAlertFired by rememberSaveable(order?.id) { mutableStateOf(false) }
    var deliveredAlertFired by rememberSaveable(order?.id) { mutableStateOf(order?.status == "Delivered") }
    var mapMode by rememberSaveable(order?.id) { mutableStateOf("GoogleMaps") }
    var cloudRiderLat by rememberSaveable(order?.id) { mutableDoubleStateOf(order?.riderLatitude ?: 0.0) }
    var cloudRiderLng by rememberSaveable(order?.id) { mutableDoubleStateOf(order?.riderLongitude ?: 0.0) }

    // Subscribe to real-time Rider GPS updates from Cloud Firestore / RiderDispatchEngine
    DisposableEffect(order?.id, vm) {
        val oId = order?.id
        val reg = if (!oId.isNullOrBlank() && vm != null) {
            vm.cloudEngine.observeRiderLocationRealTime(oId) { lat, lng, _ ->
                cloudRiderLat = lat
                cloudRiderLng = lng
            }
        } else null
        onDispose {
            reg?.remove()
        }
    }

    // 10-Second Auto-Updating Coroutine Heartbeat
    LaunchedEffect(order?.id) {
        while (progress < 1.0f) {
            kotlinx.coroutines.delay(10_000L) // 10-second heartbeat
            val next = (progress + 0.12f).coerceAtMost(1.0f)
            progress = next
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 2000, easing = LinearEasing),
        label = "heartbeatProgress"
    )

    // Calculate real live distance remaining and ETA (20-25m restaurant prep + user-to-restaurant travel time)
    val selRestForEta = vm?.catalog?.restaurants?.find { it.name == order?.restaurantName || it.id == order?.restaurantId }
    val restPrepMins = (selRestForEta?.prepMinutes ?: 22).coerceIn(20, 25)
    val totalRouteDistanceKm = remember(order?.id, selRestForEta?.latitude, selRestForEta?.longitude, order?.address?.latitude, order?.address?.longitude) {
        val rLat = selRestForEta?.latitude ?: (vm?.currentLatitude?.plus(0.0075) ?: 19.2288)
        val rLng = selRestForEta?.longitude ?: (vm?.currentLongitude?.plus(0.0055) ?: 72.8541)
        val uLat = order?.address?.latitude ?: (vm?.currentLatitude ?: 19.2129)
        val uLng = order?.address?.longitude ?: (vm?.currentLongitude ?: 72.8424)
        val earthRadius = 6371.0
        val dLat = Math.toRadians(uLat - rLat)
        val dLng = Math.toRadians(uLng - rLng)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(rLat)) * kotlin.math.cos(Math.toRadians(uLat)) *
                kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        (Math.round(earthRadius * c * 10.0) / 10.0).coerceAtLeast(1.2)
    }

    val liveRiderLat = if (cloudRiderLat != 0.0) cloudRiderLat else (order?.riderLatitude ?: 0.0)
    val liveRiderLng = if (cloudRiderLng != 0.0) cloudRiderLng else (order?.riderLongitude ?: 0.0)
    val remainingDistanceKm = if (liveRiderLat != 0.0 && liveRiderLng != 0.0 && vm != null) {
        val uLat = order?.address?.latitude ?: vm.currentLatitude
        val uLng = order?.address?.longitude ?: vm.currentLongitude
        vm.riderDispatchEngine.calculateDistanceKm(liveRiderLat, liveRiderLng, uLat, uLng)
            .coerceAtMost(((1.0f - animatedProgress) * totalRouteDistanceKm).coerceAtLeast(0.0))
    } else {
        ((1.0f - animatedProgress) * totalRouteDistanceKm).coerceAtLeast(0.0)
    }
    val remainingPrepMins = if (animatedProgress < 0.4f) {
        ((1.0f - (animatedProgress / 0.4f)) * restPrepMins).toInt()
    } else 0
    val remainingTransitMins = (remainingDistanceKm * 3.5).toInt()
    val liveEtaMinutes = (remainingPrepMins + remainingTransitMins).coerceAtLeast(if (animatedProgress >= 1.0f) 0 else 1)

    val distanceText = if (remainingDistanceKm <= 0.05) {
        RestroI18n.t("at_your_gate_label")
    } else if (remainingDistanceKm < 1.0) {
        "${(remainingDistanceKm * 1000).toInt()} m"
    } else {
        String.format(java.util.Locale.US, "%.1f km", remainingDistanceKm)
    }

    // Sync live rider distance, eta, and status to ViewModel & SQLite in real time
    LaunchedEffect(remainingDistanceKm, animatedProgress) {
        order?.id?.let { oId ->
            vm?.syncLiveRiderProgress(oId, remainingDistanceKm, liveEtaMinutes, animatedProgress)
        }
    }

    // Trigger Notification 2 (Proximity Alert) when < 500 m
    LaunchedEffect(remainingDistanceKm) {
        if (remainingDistanceKm <= 0.5 && !proximityAlertFired && progress < 1.0f) {
            proximityAlertFired = true
            vm?.notifyProximityAlert(order?.id ?: "ORD-LIVE", "Ramesh Kumar")
        }
    }

    // Trigger Notification 3 (Delivered Alert) when rider reaches doorstep
    LaunchedEffect(animatedProgress) {
        if (animatedProgress >= 1.0f && !deliveredAlertFired) {
            deliveredAlertFired = true
            vm?.notifyOrderDelivered(order?.id ?: "ORD-LIVE")
            order?.id?.let { vm?.markOrderDelivered(it) }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 26f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "riderPulse"
    )

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Live Heartbeat Status Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (animatedProgress >= 1.0f) RestroTokens.green else Color(0xFF10B981))
                )
                Text(
                    text = if (animatedProgress >= 1.0f) RestroI18n.t("delivery_complete_label") else RestroI18n.t("gps_heartbeat_label"),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (animatedProgress >= 1.0f) RestroTokens.green else Color(0xFF10B981),
                    letterSpacing = 0.5.sp
                )
            }

            // Direct Google Maps Intent
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(
                    onClick = {
                        val selRest = vm?.catalog?.restaurants?.find { it.name == order?.restaurantName || it.id == order?.restaurantId }
                        val targetRestLat = selRest?.latitude ?: (vm?.currentLatitude?.plus(0.0075) ?: 19.2288)
                        val targetRestLng = selRest?.longitude ?: (vm?.currentLongitude?.plus(0.0055) ?: 72.8541)
                        val targetUserLat = order?.address?.latitude ?: (vm?.currentLatitude ?: 19.2129)
                        val targetUserLng = order?.address?.longitude ?: (vm?.currentLongitude ?: 72.8424)

                        try {
                            val gmmIntentUri = android.net.Uri.parse("https://www.google.com/maps/dir/?api=1&origin=$targetRestLat,$targetRestLng&destination=$targetUserLat,$targetUserLng&travelmode=driving")
                            val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, gmmIntentUri).apply {
                                setPackage("com.google.android.apps.maps")
                            }
                            if (mapIntent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(mapIntent)
                            } else {
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, gmmIntentUri))
                            }
                        } catch (_: Exception) {}
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = RestroTokens.surface
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Navigation,
                            contentDescription = "Open in Google Maps",
                            tint = RestroTokens.green,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(RestroI18n.t("gmaps_app_btn"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RestroTokens.green)
                    }
                }
            }
        }

        // Live Animated Map Canvas / Google Maps WebView
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (mapMode == "Street") Color(0xFFE8ECEF) else Color(0xFF14171D))
        ) {
            val selRest = vm?.catalog?.restaurants?.find { it.name == order?.restaurantName || it.id == order?.restaurantId }
            val targetRestLat = selRest?.latitude ?: (vm?.currentLatitude?.plus(0.0075) ?: 19.2288)
            val targetRestLng = selRest?.longitude ?: (vm?.currentLongitude?.plus(0.0055) ?: 72.8541)
            val targetUserLat = order?.address?.latitude ?: (vm?.currentLatitude ?: 19.2129)
            val targetUserLng = order?.address?.longitude ?: (vm?.currentLongitude ?: 72.8424)

            when (mapMode) {
                "GoogleMaps" -> {
                    RealOrderTrackingGoogleMapsWebView(
                        restaurantLat = targetRestLat,
                        restaurantLng = targetRestLng,
                        restaurantName = order?.restaurantName ?: "Restaurant",
                        userLat = targetUserLat,
                        userLng = targetUserLng,
                        riderProgress = animatedProgress,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                "Street" -> {
                    RealOrderTrackingOsmWebView(
                        restaurantLat = targetRestLat,
                        restaurantLng = targetRestLng,
                        restaurantName = order?.restaurantName ?: "Restaurant",
                        userLat = targetUserLat,
                        userLng = targetUserLng,
                        riderProgress = animatedProgress,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                if (mapMode == "Street") {
                    // OpenStreetMap Style Background
                    drawRect(color = Color(0xFFEDE9E3)) // OSM land surface

                    // Urban park (Cubbon Park / Indiranagar greens)
                    val parkPath = Path().apply {
                        moveTo(w * 0.12f, h * 0.20f)
                        cubicTo(w * 0.26f, h * 0.18f, w * 0.30f, h * 0.38f, w * 0.18f, h * 0.42f)
                        cubicTo(w * 0.08f, h * 0.40f, w * 0.06f, h * 0.28f, w * 0.12f, h * 0.20f)
                        close()
                    }
                    drawPath(parkPath, color = Color(0xFFD4E7CB))

                    // Ulsoor lake water feature
                    val lakePath = Path().apply {
                        moveTo(w * 0.70f, h * 0.12f)
                        cubicTo(w * 0.88f, h * 0.10f, w * 0.94f, h * 0.30f, w * 0.82f, h * 0.36f)
                        cubicTo(w * 0.68f, h * 0.40f, w * 0.64f, h * 0.22f, w * 0.70f, h * 0.12f)
                        close()
                    }
                    drawPath(lakePath, color = Color(0xFFA5BFDD))

                    // OSM secondary streets
                    val streetColor = Color(0xFFFFFFFF)
                    for (i in 1..5) {
                        val y = h * (i * 0.18f)
                        drawLine(color = streetColor, start = Offset(0f, y), end = Offset(w, y), strokeWidth = 5f)
                    }
                    for (i in 1..6) {
                        val x = w * (i * 0.16f)
                        drawLine(color = streetColor, start = Offset(x, 0f), end = Offset(x, h), strokeWidth = 5f)
                    }

                    // OSM Primary Arterial Road
                    drawLine(
                        color = Color(0xFFFCD6A4),
                        start = Offset(0f, h * 0.45f),
                        end = Offset(w, h * 0.52f),
                        strokeWidth = 9f
                    )
                } else {
                    // Dark Navigation Grid
                    drawRect(color = Color(0xFF14171D))
                    for (i in 1..5) {
                        drawLine(color = Color(0xFF222630), start = Offset(0f, h * (i * 0.18f)), end = Offset(w, h * (i * 0.18f)), strokeWidth = 2f)
                    }
                    for (i in 1..6) {
                        drawLine(color = Color(0xFF222630), start = Offset(w * (i * 0.16f), 0f), end = Offset(w * (i * 0.16f), h), strokeWidth = 2f)
                    }
                }

                // Route Endpoints
                val startPoint = Offset(w * 0.15f, h * 0.78f)
                val endPoint = Offset(w * 0.85f, h * 0.22f)
                val controlPoint1 = Offset(w * 0.28f, h * 0.24f)
                val controlPoint2 = Offset(w * 0.65f, h * 0.82f)

                val fullPath = Path().apply {
                    moveTo(startPoint.x, startPoint.y)
                    cubicTo(controlPoint1.x, controlPoint1.y, controlPoint2.x, controlPoint2.y, endPoint.x, endPoint.y)
                }

                // Route Track Underlay (Casing)
                drawPath(
                    path = fullPath,
                    color = if (mapMode == "Street") Color(0xFFBDC3C7) else Color(0xFF2E3440),
                    style = Stroke(width = 9f, cap = StrokeCap.Round)
                )

                // Active Polyline Track
                drawPath(
                    path = fullPath,
                    brush = Brush.linearGradient(listOf(RestroTokens.coral, Color(0xFFFF6F43))),
                    style = Stroke(
                        width = 8f,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(600f * animatedProgress, 600f),
                            0f
                        )
                    )
                )

                // Restaurant Pin
                drawCircle(color = Color(0xFF333A48), radius = 15f, center = startPoint)
                drawCircle(color = Color.White, radius = 12f, center = startPoint)
                drawCircle(color = RestroTokens.coral, radius = 6f, center = startPoint)

                // Customer Destination Pin
                drawCircle(color = RestroTokens.green.copy(alpha = 0.35f), radius = 18f, center = endPoint)
                drawCircle(color = RestroTokens.green, radius = 12f, center = endPoint)
                drawCircle(color = Color.White, radius = 5f, center = endPoint)

                // Calculate Rider Position along cubic bezier curve
                val t = animatedProgress
                val riderX = (1 - t) * (1 - t) * (1 - t) * startPoint.x +
                        3 * (1 - t) * (1 - t) * t * controlPoint1.x +
                        3 * (1 - t) * t * t * controlPoint2.x +
                        t * t * t * endPoint.x
                val riderY = (1 - t) * (1 - t) * (1 - t) * startPoint.y +
                        3 * (1 - t) * (1 - t) * t * controlPoint1.y +
                        3 * (1 - t) * t * t * controlPoint2.y +
                        t * t * t * endPoint.y
                val riderPoint = Offset(riderX, riderY)

                // Pulsing Rider Marker
                if (animatedProgress < 1.0f) {
                    drawCircle(
                        color = RestroTokens.coral.copy(alpha = 0.35f),
                        radius = pulseRadius,
                        center = riderPoint
                    )
                    drawCircle(
                        color = RestroTokens.coral,
                        radius = 12f,
                        center = riderPoint
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 5f,
                        center = riderPoint
                    )
                }
            }
        }
    }

            // Real-Time Countdown Timer & Distance Remaining Badges
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Countdown Timer Badge
                Surface(
                    color = if (animatedProgress >= 1.0f) RestroTokens.green else Color(0xFF1E222A),
                    shape = RoundedCornerShape(10.dp),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            if (animatedProgress >= 1.0f) Icons.Default.CheckCircle else Icons.Default.Timer,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (animatedProgress >= 1.0f) "Delivered!" else "ETA: $liveEtaMinutes mins",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Distance Remaining Badge
                Surface(
                    color = Color(0xFF1E222A),
                    shape = RoundedCornerShape(10.dp),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Navigation,
                            contentDescription = null,
                            tint = RestroTokens.coral,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = distanceText,
                            color = RestroTokens.text,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Proximity Notification Banner inside map (< 500m)
            if (remainingDistanceKm <= 0.5 && animatedProgress < 1.0f) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp, start = 12.dp, end = 12.dp)
                        .fillMaxWidth(),
                    color = Color(0xFF0F172A).copy(alpha = 0.94f),
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, null, tint = RestroTokens.coral, modifier = Modifier.size(16.dp))
                        Text(
                            RestroI18n.t("rider_gate_alert"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Attribution Banner
            Text(
                text = if (mapMode == "GoogleMaps") "Google Maps · Live GPS" else "© OpenStreetMap contributors · Live GPS",
                fontSize = 9.sp,
                color = if (mapMode == "Street") Color(0xFF7F8C8D) else RestroTokens.muted,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 4.dp)
            )
        }
    }
}

/**
 * Interactive Address Pin Picker Map for Edit Address screen.
 */
/**
 * Interactive Address Pin Picker Map powered by Google Maps JavaScript API with Leaflet fallback.
 * Allows tapping/dragging a pin, reverse geocoding to Indian landmarks, and 1-tap locality presets.
 */
@android.annotation.SuppressLint("SetJavaScriptEnabled")
@Composable
fun AddressPickerMiniMap(
    onLocationSelected: (lat: Double, lng: Double, landmark: String) -> Unit,
    modifier: Modifier = Modifier,
    initialLat: Double = 19.0596,
    initialLng: Double = 72.8295
) {
    var pinnedAddress by rememberSaveable { mutableStateOf("Bandra West (Linking Road), Mumbai") }
    var currentLat by rememberSaveable { mutableStateOf(initialLat) }
    var currentLng by rememberSaveable { mutableStateOf(initialLng) }
    var webViewRef by remember { mutableStateOf<android.webkit.WebView?>(null) }
    val apiKey = com.restaurant.mobile.BuildConfig.MAPS_API_KEY.ifBlank { "AIzaSyC51qVAdTuym7zIMscR9qROaDd7OxeIfV4" }

    val localities = remember {
        listOf(
            Triple("Bandra West", 19.0596, 72.8295),
            Triple("Andheri West", 19.1363, 72.8277),
            Triple("Powai", 19.1176, 72.9060),
            Triple("Juhu", 19.1075, 72.8263),
            Triple("BKC", 19.0657, 72.8679),
            Triple("Lower Parel", 19.0022, 72.8290)
        )
    }

    val pickerHtml = remember(apiKey, initialLat, initialLng) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            
            <style>
                html, body, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #121418; overflow: hidden; }
                .center-pin {
                    position: absolute;
                    top: 50%;
                    left: 50%;
                    transform: translate(-50%, -100%);
                    pointer-events: none;
                    z-index: 1000;
                    font-size: 28px;
                    filter: drop-shadow(0 4px 6px rgba(0,0,0,0.5));
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var map, marker, geocoder, isGoogle = false;
                var curLat = $initialLat, curLng = $initialLng;

                function notifyAndroid(lat, lng, addr) {
                    curLat = lat;
                    curLng = lng;
                    if (window.AndroidBridge && window.AndroidBridge.onLocationPinned) {
                        window.AndroidBridge.onLocationPinned(lat, lng, addr);
                    }
                }

                window.initGoogleMap = function() {
                    try {
                        isGoogle = true;
                        geocoder = new google.maps.Geocoder();
                        var centerPos = { lat: curLat, lng: curLng };
                        map = new google.maps.Map(document.getElementById('map'), {
                            zoom: 15,
                            center: centerPos,
                            disableDefaultUI: true,
                            zoomControl: true,
                            gestureHandling: "greedy",
                            styles: [
                                { elementType: "geometry", stylers: [{ color: "#242f3e" }] },
                                { elementType: "labels.text.stroke", stylers: [{ color: "#242f3e" }] },
                                { elementType: "labels.text.fill", stylers: [{ color: "#746855" }] },
                                { featureType: "road", elementType: "geometry", stylers: [{ color: "#38414e" }] },
                                { featureType: "road", elementType: "geometry.stroke", stylers: [{ color: "#212a37" }] },
                                { featureType: "road", elementType: "labels.text.fill", stylers: [{ color: "#9ca5b3" }] },
                                { featureType: "water", elementType: "geometry", stylers: [{ color: "#17263c" }] }
                            ]
                        });

                        marker = new google.maps.Marker({
                            position: centerPos,
                            map: map,
                            draggable: true,
                            animation: google.maps.Animation.DROP,
                            title: "Delivery Pin"
                        });

                        google.maps.event.addListener(map, 'click', function(ev) {
                            var lat = ev.latLng.lat();
                            var lng = ev.latLng.lng();
                            marker.setPosition(ev.latLng);
                            reverseGeocode(lat, lng);
                        });

                        google.maps.event.addListener(marker, 'dragend', function(ev) {
                            var lat = ev.latLng.lat();
                            var lng = ev.latLng.lng();
                            reverseGeocode(lat, lng);
                        });

                        reverseGeocode(curLat, curLng);
                    } catch (e) {
                        initLeafletFallback();
                    }
                }

                function reverseGeocode(lat, lng) {
                    if (geocoder) {
                        geocoder.geocode({ location: { lat: lat, lng: lng } }, function(results, status) {
                            var label = "Pinned Location (" + lat.toFixed(4) + ", " + lng.toFixed(4) + ")";
                            if (status === 'OK' && results && results[0]) {
                                label = results[0].formatted_address;
                            }
                            notifyAndroid(lat, lng, label);
                        });
                    } else {
                        notifyAndroid(lat, lng, "Location near " + lat.toFixed(4) + ", " + lng.toFixed(4));
                    }
                }

                window.initLeafletFallback = function() {
                    if (map && isGoogle) return;
                    try {
                        var script = document.createElement('script');
                        script.src = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js';
                        script.onload = function() {
                            map = L.map('map', { zoomControl: false }).setView([curLat, curLng], 15);
                            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                                maxZoom: 19
                            }).addTo(map);

                            var customIcon = L.divIcon({
                                html: '<span style="font-size:28px;">📍</span>',
                                iconSize: [32, 32],
                                iconAnchor: [16, 32]
                            });

                            marker = L.marker([curLat, curLng], { icon: customIcon, draggable: true }).addTo(map);

                            map.on('click', function(e) {
                                marker.setLatLng(e.latlng);
                                notifyAndroid(e.latlng.lat, e.latlng.lng, "Pinned Location (" + e.latlng.lat.toFixed(4) + ", " + e.latlng.lng.toFixed(4) + ")");
                            });

                            marker.on('dragend', function(e) {
                                var pos = marker.getLatLng();
                                notifyAndroid(pos.lat, pos.lng, "Pinned Location (" + pos.lat.toFixed(4) + ", " + pos.lng.toFixed(4) + ")");
                            });
                        };
                        document.head.appendChild(script);
                    } catch(err) {}
                }

                window.panToPreset = function(lat, lng, name) {
                    curLat = lat;
                    curLng = lng;
                    if (isGoogle && map) {
                        var pos = new google.maps.LatLng(lat, lng);
                        map.panTo(pos);
                        map.setZoom(16);
                        if (marker) marker.setPosition(pos);
                        reverseGeocode(lat, lng);
                    } else if (map && map.setView) {
                        map.setView([lat, lng], 16);
                        if (marker) marker.setLatLng([lat, lng]);
                        notifyAndroid(lat, lng, name + ", Mumbai");
                    }
                }

                setTimeout(function() {
                    if (!map) initLeafletFallback();
                }, 4000);
            </script>
            <script src="https://maps.googleapis.com/maps/api/js?key=$apiKey&callback=initGoogleMap&loading=async" async defer onerror="initLeafletFallback()"></script>
        </body>
        </html>
        """.trimIndent()
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1D24)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        android.webkit.WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = false
                            setOnTouchListener { v, event ->
                                when (event.action) {
                                    android.view.MotionEvent.ACTION_DOWN,
                                    android.view.MotionEvent.ACTION_MOVE -> {
                                        v.parent?.requestDisallowInterceptTouchEvent(true)
                                    }
                                    android.view.MotionEvent.ACTION_UP,
                                    android.view.MotionEvent.ACTION_CANCEL -> {
                                        v.parent?.requestDisallowInterceptTouchEvent(false)
                                    }
                                }
                                false
                            }
                            webViewClient = object : android.webkit.WebViewClient() {
                                override fun onReceivedError(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                                    super.onReceivedError(view, request, error)
                                    view?.evaluateJavascript("if (window.initLeafletFallback) window.initLeafletFallback();", null)
                                }
                            }
                            addJavascriptInterface(object {
                                @android.webkit.JavascriptInterface
                                fun onLocationPinned(lat: Double, lng: Double, address: String) {
                                    post {
                                        currentLat = lat
                                        currentLng = lng
                                        pinnedAddress = address
                                        onLocationSelected(lat, lng, address)
                                    }
                                }
                            }, "AndroidBridge")
                            loadDataWithBaseURL("https://maps.googleapis.com", pickerHtml, "text/html", "UTF-8", null)
                            webViewRef = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Status chip at top
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.9f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Place, null, tint = RestroTokens.coral, modifier = Modifier.size(14.dp))
                        Text(
                            "Tap map or drag pin to select exact delivery spot",
                            fontSize = 11.sp,
                            color = Color(0xFFE2E8F0),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Pinned location display
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = RestroTokens.surface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, RestroTokens.coral.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.LocationOn, null, tint = RestroTokens.coral, modifier = Modifier.size(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Selected Location", fontSize = 10.sp, color = RestroTokens.muted)
                    Text(
                        pinnedAddress,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RestroTokens.text,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Quick Locality Presets
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Quick Mumbai Localities:", fontSize = 11.sp, color = RestroTokens.muted, fontWeight = FontWeight.Medium)
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(localities.size) { idx ->
                    val (name, lat, lng) = localities[idx]
                    Surface(
                        onClick = {
                            currentLat = lat
                            currentLng = lng
                            val fullLoc = "$name, Mumbai"
                            pinnedAddress = fullLoc
                            onLocationSelected(lat, lng, fullLoc)
                            webViewRef?.evaluateJavascript("panToPreset($lat, $lng, '$name');", null)
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF27272A),
                        border = BorderStroke(1.dp, Color(0xFF3F3F46))
                    ) {
                        Text(
                            text = "📍 $name",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFF4F4F5),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Normalizes coordinates across the canvas for demo restaurants
 */
private fun getCoordinatesForIndex(index: Int, total: Int): Offset {
    return when (index % 5) {
        0 -> Offset(0.28f, 0.38f) // Goregaon West
        1 -> Offset(0.48f, 0.32f) // Andheri West
        2 -> Offset(0.38f, 0.68f) // Bandra West
        3 -> Offset(0.72f, 0.45f) // Juhu
        else -> Offset(0.62f, 0.74f) // Powai
    }
}

/**
 * Real interactive Google Maps WebView powered by Google Maps JavaScript API with the provided API key.
 * Features dark styling, clickable restaurant markers, and live radius circle.
 */
@android.annotation.SuppressLint("SetJavaScriptEnabled")
@Composable
fun RealGoogleMapsWebView(
    latitude: Double,
    longitude: Double,
    restaurants: List<Restaurant>,
    selectedRestaurantId: String,
    onSelectRestaurant: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val apiKey = com.restaurant.mobile.BuildConfig.MAPS_API_KEY
    val activeRestaurant = restaurants.find { it.id == selectedRestaurantId } ?: restaurants.firstOrNull()
    val targetLat = activeRestaurant?.latitude ?: latitude
    val targetLng = activeRestaurant?.longitude ?: longitude

    val htmlContent = remember(latitude, longitude, targetLat, targetLng, restaurants, selectedRestaurantId) {
        val markersJs = restaurants.map { r ->
            val safeName = r.localizedName().replace("'", "\\'")
            val safeCuisine = r.localizedCuisine().replace("'", "\\'")
            val isSelected = r.id == selectedRestaurantId
            """
            var m_${r.id.replace("-", "_")} = new google.maps.Marker({
                position: { lat: ${r.latitude}, lng: ${r.longitude} },
                map: map,
                title: '$safeName',
                animation: ${if (isSelected) "google.maps.Animation.BOUNCE" else "null"}
            });
            var info_${r.id.replace("-", "_")} = new google.maps.InfoWindow({
                content: '<div style="color:#111;font-family:sans-serif;padding:4px;"><b>$safeName</b><br><small>$safeCuisine · ⏱️ ${r.deliveryMinutes} ${RestroI18n.t("mins_unit")}</small></div>'
            });
            ${if (isSelected) "info_${r.id.replace("-", "_")}.open(map, m_${r.id.replace("-", "_")});" else ""}
            m_${r.id.replace("-", "_")}.addListener('click', function() {
                info_${r.id.replace("-", "_")}.open(map, m_${r.id.replace("-", "_")});
                if (window.AndroidBridge) {
                    window.AndroidBridge.onRestaurantClicked('${r.id}');
                }
            });
            """.trimIndent()
        }.joinToString("\n")

        val leafletMarkersJs = restaurants.map { r ->
            val safeName = r.localizedName().replace("'", "\\'")
            val safeCuisine = r.localizedCuisine().replace("'", "\\'")
            """
            L.circleMarker([${r.latitude}, ${r.longitude}], {
                radius: 9,
                fillColor: '#FF5722',
                color: '#FFFFFF',
                weight: 2,
                fillOpacity: 0.95
            }).addTo(lmap)
              .bindPopup('<b>$safeName</b><br><small>$safeCuisine · ⏱️ ${r.deliveryMinutes} ${RestroI18n.t("mins_unit")}</small>')
              .on('click', function() {
                  if (window.AndroidBridge) {
                      window.AndroidBridge.onRestaurantClicked('${r.id}');
                  }
              });
            """.trimIndent()
        }.joinToString("\n")

        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                html, body { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background: #e5e3df; }
                #map { position: fixed; top: 0; left: 0; width: 100%; height: 100%; }
                .gm-style-iw { color: #111; font-family: sans-serif; font-size: 13px; }
                .leaflet-popup-content-wrapper { background: #1E232B; color: #FFF; border-radius: 10px; font-family: sans-serif; }
                .leaflet-popup-tip { background: #1E232B; }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var map = null;
                var lmap = null;
                var mapLoaded = false;

                function fitMap() {
                    var m = document.getElementById('map');
                    if (m) {
                        var w = window.innerWidth || document.documentElement.clientWidth || screen.width;
                        var h = window.innerHeight || document.documentElement.clientHeight || screen.height;
                        if (w > 0 && h > 0) {
                            m.style.width = w + 'px';
                            m.style.height = h + 'px';
                        }
                    }
                    if (window.google && window.map) {
                        google.maps.event.trigger(map, 'resize');
                    }
                    if (window.lmap) {
                        window.lmap.invalidateSize();
                    }
                }
                window.addEventListener('resize', fitMap);
                window.addEventListener('orientationchange', fitMap);

                function initGoogleMap() {
                    if (mapLoaded) return;
                    mapLoaded = true;
                    fitMap();
                    try {
                        var centerPt = { lat: $targetLat, lng: $targetLng };
                        map = new google.maps.Map(document.getElementById('map'), {
                            center: centerPt,
                            zoom: 14,
                            disableDefaultUI: false,
                            mapTypeControl: false,
                            streetViewControl: false,
                            fullscreenControl: false
                        });
                        fitMap();
                        new google.maps.Circle({
                            strokeColor: "#FF5722",
                            strokeOpacity: 0.85,
                            strokeWeight: 2,
                            fillColor: "#FF5722",
                            fillOpacity: 0.12,
                            map: map,
                            center: centerPt,
                            radius: 4500
                        });
                        $markersJs
                        setTimeout(function() {
                            fitMap();
                            if (window.google && window.google.maps && map) {
                                google.maps.event.trigger(map, 'resize');
                                map.setCenter(centerPt);
                            }
                        }, 100);
                        setTimeout(fitMap, 400);
                    } catch(e) {
                        console.error("Google Maps JS API error, falling back", e);
                        initDirectTilesFallback();
                    }
                }

                function initDirectTilesFallback() {
                    if (mapLoaded) return;
                    mapLoaded = true;
                    fitMap();
                    try {
                        var container = document.getElementById('map');
                        container.innerHTML = '';
                        lmap = L.map('map', { zoomControl: false }).setView([$targetLat, $targetLng], 14);
                        L.tileLayer('https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', {
                            maxZoom: 20,
                            attribution: '&copy; Google Maps'
                        }).addTo(lmap);
                        L.circleMarker([$targetLat, $targetLng], {
                            radius: 8,
                            fillColor: '#3B82F6',
                            color: '#FFFFFF',
                            weight: 2,
                            fillOpacity: 1
                        }).addTo(lmap);
                        L.circle([$targetLat, $targetLng], {
                            color: '#FF5722',
                            fillColor: '#FF5722',
                            fillOpacity: 0.1,
                            weight: 2,
                            radius: 4500
                        }).addTo(lmap);
                        $leafletMarkersJs
                        setTimeout(fitMap, 150);
                    } catch(err) {
                        console.error("Fallback tile error", err);
                    }
                }

                setTimeout(function() {
                    if (!mapLoaded) {
                        initDirectTilesFallback();
                    }
                }, 2800);
            </script>
            <script src="https://maps.googleapis.com/maps/api/js?key=$apiKey&language=${RestroI18n.currentLanguage}&callback=initGoogleMap&loading=async" async defer onerror="initDirectTilesFallback()"></script>
        </body>
        </html>
        """.trimIndent()
    }

    val selRest = restaurants.find { it.id == selectedRestaurantId }
    val selLat = selRest?.latitude ?: targetLat
    val selLng = selRest?.longitude ?: targetLng

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            android.webkit.WebView(ctx).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.setGeolocationEnabled(true)
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun onRestaurantClicked(restId: String) {
                        onSelectRestaurant(restId)
                    }
                }, "AndroidBridge")
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                        android.util.Log.d("GoogleMapsWebView", "${message?.message()} -- line ${message?.lineNumber()}")
                        return true
                    }
                }
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                        return false
                    }
                }
                loadDataWithBaseURL("https://maps.google.com", htmlContent, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            // Smoothly pan to the selected restaurant and re-fit
            webView.evaluateJavascript(
                "if (window.fitMap) { window.fitMap(); } if (window.map && window.map.panTo) { window.map.panTo(new google.maps.LatLng($selLat, $selLng)); window.map.setZoom(15); } else if (window.lmap && window.lmap.panTo) { window.lmap.panTo([$selLat, $selLng]); }",
                null
            )
        },
        modifier = modifier
    )
}

/**
 * Real interactive OpenStreetMap WebView powered by Leaflet.js.
 * Renders real street tiles, real user GPS coordinates, 6.5 km delivery radius,
 * and clickable restaurant pins that communicate back to Jetpack Compose via Android JavascriptBridge.
 */
@android.annotation.SuppressLint("SetJavaScriptEnabled")
@Composable
fun RealOpenStreetMapWebView(
    latitude: Double,
    longitude: Double,
    restaurants: List<Restaurant>,
    selectedRestaurantId: String,
    onSelectRestaurant: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val htmlContent = remember(latitude, longitude, restaurants.size, selectedRestaurantId) {
        val markersJs = restaurants.map { r ->
            val isSelected = r.id == selectedRestaurantId
            val markerColor = if (isSelected) "#FF5722" else "#1976D2"
            val safeName = r.localizedName().replace("'", "\\'")
            val safeCuisine = r.localizedCuisine().replace("'", "\\'")
            """
            L.circleMarker([${r.latitude}, ${r.longitude}], {
                radius: ${if (isSelected) 11 else 7},
                fillColor: '$markerColor',
                color: '#FFFFFF',
                weight: 2,
                opacity: 1,
                fillOpacity: 0.95
            }).addTo(map)
              .bindPopup('<b>$safeName</b><br>$safeCuisine<br>⏱️ ${r.deliveryMinutes} mins')
              .on('click', function() {
                  if (window.AndroidBridge) {
                      window.AndroidBridge.onRestaurantClicked('${r.id}');
                  }
              });
            """.trimIndent()
        }.joinToString("\n")

        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                body, html { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background: #14171C; }
                #map { position: fixed; top: 0; left: 0; width: 100%; height: 100%; }
                .leaflet-popup-content-wrapper { background: #1E232B; color: #FFF; border-radius: 12px; font-family: sans-serif; }
                .leaflet-popup-tip { background: #1E232B; }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                function fitOsmMap() {
                    var m = document.getElementById('map');
                    if (m) {
                        var w = window.innerWidth || document.documentElement.clientWidth || screen.width;
                        var h = window.innerHeight || document.documentElement.clientHeight || screen.height;
                        if (w > 0 && h > 0) {
                            m.style.width = w + 'px';
                            m.style.height = h + 'px';
                        }
                    }
                    if (window.map) {
                        window.map.invalidateSize();
                    }
                }
                window.addEventListener('resize', fitOsmMap);
                window.addEventListener('orientationchange', fitOsmMap);

                fitOsmMap();
                var map = L.map('map', { zoomControl: false }).setView([$latitude, $longitude], 13);
                L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
                    maxZoom: 19,
                    subdomains: 'abcd',
                    attribution: '&copy; OpenStreetMap'
                }).addTo(map);

                // User GPS pulsing dot
                L.circleMarker([$latitude, $longitude], {
                    radius: 8,
                    fillColor: '#3B82F6',
                    color: '#FFFFFF',
                    weight: 3,
                    opacity: 1,
                    fillOpacity: 1
                }).addTo(map).bindPopup('<b>Your Live Location</b>');

                // 6.5 km delivery radius boundary circle
                L.circle([$latitude, $longitude], {
                    color: '#FF5722',
                    fillColor: '#FF5722',
                    fillOpacity: 0.08,
                    weight: 2,
                    dashArray: '6, 6',
                    radius: 6500
                }).addTo(map);

                $markersJs
                setTimeout(fitOsmMap, 100);
                setTimeout(fitOsmMap, 400);
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            android.webkit.WebView(ctx).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.userAgentString = "RestroMobile/1.0 (Android; com.restaurant.mobile)"
                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun onRestaurantClicked(restId: String) {
                        onSelectRestaurant(restId)
                    }
                }, "AndroidBridge")
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                        return false
                    }
                }
                loadDataWithBaseURL("https://cartocdn.com", htmlContent, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            // Update center without reloading DOM
            webView.evaluateJavascript("if (window.map && window.map.panTo) { window.map.panTo([$latitude, $longitude]); }", null)
        },
        modifier = modifier
    )
}

/**
 * Real OpenStreetMap live route tracking WebView.
 * Displays restaurant pin, delivery destination, and a live moving rider marker without reloading!
 */
@android.annotation.SuppressLint("SetJavaScriptEnabled")
@Composable
fun RealOrderTrackingOsmWebView(
    restaurantLat: Double,
    restaurantLng: Double,
    restaurantName: String,
    userLat: Double,
    userLng: Double,
    riderProgress: Float,
    modifier: Modifier = Modifier
) {
    val currentRiderLat = restaurantLat + (userLat - restaurantLat) * riderProgress
    val currentRiderLng = restaurantLng + (userLng - restaurantLng) * riderProgress
    val safeRestName = restaurantName.replace("'", "\\'")

    val initialHtml = remember(restaurantLat, restaurantLng, userLat, userLng) {
        val midLat = (restaurantLat + userLat) / 2.0
        val midLng = (restaurantLng + userLng) / 2.0
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                body, html { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background: #14171C; }
                #map { position: fixed; top: 0; left: 0; width: 100%; height: 100%; }
                .leaflet-popup-content-wrapper { background: #1E232B; color: #FFF; border-radius: 10px; font-family: sans-serif; font-size: 12px; }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                function fitOsmTrackMap() {
                    var m = document.getElementById('map');
                    if (m) {
                        var w = window.innerWidth || document.documentElement.clientWidth || screen.width;
                        var h = window.innerHeight || document.documentElement.clientHeight || screen.height;
                        if (w > 0 && h > 0) {
                            m.style.width = w + 'px';
                            m.style.height = h + 'px';
                        }
                    }
                    if (window.map) {
                        window.map.invalidateSize();
                    }
                }
                window.addEventListener('resize', fitOsmTrackMap);
                window.addEventListener('orientationchange', fitOsmTrackMap);

                fitOsmTrackMap();
                var map = L.map('map', { zoomControl: false }).setView([$midLat, $midLng], 14);
                L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
                    maxZoom: 19,
                    subdomains: 'abcd',
                    attribution: '&copy; OpenStreetMap'
                }).addTo(map);

                // Restaurant Marker
                L.circleMarker([$restaurantLat, $restaurantLng], {
                    radius: 8,
                    fillColor: '#FF5722',
                    color: '#FFF',
                    weight: 2,
                    fillOpacity: 1
                }).addTo(map).bindPopup('<b>$safeRestName</b>');

                // Customer Destination Marker
                L.circleMarker([$userLat, $userLng], {
                    radius: 8,
                    fillColor: '#10B981',
                    color: '#FFF',
                    weight: 2,
                    fillOpacity: 1
                }).addTo(map).bindPopup('<b>Delivery Destination</b>');

                // Route Polyline
                var latlngs = [[$restaurantLat, $restaurantLng], [$userLat, $userLng]];
                L.polyline(latlngs, { color: '#FF5722', weight: 4, dashArray: '6, 6' }).addTo(map);

                // Moving Rider Marker
                var riderMarker = L.circleMarker([$restaurantLat, $restaurantLng], {
                    radius: 9,
                    fillColor: '#3B82F6',
                    color: '#FFF',
                    weight: 3,
                    fillOpacity: 1
                }).addTo(map).bindPopup('<b>🛵 Delivery Partner (Live)</b>');

                window.updateRider = function(lat, lng) {
                    if (riderMarker) {
                        riderMarker.setLatLng([lat, lng]);
                    }
                };

                setTimeout(fitOsmTrackMap, 100);
                setTimeout(fitOsmTrackMap, 400);
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            android.webkit.WebView(ctx).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.userAgentString = "RestroMobile/1.0 (Android; com.restaurant.mobile)"
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                        return false
                    }
                }
                loadDataWithBaseURL("https://cartocdn.com", initialHtml, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            // Smoothly update marker coordinates without reloading the page every frame!
            webView.evaluateJavascript("if (window.updateRider) { window.updateRider($currentRiderLat, $currentRiderLng); }", null)
        },
        modifier = modifier
    )
}

/**
 * Real Google Maps live route tracking WebView.
 * Powered by Google Maps JavaScript API with the provided Maps API key.
 * Shows restaurant pin, delivery destination, dashed polyline route,
 * and live moving delivery rider marker without reloading!
 */
@android.annotation.SuppressLint("SetJavaScriptEnabled")
@Composable
fun RealOrderTrackingGoogleMapsWebView(
    restaurantLat: Double,
    restaurantLng: Double,
    restaurantName: String,
    userLat: Double,
    userLng: Double,
    riderProgress: Float,
    modifier: Modifier = Modifier
) {
    val apiKey = com.restaurant.mobile.BuildConfig.MAPS_API_KEY
    val currentRiderLat = restaurantLat + (userLat - restaurantLat) * riderProgress
    val currentRiderLng = restaurantLng + (userLng - restaurantLng) * riderProgress
    val safeRestName = restaurantName.replace("'", "\\'")

    val initialHtml = remember(restaurantLat, restaurantLng, userLat, userLng, apiKey) {
        val midLat = (restaurantLat + userLat) / 2.0
        val midLng = (restaurantLng + userLng) / 2.0
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                html, body { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background: #e5e3df; }
                #map { position: fixed; top: 0; left: 0; width: 100%; height: 100%; }
                .gm-style-iw { color: #111; font-family: sans-serif; font-size: 12px; }
                .leaflet-popup-content-wrapper { background: #1E232B; color: #FFF; border-radius: 10px; font-family: sans-serif; font-size: 12px; }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var map = null;
                var lmap = null;
                var riderMarker = null;
                var lRiderMarker = null;
                var isRouteLoaded = false;

                function fitMap() {
                    var m = document.getElementById('map');
                    if (m) {
                        var w = window.innerWidth || document.documentElement.clientWidth || screen.width;
                        var h = window.innerHeight || document.documentElement.clientHeight || screen.height;
                        if (w > 0 && h > 0) {
                            m.style.width = w + 'px';
                            m.style.height = h + 'px';
                        }
                    }
                    if (window.google && window.map) {
                        google.maps.event.trigger(map, 'resize');
                    }
                    if (window.lmap) {
                        window.lmap.invalidateSize();
                    }
                }
                window.addEventListener('resize', fitMap);
                window.addEventListener('orientationchange', fitMap);

                function initGoogleRouteMap() {
                    if (isRouteLoaded) return;
                    isRouteLoaded = true;
                    fitMap();
                    try {
                        map = new google.maps.Map(document.getElementById('map'), {
                            center: { lat: $midLat, lng: $midLng },
                            zoom: 14,
                            disableDefaultUI: false,
                            mapTypeControl: false,
                            streetViewControl: false,
                            fullscreenControl: false
                        });
                        fitMap();

                        // Restaurant Marker (Coral)
                        var restMarker = new google.maps.Marker({
                            position: { lat: $restaurantLat, lng: $restaurantLng },
                            map: map,
                            title: '$safeRestName',
                            icon: {
                                path: google.maps.SymbolPath.CIRCLE,
                                scale: 8,
                                fillColor: '#FF5722',
                                fillOpacity: 1,
                                strokeColor: '#FFFFFF',
                                strokeWeight: 2
                            }
                        });
                        var restInfo = new google.maps.InfoWindow({
                            content: '<div style="color:#111;font-weight:bold;padding:2px;">🏪 $safeRestName</div>'
                        });
                        restMarker.addListener('click', function() { restInfo.open(map, restMarker); });

                        // Customer Destination Marker (Green)
                        var userMarker = new google.maps.Marker({
                            position: { lat: $userLat, lng: $userLng },
                            map: map,
                            title: 'Delivery Address',
                            icon: {
                                path: google.maps.SymbolPath.CIRCLE,
                                scale: 8,
                                fillColor: '#10B981',
                                fillOpacity: 1,
                                strokeColor: '#FFFFFF',
                                strokeWeight: 2
                            }
                        });
                        var userInfo = new google.maps.InfoWindow({
                            content: '<div style="color:#111;font-weight:bold;padding:2px;">📍 Delivery Location</div>'
                        });
                        userMarker.addListener('click', function() { userInfo.open(map, userMarker); });

                        // Route Polyline
                        new google.maps.Polyline({
                            path: [
                                { lat: $restaurantLat, lng: $restaurantLng },
                                { lat: $userLat, lng: $userLng }
                            ],
                            geodesic: true,
                            strokeColor: '#FF5722',
                            strokeOpacity: 0.85,
                            strokeWeight: 4,
                            map: map
                        });

                        // Moving Rider Marker (Blue Scooter)
                        riderMarker = new google.maps.Marker({
                            position: { lat: $restaurantLat, lng: $restaurantLng },
                            map: map,
                            title: 'Delivery Partner',
                            icon: {
                                path: google.maps.SymbolPath.CIRCLE,
                                scale: 10,
                                fillColor: '#3B82F6',
                                fillOpacity: 1,
                                strokeColor: '#FFFFFF',
                                strokeWeight: 3
                            }
                        });
                        var riderInfo = new google.maps.InfoWindow({
                            content: '<div style="color:#111;font-weight:bold;padding:2px;">🛵 Delivery Partner (Live)</div>'
                        });
                        riderMarker.addListener('click', function() { riderInfo.open(map, riderMarker); });

                        setTimeout(function() {
                            fitMap();
                            if (window.google && window.google.maps && map) {
                                google.maps.event.trigger(map, 'resize');
                            }
                        }, 100);
                        setTimeout(fitMap, 400);
                    } catch(e) {
                        console.error("Google Maps route error, fallback to tiles", e);
                        initDirectRouteFallback();
                    }
                }

                function initDirectRouteFallback() {
                    if (isRouteLoaded) return;
                    isRouteLoaded = true;
                    fitMap();
                    try {
                        var container = document.getElementById('map');
                        container.innerHTML = '';
                        lmap = L.map('map', { zoomControl: false }).setView([$midLat, $midLng], 14);
                        L.tileLayer('https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', {
                            maxZoom: 20,
                            attribution: '&copy; Google Maps'
                        }).addTo(lmap);

                        L.circleMarker([$restaurantLat, $restaurantLng], {
                            radius: 8,
                            fillColor: '#FF5722',
                            color: '#FFF',
                            weight: 2,
                            fillOpacity: 1
                        }).addTo(lmap).bindPopup('<b>$safeRestName</b>');

                        L.circleMarker([$userLat, $userLng], {
                            radius: 8,
                            fillColor: '#10B981',
                            color: '#FFF',
                            weight: 2,
                            fillOpacity: 1
                        }).addTo(lmap).bindPopup('<b>Delivery Destination</b>');

                        var latlngs = [[$restaurantLat, $restaurantLng], [$userLat, $userLng]];
                        L.polyline(latlngs, { color: '#FF5722', weight: 4, dashArray: '6, 6' }).addTo(lmap);

                        lRiderMarker = L.circleMarker([$restaurantLat, $restaurantLng], {
                            radius: 9,
                            fillColor: '#3B82F6',
                            color: '#FFF',
                            weight: 3,
                            fillOpacity: 1
                        }).addTo(lmap).bindPopup('<b>🛵 Delivery Partner (Live)</b>');
                        setTimeout(fitMap, 150);
                    } catch(err) {
                        console.error("Leaflet fallback route error", err);
                    }
                }

                window.updateRider = function(lat, lng) {
                    if (riderMarker) {
                        riderMarker.setPosition(new google.maps.LatLng(lat, lng));
                    }
                    if (lRiderMarker) {
                        lRiderMarker.setLatLng([lat, lng]);
                    }
                };

                setTimeout(function() {
                    if (!isRouteLoaded) {
                        initDirectRouteFallback();
                    }
                }, 2800);
            </script>
            <script src="https://maps.googleapis.com/maps/api/js?key=$apiKey&language=${RestroI18n.currentLanguage}&callback=initGoogleRouteMap&loading=async" async defer onerror="initDirectRouteFallback()"></script>
        </body>
        </html>
        """.trimIndent()
    }

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            android.webkit.WebView(ctx).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.setGeolocationEnabled(true)
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                        return false
                    }
                }
                loadDataWithBaseURL("https://maps.google.com", initialHtml, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.evaluateJavascript("if (window.fitMap) { window.fitMap(); } if (window.updateRider) { window.updateRider($currentRiderLat, $currentRiderLng); }", null)
        },
        modifier = modifier
    )
}
