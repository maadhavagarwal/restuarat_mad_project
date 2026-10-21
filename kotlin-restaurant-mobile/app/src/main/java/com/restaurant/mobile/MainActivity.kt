package com.restaurant.mobile

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.restaurant.mobile.api.ApiClient
import com.restaurant.mobile.delivery.BillSplitResult
import com.restaurant.mobile.delivery.BillSplittingRules
import com.restaurant.mobile.delivery.DerivedTableState
import com.restaurant.mobile.delivery.DiningTabStatus
import com.restaurant.mobile.delivery.TableLifecycleRules
import com.restaurant.mobile.delivery.TableLifecycleState
import com.restaurant.mobile.delivery.TipAndServiceChargeRules
import com.restaurant.mobile.delivery.TipAndServiceChargeRules.TipOption
import com.restaurant.mobile.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.delay
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
                    sqlDb = deliveryVm.sqlDb,
                    backendEngine = deliveryVm.backend
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
    sqlDb: com.restaurant.mobile.delivery.RestroSqlDatabase? = null,
    backendEngine: com.restaurant.mobile.delivery.RestroFirebaseBackendEngine? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val database = remember(sqlDb) { sqlDb ?: com.restaurant.mobile.delivery.RestroSqlDatabase(context) }
    val backend = remember(backendEngine, database) {
        backendEngine ?: com.restaurant.mobile.delivery.RestroFirebaseBackendEngine(context, database)
    }

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

    var readyNotificationBanner by rememberSaveable { mutableStateOf<String?>(null) }
    var notifiedReadyKotIds by rememberSaveable { mutableStateOf(setOf<Long>()) }

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
            val remoteAnalytics = try {
                ApiClient.getAnalytics()
            } catch (_: Exception) {
                null
            }
            analytics = withContext(Dispatchers.IO) {
                try {
                    backend.getAnalyticsData(fallback = remoteAnalytics)
                } catch (_: Exception) {
                    remoteAnalytics ?: AnalyticsData()
                }
            }
            val localKots = database.getAllKotTickets()
            val remoteKots = ApiClient.getKOTs()
            kots = if (localKots.isNotEmpty()) {
                val localIds = localKots.map { it.id }.toSet()
                localKots + remoteKots.filter { it.id !in localIds }
            } else {
                remoteKots
            }
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFFF8FAFC))
            ) {
                // In-App Ready Notification Banner for Waiter
                if (readyNotificationBanner != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF16A34A))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = Color(0xFF15803D),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = readyNotificationBanner!!,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF166534)
                                )
                            }
                            IconButton(
                                onClick = { readyNotificationBanner = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = Color(0xFF166534),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
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
                            },
                            onInitiatePayment = { tab, tip, isOptedOut ->
                                coroutineScope.launch {
                                    database.initiatePaymentForDiningTab(
                                        tabId = tab.id,
                                        paymentMethod = "Card",
                                        tip = tip,
                                        isServiceChargeOptedOut = isOptedOut
                                    )
                                    refreshAll()
                                    onShowToast("Bill requested for Table ${tab.tableNumber}: Payment Pending")
                                }
                            }
                        )
                        NavigationScreen.KDS -> KDSScreenView(
                            kots = kots,
                            onUpdateKOTStatus = { id, status ->
                                coroutineScope.launch {
                                    database.updateKotTicketStatus(id, status)
                                    ApiClient.updateKOTStatus(id, status)
                                    val targetKot = kots.find { it.id == id }
                                    if (status.equals("Ready", ignoreCase = true) && targetKot != null) {
                                        if (id !in notifiedReadyKotIds) {
                                            notifiedReadyKotIds = notifiedReadyKotIds + id
                                            val message = com.restaurant.mobile.delivery.KotTimerRules.formatReadyNotificationMessage(
                                                ticketId = targetKot.id,
                                                tableNumber = targetKot.tableNumber
                                            )
                                            readyNotificationBanner = message
                                            onShowToast(message)
                                        }
                                    } else {
                                        onShowToast("KOT #$id marked as $status")
                                    }
                                    refreshAll()
                                }
                            }
                        )
                        NavigationScreen.TABLES -> TablesScreenView(
                            tables = tables,
                            getActiveTab = { tableNum -> database.getActiveDiningTabForTable(tableNum) },
                            getBookingsForTable = { tableNum -> database.getTableBookingsForTableNumber(tableNum) },
                            onSeatTable = { table, booking ->
                                coroutineScope.launch {
                                    val (seatedTab, _) = database.seatTableAndOpenTab(
                                        tableNumber = table.tableNumber,
                                        tableId = "${table.id}",
                                        guestCount = booking?.partySize ?: 2,
                                        bookingId = booking?.id,
                                        customerName = booking?.userName,
                                        customerPhone = booking?.userPhone
                                    )
                                    refreshActiveTableState(table)
                                    onShowToast("Table ${table.tableNumber} is now Occupied (${seatedTab.guestCount} guests)")
                                    refreshAll()
                                }
                            },
                            onSelectTableForOrder = { table ->
                                refreshActiveTableState(table)
                                currentScreen = NavigationScreen.POS
                                onShowToast("Taking order for Table ${table.tableNumber}")
                            },
                            onInitiatePayment = { tab, tip, isOptedOut ->
                                coroutineScope.launch {
                                    database.initiatePaymentForDiningTab(
                                        tabId = tab.id,
                                        paymentMethod = "Card",
                                        tip = tip,
                                        isServiceChargeOptedOut = isOptedOut
                                    )
                                    refreshAll()
                                    onShowToast("Bill requested for Table ${tab.tableNumber}: Payment Pending")
                                }
                            },
                            onVerifyPayment = { tab ->
                                coroutineScope.launch {
                                    val result = database.verifyAndCompletePaymentForDiningTab(tab.id, tab.paymentTransactionId)
                                    if (result != null) {
                                        refreshAll()
                                        onShowToast("Payment verified for Table ${tab.tableNumber}! Table is now Available.")
                                    } else {
                                        onShowToast("Payment verification failed for Table ${tab.tableNumber}: Awaiting authorization")
                                    }
                                }
                            },
                            onRevertPayment = { tab ->
                                coroutineScope.launch {
                                    database.failOrRevertPaymentForDiningTab(tab.id, "Payment cancelled or failed")
                                    refreshAll()
                                    onShowToast("Payment reverted for Table ${tab.tableNumber}. Table remains Occupied.")
                                }
                            },
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
                            categories = categories,
                            onUpdateQty = { itemId, delta ->
                                cart = cart.mapNotNull { item ->
                                    if (item.menuItem.id == itemId) {
                                        val newQ = item.quantity + delta
                                        if (newQ > 0) item.copy(quantity = newQ) else null
                                    } else item
                                }
                            },
                            onSubmitOrder = { paymentMethod, roundName ->
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
                                        val roundNumber = database.getNextRoundNumberForTable(tableNum)

                                        val billItems = cart.map { c ->
                                            com.restaurant.mobile.delivery.TableBillItem(
                                                menuItemId = c.menuItem.id,
                                                name = c.menuItem.name,
                                                price = c.menuItem.price,
                                                quantity = c.quantity,
                                                taxRate = c.menuItem.taxRate,
                                                round = roundNumber,
                                                roundName = roundName
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

                                        // 2. Persist Kitchen Order Ticket (KOT) for this round
                                        val kotItems = cart.map { c ->
                                            com.restaurant.mobile.model.KOTItem(
                                                id = c.menuItem.id,
                                                menuItemName = c.menuItem.name,
                                                quantity = c.quantity,
                                                priceAtTimeOfOrder = c.menuItem.price
                                            )
                                        }
                                        database.createKotTicket(
                                            tableNumber = tableNum,
                                            tableId = tableId,
                                            items = kotItems,
                                            round = roundNumber,
                                            roundName = roundName,
                                            customerName = activeBooking?.userName
                                        )

                                        // 3. Dispatch to kitchen workflow / API
                                        ApiClient.checkoutOrder(
                                            paymentMethod = paymentMethod,
                                            items = cart,
                                            customerId = activeBooking?.userId?.toLongOrNull(),
                                            tableId = selectedTable!!.id
                                        )

                                        // 4. Reset cart & notify
                                        val custNotice = if (!savedTab.customerName.isNullOrBlank()) " (${savedTab.customerName})" else ""
                                        onShowToast("✅ Round $roundNumber ($roundName) for Table $tableNum$custNotice sent to Kitchen & Live Tab!")
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
                        getActiveTab = { tableNum -> database.getActiveDiningTabForTable(tableNum) },
                        getBookingsForTable = { tableNum -> database.getTableBookingsForTableNumber(tableNum) },
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
    onAddToCart: (MenuItem) -> Unit,
    onInitiatePayment: ((com.restaurant.mobile.delivery.DiningTab, Double, Boolean) -> Unit)? = null
) {
    var showLiveTabDialog by remember { mutableStateOf(false) }

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
                                    append(" • Tab: ₹${activeDiningTab.totalRupees.toInt()} (${activeDiningTab.items.sumOf { it.quantity }} items)")
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

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (activeDiningTab != null && activeDiningTab.items.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showLiveTabDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.Receipt,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFF2563EB)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Live Tab",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2563EB)
                            )
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

    if (showLiveTabDialog && activeDiningTab != null) {
        LiveDiningTabDialog(
            diningTab = activeDiningTab,
            onDismiss = { showLiveTabDialog = false },
            onInitiatePayment = onInitiatePayment
        )
    }
}

@Composable
fun LiveDiningTabDialog(
    diningTab: com.restaurant.mobile.delivery.DiningTab,
    onDismiss: () -> Unit,
    onInitiatePayment: ((tab: com.restaurant.mobile.delivery.DiningTab, tip: Double, isServiceChargeOptedOut: Boolean) -> Unit)? = null
) {
    var selectedTipOption by remember {
        mutableStateOf(
            when (diningTab.tipRupees) {
                0.0 -> TipOption.NO_TIP
                50.0 -> TipOption.TIP_50
                100.0 -> TipOption.TIP_100
                200.0 -> TipOption.TIP_200
                else -> TipOption.CUSTOM
            }
        )
    }
    var customTipInput by remember {
        mutableStateOf(
            if (diningTab.tipRupees > 0.0 && diningTab.tipRupees !in TipAndServiceChargeRules.PRESET_TIPS) {
                diningTab.tipRupees.toInt().toString()
            } else ""
        )
    }
    var isServiceChargeOptedOut by remember {
        mutableStateOf(diningTab.isServiceChargeOptedOut)
    }

    val isCustomInvalid = remember(selectedTipOption, customTipInput) {
        selectedTipOption == TipOption.CUSTOM &&
                customTipInput.isNotBlank() &&
                TipAndServiceChargeRules.validateCustomTip(customTipInput) == null
    }

    val effectiveTip = remember(selectedTipOption, customTipInput) {
        TipAndServiceChargeRules.resolveTipAmount(selectedTipOption, customTipInput)
    }

    val liveBilling = remember(diningTab.subtotalRupees, diningTab.taxRupees, isServiceChargeOptedOut, effectiveTip) {
        TipAndServiceChargeRules.computeFinalBill(
            subtotal = diningTab.subtotalRupees,
            tax = diningTab.taxRupees,
            isServiceChargeOptedOut = isServiceChargeOptedOut,
            tipAmount = effectiveTip
        )
    }

    var splitPartySize by remember {
        mutableStateOf(diningTab.guestCount.takeIf { it > 0 } ?: 1)
    }

    val billSplitResult = remember(liveBilling.total, splitPartySize) {
        BillSplittingRules.splitBill(
            totalAmount = liveBilling.total,
            partySize = splitPartySize
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Tab: Table ${diningTab.tableNumber}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "${diningTab.items.sumOf { it.quantity }} items",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!diningTab.customerName.isNullOrBlank()) {
                    Text(
                        text = "Customer: ${diningTab.customerName}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2563EB)
                    )
                }

                val groupedRounds = diningTab.itemsGroupedByRound().toSortedMap()
                groupedRounds.forEach { (round, roundItems) ->
                    val courseName = roundItems.firstOrNull()?.roundName?.takeIf { it.isNotBlank() }
                    val header = if (courseName != null) "Round $round — $courseName" else "Round $round"
                    Text(
                        text = header,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFFC2410C)
                    )
                    roundItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${item.quantity}x ${item.name}",
                                fontSize = 13.sp,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = "₹${(item.price * item.quantity).toInt()}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                HorizontalDivider(color = Color(0xFFE2E8F0))

                // Service Charge Opt-Out Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "5% Service Charge",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = if (isServiceChargeOptedOut) "Opted out (discretionary)" else "Included (discretionary for staff)",
                                fontSize = 11.sp,
                                color = if (isServiceChargeOptedOut) Color(0xFFDC2626) else Color(0xFF64748B)
                            )
                        }
                        Switch(
                            checked = !isServiceChargeOptedOut,
                            onCheckedChange = { isServiceChargeOptedOut = !it }
                        )
                    }
                }

                // Tip Selection Section
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Add Staff Tip",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TipOption.values().forEach { option ->
                            val isSelected = selectedTipOption == option
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) Color(0xFFC2410C) else Color(0xFFF1F5F9),
                                border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedTipOption = option
                                    }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = option.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Color(0xFF334155),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    if (selectedTipOption == TipOption.CUSTOM) {
                        OutlinedTextField(
                            value = customTipInput,
                            onValueChange = { customTipInput = it },
                            label = { Text("Custom Tip (₹)", fontSize = 12.sp) },
                            placeholder = { Text("Enter amount", fontSize = 12.sp) },
                            isError = isCustomInvalid,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (isCustomInvalid) {
                            Text(
                                text = "Please enter a valid positive amount (e.g. 150)",
                                fontSize = 11.sp,
                                color = Color(0xFFDC2626)
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFE2E8F0))

                // Breakdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Subtotal", fontSize = 13.sp, color = Color(0xFF64748B))
                    Text("₹${liveBilling.subtotal.toInt()}", fontSize = 13.sp, color = Color(0xFF0F172A))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Taxes (5% GST)", fontSize = 13.sp, color = Color(0xFF64748B))
                    Text("₹${liveBilling.tax.toInt()}", fontSize = 13.sp, color = Color(0xFF0F172A))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isServiceChargeOptedOut) "Service Charge (5% - Opted out)" else "Service Charge (5%)",
                        fontSize = 13.sp,
                        color = if (isServiceChargeOptedOut) Color(0xFF94A3B8) else Color(0xFF64748B)
                    )
                    Text(
                        text = if (isServiceChargeOptedOut) "₹0" else "₹${liveBilling.serviceCharge.toInt()}",
                        fontSize = 13.sp,
                        color = if (isServiceChargeOptedOut) Color(0xFF94A3B8) else Color(0xFF0F172A)
                    )
                }
                if (liveBilling.tip > 0.0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Staff Tip (${selectedTipOption.label})", fontSize = 13.sp, color = Color(0xFF059669))
                        Text("₹${liveBilling.tip.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF059669))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Grand Total", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                    Text("₹${liveBilling.total.toInt()}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFFC2410C))
                }

                HorizontalDivider(color = Color(0xFFE2E8F0))

                // Bill Splitting Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Split Bill",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.White,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clickable(enabled = splitPartySize > 1) {
                                            if (splitPartySize > 1) splitPartySize--
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "−",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = if (splitPartySize > 1) Color(0xFF0F172A) else Color(0xFF94A3B8)
                                        )
                                    }
                                }

                                Text(
                                    text = "$splitPartySize ${if (splitPartySize == 1) "person" else "people"}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1E293B)
                                )

                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.White,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clickable(enabled = splitPartySize < BillSplittingRules.MAX_PARTY_SIZE) {
                                            if (splitPartySize < BillSplittingRules.MAX_PARTY_SIZE) splitPartySize++
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "+",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color(0xFF0F172A)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Each person pays",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = "₹${String.format(java.util.Locale.ENGLISH, "%.2f", billSplitResult.perPersonBaseAmount)}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2563EB)
                            )
                        }

                        if (billSplitResult.remainderAmount > 0.0) {
                            Text(
                                text = "Note: ₹${String.format(java.util.Locale.ENGLISH, "%.2f", billSplitResult.remainderAmount)} remainder rounded to first diner for exact reconciliation.",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B),
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onInitiatePayment != null) {
                    Button(
                        onClick = {
                            onInitiatePayment(diningTab, effectiveTip, isServiceChargeOptedOut)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Bill ₹${liveBilling.total.toInt()}", fontWeight = FontWeight.Bold)
                    }
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC2410C)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

// 2. KDS Screen Component
@Composable
fun KDSScreenView(
    kots: List<KOT>,
    onUpdateKOTStatus: (Long, String) -> Unit
) {
    var currentTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(10_000L) // Refresh elapsed order timer visibly every 10 seconds
            currentTick = System.currentTimeMillis()
        }
    }

    val activeTickets = remember(kots) { kots.filter { it.status != "Served" } }
    val groupedByTable = remember(activeTickets) {
        activeTickets.groupBy { it.tableNumber.ifBlank { "Unassigned" } }.toSortedMap()
    }

    if (activeTickets.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SoupKitchen,
                        contentDescription = null,
                        tint = Color(0xFFC2410C),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Kitchen Display Queue Clear",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "All KOT tickets have been prepared and served. Incoming orders from Waiter POS will display here grouped by table and round.",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            groupedByTable.forEach { (tableNumber, ticketsForTable) ->
                item(key = "table_header_$tableNumber") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.TableBar,
                                contentDescription = null,
                                tint = Color(0xFFC2410C),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Table $tableNumber",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = Color(0xFF0F172A)
                            )
                        }
                        Text(
                            text = "${ticketsForTable.size} Active ${if (ticketsForTable.size == 1) "Round" else "Rounds"}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                items(ticketsForTable.sortedWith(compareBy({ it.round }, { it.id })), key = { it.id }) { kot ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Primary presentation: Table T3 — Round 1 (Starters) — READY
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val roundTitle = buildString {
                                    append("Table ${kot.tableNumber.ifBlank { tableNumber }}")
                                    append(" — Round ${kot.round}")
                                    if (kot.roundName.isNotBlank()) {
                                        append(" (${kot.roundName})")
                                    }
                                }
                                Text(
                                    text = roundTitle,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF0F172A)
                                )

                                val (badgeBg, badgeFg) = when (kot.status.lowercase()) {
                                    "new" -> Color(0xFFEFF6FF) to Color(0xFF1D4ED8)
                                    "preparing" -> Color(0xFFFEF3C7) to Color(0xFFD97706)
                                    "ready" -> Color(0xFFDCFCE7) to Color(0xFF15803D)
                                    else -> Color(0xFFF1F5F9) to Color(0xFF475569)
                                }
                                Text(
                                    text = kot.status.uppercase(),
                                    color = badgeFg,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .background(badgeBg, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Subheader: Order timer & ticket ID
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = com.restaurant.mobile.delivery.KotTimerRules.formatElapsedOrderTime(
                                            kot.createdAt,
                                            java.time.Instant.ofEpochMilli(currentTick)
                                        ),
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = " • #${kot.ticketNumber}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }

                                if (!kot.customerName.isNullOrBlank()) {
                                    Text(
                                        text = kot.customerName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF2563EB)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Items List
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                kot.items.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row {
                                            Text(
                                                text = "${item.quantity}x ",
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFC2410C),
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = item.menuItemName,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                color = Color(0xFF1E293B)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Action button
                            val (nextStatus, btnText, btnColor) = when (kot.status.lowercase()) {
                                "new" -> Triple("Preparing", "Start Preparing", Color(0xFFD97706))
                                "preparing" -> Triple("Ready", "Mark Ready", Color(0xFF16A34A))
                                else -> Triple("Served", "Serve Order", Color(0xFF475569))
                            }

                            Button(
                                onClick = { onUpdateKOTStatus(kot.id, nextStatus) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(text = btnText, fontWeight = FontWeight.Bold)
                            }
                        }
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
    onCycleTableStatus: (DiningTable) -> Unit = {},
    onSelectTableForOrder: ((DiningTable) -> Unit)? = null,
    getActiveTab: ((String) -> com.restaurant.mobile.delivery.DiningTab?)? = null,
    getBookingsForTable: ((String) -> List<com.restaurant.mobile.delivery.TableBooking>)? = null,
    onSeatTable: ((DiningTable, com.restaurant.mobile.delivery.TableBooking?) -> Unit)? = null,
    onInitiatePayment: ((com.restaurant.mobile.delivery.DiningTab, Double, Boolean) -> Unit)? = null,
    onVerifyPayment: ((com.restaurant.mobile.delivery.DiningTab) -> Unit)? = null,
    onRevertPayment: ((com.restaurant.mobile.delivery.DiningTab) -> Unit)? = null
) {
    var currentTick by remember { mutableStateOf(System.currentTimeMillis()) }
    var billingTabTarget by remember { mutableStateOf<com.restaurant.mobile.delivery.DiningTab?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10000L) // 10s tick for elapsed dining timer
            currentTick = System.currentTimeMillis()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Lifecycle Legend Header
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusLegendChip("Available", Color(0xFF10B981))
                StatusLegendChip("Reserved", Color(0xFF8B5CF6))
                StatusLegendChip("Occupied", Color(0xFFF59E0B))
                StatusLegendChip("Billed", Color(0xFFEF4444))
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tables) { table ->
                val activeTab = getActiveTab?.invoke(table.tableNumber)
                val bookings = getBookingsForTable?.invoke(table.tableNumber) ?: emptyList()
                val derivedState = remember(table.tableNumber, activeTab, bookings, currentTick) {
                    if (getActiveTab != null || getBookingsForTable != null) {
                        TableLifecycleRules.deriveTableState(
                            tableNumber = table.tableNumber,
                            diningTab = activeTab,
                            bookingsForTable = bookings
                        )
                    } else {
                        when (table.status) {
                            "Occupied" -> DerivedTableState(
                                tableNumber = table.tableNumber,
                                lifecycleState = TableLifecycleState.OCCUPIED,
                                guestCount = 2,
                                elapsedDiningTime = "Dining active"
                            )
                            "Reserved" -> DerivedTableState(
                                tableNumber = table.tableNumber,
                                lifecycleState = TableLifecycleState.RESERVED,
                                reservationInfo = "Upcoming Reservation"
                            )
                            "Billed" -> DerivedTableState(
                                tableNumber = table.tableNumber,
                                lifecycleState = TableLifecycleState.BILLED,
                                elapsedDiningTime = "Payment Pending"
                            )
                            else -> DerivedTableState(
                                tableNumber = table.tableNumber,
                                lifecycleState = TableLifecycleState.AVAILABLE
                            )
                        }
                    }
                }

                val state = derivedState.lifecycleState
                val stateColor = Color(state.colorHex)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCycleTableStatus(table) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.5.dp,
                        color = stateColor.copy(alpha = 0.6f)
                    ),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Header: Table Number & Status Chip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = table.tableNumber,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0F172A)
                            )
                            Surface(
                                color = stateColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = state.label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = stateColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = "${table.seatingCapacity} Seats",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )

                        // Body: contextual details per state
                        when (state) {
                            TableLifecycleState.RESERVED -> {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = derivedState.reservationInfo ?: "Reserved",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = stateColor
                                    )
                                    val booking = derivedState.associatedBooking
                                    if (booking != null) {
                                        Text(
                                            text = "${booking.partySize} Guests · ${booking.userName}",
                                            fontSize = 10.sp,
                                            color = Color(0xFF475569)
                                        )
                                    }
                                }
                            }
                            TableLifecycleState.OCCUPIED -> {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "${derivedState.guestCount ?: 2} Guests Seated",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = stateColor
                                    )
                                    Text(
                                        text = derivedState.elapsedDiningTime ?: "Dining just now",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF475569)
                                    )
                                    if (activeTab != null && activeTab.items.isNotEmpty()) {
                                        Text(
                                            text = "Tab: ₹${activeTab.totalRupees.toInt()} (${activeTab.items.sumOf { it.quantity }} items)",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                    }
                                }
                            }
                            TableLifecycleState.BILLED -> {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Payment Pending",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = stateColor
                                    )
                                    Text(
                                        text = "Due: ₹${activeTab?.totalRupees?.toInt() ?: 0}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = if (!activeTab?.paymentTransactionId.isNullOrBlank()) "Auth: ${activeTab?.paymentTransactionId}" else (derivedState.elapsedDiningTime ?: "Awaiting verification"),
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                            TableLifecycleState.AVAILABLE -> {
                                Text(
                                    text = "Ready for guests",
                                    fontSize = 11.sp,
                                    color = Color(0xFF059669)
                                )
                            }
                        }

                        // Contextual Action Buttons
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            when (state) {
                                TableLifecycleState.AVAILABLE -> {
                                    if (onSelectTableForOrder != null) {
                                        Button(
                                            onClick = { onSelectTableForOrder(table) },
                                            modifier = Modifier.fillMaxWidth().height(28.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC2410C)),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text("Take Order", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (onSeatTable != null) {
                                        OutlinedButton(
                                            onClick = { onSeatTable(table, null) },
                                            modifier = Modifier.fillMaxWidth().height(26.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text("Seat Walk-in", fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                                TableLifecycleState.RESERVED -> {
                                    if (onSeatTable != null) {
                                        Button(
                                            onClick = { onSeatTable(table, derivedState.associatedBooking) },
                                            modifier = Modifier.fillMaxWidth().height(28.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text("Seat Guests", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (onSelectTableForOrder != null) {
                                        OutlinedButton(
                                            onClick = { onSelectTableForOrder(table) },
                                            modifier = Modifier.fillMaxWidth().height(26.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text("Take Order", fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                                TableLifecycleState.OCCUPIED -> {
                                    if (onSelectTableForOrder != null) {
                                        Button(
                                            onClick = { onSelectTableForOrder(table) },
                                            modifier = Modifier.fillMaxWidth().height(28.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC2410C)),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text("Take Order", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (onInitiatePayment != null && activeTab != null) {
                                        Button(
                                            onClick = { billingTabTarget = activeTab },
                                            modifier = Modifier.fillMaxWidth().height(26.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text("Bill Table", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                                TableLifecycleState.BILLED -> {
                                    val hasAuth = !activeTab?.paymentTransactionId.isNullOrBlank()
                                    if (onVerifyPayment != null && activeTab != null) {
                                        Button(
                                            onClick = { onVerifyPayment(activeTab) },
                                            modifier = Modifier.fillMaxWidth().height(28.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (hasAuth) Color(0xFF10B981) else Color(0xFF64748B)
                                            ),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(if (hasAuth) "Verify Payment" else "Awaiting Auth", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (onRevertPayment != null && activeTab != null) {
                                        OutlinedButton(
                                            onClick = { onRevertPayment(activeTab) },
                                            modifier = Modifier.fillMaxWidth().height(26.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text("Revert", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFEF4444))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (billingTabTarget != null) {
            LiveDiningTabDialog(
                diningTab = billingTabTarget!!,
                onDismiss = { billingTabTarget = null },
                onInitiatePayment = { tab, tip, isOptedOut ->
                    billingTabTarget = null
                    onInitiatePayment?.invoke(tab, tip, isOptedOut)
                }
            )
        }
    }
}

@Composable
fun StatusLegendChip(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF334155)
        )
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
    categories: List<Category> = emptyList(),
    onUpdateQty: (Long, Int) -> Unit,
    onSubmitOrder: (String, String) -> Unit,
    onOpenTableSelector: () -> Unit,
    onClose: () -> Unit
) {
    val subtotal = cart.sumOf { it.menuItem.price * it.quantity }
    val tax = subtotal * 0.05
    val grandTotal = subtotal + tax
    var isSubmitting by remember { mutableStateOf(false) }

    var selectedRoundName by remember(cart) {
        mutableStateOf(com.restaurant.mobile.delivery.KotRoundRules.detectRoundName(cart, categories))
    }

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

                // Course / Round Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Order Round / Course:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569)
                    )
                    Text(
                        text = selectedRoundName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC2410C)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(com.restaurant.mobile.delivery.KotRoundRules.STANDARD_COURSES) { course ->
                        FilterChip(
                            selected = selectedRoundName.equals(course, ignoreCase = true),
                            onClick = { selectedRoundName = course },
                            label = { Text(course, fontSize = 11.sp) }
                        )
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
                            onSubmitOrder("Dine-in Tab", selectedRoundName)
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
                                onSubmitOrder("Cash", selectedRoundName)
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
                                onSubmitOrder("Card", selectedRoundName)
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
                                onSubmitOrder("UPI", selectedRoundName)
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
    getActiveTab: ((String) -> com.restaurant.mobile.delivery.DiningTab?)? = null,
    getBookingsForTable: ((String) -> List<com.restaurant.mobile.delivery.TableBooking>)? = null,
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
                    val activeTab = remember(table.tableNumber) { getActiveTab?.invoke(table.tableNumber) }
                    val bookings = remember(table.tableNumber) {
                        getBookingsForTable?.invoke(table.tableNumber) ?: listOfNotNull(booking)
                    }
                    val derivedState = remember(table.tableNumber, activeTab, bookings) {
                        TableLifecycleRules.deriveTableState(
                            tableNumber = table.tableNumber,
                            diningTab = activeTab,
                            bookingsForTable = bookings
                        )
                    }
                    val statusColor = Color(derivedState.lifecycleState.colorHex)
                    val statusLabel = derivedState.lifecycleState.label

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
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = statusColor.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = statusLabel,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (booking != null) {
                                    Text(
                                        text = "Customer: ${booking.userName} (${booking.status})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF2563EB)
                                    )
                                } else if (activeTab != null && activeTab.items.isNotEmpty()) {
                                    Text(
                                        text = "Tab: ₹${activeTab.totalRupees.toInt()} · ${derivedState.elapsedDiningTime ?: "Active"}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF0F172A)
                                    )
                                } else {
                                    Text(
                                        text = "Status: $statusLabel (Walk-in)",
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
