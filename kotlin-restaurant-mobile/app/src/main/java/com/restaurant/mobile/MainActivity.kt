package com.restaurant.mobile
import com.restaurant.mobile.delivery.localizedName
import android.content.Intent

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.restaurant.mobile.delivery.TopDishStat

class MainActivity : ComponentActivity(), com.razorpay.PaymentResultWithDataListener {

    private var onNewIntentCallback: ((Intent) -> Unit)? = null
    private var deliveryVmRef: com.restaurant.mobile.delivery.DeliveryViewModel? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        onNewIntentCallback?.invoke(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            deliveryVmRef?.detectAndApplyLiveGps(force = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.razorpay.Checkout.preload(applicationContext)
        com.restaurant.mobile.delivery.RestroI18n.init(this)
        val requiredPerms = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.SEND_SMS
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requiredPerms.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val missingPerms = requiredPerms.filter {
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missingPerms.isNotEmpty()) {
            requestPermissions(missingPerms.toTypedArray(), 101)
        }
        setContent {
            val deliveryVm: com.restaurant.mobile.delivery.DeliveryViewModel =
                androidx.lifecycle.viewmodel.compose.viewModel()
            deliveryVmRef = deliveryVm
            deliveryVm.currentActivity = this

            LaunchedEffect(Unit) {
                deliveryVm.detectAndApplyLiveGps()
            }

            // Handle notification tap navigation
            DisposableEffect(Unit) {
                fun handleAuthIntent(i: Intent?) {
                    val loginEmail = i?.getStringExtra("EXTRA_LOGIN_EMAIL")
                    val loginPwd = i?.getStringExtra("EXTRA_LOGIN_PWD") ?: "password123"
                    if (!loginEmail.isNullOrBlank()) {
                        deliveryVm.auth(loginEmail, loginPwd, "User", false)
                    }
                    val orderStatusAction = i?.getStringExtra("EXTRA_ORDER_STATUS")
                    val targetOrderId = i?.getStringExtra("EXTRA_ORDER_ID")
                    if (!orderStatusAction.isNullOrBlank() && !targetOrderId.isNullOrBlank()) {
                        deliveryVm.sqlDb.updateOrderStatus(targetOrderId, orderStatusAction)
                        deliveryVm.cloudEngine.updateOrderStatusInCloud(targetOrderId, orderStatusAction)
                        deliveryVm.orders = deliveryVm.orders.map {
                            if (it.id == targetOrderId) it.copy(status = orderStatusAction) else it
                        }
                        deliveryVm.liveRiderStatus = orderStatusAction
                        deliveryVm.liveRiderEtaMins = when (orderStatusAction.trim().lowercase()) {
                            "placed", "received", "pending" -> 28
                            "confirmed" -> 25
                            "preparing" -> 20
                            "ready" -> 14
                            "accepted" -> 10
                            "out for delivery", "picked up" -> 6
                            "delivered" -> 0
                            else -> 15
                        }
                        deliveryVm.liveRiderDistanceKm = when (orderStatusAction.trim().lowercase()) {
                            "placed", "received", "pending" -> 5.2
                            "confirmed" -> 4.8
                            "preparing" -> 3.8
                            "ready" -> 2.8
                            "accepted" -> 2.0
                            "out for delivery", "picked up" -> 0.8
                            "delivered" -> 0.0
                            else -> 2.0
                        }
                        if (orderStatusAction.equals("Delivered", ignoreCase = true)) {
                            deliveryVm.notifyOrderDelivered(targetOrderId)
                        }
                    }
                    val assignRiderAction = i?.getStringExtra("EXTRA_ASSIGN_RIDER")
                    if (!assignRiderAction.isNullOrBlank() && !targetOrderId.isNullOrBlank()) {
                        val rId = i.getStringExtra("EXTRA_RIDER_ID") ?: "staff-rider-1"
                        val rName = i.getStringExtra("EXTRA_RIDER_NAME") ?: "Ramesh Kumar"
                        val rPhone = i.getStringExtra("EXTRA_RIDER_PHONE") ?: "+91 98112 34567"
                        deliveryVm.sqlDb.assignRiderToOrder(targetOrderId, rId)
                        deliveryVm.sqlDb.updateOrderStatus(targetOrderId, "Accepted")
                        deliveryVm.cloudEngine.assignRiderToOrderInCloud(targetOrderId, rId, rName, rPhone, "Accepted")
                        deliveryVm.orders = deliveryVm.orders.map {
                            if (it.id == targetOrderId) it.copy(riderId = rId, riderName = rName, riderPhone = rPhone, status = "Accepted") else it
                        }
                        deliveryVm.liveRiderStatus = "Accepted"
                        deliveryVm.liveRiderEtaMins = 10
                        deliveryVm.liveRiderDistanceKm = 2.0
                    }
                    if (!targetOrderId.isNullOrBlank()) {
                        deliveryVm.orderId = targetOrderId
                        deliveryVm.track(targetOrderId)
                    }
                }
                handleAuthIntent(intent)
                val dest = intent?.getStringExtra("EXTRA_DESTINATION")
                val tableNum = intent?.getStringExtra("EXTRA_TABLE_NUMBER")
                if (!dest.isNullOrBlank()) {
                    deliveryVm.handleNotificationNavigation(dest, tableNum)
                }
                onNewIntentCallback = { newIntent ->
                    handleAuthIntent(newIntent)
                    val newDest = newIntent.getStringExtra("EXTRA_DESTINATION")
                    val newTableNum = newIntent.getStringExtra("EXTRA_TABLE_NUMBER")
                    if (!newDest.isNullOrBlank()) {
                        deliveryVm.handleNotificationNavigation(newDest, newTableNum)
                    }
                }
                onDispose {
                    onNewIntentCallback = null
                }
            }

            var isStaffMode by rememberSaveable { mutableStateOf(false) }
            var userRequestedCustomerApp by rememberSaveable { mutableStateOf(false) }
            var lastStaffInteractionMs by remember { mutableStateOf(System.currentTimeMillis()) }

            LaunchedEffect(deliveryVm.profile?.id, deliveryVm.profile?.role) {
                val p = deliveryVm.profile
                val isStaff = (p?.role ?: com.restaurant.mobile.delivery.UserRole.CUSTOMER).isStaff
                if (p != null && isStaff && !userRequestedCustomerApp) {
                    isStaffMode = true
                } else if (p == null || !isStaff) {
                    isStaffMode = false
                    userRequestedCustomerApp = false
                }
            }

            // 3-Minute (180,000 ms) Inactivity Auto-Lock Timer for Staff Portal (Kiosk stays locked in Kiosk mode)
            LaunchedEffect(isStaffMode, lastStaffInteractionMs, deliveryVm.profile?.role) {
                val curRole = deliveryVm.profile?.role ?: com.restaurant.mobile.delivery.UserRole.CUSTOMER
                if (isStaffMode && curRole != com.restaurant.mobile.delivery.UserRole.KIOSK) {
                    kotlinx.coroutines.delay(180_000L)
                    if (System.currentTimeMillis() - lastStaffInteractionMs >= 180_000L) {
                        isStaffMode = false
                        userRequestedCustomerApp = true
                        Toast.makeText(
                            this@MainActivity,
                            "Staff Portal auto-locked after 3 minutes of inactivity",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            if (isStaffMode && deliveryVm.profile?.role?.isStaff == true) {
                RestaurantMobileApp(
                    userRole = deliveryVm.profile?.role ?: com.restaurant.mobile.delivery.UserRole.WAITER,
                    onBackToCustomer = {
                        userRequestedCustomerApp = true
                        isStaffMode = false
                    },
                    onSignOut = {
                        userRequestedCustomerApp = false
                        deliveryVm.logout()
                        isStaffMode = false
                        Toast.makeText(this@MainActivity, "Signed out of Staff Portal", Toast.LENGTH_SHORT).show()
                    },
                    onUserInteraction = {
                        lastStaffInteractionMs = System.currentTimeMillis()
                    },
                    onShowToast = { msg ->
                        lastStaffInteractionMs = System.currentTimeMillis()
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    },
                    sqlDb = deliveryVm.sqlDb,
                    backendEngine = deliveryVm.backend,
                    deliveryVm = deliveryVm
                )
            } else {
                com.restaurant.mobile.delivery.DeliveryApp(
                    vm = deliveryVm,
                    onOpenStaffPortal = {
                        userRequestedCustomerApp = false
                        isStaffMode = true
                    }
                )
            }
        }
    }

    override fun onPaymentSuccess(razorpayPaymentID: String?, paymentData: com.razorpay.PaymentData?) {
        com.restaurant.mobile.delivery.RazorpayCheckoutLauncher.handleSuccess(razorpayPaymentID, paymentData)
    }

    override fun onPaymentError(code: Int, response: String?, paymentData: com.razorpay.PaymentData?) {
        com.restaurant.mobile.delivery.RazorpayCheckoutLauncher.handleError(code, response, paymentData)
    }
}

enum class NavigationScreen(val title: String, val icon: ImageVector) {
    ORDERS("Online Orders", Icons.Default.ReceiptLong),
    POS("POS Cashier", Icons.Default.ShoppingCart),
    KDS("Kitchen KDS", Icons.Default.SoupKitchen),
    TABLES("Tables", Icons.Default.TableBar),
    MENU("Menu & Prices", Icons.Default.MenuBook),
    INVENTORY("Inventory", Icons.Default.Inventory),
    SALARY("Staff Payroll", Icons.Default.Payments),
    ANALYTICS("Analytics", Icons.Default.BarChart),
    RIDER("Rider Hub", Icons.Default.DeliveryDining);

    fun getLocalizedTitle(): String {
        return when (this) {
            ORDERS -> com.restaurant.mobile.delivery.RestroI18n.t("admin_orders")
            POS -> com.restaurant.mobile.delivery.RestroI18n.t("pos_cashier")
            KDS -> com.restaurant.mobile.delivery.RestroI18n.t("kitchen_kds")
            TABLES -> com.restaurant.mobile.delivery.RestroI18n.t("tables_floor")
            MENU -> com.restaurant.mobile.delivery.RestroI18n.t("menu_prices")
            INVENTORY -> com.restaurant.mobile.delivery.RestroI18n.t("inventory_stock")
            SALARY -> com.restaurant.mobile.delivery.RestroI18n.t("staff_payroll")
            ANALYTICS -> com.restaurant.mobile.delivery.RestroI18n.t("analytics_tab")
            RIDER -> com.restaurant.mobile.delivery.RestroI18n.t("rider_portal_title")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantMobileApp(
    userRole: com.restaurant.mobile.delivery.UserRole = com.restaurant.mobile.delivery.UserRole.ADMIN,
    onBackToCustomer: (() -> Unit)? = null,
    onSignOut: (() -> Unit)? = null,
    onUserInteraction: (() -> Unit)? = null,
    onShowToast: (String) -> Unit,
    sqlDb: com.restaurant.mobile.delivery.RestroSqlDatabase? = null,
    backendEngine: com.restaurant.mobile.delivery.RestroFirebaseBackendEngine? = null,
    deliveryVm: com.restaurant.mobile.delivery.DeliveryViewModel? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val database = remember(sqlDb) { sqlDb ?: com.restaurant.mobile.delivery.RestroSqlDatabase(context) }
    val backend = remember(backendEngine, database) {
        backendEngine ?: com.restaurant.mobile.delivery.RestroFirebaseBackendEngine(context, database)
    }

    LaunchedEffect(database, deliveryVm) {
        ApiClient.initialize(database, deliveryVm?.cloudEngine)
    }
    var showStaffAiSheet by rememberSaveable { mutableStateOf(false) }

    val allowedScreens = remember(userRole) {
        when (userRole) {
            com.restaurant.mobile.delivery.UserRole.WAITER -> listOf(NavigationScreen.ORDERS, NavigationScreen.POS, NavigationScreen.TABLES)
            com.restaurant.mobile.delivery.UserRole.CHEF_KDS -> listOf(NavigationScreen.KDS, NavigationScreen.ORDERS, NavigationScreen.TABLES)
            com.restaurant.mobile.delivery.UserRole.RIDER -> listOf(NavigationScreen.RIDER, NavigationScreen.ORDERS)
            com.restaurant.mobile.delivery.UserRole.KIOSK -> listOf(NavigationScreen.POS)
            com.restaurant.mobile.delivery.UserRole.ADMIN -> listOf(
                NavigationScreen.ORDERS,
                NavigationScreen.POS,
                NavigationScreen.KDS,
                NavigationScreen.TABLES,
                NavigationScreen.MENU,
                NavigationScreen.INVENTORY,
                NavigationScreen.RIDER,
                NavigationScreen.SALARY,
                NavigationScreen.ANALYTICS
            )
            com.restaurant.mobile.delivery.UserRole.MASTER_ADMIN -> NavigationScreen.entries
            com.restaurant.mobile.delivery.UserRole.CUSTOMER -> emptyList()
        }
    }
    var currentScreen by remember(userRole) {
        mutableStateOf(allowedScreens.firstOrNull() ?: NavigationScreen.ORDERS)
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
    var counterBillBanner by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCounterTabId by rememberSaveable { mutableStateOf<String?>(null) }
    var showKioskExitPinDialog by rememberSaveable { mutableStateOf(false) }
    var kioskExitPinInput by rememberSaveable { mutableStateOf("") }
    var kioskExitPinError by rememberSaveable { mutableStateOf<String?>(null) }

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
            val allDishes = withContext(Dispatchers.IO) {
                database.getAllDishes().ifEmpty { com.restaurant.mobile.delivery.MockCatalog.sample.dishes }
            }
            val uniqueCats = allDishes.map { it.category }.distinct()
            categories = uniqueCats.mapIndexed { idx, catName ->
                Category((idx + 1).toLong(), catName)
            }
            menuItems = allDishes.mapIndexed { idx, dish ->
                val catId = (uniqueCats.indexOf(dish.category) + 1).toLong()
                MenuItem(
                    id = (idx + 1).toLong(),
                    categoryId = catId,
                    name = dish.name,
                    price = (dish.price / 100.0),
                    taxRate = 5.0,
                    isAvailable = true
                )
            }
            val restTables = com.restaurant.mobile.delivery.RestaurantTableDefaults.getTablesForRestaurant("rest-1")
            tables = restTables.mapIndexed { idx, t ->
                DiningTable(
                    id = (idx + 1).toLong(),
                    tableNumber = t.tableNumber,
                    seatingCapacity = t.capacity,
                    status = database.getFloorTableStatus(t.tableNumber)
                )
            }
            analytics = withContext(Dispatchers.IO) {
                try {
                    backend.getAnalyticsData()
                } catch (_: Exception) {
                    AnalyticsData()
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
            val pendingCounterTab = withContext(Dispatchers.IO) {
                database.getAllDiningTabs().firstOrNull {
                    it.status == com.restaurant.mobile.delivery.DiningTabStatus.PAYMENT_PENDING &&
                        it.paymentMethod?.contains("Counter", ignoreCase = true) == true
                }
            }
            if (pendingCounterTab != null) {
                pendingCounterTabId = pendingCounterTab.id
                val guest = pendingCounterTab.customerName?.ifBlank { pendingCounterTab.userName } ?: pendingCounterTab.userName
                counterBillBanner = "Counter Bill Requested: Table ${pendingCounterTab.tableNumber} ($guest) • ₹${pendingCounterTab.total / 100} — Collect at Counter"
            } else if (deliveryVm?.counterBillAlertBanner != null) {
                counterBillBanner = deliveryVm.counterBillAlertBanner
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshAll()
    }

    LaunchedEffect(deliveryVm?.catalog) {
        refreshAll()
    }

    LaunchedEffect(deliveryVm) {
        deliveryVm?.cloudEngine?.observeKotTicketsRealTime("rest-1") { updatedKots ->
            kots = updatedKots
        }
        deliveryVm?.cloudEngine?.observeDiningTabsRealTime("rest-1") {
            refreshAll()
        }
        deliveryVm?.cloudEngine?.observeTableBookingsRealTime(restaurantId = "rest-1", isStaff = true) {
            refreshAll()
        }
        deliveryVm?.cloudEngine?.observeOrdersRealTime(restaurantId = "rest-1", isStaffOrRider = true) { cloudOrders ->
            deliveryVm?.orders = cloudOrders
            refreshAll()
        }
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
                        if (onBackToCustomer != null && userRole != com.restaurant.mobile.delivery.UserRole.KIOSK) {
                            IconButton(onClick = onBackToCustomer) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back to Customer App", tint = Color(0xFF1C1917))
                            }
                        }
                    },
                    title = {
                        val activeLang = com.restaurant.mobile.delivery.RestroI18n.currentLanguage
                        val restName = deliveryVm?.catalog?.restaurants?.find { it.id == "rest-1" }?.localizedName()
                            ?: "Bistro Lumière"
                        Column {
                            Text(
                                text = restName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF1C1917),
                                maxLines = 1,
                                softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${com.restaurant.mobile.delivery.RestroI18n.roleLabel(userRole.name)} • ${currentScreen.getLocalizedTitle()}",
                                fontSize = 11.sp,
                                color = Color(0xFF78716C),
                                maxLines = 1,
                                softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        // Multi-language & Restro.AI available on ALL portals EXCEPT Self-Service Kiosk
                        if (userRole != com.restaurant.mobile.delivery.UserRole.KIOSK) {
                            val currentLang = com.restaurant.mobile.delivery.RestroI18n.currentLanguage
                            Surface(
                                color = Color(0xFFF1F5F9),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf("en" to "EN", "hi" to "हिं", "mr" to "मरा").forEach { (code, shortLabel) ->
                                        val selected = currentLang == code
                                        Surface(
                                            color = if (selected) Color(0xFFC2410C) else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.clickable {
                                                onUserInteraction?.invoke()
                                                if (deliveryVm != null) {
                                                    deliveryVm.selectLanguage(code, isInitialOnboarding = false)
                                                } else {
                                                    com.restaurant.mobile.delivery.RestroI18n.setLanguage(code)
                                                }
                                            }
                                        ) {
                                            Text(
                                                text = shortLabel,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (selected) Color.White else Color(0xFF334155),
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (deliveryVm != null) {
                                Surface(
                                    color = Color(0xFFC2410C),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .padding(end = 2.dp)
                                        .clickable {
                                            onUserInteraction?.invoke()
                                            showStaffAiSheet = true
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = "Restro.AI",
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "AI",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        if (userRole == com.restaurant.mobile.delivery.UserRole.KIOSK) {
                            Surface(
                                color = Color(0xFFF1F5F9),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .clickable {
                                        kioskExitPinInput = ""
                                        kioskExitPinError = null
                                        showKioskExitPinDialog = true
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = "Staff Exit (PIN Protected)",
                                        tint = Color(0xFF475569),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Staff Exit",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF475569),
                                        maxLines = 1
                                    )
                                }
                            }
                        } else if (onSignOut != null) {
                            IconButton(onClick = onSignOut) {
                                Icon(
                                    Icons.Default.Logout,
                                    contentDescription = "Log Out Staff Account",
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
                if (userRole != com.restaurant.mobile.delivery.UserRole.KIOSK) {
                Surface(
                    color = Color.White,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = if (allowedScreens.size <= 4) Arrangement.SpaceEvenly else Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val pendingOrdersCount = (deliveryVm?.orders ?: database.getAllOrders()).count {
                            it.status.equals("Placed", ignoreCase = true) || it.status.equals("Confirmed", ignoreCase = true)
                        }
                        allowedScreens.forEach { screen ->
                            val selected = currentScreen == screen
                            Surface(
                                onClick = {
                                    onUserInteraction?.invoke()
                                    currentScreen = screen
                                    refreshAll()
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) Color(0xFFFFE4E6) else Color.Transparent,
                                modifier = Modifier.widthIn(min = 76.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = screen.icon,
                                            contentDescription = screen.getLocalizedTitle(),
                                            tint = if (selected) Color(0xFFC2410C) else Color(0xFF64748B),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = screen.getLocalizedTitle(),
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) Color(0xFFC2410C) else Color(0xFF64748B),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                    if (screen == NavigationScreen.ORDERS && pendingOrdersCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 4.dp, y = (-2).dp)
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFDC2626)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (pendingOrdersCount > 9) "9+" else "$pendingOrdersCount",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
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
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFFF8FAFC))
            ) {
                // Multi-Language Switcher for All Staff Portals
                if (userRole != com.restaurant.mobile.delivery.UserRole.KIOSK) {
                    Surface(
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(width = 0.5.dp, color = Color(0xFFE2E8F0))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                Triple("en", "English", "EN"),
                                Triple("hi", "हिंदी", "HI"),
                                Triple("mr", "मराठी", "MR")
                            ).forEach { (code, native, shortCode) ->
                                val selected = com.restaurant.mobile.delivery.RestroI18n.currentLanguage == code
                                Surface(
                                    onClick = {
                                        onUserInteraction?.invoke()
                                        if (deliveryVm != null) {
                                            deliveryVm.selectLanguage(code, isInitialOnboarding = false)
                                        } else {
                                            com.restaurant.mobile.delivery.RestroI18n.setLanguage(code)
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) Color(0xFFC2410C) else Color(0xFFF1F5F9),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (selected) Color(0xFFC2410C) else Color.Transparent
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            native,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (selected) Color.White else Color(0xFF334155)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
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

                // Live Counter Bill Request Alert Banner for Waiter / Cashier POS
                if (counterBillBanner != null && userRole != com.restaurant.mobile.delivery.UserRole.KIOSK) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD97706))
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
                                    Icons.Default.ReceiptLong,
                                    contentDescription = null,
                                    tint = Color(0xFFB45309),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = counterBillBanner!!,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF92400E)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (pendingCounterTabId != null) {
                                    TextButton(
                                        onClick = {
                                            val tabId = pendingCounterTabId!!
                                            coroutineScope.launch {
                                                val tab = database.getDiningTab(tabId)
                                                if (tab != null) {
                                                    database.verifyAndCompletePaymentForDiningTab(tabId, tab.paymentTransactionId)
                                                }
                                                counterBillBanner = null
                                                pendingCounterTabId = null
                                                deliveryVm?.counterBillAlertBanner = null
                                                refreshAll()
                                                onShowToast("Counter bill settled & table released!")
                                            }
                                        }
                                    ) {
                                        Text("Settle", fontWeight = FontWeight.ExtraBold, color = Color(0xFFB45309), fontSize = 12.sp)
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        counterBillBanner = null
                                        deliveryVm?.counterBillAlertBanner = null
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = Color(0xFF92400E),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (showKioskExitPinDialog) {
                    AlertDialog(
                        onDismissRequest = { showKioskExitPinDialog = false },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFC2410C))
                                Text(com.restaurant.mobile.delivery.RestroI18n.t("kiosk_pin_required"), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            }
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Enter the 4-digit Staff / Admin PIN to unlock Kiosk Lockdown and access settings or sign out.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF475569)
                                )
                                OutlinedTextField(
                                    value = kioskExitPinInput,
                                    onValueChange = {
                                        kioskExitPinInput = it.filter { ch -> ch.isDigit() }.take(6)
                                        kioskExitPinError = null
                                    },
                                    label = { Text(com.restaurant.mobile.delivery.RestroI18n.t("kiosk_pin_placeholder")) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (kioskExitPinError != null) {
                                    Text(kioskExitPinError!!, color = Color(0xFFDC2626), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val pin = kioskExitPinInput.trim()
                                    val isValid = pin == "0000" || pin == "1234" || pin == "9999" ||
                                        database.verifyStaffPin(pin) != null
                                    if (isValid) {
                                        showKioskExitPinDialog = false
                                        onSignOut?.invoke()
                                    } else {
                                        kioskExitPinError = "Invalid Staff PIN. Try 0000 or 1234."
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC2410C))
                            ) {
                                Text("Sign Out Kiosk", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (onBackToCustomer != null) {
                                    OutlinedButton(
                                        onClick = {
                                            val pin = kioskExitPinInput.trim()
                                            val isValid = pin == "0000" || pin == "1234" || pin == "9999" ||
                                                database.verifyStaffPin(pin) != null
                                            if (isValid) {
                                                showKioskExitPinDialog = false
                                                onBackToCustomer.invoke()
                                            } else {
                                                kioskExitPinError = "Invalid Staff PIN. Try 0000 or 1234."
                                            }
                                        }
                                    ) {
                                        Text(com.restaurant.mobile.delivery.RestroI18n.t("customer_view"))
                                    }
                                }
                                TextButton(onClick = { showKioskExitPinDialog = false }) {
                                    Text(com.restaurant.mobile.delivery.RestroI18n.t("action_cancel"))
                                }
                            }
                        }
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (currentScreen) {
                        NavigationScreen.ORDERS -> com.restaurant.mobile.delivery.AdminOrdersScreenView(
                            database = database,
                            cloudEngine = deliveryVm?.cloudEngine,
                            deliveryVm = deliveryVm,
                            onShowToast = onShowToast
                        )
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
                            database = database,
                            deliveryVm = deliveryVm,
                            onOrderAccepted = { refreshAll() },
                            onShowToast = onShowToast,
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
                                    if (targetKot != null) {
                                        deliveryVm?.cloudEngine?.saveKotToCloud(targetKot.copy(status = status), "rest-1")
                                    }
                                    if (targetKot != null && targetKot.tableNumber.startsWith("DELIVERY-")) {
                                        val orderId = targetKot.tableNumber.removePrefix("DELIVERY-")
                                        if (status.equals("Preparing", ignoreCase = true)) {
                                            database.updateOrderStatus(orderId, "Preparing")
                                            deliveryVm?.cloudEngine?.updateOrderStatusInCloud(orderId, "Preparing")
                                            database.recordAuditLog("ORDER_PREPARING", "Kitchen Chef", "CHEF_KDS", orderId, "Kitchen started preparing order")
                                            deliveryVm?.loadOrders()
                                        } else if (status.equals("Ready", ignoreCase = true)) {
                                            database.updateOrderStatus(orderId, "Ready")
                                            deliveryVm?.cloudEngine?.updateOrderStatusInCloud(orderId, "Ready")
                                            database.recordAuditLog("ORDER_READY", "Kitchen Chef", "CHEF_KDS", orderId, "Order prepared and marked ready for rider pickup")
                                            deliveryVm?.loadOrders()
                                        }
                                    }
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
                                    val restTables = com.restaurant.mobile.delivery.RestaurantTableDefaults.getTablesForRestaurant("rest-1")
            tables = restTables.mapIndexed { idx, t ->
                DiningTable(
                    id = (idx + 1).toLong(),
                    tableNumber = t.tableNumber,
                    seatingCapacity = t.capacity,
                    status = database.getFloorTableStatus(t.tableNumber)
                )
            }
                                    if (selectedTable?.id == table.id) {
                                        selectedTable = selectedTable?.copy(status = nextStatus)
                                    }
                                    onShowToast("Table ${table.tableNumber} is now $nextStatus")
                                }
                            }
                        )
                        NavigationScreen.MENU -> AdminMenuManagementScreenView(
                            database = database,
                            onShowToast = onShowToast,
                            catalog = deliveryVm?.catalog,
                            deliveryVm = deliveryVm
                        )
                        NavigationScreen.INVENTORY -> InventoryAndAuditScreenView(
                            database = database,
                            onShowToast = onShowToast
                        )
                        NavigationScreen.SALARY -> StaffPayrollScreenView(
                            database = database,
                            onShowToast = onShowToast
                        )
                        NavigationScreen.ANALYTICS -> AnalyticsScreenView(
                            analytics = analytics,
                            isMasterAdmin = (userRole == com.restaurant.mobile.delivery.UserRole.MASTER_ADMIN)
                        )
                        NavigationScreen.RIDER -> com.restaurant.mobile.delivery.RiderPortalScreenView(
                            riderId = if (userRole.isStaff && userRole != com.restaurant.mobile.delivery.UserRole.RIDER) "staff-rider-1" else (deliveryVm?.profile?.id ?: "staff-rider-1"),
                            riderName = if (userRole.isStaff && userRole != com.restaurant.mobile.delivery.UserRole.RIDER) "Ramesh Rider (Staff Hub)" else (deliveryVm?.profile?.name ?: "Ramesh Kumar"),
                            database = database,
                            cloudEngine = deliveryVm?.cloudEngine,
                            deliveryVm = deliveryVm,
                            onShowToast = onShowToast
                        )
                    }

                    // Restro.AI Assistant Sheet across all portals EXCEPT Kiosk
                    if (showStaffAiSheet && deliveryVm != null && userRole != com.restaurant.mobile.delivery.UserRole.KIOSK) {
                        com.restaurant.mobile.delivery.RestroAiBottomSheet(
                            vm = deliveryVm,
                            onDismiss = { showStaffAiSheet = false }
                        )
                    }

                    if (deliveryVm?.isVoiceListening == true) {
                        com.restaurant.mobile.delivery.VoiceListeningDialog(
                            language = com.restaurant.mobile.delivery.RestroI18n.currentLanguage,
                            voiceManager = deliveryVm.voiceManager,
                            onDismiss = { deliveryVm.isVoiceListening = false },
                            onSimulateVoiceResult = { spokenText ->
                                if (showStaffAiSheet) {
                                    deliveryVm.isVoiceListening = false
                                    val clean = spokenText.trim()
                                    if (clean.isNotBlank()) {
                                        deliveryVm.aiChatHistory.add(
                                            com.restaurant.mobile.delivery.RestroAiMessage(isUser = true, text = clean)
                                        )
                                        deliveryVm.sendRestroAiMessage(clean) {}
                                    }
                                } else {
                                    deliveryVm.isVoiceListening = false
                                    searchQuery = spokenText.trim()
                                }
                            }
                        )
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

                                        // 3. Consume inventory and record audit event
                                        database.consumeInventoryForOrderIdempotent(
                                            "KOT-${tableNum}-R$roundNumber",
                                            cart.map {
                                                com.restaurant.mobile.delivery.QuoteLine(
                                                    dishId = "dish-${it.menuItem.id}",
                                                    name = it.menuItem.name,
                                                    quantity = it.quantity,
                                                    addons = emptyList(),
                                                    unitPrice = (it.menuItem.price * 100).toLong(),
                                                    total = (it.menuItem.price * it.quantity * 100).toLong()
                                                )
                                            }
                                        )
                                        database.recordAuditLog(
                                            action = "KOT_ROUND_FIRED",
                                            actorEmail = activeBooking?.userName ?: "waiter@restro.com",
                                            actorRole = userRole.name,
                                            targetId = savedTab.id,
                                            details = "Round $roundNumber ($roundName) for Table $tableNum fired to Kitchen KDS (${kotItems.size} dishes)"
                                        )

                                        // 4. Dispatch to kitchen workflow / API
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
    onInitiatePayment: ((com.restaurant.mobile.delivery.DiningTab, Double, Boolean) -> Unit)? = null,
    database: com.restaurant.mobile.delivery.RestroSqlDatabase? = null,
    deliveryVm: com.restaurant.mobile.delivery.DeliveryViewModel? = null,
    onOrderAccepted: (() -> Unit)? = null,
    onShowToast: ((String) -> Unit)? = null
) {
    var showLiveTabDialog by remember { mutableStateOf(false) }

    val filtered = menuItems.filter { item ->
        (selectedCategoryId == null || item.categoryId == selectedCategoryId) &&
                (searchQuery.isEmpty() || item.name.contains(searchQuery, ignoreCase = true))
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Incoming Online Orders Pending Acceptance (authoritative real-time sync)
        val pendingOrders = remember(database, deliveryVm?.orders) {
            (deliveryVm?.orders ?: database?.getAllOrders().orEmpty()).filter { it.status.equals("Placed", ignoreCase = true) }
        }
        if (pendingOrders.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFF59E0B))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, null, tint = Color(0xFFD97706))
                        Text(
                            "Incoming Online Orders (${pendingOrders.size} Pending)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF92400E)
                        )
                    }
                    pendingOrders.forEach { ord ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Order #${ord.id} • ₹${ord.quote.total / 100}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(ord.address.label.ifBlank { "Delivery Address" }, fontSize = 11.sp, color = Color.Gray)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = {
                                            database?.updateOrderStatus(ord.id, "Preparing")
                                            database?.recordAuditLog("ORDER_ACCEPTED", "Admin/POS", "ADMIN", ord.id, "Order accepted & dispatched to KDS")
                                            
                                            // Dispatch / sync KOT ticket for Kitchen KDS
                                            val existingKot = database?.getAllKotTickets()?.find { it.tableNumber.contains(ord.id) }
                                            val finalKot = if (existingKot == null) {
                                                val kotItems = ord.quote.lines.map { line ->
                                                    com.restaurant.mobile.model.KOTItem(
                                                        menuItemName = line.name,
                                                        quantity = line.quantity
                                                    )
                                                }
                                                val orderNumericId = (ord.id.filter { it.isDigit() }.take(8).toLongOrNull() ?: 1001L)
                                                database?.createKotTicket(
                                                    tableNumber = "DELIVERY-${ord.id}",
                                                    tableId = "DELIVERY-${ord.id}",
                                                    items = kotItems,
                                                    round = 1,
                                                    roundName = "Delivery KDS Order",
                                                    customerName = ord.address.label.ifBlank { "Delivery Customer" },
                                                    orderId = orderNumericId
                                                )
                                            } else {
                                                database?.updateKotTicketStatus(existingKot.id, "Preparing")
                                                existingKot.copy(status = "Preparing")
                                            }
                                            if (finalKot != null) {
                                                database?.updateKotTicketStatus(finalKot.id, "Preparing")
                                                deliveryVm?.cloudEngine?.saveKotToCloud(finalKot.copy(status = "Preparing"), ord.restaurantId)
                                            }
                                            
                                            // Real-time Cloud Sync
                                            deliveryVm?.cloudEngine?.updateOrderStatusInCloud(ord.id, "Preparing")
                                            
                                            deliveryVm?.loadOrders()
                                            onOrderAccepted?.invoke()
                                            onShowToast?.invoke("Order #${ord.id} accepted & dispatched to Kitchen KDS!")
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text("Accept", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            database?.updateOrderStatus(ord.id, "Cancelled")
                                            database?.recordAuditLog("ORDER_DECLINED", "Admin/POS", "ADMIN", ord.id, "Order declined by admin")
                                            deliveryVm?.cloudEngine?.updateOrderStatusInCloud(ord.id, "Cancelled")
                                            deliveryVm?.loadOrders()
                                            onOrderAccepted?.invoke()
                                            onShowToast?.invoke("Order #${ord.id} declined")
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text("Decline", fontSize = 12.sp, color = Color(0xFFDC2626))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

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
                                text = com.restaurant.mobile.delivery.RestroI18n.t("pos_no_table"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                text = com.restaurant.mobile.delivery.RestroI18n.t("pos_select_table_hint"),
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
                                com.restaurant.mobile.delivery.RestroI18n.t("pos_live_tab"),
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
                            text = if (selectedTable != null) com.restaurant.mobile.delivery.RestroI18n.t("pos_change_table") else com.restaurant.mobile.delivery.RestroI18n.t("pos_select_table"),
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
            placeholder = { Text(com.restaurant.mobile.delivery.RestroI18n.t("pos_search_placeholder")) },
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
                    label = { Text(com.restaurant.mobile.delivery.RestroI18n.t("filter_all")) }
                )
            }
            items(categories) { cat ->
                FilterChip(
                    selected = selectedCategoryId == cat.id,
                    onClick = { onSelectCategory(cat.id) },
                    label = { Text(com.restaurant.mobile.delivery.RestroMenuTranslationManager.translateCategory(cat.name)) }
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
                                text = item.localizedName(),
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
                    Text(com.restaurant.mobile.delivery.RestroI18n.t("label_subtotal"), fontSize = 13.sp, color = Color(0xFF64748B))
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
                    Text(com.restaurant.mobile.delivery.RestroI18n.t("label_grand_total"), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
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
                        val isDeliveryTable = tableNumber.startsWith("DELIVERY-")
                        val cleanDisplayTitle = if (isDeliveryTable) "Online Delivery Order #${tableNumber.removePrefix("DELIVERY-")}" else "Table $tableNumber"
                        val tableIcon = if (isDeliveryTable) Icons.Default.DeliveryDining else Icons.Default.TableBar
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                tableIcon,
                                contentDescription = null,
                                tint = Color(0xFFC2410C),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = cleanDisplayTitle,
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
                                val isDelivTicket = kot.tableNumber.startsWith("DELIVERY-")
                                val roundTitle = if (isDelivTicket) {
                                    "Delivery Order #${kot.tableNumber.removePrefix("DELIVERY-")} — ${kot.roundName.ifBlank { "Kitchen Prep" }}"
                                } else {
                                    buildString {
                                        append("Table ${kot.tableNumber.ifBlank { tableNumber }}")
                                        append(" — Round ${kot.round}")
                                        if (kot.roundName.isNotBlank()) {
                                            append(" (${kot.roundName})")
                                        }
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
                                "new" -> Triple("Preparing", com.restaurant.mobile.delivery.RestroI18n.t("kds_in_progress"), Color(0xFFD97706))
                                "preparing" -> Triple("Ready", com.restaurant.mobile.delivery.RestroI18n.t("kds_mark_ready"), Color(0xFF16A34A))
                                else -> Triple("Served", com.restaurant.mobile.delivery.RestroI18n.t("kds_served"), Color(0xFF475569))
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
                StatusLegendChip(com.restaurant.mobile.delivery.RestroI18n.t("tables_available"), Color(0xFF10B981))
                StatusLegendChip("Reserved", Color(0xFF8B5CF6))
                StatusLegendChip(com.restaurant.mobile.delivery.RestroI18n.t("tables_occupied"), Color(0xFFF59E0B))
                StatusLegendChip(com.restaurant.mobile.delivery.RestroI18n.t("tables_billed"), Color(0xFFEF4444))
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

// 4. Analytics Screen Component — Unified Dine-In & Delivery Dashboard
@Composable
fun AnalyticsScreenView(analytics: AnalyticsData, isMasterAdmin: Boolean = false) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        if (isMasterAdmin) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Shield, null, tint = Color(0xFFA855F7), modifier = Modifier.size(28.dp))
                    Column {
                        Text("👑 MASTER ADMIN CONSOLE", color = Color(0xFFA855F7), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        Text(com.restaurant.mobile.delivery.RestroI18n.t("master_admin_privileges"), color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                    }
                }
            }
        }

        // ── Section header ───────────────────────────────────────────────────
        Text(
            text = "Admin Analytics Dashboard",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF0F172A)
        )

        // ── 1. Revenue Split Bar Chart ────────────────────────────────────────
        AnalyticsRevenueSplitCard(analytics)

        // ── 2. Key Metrics Grid (2-column) ───────────────────────────────────
        AnalyticsKeyMetricsGrid(analytics)

        // ── 3. Popular Dishes ─────────────────────────────────────────────────
        AnalyticsPopularDishesCard(analytics.topDishes)

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ── Revenue Split Card with custom Compose bar chart ───────────────────────
@Composable
private fun AnalyticsRevenueSplitCard(analytics: AnalyticsData) {
    val dineIn = analytics.dineInRevenue
    val delivery = analytics.deliveryRevenue
    val maxVal = maxOf(dineIn, delivery, 1.0)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Revenue Split",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF374151)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Dine-In vs Delivery (in \u20B9)",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Bar chart drawn with Canvas
            val dineInColor = Color(0xFF10B981)
            val deliveryColor = Color(0xFF6366F1)
            val barHeight = 36f
            val labelGap = 12f

            // Dine-In bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Dine-In",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF374151),
                    modifier = Modifier.width(70.dp)
                )
                BoxWithConstraints(modifier = Modifier.weight(1f).height(36.dp)) {
                    val availableWidthPx = constraints.maxWidth.toFloat()
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val barWidth = (dineIn / maxVal * availableWidthPx).toFloat()
                            .coerceAtLeast(4f)
                        drawRoundRect(
                            color = dineInColor,
                            topLeft = Offset(0f, (size.height - barHeight) / 2f),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "\u20B9${dineIn.toLong()}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = dineInColor,
                    modifier = Modifier.width(80.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Delivery bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Delivery",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF374151),
                    modifier = Modifier.width(70.dp)
                )
                BoxWithConstraints(modifier = Modifier.weight(1f).height(36.dp)) {
                    val availableWidthPx = constraints.maxWidth.toFloat()
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val barWidth = (delivery / maxVal * availableWidthPx).toFloat()
                            .coerceAtLeast(4f)
                        drawRoundRect(
                            color = deliveryColor,
                            topLeft = Offset(0f, (size.height - barHeight) / 2f),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "\u20B9${delivery.toLong()}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = deliveryColor,
                    modifier = Modifier.width(80.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFE5E7EB))
            Spacer(modifier = Modifier.height(12.dp))

            // Gross Revenue total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(com.restaurant.mobile.delivery.RestroI18n.t("label_gross_revenue"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151))
                Text(
                    text = "\u20B9${analytics.totalRevenue.toLong()}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A)
                )
            }
        }
    }
}

// ── Key Metrics 2-column grid ───────────────────────────────────────────────
@Composable
private fun AnalyticsKeyMetricsGrid(analytics: AnalyticsData) {
    val occupancyPct = if (analytics.occupancyRate > 0.0)
        "${(analytics.occupancyRate * 100).toInt()}%"
    else if (analytics.occupiedTables + analytics.availableTables > 0)
        "${(analytics.occupiedTables.toDouble() / (analytics.occupiedTables + analytics.availableTables) * 100).toInt()}%"
    else "0%"

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Key Metrics",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF374151)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                label = "Gross Revenue",
                value = "\u20B9${analytics.totalRevenue.toLong()}",
                color = Color(0xFF10B981),
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "Avg Dine-In Check",
                value = "\u20B9${analytics.averageDineInBill.toLong()}",
                color = Color(0xFF6366F1),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                label = "Table Occupancy",
                value = occupancyPct,
                color = Color(0xFFF59E0B),
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "Diners Served",
                value = "${analytics.totalDinersServed}",
                color = Color(0xFF0EA5E9),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = modifier,
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = label, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}

// ── Popular Dishes Card ─────────────────────────────────────────────────────
@Composable
private fun AnalyticsPopularDishesCard(dishes: List<TopDishStat>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Top 5 Popular Dishes",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF374151)
            )
            Text(
                text = "Dine-in + Delivery combined",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(14.dp))

            if (dishes.isEmpty()) {
                Text(
                    text = "No sales data yet",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    fontStyle = FontStyle.Italic
                )
            } else {
                dishes.take(5).forEachIndexed { index, dish ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = Color(0xFFF3F4F6),
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rank badge
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    when (index) {
                                        0 -> Color(0xFFFFD700)
                                        1 -> Color(0xFFC0C0C0)
                                        2 -> Color(0xFFCD7F32)
                                        else -> Color(0xFFE5E7EB)
                                    }
                                )
                        ) {
                            Text(
                                text = "${index + 1}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (index < 3) Color.White else Color(0xFF374151)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = dish.name.ifBlank { "Unknown dish" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Dine-in: ${dish.dineInQuantity}  \u2022  Delivery: ${dish.deliveryQuantity}",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${dish.totalQuantity} sold",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }
        }
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
                Text(com.restaurant.mobile.delivery.RestroI18n.t("select_table_order"), fontWeight = FontWeight.Bold, fontSize = 18.sp)
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

@Composable
fun StaffPayrollScreenView(
    database: com.restaurant.mobile.delivery.RestroSqlDatabase,
    onShowToast: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var payrollList by remember { mutableStateOf<List<com.restaurant.mobile.delivery.StaffPayrollRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var showAddStaffDialog by remember { mutableStateOf(false) }
    var newStaffName by remember { mutableStateOf("") }
    var newStaffLogin by remember { mutableStateOf("") }
    var newStaffPassword by remember { mutableStateOf("") }
    var newStaffRole by remember { mutableStateOf(com.restaurant.mobile.delivery.UserRole.WAITER) }
    var newStaffSalary by remember { mutableStateOf("25000") }

    fun refreshPayroll() {
        coroutineScope.launch(Dispatchers.IO) {
            val list = database.getAllPayrollRecords()
            withContext(Dispatchers.Main) {
                payrollList = list
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshPayroll()
    }

    val totalPaid = payrollList.filter { it.status == "PAID" }.sumOf { it.netSalary }
    val totalPending = payrollList.filter { it.status != "PAID" }.sumOf { it.netSalary }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Staff Compensation & Payroll", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(com.restaurant.mobile.delivery.RestroI18n.t("status_disbursed"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("₹$totalPaid", fontWeight = FontWeight.Bold, color = Color(0xFF10B981), fontSize = 16.sp)
                    }
                    Column {
                        Text(com.restaurant.mobile.delivery.RestroI18n.t("status_pending_payouts"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("₹$totalPending", fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B), fontSize = 16.sp)
                    }
                    Column {
                        Text(com.restaurant.mobile.delivery.RestroI18n.t("label_total_staff"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${payrollList.size} Staff", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }

        // Action: Add Staff Member
        Button(
            onClick = { showAddStaffDialog = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(com.restaurant.mobile.delivery.RestroI18n.t("add_staff_subtitle"), fontWeight = FontWeight.Bold)
        }

        // Employee Payroll List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(payrollList, key = { it.id }) { record ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(record.staffName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(record.staffRole, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Surface(
                                color = if (record.status == "PAID") Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFF59E0B).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    record.status,
                                    color = if (record.status == "PAID") Color(0xFF10B981) else Color(0xFFF59E0B),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Divider(color = Color.White.copy(alpha = 0.08f))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Base: ₹${record.baseSalary}", fontSize = 12.sp)
                            Text("Bonus: +₹${record.bonusTips}", fontSize = 12.sp, color = Color(0xFF10B981))
                            Text("Deduct: -₹${record.deductions}", fontSize = 12.sp, color = Color(0xFFEF4444))
                            Text("Net: ₹${record.netSalary}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        if (record.status != "PAID") {
                            Button(
                                onClick = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val tx = "SAL-UPI-" + java.util.UUID.randomUUID().toString().take(8).uppercase()
                                        database.disburseSalary(record.id, "UPI Instant Pay", tx)
                                        withContext(Dispatchers.Main) {
                                            refreshPayroll()
                                            onShowToast("Salary of ₹${record.netSalary} disbursed to ${record.staffName} via UPI!")
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Payment, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Disburse ₹${record.netSalary} via UPI / Bank")
                            }
                        } else {
                            Text(
                                "Disbursed via ${record.paymentMethod} · Ref: ${record.transactionRef.ifBlank { "PAID_CONFIRMED" }}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddStaffDialog) {
        AlertDialog(
            onDismissRequest = { showAddStaffDialog = false },
            title = { Text(com.restaurant.mobile.delivery.RestroI18n.t("add_staff_member"), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Create staff credentials. The staff member will log in directly using these credentials with NO PIN required.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newStaffName,
                        onValueChange = { newStaffName = it },
                        label = { Text("Staff Full Name") },
                        placeholder = { Text("e.g. Ramesh Kumar") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newStaffLogin,
                        onValueChange = { newStaffLogin = it },
                        label = { Text("Login Email or Mobile") },
                        placeholder = { Text("e.g. ramesh@restro.app") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newStaffPassword,
                        onValueChange = { newStaffPassword = it },
                        label = { Text("Login Password (min 6 chars)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Select Role:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val roles = listOf(
                            com.restaurant.mobile.delivery.UserRole.WAITER to "Waiter",
                            com.restaurant.mobile.delivery.UserRole.CHEF_KDS to "Kitchen",
                            com.restaurant.mobile.delivery.UserRole.ADMIN to "Manager"
                        )
                        roles.forEach { (role, label) ->
                            FilterChip(
                                selected = newStaffRole == role,
                                onClick = { newStaffRole = role },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = newStaffSalary,
                        onValueChange = { newStaffSalary = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Base Monthly Salary (₹)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanName = newStaffName.trim()
                        val cleanLogin = newStaffLogin.trim()
                        val cleanPass = newStaffPassword.trim()
                        if (cleanName.length < 2) {
                            onShowToast("Enter a valid staff name")
                            return@Button
                        }
                        if (cleanLogin.length < 5) {
                            onShowToast("Enter a valid email or phone")
                            return@Button
                        }
                        if (cleanPass.length < 6) {
                            onShowToast("Password must be at least 6 characters")
                            return@Button
                        }
                        val salaryVal = newStaffSalary.toLongOrNull() ?: 25000L
                        val staffId = "staff-" + java.util.UUID.randomUUID().toString().take(8)
                        val isEmail = cleanLogin.contains("@")
                        val profile = com.restaurant.mobile.delivery.Profile(
                            id = staffId,
                            name = cleanName,
                            email = if (isEmail) cleanLogin else "",
                            phone = if (!isEmail) cleanLogin else "",
                            role = newStaffRole
                        )
                        coroutineScope.launch(Dispatchers.IO) {
                            database.saveUser(profile)
                            database.saveUserPassword(cleanLogin, cleanPass)
                            val payrollRec = com.restaurant.mobile.delivery.StaffPayrollRecord(
                                id = "pay-" + java.util.UUID.randomUUID().toString().take(8),
                                staffId = staffId,
                                staffName = cleanName,
                                staffRole = newStaffRole.label,
                                monthYear = "October 2026",
                                baseSalary = salaryVal,
                                bonusTips = 0L,
                                deductions = 0L,
                                netSalary = salaryVal,
                                status = "PENDING"
                            )
                            database.insertStaffPayrollRecord(payrollRec)
                            withContext(Dispatchers.Main) {
                                showAddStaffDialog = false
                                newStaffName = ""
                                newStaffLogin = ""
                                newStaffPassword = ""
                                refreshPayroll()
                                onShowToast("Staff account created for ${cleanName} as ${newStaffRole.label}!")
                            }
                        }
                    },
                    enabled = newStaffName.isNotBlank() && newStaffLogin.isNotBlank() && newStaffPassword.length >= 6
                ) {
                    Text("Create Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddStaffDialog = false }) {
                    Text(com.restaurant.mobile.delivery.RestroI18n.t("action_cancel"))
                }
            }
        )
    }
}

@Composable
fun AdminMenuManagementScreenView(
    database: com.restaurant.mobile.delivery.RestroSqlDatabase,
    onShowToast: (String) -> Unit,
    catalog: com.restaurant.mobile.delivery.Catalog? = null,
    deliveryVm: com.restaurant.mobile.delivery.DeliveryViewModel? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var restaurants by remember { mutableStateOf<List<com.restaurant.mobile.delivery.Restaurant>>(emptyList()) }
    var selectedRestaurantId by remember { mutableStateOf("rest-1") }
    var dishes by remember { mutableStateOf<List<com.restaurant.mobile.delivery.Dish>>(emptyList()) }

    var editingDish by remember { mutableStateOf<com.restaurant.mobile.delivery.Dish?>(null) }
    var showDishDialog by remember { mutableStateOf(false) }

    fun refreshMenu() {
        coroutineScope.launch(Dispatchers.IO) {
            val restList = database.getAllRestaurants().ifEmpty { com.restaurant.mobile.delivery.MockCatalog.sample.restaurants }
            val dishList = database.getAllDishes().ifEmpty { com.restaurant.mobile.delivery.MockCatalog.sample.dishes }
            withContext(Dispatchers.Main) {
                restaurants = restList
                if (restList.isNotEmpty() && restList.none { it.id == selectedRestaurantId }) {
                    selectedRestaurantId = restList.first().id
                }
                dishes = dishList
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshMenu()
    }

    LaunchedEffect(catalog) {
        if (catalog != null && (catalog.restaurants.isNotEmpty() || catalog.dishes.isNotEmpty())) {
            restaurants = catalog.restaurants
            if (catalog.restaurants.isNotEmpty() && catalog.restaurants.none { it.id == selectedRestaurantId }) {
                selectedRestaurantId = catalog.restaurants.first().id
            }
            dishes = catalog.dishes
        } else {
            refreshMenu()
        }
    }

    val selectedRest = restaurants.find { it.id == selectedRestaurantId }
    val filteredDishes = dishes.filter { it.restaurantId == selectedRestaurantId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(restaurants, key = { it.id }) { rest ->
                FilterChip(
                    selected = rest.id == selectedRestaurantId,
                    onClick = { selectedRestaurantId = rest.id },
                    label = { Text("${rest.name} (${rest.id})") }
                )
            }
        }

        selectedRest?.let { rest ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${rest.name} Operations", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                "Prep: ${rest.prepMinutes}m · Delivery: ${if (rest.open) "OPEN" else "CLOSED"} · Dining Pay: ${if (rest.acceptsDiningPayment) "ONLINE" else "COUNTER ONLY"}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                editingDish = null
                                showDishDialog = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(com.restaurant.mobile.delivery.RestroI18n.t("admin_add_dish"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = rest.open,
                            onClick = {
                                val nextOpen = !rest.open
                                coroutineScope.launch(Dispatchers.IO) {
                                    database.saveRestaurantSettings(rest.id, nextOpen, rest.prepMinutes, rest.acceptsDiningPayment)
                                    refreshMenu()
                                    withContext(Dispatchers.Main) {
                                        onShowToast("${rest.name} is now ${if (nextOpen) "OPEN for delivery" else "CLOSED for delivery"}")
                                    }
                                }
                            },
                            label = { Text(if (rest.open) "Open for Delivery" else "Closed for Delivery") }
                        )
                        FilterChip(
                            selected = rest.acceptsDiningPayment,
                            onClick = {
                                val nextDining = !rest.acceptsDiningPayment
                                coroutineScope.launch(Dispatchers.IO) {
                                    database.saveRestaurantSettings(rest.id, rest.open, rest.prepMinutes, nextDining)
                                    refreshMenu()
                                    withContext(Dispatchers.Main) {
                                        onShowToast("${rest.name}: ${if (nextDining) "Online Dining Pay Enabled" else "Counter Settlement Only"}")
                                    }
                                }
                            },
                            label = { Text(if (rest.acceptsDiningPayment) "Online Dining Pay: ON" else "Counter Pay Only") }
                        )
                    }

                    var isUploadingAdminLogo by remember { mutableStateOf(false) }
                    var adminLogoUploadProgress by remember { mutableStateOf(0f) }

                    val adminLogoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                        if (uri != null) {
                            coroutineScope.launch {
                                isUploadingAdminLogo = true
                                adminLogoUploadProgress = 0f
                                val res = deliveryVm?.uploadRestaurantLogo(rest.id, uri) { prog ->
                                    adminLogoUploadProgress = prog
                                } ?: com.restaurant.mobile.delivery.FirebaseStorageEngine(context).uploadRestaurantLogo(rest.id, uri) { prog ->
                                    adminLogoUploadProgress = prog
                                }
                                isUploadingAdminLogo = false
                                res.fold(
                                    onSuccess = { downloadUrl ->
                                        database.updateRestaurantLogo(rest.id, downloadUrl)
                                        refreshMenu()
                                        onShowToast("Restaurant logo uploaded and saved!")
                                    },
                                    onFailure = { err ->
                                        onShowToast("Logo upload failed: ${err.localizedMessage ?: "Unknown error"}")
                                    }
                                )
                            }
                        }
                    }

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
                            val activeLogo = (rest.logoUrl ?: "").ifBlank { rest.image }
                            com.restaurant.mobile.delivery.Photo(
                                url = activeLogo,
                                description = rest.name,
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                            )
                            Column {
                                Text(
                                    "Brand Logo",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    if (isUploadingAdminLogo) "Uploading: ${(adminLogoUploadProgress * 100).toInt()}%"
                                    else activeLogo.take(28) + "...",
                                    fontSize = 11.sp,
                                    color = if (isUploadingAdminLogo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                try {
                                    adminLogoPicker.launch("image/*")
                                } catch (_: Exception) {
                                    onShowToast("Unable to open image picker")
                                }
                            },
                            enabled = !isUploadingAdminLogo,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Image, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isUploadingAdminLogo) "Uploading..." else com.restaurant.mobile.delivery.RestroI18n.t("upload_logo_btn"), fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredDishes, key = { it.id }) { dish ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            com.restaurant.mobile.delivery.Photo(
                                url = dish.image,
                                description = dish.name,
                                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp))
                            )
                            Column {
                                Text(dish.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(
                                    "₹${dish.price / 100} · ${dish.category} · Mapped to ${dish.restaurantId}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                color = if (dish.available) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    if (dish.available) "AVAILABLE" else "86'D (OUT)",
                                    color = if (dish.available) Color(0xFF10B981) else Color(0xFFEF4444),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            IconButton(onClick = {
                                editingDish = dish
                                showDishDialog = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Dish", tint = Color(0xFFC2410C))
                            }

                            IconButton(onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    database.deleteDish(dish.id)
                                    database.recordAuditLog("DISH_DELETED", "admin@restro.app", "ADMIN", dish.id, "Deleted ${dish.name}")
                                    refreshMenu()
                                    withContext(Dispatchers.Main) {
                                        onShowToast("Deleted ${dish.name}")
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Dish", tint = Color(0xFFEF4444))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDishDialog) {
        val target = editingDish
        var dName by remember(target) { mutableStateOf(target?.name ?: "") }
        var dCategory by remember(target) { mutableStateOf(target?.category ?: "Mains") }
        var dDesc by remember(target) { mutableStateOf(target?.description ?: "Freshly prepared house specialty") }
        var editPriceStr by remember(target) { mutableStateOf(if (target != null) (target.price / 100).toString() else "249") }
        var editVeg by remember(target) { mutableStateOf(target?.vegetarian ?: true) }
        var editAvailable by remember(target) { mutableStateOf(target?.available ?: true) }
        var editDishImage by remember(target) {
            mutableStateOf(target?.image ?: (selectedRest?.image ?: "https://images.unsplash.com/photo-1563379091339-03b21ab4a4f8?w=800"))
        }

        var isUploadingAdminDishImage by remember { mutableStateOf(false) }
        var adminDishImageProgress by remember { mutableStateOf(0f) }
        var adminDishImageError by remember { mutableStateOf<String?>(null) }

        val adminDishImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    isUploadingAdminDishImage = true
                    adminDishImageProgress = 0f
                    adminDishImageError = null
                    val dishId = target?.id ?: "dish-${System.currentTimeMillis()}"
                    val restId = selectedRestaurantId
                    val res = deliveryVm?.uploadDishImage(restId, dishId, uri) { prog ->
                        adminDishImageProgress = prog
                    } ?: com.restaurant.mobile.delivery.FirebaseStorageEngine(context).uploadDishImage(restId, dishId, uri) { prog ->
                        adminDishImageProgress = prog
                    }
                    isUploadingAdminDishImage = false
                    res.fold(
                        onSuccess = { downloadUrl ->
                            editDishImage = downloadUrl
                            onShowToast("Dish image uploaded successfully!")
                        },
                        onFailure = { err ->
                            adminDishImageError = err.localizedMessage ?: "Failed to upload image"
                            onShowToast("Dish image upload failed: ${err.localizedMessage}")
                        }
                    )
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showDishDialog = false },
            title = { Text(if (target == null) "Add Dish to ${selectedRest?.name ?: selectedRestaurantId}" else "Update ${target.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = dName,
                        onValueChange = { dName = it },
                        label = { Text("Dish Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dCategory,
                        onValueChange = { dCategory = it },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editPriceStr,
                        onValueChange = { editPriceStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Price in ₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dDesc,
                        onValueChange = { dDesc = it },
                        label = { Text("Description") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editDishImage,
                        onValueChange = { editDishImage = it },
                        label = { Text("Custom Dish Image URL / URI") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        com.restaurant.mobile.delivery.Photo(
                            url = editDishImage,
                            description = dName,
                            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            if (isUploadingAdminDishImage) {
                                LinearProgressIndicator(
                                    progress = { adminDishImageProgress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text("Uploading: ${(adminDishImageProgress * 100).toInt()}%", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                            } else if (adminDishImageError != null) {
                                Text(adminDishImageError!!, fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                            } else {
                                Text(
                                    if (editDishImage.startsWith("http")) "Cloud image linked" else "Standard catalog image",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            try {
                                adminDishImagePicker.launch("image/*")
                            } catch (_: Exception) {
                                onShowToast("Unable to open image picker")
                            }
                        },
                        enabled = !isUploadingAdminDishImage,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isUploadingAdminDishImage) "Uploading..." else com.restaurant.mobile.delivery.RestroI18n.t("upload_dish_image_btn"), fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = editVeg, onCheckedChange = { editVeg = it })
                            Text("Veg")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = editAvailable, onCheckedChange = { editAvailable = it })
                            Text("Available")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newPricePaise = (editPriceStr.toLongOrNull() ?: 199L) * 100L
                    val dishObj = com.restaurant.mobile.delivery.Dish(
                        id = target?.id ?: "dish-${System.currentTimeMillis()}",
                        restaurantId = target?.restaurantId ?: selectedRestaurantId,
                        name = dName.ifBlank { "Special Dish" },
                        category = dCategory.ifBlank { "Mains" },
                        description = dDesc.ifBlank { "Chef special" },
                        price = newPricePaise,
                        vegetarian = editVeg,
                        available = editAvailable,
                        image = editDishImage.ifBlank { selectedRest?.image ?: "https://images.unsplash.com/photo-1563379091339-03b21ab4a4f8?w=800" },
                        addons = target?.addons ?: emptyList()
                    )
                    coroutineScope.launch(Dispatchers.IO) {
                        database.upsertDish(dishObj)
                        database.recordAuditLog(
                            if (target == null) "DISH_ADDED" else "MENU_UPDATED",
                            "admin@restro.app",
                            "ADMIN",
                            dishObj.id,
                            "${dishObj.name} (₹${newPricePaise / 100}, available=$editAvailable)"
                        )
                        withContext(Dispatchers.Main) {
                            showDishDialog = false
                            editingDish = null
                            refreshMenu()
                            onShowToast("${dishObj.name} saved (₹${newPricePaise / 100})")
                        }
                    }
                }) {
                    Text("Save Dish")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDishDialog = false }) {
                    Text(com.restaurant.mobile.delivery.RestroI18n.t("action_cancel"))
                }
            }
        )
    }
}

@Composable
fun InventoryAndAuditScreenView(
    database: com.restaurant.mobile.delivery.RestroSqlDatabase,
    onShowToast: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var inventoryList by remember { mutableStateOf<List<com.restaurant.mobile.delivery.InventoryItem>>(emptyList()) }
    var auditLogs by remember { mutableStateOf<List<com.restaurant.mobile.delivery.AuditLogEntry>>(emptyList()) }

    fun refresh() {
        coroutineScope.launch(Dispatchers.IO) {
            val inv = database.getAllInventoryItems()
            val logs = database.getAllAuditLogs(50)
            withContext(Dispatchers.Main) {
                inventoryList = inv
                auditLogs = logs
            }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.White
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Inventory, null, modifier = Modifier.size(18.dp))
                    Text("Stock Levels")
                }
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.History, null, modifier = Modifier.size(18.dp))
                    Text("Audit Logs")
                }
            }
        }

        if (selectedTab == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(inventoryList, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("Category: ${item.category}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Surface(
                                    color = if (item.isLowStock) Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFF10B981).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        if (item.isLowStock) "LOW STOCK" else "IN STOCK",
                                        color = if (item.isLowStock) Color(0xFFEF4444) else Color(0xFF10B981),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Stock: ${item.currentStock} ${item.unit} (Reorder: ${item.reorderLevel} ${item.unit})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Button(
                                    onClick = {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            database.recordStockMovement(item.id, 10.0, "RESTOCK", actor = "Admin Manager")
                                            withContext(Dispatchers.Main) {
                                                refresh()
                                                onShowToast("Restocked +10 ${item.unit} for ${item.name}")
                                            }
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("+10 ${item.unit}", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(auditLogs, key = { it.id }) { log ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(log.action, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                Text(log.createdAt.take(19).replace("T", " "), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(log.details, fontSize = 12.sp)
                            Text("Actor: ${log.actorEmail} (${log.actorRole})", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
