package com.restaurant.mobile

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.restaurant.mobile.api.ApiClient
import com.restaurant.mobile.model.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        setContent {
            val deliveryVm: com.restaurant.mobile.delivery.DeliveryViewModel =
                androidx.lifecycle.viewmodel.compose.viewModel()
            var isStaffMode by rememberSaveable { mutableStateOf(false) }
            var showStaffPinPad by rememberSaveable { mutableStateOf(false) }
            var lastStaffInteractionMs by remember { mutableStateOf(System.currentTimeMillis()) }

            LaunchedEffect(deliveryVm.profile) {
                if ((deliveryVm.profile == null || deliveryVm.profile?.role?.isStaff != true) && isStaffMode) {
                    isStaffMode = false
                }
            }

            // 3-Minute (180,000 ms) Inactivity Auto-Lock Timer for Staff Portal
            LaunchedEffect(isStaffMode, lastStaffInteractionMs) {
                if (isStaffMode) {
                    kotlinx.coroutines.delay(180_000L)
                    if (System.currentTimeMillis() - lastStaffInteractionMs >= 180_000L) {
                        isStaffMode = false
                        Toast.makeText(
                            this@MainActivity,
                            "Staff Portal auto-locked after 3 minutes of inactivity",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            if (showStaffPinPad && deliveryVm.profile != null) {
                com.restaurant.mobile.delivery.StaffPinPadDialog(
                    onVerifyPin = { pin -> deliveryVm.verifyStaffPinAndPromoteRole(pin) },
                    onSuccess = { verifiedRole ->
                        showStaffPinPad = false
                        lastStaffInteractionMs = System.currentTimeMillis()
                        isStaffMode = true
                        Toast.makeText(
                            this@MainActivity,
                            "Vault Unlocked: ${verifiedRole.label}",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onDismiss = { showStaffPinPad = false }
                )
            }

            if (isStaffMode && deliveryVm.profile?.role?.isStaff == true) {
                RestaurantMobileApp(
                    userRole = deliveryVm.profile?.role ?: com.restaurant.mobile.delivery.UserRole.WAITER,
                    onBackToCustomer = { isStaffMode = false },
                    onSwitchStaffPin = {
                        lastStaffInteractionMs = System.currentTimeMillis()
                        showStaffPinPad = true
                    },
                    onUserInteraction = {
                        lastStaffInteractionMs = System.currentTimeMillis()
                    },
                    onShowToast = { msg ->
                        lastStaffInteractionMs = System.currentTimeMillis()
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    },
                    sqlDb = deliveryVm.sqlDb
                )
            } else {
                com.restaurant.mobile.delivery.DeliveryApp(
                    vm = deliveryVm,
                    onOpenStaffPortal = {
                        val profile = deliveryVm.profile
                        if (profile == null) {
                            Toast.makeText(
                                this@MainActivity,
                                "Please sign in first to access the Staff Portal",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            showStaffPinPad = true
                        }
                    }
                )
            }
        }
    }
}

enum class NavigationScreen(val title: String, val icon: ImageVector) {
    POS("POS Cashier", Icons.Default.ShoppingCart),
    KDS("Kitchen KDS", Icons.Default.SoupKitchen),
    TABLES("Tables", Icons.Default.TableBar),
    ANALYTICS("Analytics", Icons.Default.BarChart)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantMobileApp(
    userRole: com.restaurant.mobile.delivery.UserRole = com.restaurant.mobile.delivery.UserRole.ADMIN,
    onBackToCustomer: (() -> Unit)? = null,
    onSwitchStaffPin: (() -> Unit)? = null,
    onUserInteraction: (() -> Unit)? = null,
    onShowToast: (String) -> Unit,
    sqlDb: com.restaurant.mobile.delivery.RestroSqlDatabase? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val database = remember(sqlDb) { sqlDb ?: com.restaurant.mobile.delivery.RestroSqlDatabase(context) }

    val allowedScreens = remember(userRole) {
        when (userRole) {
            com.restaurant.mobile.delivery.UserRole.WAITER -> listOf(NavigationScreen.POS, NavigationScreen.TABLES)
            com.restaurant.mobile.delivery.UserRole.CHEF_KDS -> listOf(NavigationScreen.KDS, NavigationScreen.TABLES)
            com.restaurant.mobile.delivery.UserRole.ADMIN -> NavigationScreen.entries
            com.restaurant.mobile.delivery.UserRole.CUSTOMER -> emptyList()
        }
    }
    var currentScreen by remember(userRole) {
        mutableStateOf(allowedScreens.firstOrNull() ?: NavigationScreen.POS)
    }

    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var menuItems by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    var tables by remember { mutableStateOf<List<DiningTable>>(emptyList()) }
    var kots by remember { mutableStateOf<List<KOT>>(emptyList()) }
    var analytics by remember { mutableStateOf(AnalyticsData()) }

    var selectedTable by remember { mutableStateOf<DiningTable?>(null) }
    var activeBooking by remember { mutableStateOf<com.restaurant.mobile.delivery.TableBooking?>(null) }
    var activeDiningTab by remember { mutableStateOf<com.restaurant.mobile.delivery.DiningTab?>(null) }
    var showTableSelectDialog by remember { mutableStateOf(false) }

    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var cart by remember { mutableStateOf<List<CartItem>>(emptyList()) }
    var isCartOpen by remember { mutableStateOf(false) }

    fun refreshActiveTableState(table: DiningTable?) {
        selectedTable = table
        if (table != null) {
            activeBooking = database.getActiveBookingForTable(table.tableNumber)
            activeDiningTab = database.getActiveDiningTabForTable(table.tableNumber)
        } else {
            activeBooking = null
            activeDiningTab = null
        }
    }

    fun refreshAll() {
        onUserInteraction?.invoke()
        coroutineScope.launch {
            categories = ApiClient.getCategories()
            menuItems = ApiClient.getMenuItems()
            tables = ApiClient.getTables()
            kots = ApiClient.getKOTs()
            analytics = ApiClient.getAnalytics()
            selectedTable?.let { current ->
                val updatedTable = tables.find { it.tableNumber == current.tableNumber } ?: current
                refreshActiveTableState(updatedTable)
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshAll()
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFFC2410C),
            secondary = Color(0xFF059669),
            background = Color(0xFFFAF8F5),
            surface = Color.White
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        if (onBackToCustomer != null) {
                            IconButton(onClick = onBackToCustomer) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back to Customer App", tint = Color(0xFF1C1917))
                            }
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = "Bistro Lumière",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color(0xFF1C1917)
                            )
                            Text(
                                text = "${userRole.label} • ${currentScreen.title}",
                                fontSize = 12.sp,
                                color = Color(0xFF78716C)
                            )
                        }
                    },
                    actions = {
                        if (onSwitchStaffPin != null) {
                            IconButton(onClick = onSwitchStaffPin) {
                                Icon(
                                    Icons.Default.Pin,
                                    contentDescription = "Switch Staff PIN Role",
                                    tint = Color(0xFFC2410C)
                                )
                            }
                        }
                        if (currentScreen == NavigationScreen.POS) {
                            IconButton(onClick = {
                                onUserInteraction?.invoke()
                                isCartOpen = !isCartOpen
                            }) {
                                BadgedBox(
                                    badge = {
                                        if (cart.isNotEmpty()) {
                                            Badge(containerColor = Color(0xFFC2410C)) {
                                                Text(cart.sumOf { it.quantity }.toString(), color = Color.White)
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.ShoppingCart, contentDescription = "Cart", tint = Color(0xFF1C1917))
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    allowedScreens.forEach { screen ->
                        NavigationBarItem(
                            selected = currentScreen == screen,
                            onClick = {
                                onUserInteraction?.invoke()
                                currentScreen = screen
                                refreshAll()
                            },
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFFC2410C),
                                selectedTextColor = Color(0xFFC2410C),
                                indicatorColor = Color(0xFFFFE4E6)
                            )
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFFF8FAFC))
            ) {
                when (currentScreen) {
                    NavigationScreen.POS -> POSScreenView(
                        categories = categories,
                        menuItems = menuItems,
                        selectedCategoryId = selectedCategoryId,
                        onSelectCategory = { selectedCategoryId = it },
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        selectedTable = selectedTable,
                        activeBooking = activeBooking,
                        activeDiningTab = activeDiningTab,
                        onOpenTableSelector = { showTableSelectDialog = true },
                        onAddToCart = { item ->
                            if (selectedTable == null) {
                                onShowToast("⚠️ Please select a table before adding items.")
                                showTableSelectDialog = true
                            } else {
                                val existing = cart.find { it.menuItem.id == item.id }
                                cart = if (existing != null) {
                                    cart.map {
                                        if (it.menuItem.id == item.id) it.copy(quantity = it.quantity + 1)
                                        else it
                                    }
                                } else {
                                    cart + CartItem(item, 1)
                                }
                                onShowToast("${item.name} added for Table ${selectedTable?.tableNumber}")
                            }
                        }
                    )
                    NavigationScreen.KDS -> KDSScreenView(
                        kots = kots,
                        onUpdateKOTStatus = { id, status ->
                            coroutineScope.launch {
                                ApiClient.updateKOTStatus(id, status)
                                kots = ApiClient.getKOTs()
                                onShowToast("KOT #$id marked as $status")
                            }
                        }
                    )
                    NavigationScreen.TABLES -> TablesScreenView(
                        tables = tables,
                        onCycleTableStatus = { table ->
                            val nextStatus = when (table.status) {
                                "Available" -> "Occupied"
                                "Occupied" -> "Reserved"
                                else -> "Available"
                            }
                            coroutineScope.launch {
                                ApiClient.updateTableStatus(table.id, nextStatus)
                                tables = ApiClient.getTables()
                                if (selectedTable?.id == table.id) {
                                    selectedTable = selectedTable?.copy(status = nextStatus)
                                }
                                onShowToast("Table ${table.tableNumber} is now $nextStatus")
                            }
                        },
                        onSelectTableForOrder = { table ->
                            refreshActiveTableState(table)
                            currentScreen = NavigationScreen.POS
                            onShowToast("Taking order for Table ${table.tableNumber}")
                        }
                    )
                    NavigationScreen.ANALYTICS -> AnalyticsScreenView(analytics = analytics)
                }

                // Slide Cart Bottom Sheet
                if (isCartOpen) {
                    CartBottomSheetView(
                        cart = cart,
                        selectedTable = selectedTable,
                        activeBooking = activeBooking,
                        onUpdateQty = { itemId, delta ->
                            cart = cart.mapNotNull { item ->
                                if (item.menuItem.id == itemId) {
                                    val newQ = item.quantity + delta
                                    if (newQ > 0) item.copy(quantity = newQ) else null
                                } else item
                            }
                        },
                        onSubmitOrder = { paymentMethod ->
                            if (selectedTable == null) {
                                onShowToast("⚠️ Please select a table before submitting order.")
                                showTableSelectDialog = true
                                return@CartBottomSheetView
                            }
                            if (cart.isEmpty()) {
                                onShowToast("Cart is empty.")
                                return@CartBottomSheetView
                            }

                            coroutineScope.launch {
                                try {
                                    val tableNum = selectedTable!!.tableNumber
                                    val tableId = "${selectedTable!!.id}"

                                    val billItems = cart.map { c ->
                                        com.restaurant.mobile.delivery.TableBillItem(
                                            menuItemId = c.menuItem.id,
                                            name = c.menuItem.name,
                                            price = c.menuItem.price,
                                            quantity = c.quantity,
                                            taxRate = c.menuItem.taxRate
                                        )
                                    }

                                    // 1. Transactionally persist / append to SQLite live dining tab
                                    val savedTab = database.appendItemsToDiningTab(
                                        tableNumber = tableNum,
                                        tableId = tableId,
                                        newItems = billItems,
                                        associatedBooking = activeBooking
                                    )
                                    activeDiningTab = savedTab

                                    // 2. Dispatch to kitchen workflow
                                    ApiClient.checkoutOrder(
                                        paymentMethod = paymentMethod,
                                        items = cart,
                                        customerId = activeBooking?.userId?.toLongOrNull(),
                                        tableId = selectedTable!!.id
                                    )

                                    // 3. Reset cart & notify
                                    val custNotice = if (!savedTab.customerName.isNullOrBlank()) " (${savedTab.customerName})" else ""
                                    onShowToast("✅ Order for Table $tableNum$custNotice sent to Kitchen & live tab updated!")
                                    cart = emptyList()
                                    isCartOpen = false
                                    refreshAll()
                                } catch (e: Exception) {
                                    onShowToast("Failed to submit order: ${e.message}")
                                }
                            }
                        },
                        onOpenTableSelector = {
                            isCartOpen = false
                            showTableSelectDialog = true
                        },
                        onClose = { isCartOpen = false }
                    )
                }

                if (showTableSelectDialog) {
                    TableSelectionDialog(
                        tables = tables,
                        selectedTable = selectedTable,
                        getActiveBooking = { tableNum -> database.getActiveBookingForTable(tableNum) },
                        onSelectTable = { table ->
                            refreshActiveTableState(table)
                            showTableSelectDialog = false
                            onShowToast("Selected Table ${table.tableNumber}")
                        },
                        onDismiss = { showTableSelectDialog = false }
                    )
                }
            }
        }
    }
}

// 1. POS Screen Component
@Composable
fun POSScreenView(
    categories: List<Category>,
    menuItems: List<MenuItem>,
    selectedCategoryId: Long?,
    onSelectCategory: (Long?) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedTable: DiningTable?,
    activeBooking: com.restaurant.mobile.delivery.TableBooking?,
    activeDiningTab: com.restaurant.mobile.delivery.DiningTab?,
    onOpenTableSelector: () -> Unit,
    onAddToCart: (MenuItem) -> Unit
) {
    val filtered = menuItems.filter { item ->
        (selectedCategoryId == null || item.categoryId == selectedCategoryId) &&
                (searchQuery.isEmpty() || item.name.contains(searchQuery, ignoreCase = true))
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Persistent Table & Seated Customer Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenTableSelector() },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (selectedTable != null) Color(0xFFEFF6FF) else Color(0xFFFEF3C7)
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (selectedTable != null) Color(0xFFBFDBFE) else Color(0xFFFDE68A)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TableBar,
                        contentDescription = null,
                        tint = if (selectedTable != null) Color(0xFF1D4ED8) else Color(0xFFD97706),
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        if (selectedTable != null) {
                            Text(
                                text = com.restaurant.mobile.delivery.WaiterTablePairingRules.formatServingHeader(
                                    selectedTable.tableNumber,
                                    activeBooking
                                ),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF1E3A8A)
                            )
                            val subtext = buildString {
                                append("${selectedTable.seatingCapacity} Seats")
                                if (activeBooking?.partySize != null) {
                                    append(" • Party of ${activeBooking.partySize}")
                                }
                                if (activeDiningTab != null && activeDiningTab.items.isNotEmpty()) {
                                    append(" • Tab: ₹${activeDiningTab.total.toInt()} (${activeDiningTab.items.sumOf { it.quantity }} items)")
                                }
                            }
                            Text(
                                text = subtext,
                                fontSize = 12.sp,
                                color = Color(0xFF2563EB)
                            )
                        } else {
                            Text(
                                text = "No Table Selected",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                text = "Select an active table to begin taking orders",
                                fontSize = 12.sp,
                                color = Color(0xFFB45309)
                            )
                        }
                    }
                }

                Button(
                    onClick = onOpenTableSelector,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTable != null) Color(0xFF2563EB) else Color(0xFFD97706)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (selectedTable != null) "Change" else "Select Table",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search menu items...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Categories Filter Chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = selectedCategoryId == null,
                    onClick = { onSelectCategory(null) },
                    label = { Text("All") }
                )
            }
            items(categories) { cat ->
                FilterChip(
                    selected = selectedCategoryId == cat.id,
                    onClick = { onSelectCategory(cat.id) },
                    label = { Text(cat.name) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Menu Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filtered) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clickable { onAddToCart(item) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = item.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = "GST ${item.taxRate.toInt()}%",
                                fontSize = 11.sp,
                                color = Color(0xFF6366F1),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "₹${item.price.toInt()}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = Color(0xFF0F172A)
                            )
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEEF2FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Add",
                                    tint = Color(0xFF6366F1),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 2. KDS Screen Component
@Composable
fun KDSScreenView(
    kots: List<KOT>,
    onUpdateKOTStatus: (Long, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(kots.filter { it.status != "Served" }) { kot ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Order #${kot.orderId}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        val badgeColor = when (kot.status) {
                            "New" -> Color(0xFF2563EB)
                            "Preparing" -> Color(0xFFD97706)
                            else -> Color(0xFF16A34A)
                        }
                        Text(
                            text = kot.status,
                            color = badgeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    kot.items.forEach { item ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(
                                text = "${item.quantity}x ",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6366F1)
                            )
                            Text(text = item.menuItemName, fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val (nextStatus, btnText) = when (kot.status) {
                        "New" -> "Preparing" to "Start Preparing"
                        "Preparing" -> "Ready" to "Mark Ready"
                        else -> "Served" to "Serve Order"
                    }

                    Button(
                        onClick = { onUpdateKOTStatus(kot.id, nextStatus) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = btnText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// 3. Tables Screen Component
@Composable
fun TablesScreenView(
    tables: List<DiningTable>,
    onCycleTableStatus: (DiningTable) -> Unit,
    onSelectTableForOrder: ((DiningTable) -> Unit)? = null
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(tables) { table ->
            val color = when (table.status) {
                "Available" -> Color(0xFF10B981)
                "Occupied" -> Color(0xFFEF4444)
                else -> Color(0xFFF59E0B)
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCycleTableStatus(table) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = table.tableNumber,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = color
                    )
                    Text(
                        text = "${table.seatingCapacity} Seats",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = table.status,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier
                            .background(color.copy(alpha = 0.15f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                    if (onSelectTableForOrder != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = { onSelectTableForOrder(table) },
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC2410C)),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Take Order", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// 4. Analytics Screen Component
@Composable
fun AnalyticsScreenView(analytics: AnalyticsData) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        StatCard("Total Sales Revenue", "₹${analytics.totalRevenue.toInt()}", Color(0xFF10B981))
        StatCard("Total Completed Orders", "${analytics.totalOrders}", Color(0xFF6366F1))
        StatCard("Active KOT Queue", "${analytics.activeKOTs}", Color(0xFFF59E0B))
        StatCard("Table Occupancy", "${analytics.occupiedTables} Occupied / ${analytics.availableTables} Available", Color(0xFF0F172A))
    }
}

@Composable
fun StatCard(label: String, value: String, color: Color) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = label, fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}

// Cart Drawer
@Composable
fun CartBottomSheetView(
    cart: List<CartItem>,
    selectedTable: DiningTable?,
    activeBooking: com.restaurant.mobile.delivery.TableBooking?,
    onUpdateQty: (Long, Int) -> Unit,
    onSubmitOrder: (String) -> Unit,
    onOpenTableSelector: () -> Unit,
    onClose: () -> Unit
) {
    val subtotal = cart.sumOf { it.menuItem.price * it.quantity }
    val tax = subtotal * 0.05
    val grandTotal = subtotal + tax
    var isSubmitting by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onClose() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Current Order Cart", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (selectedTable != null) {
                            val custLabel = if (activeBooking != null) " (${activeBooking.userName})" else " (Walk-in)"
                            Text(
                                text = "Table ${selectedTable.tableNumber}$custLabel",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2563EB)
                            )
                        }
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (selectedTable == null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .clickable { onOpenTableSelector() },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD97706))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "No table selected. Tap here to select a table before submitting.",
                                fontSize = 12.sp,
                                color = Color(0xFF92400E),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(cart) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = item.menuItem.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(text = "₹${(item.menuItem.price * item.quantity).toInt()}", color = Color(0xFF6366F1), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onUpdateQty(item.menuItem.id, -1) }) {
                                    Text("-", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                                Text(text = "${item.quantity}", fontWeight = FontWeight.Bold)
                                IconButton(onClick = { onUpdateQty(item.menuItem.id, 1) }) {
                                    Text("+", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total (inc. GST 5%):", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("₹${grandTotal.toInt()}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color(0xFF6366F1))
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Primary Order Dispatch to Kitchen & Tab
                Button(
                    onClick = {
                        if (!isSubmitting) {
                            isSubmitting = true
                            onSubmitOrder("Dine-in Tab")
                        }
                    },
                    enabled = selectedTable != null && cart.isNotEmpty() && !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC2410C)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.SoupKitchen, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSubmitting) "Submitting..." else "Send to Kitchen & Live Tab",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Settlement Options
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (!isSubmitting) {
                                isSubmitting = true
                                onSubmitOrder("Cash")
                            }
                        },
                        enabled = selectedTable != null && cart.isNotEmpty() && !isSubmitting,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cash")
                    }
                    Button(
                        onClick = {
                            if (!isSubmitting) {
                                isSubmitting = true
                                onSubmitOrder("Card")
                            }
                        },
                        enabled = selectedTable != null && cart.isNotEmpty() && !isSubmitting,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Card")
                    }
                    Button(
                        onClick = {
                            if (!isSubmitting) {
                                isSubmitting = true
                                onSubmitOrder("UPI")
                            }
                        },
                        enabled = selectedTable != null && cart.isNotEmpty() && !isSubmitting,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("UPI")
                    }
                }
            }
        }
    }
}

// Table Selection Dialog
@Composable
fun TableSelectionDialog(
    tables: List<DiningTable>,
    selectedTable: DiningTable?,
    getActiveBooking: (String) -> com.restaurant.mobile.delivery.TableBooking?,
    onSelectTable: (DiningTable) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.TableBar,
                    contentDescription = null,
                    tint = Color(0xFFC2410C)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select Table for Order", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tables) { table ->
                    val isSelected = selectedTable?.id == table.id
                    val booking = remember(table.tableNumber) { getActiveBooking(table.tableNumber) }
                    val statusColor = when (table.status) {
                        "Available" -> Color(0xFF10B981)
                        "Occupied" -> Color(0xFFEF4444)
                        else -> Color(0xFFF59E0B)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectTable(table) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFFFFEDD5) else Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color(0xFFC2410C) else Color(0xFFE2E8F0)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = table.tableNumber,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "(${table.seatingCapacity} Seats)",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                                if (booking != null) {
                                    Text(
                                        text = "Customer: ${booking.userName} (${booking.status})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF2563EB)
                                    )
                                } else {
                                    Text(
                                        text = "Status: ${table.status} (Walk-in)",
                                        fontSize = 12.sp,
                                        color = statusColor
                                    )
                                }
                            }

                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = Color(0xFFC2410C),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF78716C))
            }
        }
    )
}
