package com.restaurant.mobile.delivery

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AdminOrderStatusFilter(val label: String) {
    ALL("All Orders"),
    NEW("New / Received"),
    PREPARING("Kitchen Preparing"),
    READY("Ready for Pickup"),
    OUT_FOR_DELIVERY("Out for Delivery"),
    DELIVERED("Delivered"),
    CANCELLED("Cancelled")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminOrdersScreenView(
    database: RestroSqlDatabase,
    cloudEngine: FirebaseCloudEngine? = null,
    deliveryVm: DeliveryViewModel? = null,
    onShowToast: (String) -> Unit,
    onNavigateToMenu: (() -> Unit)? = null,
    onNavigateToSales: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentLang = RestroI18n.currentLanguage

    var allOrders by remember { mutableStateOf<List<DeliveryOrder>>(emptyList()) }
    var selectedFilter by rememberSaveable { mutableStateOf(AdminOrderStatusFilter.ALL) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isRefreshing by remember { mutableStateOf(false) }

    fun refreshOrders() {
        coroutineScope.launch(Dispatchers.IO) {
            val localOrders = database.getAllOrders()
            withContext(Dispatchers.Main) {
                allOrders = localOrders
                deliveryVm?.orders = localOrders
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshOrders()
    }

    // Subscribe to real-time Cloud Firestore updates
    DisposableEffect(cloudEngine) {
        val reg = cloudEngine?.observeOrdersRealTime(isStaffOrRider = true) { cloudList ->
            allOrders = cloudList
            deliveryVm?.orders = cloudList
        }
        onDispose {
            reg?.remove()
        }
    }

    // Count badges for status chips
    val newCount = remember(allOrders) {
        allOrders.count { it.status.equals("Placed", ignoreCase = true) || it.status.equals("Confirmed", ignoreCase = true) }
    }
    val prepCount = remember(allOrders) {
        allOrders.count { it.status.equals("Preparing", ignoreCase = true) }
    }
    val readyCount = remember(allOrders) {
        allOrders.count { it.status.equals("Ready", ignoreCase = true) }
    }
    val outCount = remember(allOrders) {
        allOrders.count { it.status.equals("Out for delivery", ignoreCase = true) || it.status.equals("Accepted", ignoreCase = true) }
    }
    val deliveredCount = remember(allOrders) {
        allOrders.count { it.status.equals("Delivered", ignoreCase = true) }
    }
    val cancelledCount = remember(allOrders) {
        allOrders.count { it.status.equals("Cancelled", ignoreCase = true) || it.status.equals("Canceled", ignoreCase = true) }
    }

    val filteredOrders = remember(allOrders, selectedFilter, searchQuery) {
        allOrders.filter { order ->
            val matchesFilter = when (selectedFilter) {
                AdminOrderStatusFilter.ALL -> true
                AdminOrderStatusFilter.NEW -> order.status.equals("Placed", ignoreCase = true) || order.status.equals("Confirmed", ignoreCase = true)
                AdminOrderStatusFilter.PREPARING -> order.status.equals("Preparing", ignoreCase = true)
                AdminOrderStatusFilter.READY -> order.status.equals("Ready", ignoreCase = true)
                AdminOrderStatusFilter.OUT_FOR_DELIVERY -> order.status.equals("Out for delivery", ignoreCase = true) || order.status.equals("Accepted", ignoreCase = true)
                AdminOrderStatusFilter.DELIVERED -> order.status.equals("Delivered", ignoreCase = true)
                AdminOrderStatusFilter.CANCELLED -> order.status.equals("Cancelled", ignoreCase = true) || order.status.equals("Canceled", ignoreCase = true)
            }
            val matchesSearch = if (searchQuery.isBlank()) true else {
                order.id.contains(searchQuery, ignoreCase = true) ||
                        order.address.line.contains(searchQuery, ignoreCase = true) ||
                        order.address.city.contains(searchQuery, ignoreCase = true) ||
                        order.riderName.contains(searchQuery, ignoreCase = true) ||
                        order.quote.lines.any { it.name.contains(searchQuery, ignoreCase = true) }
            }
            matchesFilter && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Restaurant Online Orders",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Text(
                        text = "Live Sync Active • ${allOrders.size} total orders",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            IconButton(
                onClick = {
                    isRefreshing = true
                    refreshOrders()
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(600)
                        isRefreshing = false
                        onShowToast("Orders refreshed from Firebase!")
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = if (isRefreshing) Color(0xFFC2410C) else Color(0xFF0F172A)
                )
            }
        }

        // Quick Direct Access to Menu & Prices and Total Sales
        if (onNavigateToMenu != null || onNavigateToSales != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onNavigateToMenu != null) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToMenu() },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, Color(0xFFFECACA))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.MenuBook,
                                contentDescription = "Menu & Prices",
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    "Menu & Prices",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF991B1B)
                                )
                                Text(
                                    "Edit dishes & rates",
                                    fontSize = 10.sp,
                                    color = Color(0xFFB91C1C)
                                )
                            }
                        }
                    }
                }
                if (onNavigateToSales != null) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToSales() },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFEFF6FF),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.TrendingUp,
                                contentDescription = "Total Sales",
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    "Total Sales",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF1E40AF)
                                )
                                Text(
                                    "Revenue & Analytics",
                                    fontSize = 10.sp,
                                    color = Color(0xFF3B82F6)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick KPI Metric Cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AdminOrderKpiCard(
                title = "New Orders",
                count = newCount,
                accentColor = Color(0xFFD97706),
                bgColor = Color(0xFFFEF3C7),
                icon = Icons.Default.NotificationsActive,
                isSelected = selectedFilter == AdminOrderStatusFilter.NEW,
                onClick = { selectedFilter = AdminOrderStatusFilter.NEW }
            )
            AdminOrderKpiCard(
                title = "Kitchen",
                count = prepCount,
                accentColor = Color(0xFF2563EB),
                bgColor = Color(0xFFDBEAFE),
                icon = Icons.Default.SoupKitchen,
                isSelected = selectedFilter == AdminOrderStatusFilter.PREPARING,
                onClick = { selectedFilter = AdminOrderStatusFilter.PREPARING }
            )
            AdminOrderKpiCard(
                title = "Ready",
                count = readyCount,
                accentColor = Color(0xFF7C3AED),
                bgColor = Color(0xFFEDE9FE),
                icon = Icons.Default.DoneAll,
                isSelected = selectedFilter == AdminOrderStatusFilter.READY,
                onClick = { selectedFilter = AdminOrderStatusFilter.READY }
            )
            AdminOrderKpiCard(
                title = "On Way",
                count = outCount,
                accentColor = Color(0xFF059669),
                bgColor = Color(0xFFD1FAE5),
                icon = Icons.Default.DeliveryDining,
                isSelected = selectedFilter == AdminOrderStatusFilter.OUT_FOR_DELIVERY,
                onClick = { selectedFilter = AdminOrderStatusFilter.OUT_FOR_DELIVERY }
            )
            AdminOrderKpiCard(
                title = "Delivered",
                count = deliveredCount,
                accentColor = Color(0xFF475569),
                bgColor = Color(0xFFF1F5F9),
                icon = Icons.Default.CheckCircle,
                isSelected = selectedFilter == AdminOrderStatusFilter.DELIVERED,
                onClick = { selectedFilter = AdminOrderStatusFilter.DELIVERED }
            )
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search by Order ID, dish, customer, or rider...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Color(0xFFC2410C),
                unfocusedBorderColor = Color(0xFFE2E8F0)
            )
        )

        // Status Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AdminOrderStatusFilter.entries.forEach { filter ->
                val isSelected = selectedFilter == filter
                val countText = when (filter) {
                    AdminOrderStatusFilter.ALL -> allOrders.size
                    AdminOrderStatusFilter.NEW -> newCount
                    AdminOrderStatusFilter.PREPARING -> prepCount
                    AdminOrderStatusFilter.READY -> readyCount
                    AdminOrderStatusFilter.OUT_FOR_DELIVERY -> outCount
                    AdminOrderStatusFilter.DELIVERED -> deliveredCount
                    AdminOrderStatusFilter.CANCELLED -> cancelledCount
                }
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilter = filter },
                    label = {
                        Text(
                            text = "${filter.label} ($countText)",
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFC2410C),
                        selectedLabelColor = Color.White,
                        containerColor = Color.White,
                        labelColor = Color(0xFF475569)
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) Color(0xFFC2410C) else Color(0xFFE2E8F0)
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }

        // Orders List
        if (filteredOrders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No orders found for this tab",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Try clearing your search query" else "New incoming online customer orders will automatically sync here in real-time.",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredOrders, key = { it.id }) { order ->
                    AdminOrderCardItem(
                        order = order,
                        database = database,
                        cloudEngine = cloudEngine,
                        deliveryVm = deliveryVm,
                        onShowToast = onShowToast,
                        onRefreshRequested = { refreshOrders() }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminOrderKpiCard(
    title: String,
    count: Int,
    accentColor: Color,
    bgColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) bgColor else Color.White,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) accentColor else Color(0xFFE2E8F0)
        ),
        modifier = Modifier.widthIn(min = 100.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
            }
            Column {
                Text(text = "$count", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
                Text(text = title, fontSize = 10.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun AdminOrderCardItem(
    order: DeliveryOrder,
    database: RestroSqlDatabase,
    cloudEngine: FirebaseCloudEngine?,
    deliveryVm: DeliveryViewModel?,
    onShowToast: (String) -> Unit,
    onRefreshRequested: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }

    val statusColor = when (order.status.trim().lowercase()) {
        "placed", "confirmed", "pending" -> Color(0xFFD97706)
        "preparing" -> Color(0xFF2563EB)
        "ready" -> Color(0xFF7C3AED)
        "accepted", "out for delivery" -> Color(0xFF059669)
        "delivered" -> Color(0xFF10B981)
        else -> Color(0xFF64748B)
    }

    val statusBg = when (order.status.trim().lowercase()) {
        "placed", "confirmed", "pending" -> Color(0xFFFEF3C7)
        "preparing" -> Color(0xFFDBEAFE)
        "ready" -> Color(0xFFEDE9FE)
        "accepted", "out for delivery" -> Color(0xFFD1FAE5)
        "delivered" -> Color(0xFFECFDF5)
        else -> Color(0xFFF1F5F9)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Order ID, Status, Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Order #${order.id}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                    Surface(
                        color = statusBg,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = order.status.uppercase(),
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = "₹${order.quote.total / 100}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = Color(0xFF0F172A)
                )
            }

            // Customer Address & Payment Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color(0xFFC2410C),
                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${order.address.line}, ${order.address.city}",
                        fontSize = 12.sp,
                        color = Color(0xFF334155),
                        fontWeight = FontWeight.Medium,
                        maxLines = if (isExpanded) 4 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Payment: ${order.paymentMethod} • ${order.paymentStatus}",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            // Items List (Collapsible/Preview)
            Surface(
                color = Color(0xFFF8FAFC),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val displayedLines = if (isExpanded) order.quote.lines else order.quote.lines.take(2)
                    displayedLines.forEach { line ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${line.quantity}x ${line.name}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = "₹${line.total / 100}",
                                fontSize = 12.sp,
                                color = Color(0xFF475569)
                            )
                        }
                    }
                    if (order.quote.lines.size > 2) {
                        Text(
                            text = if (isExpanded) "Show Less" else "+${order.quote.lines.size - 2} more items...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC2410C),
                            modifier = Modifier.clickable { isExpanded = !isExpanded }
                        )
                    }
                }
            }

            // Rider Assignment Information
            if (order.riderName.isNotBlank() || order.riderId.isNotBlank()) {
                Surface(
                    color = Color(0xFFF0FDF4),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.DirectionsBike,
                                contentDescription = null,
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "Rider: ${order.riderName.ifBlank { "Assigned Partner" }}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF166534)
                                )
                                if (order.riderVehicle.isNotBlank()) {
                                    Text(
                                        text = order.riderVehicle,
                                        fontSize = 10.sp,
                                        color = Color(0xFF15803D)
                                    )
                                }
                            }
                        }

                        if (order.riderPhone.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${order.riderPhone}"))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Call Rider", tint = Color(0xFF059669), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Action Buttons by Order Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (order.status.trim().lowercase()) {
                    "placed", "confirmed", "pending" -> {
                        // 1. Accept Order (Admin sends order to Kitchen KDS)
                        Button(
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    database.updateOrderStatus(order.id, "Preparing")
                                    cloudEngine?.updateOrderStatusInCloud(order.id, "Preparing")

                                    // Create KOT Ticket for Kitchen KDS
                                    val kotItems = order.quote.lines.map { line ->
                                        com.restaurant.mobile.model.KOTItem(
                                            menuItemName = line.name,
                                            quantity = line.quantity
                                        )
                                    }
                                    val orderNumericId = (order.id.filter { it.isDigit() }.take(8).toLongOrNull() ?: 1001L)
                                    val kot = database.createKotTicket(
                                        tableNumber = "DELIVERY-${order.id}",
                                        tableId = "DELIVERY-${order.id}",
                                        items = kotItems,
                                        round = 1,
                                        roundName = "Online Delivery KDS",
                                        customerName = order.address.label.ifBlank { "Customer Delivery" },
                                        orderId = orderNumericId
                                    )
                                    database.updateKotTicketStatus(kot.id, "Preparing")
                                    cloudEngine?.saveKotToCloud(kot.copy(status = "Preparing"), order.restaurantId)

                                    withContext(Dispatchers.Main) {
                                        deliveryVm?.orders = deliveryVm?.orders?.map {
                                            if (it.id == order.id) it.copy(status = "Preparing") else it
                                        } ?: emptyList()
                                        onRefreshRequested()
                                        onShowToast("Order #${order.id} accepted & sent to Kitchen KDS!")
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.SoupKitchen, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Accept & Send to KDS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    database.updateOrderStatus(order.id, "Cancelled")
                                    cloudEngine?.updateOrderStatusInCloud(order.id, "Cancelled")
                                    withContext(Dispatchers.Main) {
                                        onRefreshRequested()
                                        onShowToast("Order #${order.id} cancelled.")
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                            border = BorderStroke(1.dp, Color(0xFFDC2626))
                        ) {
                            Text("Reject", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    "preparing" -> {
                        // 2. Mark Food Ready (Chef/Kitchen packs the food)
                        Button(
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    database.updateOrderStatus(order.id, "Ready")
                                    cloudEngine?.updateOrderStatusInCloud(order.id, "Ready")
                                    withContext(Dispatchers.Main) {
                                        deliveryVm?.orders = deliveryVm?.orders?.map {
                                            if (it.id == order.id) it.copy(status = "Ready") else it
                                        } ?: emptyList()
                                        onRefreshRequested()
                                        onShowToast("Food for Order #${order.id} marked READY for Rider pickup!")
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.DoneAll, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Mark Food Ready (Packed)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    "ready" -> {
                        // 3. Handover to Rider (Mark Out for Delivery)
                        Button(
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    database.updateOrderStatus(order.id, "Out for delivery")
                                    cloudEngine?.updateOrderStatusInCloud(order.id, "Out for delivery")
                                    deliveryVm?.notifyOrderPickedUp(order.id, order.riderId)
                                    withContext(Dispatchers.Main) {
                                        onRefreshRequested()
                                        onShowToast("Order #${order.id} dispatched! Out for delivery with ${order.riderName.ifBlank { "Rider" }}.")
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC2410C)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.DeliveryDining, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Handover to Rider (Out for Delivery)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    "accepted", "out for delivery" -> {
                        // 4. In Transit / Mark Delivered Fallback
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    database.updateOrderStatus(order.id, "Delivered")
                                    cloudEngine?.updateOrderStatusInCloud(order.id, "Delivered")
                                    deliveryVm?.notifyOrderDelivered(order.id)
                                    withContext(Dispatchers.Main) {
                                        onRefreshRequested()
                                        onShowToast("Order #${order.id} marked as Delivered!")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF10B981)),
                            border = BorderStroke(1.dp, Color(0xFF10B981))
                        ) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Admin Confirm: Delivered", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    "delivered" -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Order Complete • Delivered at Doorstep", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
