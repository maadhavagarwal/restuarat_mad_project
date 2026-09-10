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
        setContent {
            RestaurantMobileApp(
                onShowToast = { msg ->
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

enum class NavigationScreen(val title: String, val icon: ImageVector) {
    POS("POS Cashier", Icons.Default.ShoppingCart),
    KDS("Kitchen KDS", Icons.Default.SoupKitchen),
    TABLES("Tables", Icons.Default.TableBar),
    CUSTOMER("Customers", Icons.Default.Person),
    ANALYTICS("Analytics", Icons.Default.BarChart)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantMobileApp(onShowToast: (String) -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(NavigationScreen.POS) }

    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var menuItems by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    var tables by remember { mutableStateOf<List<DiningTable>>(emptyList()) }
    var kots by remember { mutableStateOf<List<KOT>>(emptyList()) }
    var analytics by remember { mutableStateOf(AnalyticsData()) }

    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var cart by remember { mutableStateOf<List<CartItem>>(emptyList()) }
    var isCartOpen by remember { mutableStateOf(false) }

    fun refreshAll() {
        coroutineScope.launch {
            categories = ApiClient.getCategories()
            menuItems = ApiClient.getMenuItems()
            tables = ApiClient.getTables()
            kots = ApiClient.getKOTs()
            analytics = ApiClient.getAnalytics()
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
                    title = {
                        Column {
                            Text(
                                text = "Bistro Lumière",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color(0xFF1C1917)
                            )
                            Text(
                                text = "Artisanal Mobile OS • ${currentScreen.title}",
                                fontSize = 12.sp,
                                color = Color(0xFF78716C)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { isCartOpen = !isCartOpen }) {
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
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    NavigationScreen.values().forEach { screen ->
                        NavigationBarItem(
                            selected = currentScreen == screen,
                            onClick = {
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
                        onAddToCart = { item ->
                            val existing = cart.find { it.menuItem.id == item.id }
                            cart = if (existing != null) {
                                cart.map {
                                    if (it.menuItem.id == item.id) it.copy(quantity = it.quantity + 1)
                                    else it
                                }
                            } else {
                                cart + CartItem(item, 1)
                            }
                            onShowToast("${item.name} added to cart")
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
                                onShowToast("Table ${table.tableNumber} is now $nextStatus")
                            }
                        }
                    )
                    NavigationScreen.CUSTOMER -> CustomerScreenView()
                    NavigationScreen.ANALYTICS -> AnalyticsScreenView(analytics = analytics)
                }

                // Slide Cart Bottom Sheet
                if (isCartOpen) {
                    CartBottomSheetView(
                        cart = cart,
                        onUpdateQty = { itemId, delta ->
                            cart = cart.mapNotNull { item ->
                                if (item.menuItem.id == itemId) {
                                    val newQ = item.quantity + delta
                                    if (newQ > 0) item.copy(quantity = newQ) else null
                                } else item
                            }
                        },
                        onCheckout = { paymentMethod ->
                            coroutineScope.launch {
                                val success = ApiClient.checkoutOrder(paymentMethod, cart)
                                if (success) {
                                    onShowToast("✅ Order Paid via $paymentMethod! KOT sent to Kitchen.")
                                    cart = emptyList()
                                    isCartOpen = false
                                    refreshAll()
                                } else {
                                    onShowToast("Checkout failed")
                                }
                            }
                        },
                        onClose = { isCartOpen = false }
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
    onAddToCart: (MenuItem) -> Unit
) {
    val filtered = menuItems.filter { item ->
        (selectedCategoryId == null || item.categoryId == selectedCategoryId) &&
                (searchQuery.isEmpty() || item.name.contains(searchQuery, ignoreCase = true))
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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
    onCycleTableStatus: (DiningTable) -> Unit
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
                    .height(130.dp)
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
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = table.status,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier
                            .background(color.copy(alpha = 0.15f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

// 4. Customer Screen Component
@Composable
fun CustomerScreenView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF6366F1)),
            modifier = Modifier.fillMaxWidth().height(160.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Customer Loyalty Pass",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(text = "Rahul Sharma", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text(text = "+91 98765 43210", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                    Text(text = "35 Pts", color = Color.Yellow, fontWeight = FontWeight.Black, fontSize = 22.sp)
                }
            }
        }
    }
}

// 5. Analytics Screen Component
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
    onUpdateQty: (Long, Int) -> Unit,
    onCheckout: (String) -> Unit,
    onClose: () -> Unit
) {
    val subtotal = cart.sumOf { it.menuItem.price * it.quantity }
    val tax = subtotal * 0.05
    val grandTotal = subtotal + tax

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
                .fillMaxHeight(0.7f)
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
                    Text(text = "Current Order Cart", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

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

                Divider()

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total (inc. GST 5%):", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("₹${grandTotal.toInt()}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color(0xFF6366F1))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onCheckout("Cash") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("Cash")
                    }
                    Button(
                        onClick = { onCheckout("Card") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("Card")
                    }
                    Button(
                        onClick = { onCheckout("UPI") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                    ) {
                        Text("UPI")
                    }
                }
            }
        }
    }
}
