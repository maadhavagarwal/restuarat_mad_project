package com.restaurant.restaurant_os.repository

import com.restaurant.restaurant_os.model.*
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Repository
class RestaurantRepository {

    private val customerIdGen = AtomicLong(0)
    private val categoryIdGen = AtomicLong(0)
    private val menuItemIdGen = AtomicLong(0)
    private val tableIdGen = AtomicLong(0)
    private val orderIdGen = AtomicLong(0)
    private val orderItemIdGen = AtomicLong(0)
    private val kotIdGen = AtomicLong(0)

    val customers = ConcurrentHashMap<Long, Customer>()
    val categories = ConcurrentHashMap<Long, Category>()
    val menuItems = ConcurrentHashMap<Long, MenuItem>()
    val diningTables = ConcurrentHashMap<Long, DiningTable>()
    val orders = ConcurrentHashMap<Long, Order>()
    val orderItems = ConcurrentHashMap<Long, OrderItem>()
    val kots = ConcurrentHashMap<Long, KOT>()

    // Categories
    fun addCategory(name: String): Category {
        val id = categoryIdGen.incrementAndGet()
        val cat = Category(id = id, name = name)
        categories[id] = cat
        return cat
    }

    fun getAllCategories(): List<Category> = categories.values.toList().sortedBy { it.id }

    // Menu Items
    fun addMenuItem(categoryId: Long, name: String, price: Double, taxRate: Double = 5.0): MenuItem {
        val id = menuItemIdGen.incrementAndGet()
        val item = MenuItem(id = id, categoryId = categoryId, name = name, price = price, taxRate = taxRate)
        menuItems[id] = item
        return item
    }

    fun getMenuItems(categoryId: Long?): List<MenuItem> {
        val list = menuItems.values.toList()
        return if (categoryId == null || categoryId == 0L) {
            list
        } else {
            list.filter { it.categoryId == categoryId }
        }
    }

    fun getMenuItemById(id: Long): MenuItem? = menuItems[id]

    // Dining Tables
    fun addDiningTable(tableNumber: String, capacity: Int, status: String = "Available"): DiningTable {
        val id = tableIdGen.incrementAndGet()
        val table = DiningTable(id = id, tableNumber = tableNumber, seatingCapacity = capacity, status = status)
        diningTables[id] = table
        return table
    }

    fun getAllDiningTables(): List<DiningTable> = diningTables.values.toList().sortedBy { it.id }

    fun updateTableStatus(id: Long, status: String): DiningTable? {
        val table = diningTables[id] ?: return null
        table.status = status
        table.updatedAt = Instant.now().toString()
        return table
    }

    // Customers
    fun addCustomer(name: String, phone: String): Customer {
        val existing = customers.values.firstOrNull { it.phone == phone }
        if (existing != null) return existing
        
        val id = customerIdGen.incrementAndGet()
        val customer = Customer(id = id, phone = phone, name = name, loyaltyPoints = 0)
        customers[id] = customer
        return customer
    }

    fun findCustomerByPhone(phone: String): Customer? {
        return customers.values.firstOrNull { it.phone == phone }
    }

    fun getCustomerById(id: Long): Customer? = customers[id]

    fun updateCustomerPoints(id: Long, additionalPoints: Int): Customer? {
        val cust = customers[id] ?: return null
        cust.loyaltyPoints += additionalPoints
        cust.updatedAt = Instant.now().toString()
        return cust
    }

    // Orders & Checkout
    fun createOrder(
        orderType: String,
        tableId: Long?,
        customerId: Long?,
        paymentMethod: String,
        items: List<CartItemRequest>
    ): Order {
        val orderId = orderIdGen.incrementAndGet()

        var calculatedTotal = 0.0
        val createdOrderItems = mutableListOf<OrderItem>()

        items.forEach { cartItem ->
            val menuItem = menuItems[cartItem.menuItemId]
            if (menuItem != null) {
                val itemTotal = menuItem.price * cartItem.quantity
                calculatedTotal += itemTotal

                val orderItemId = orderItemIdGen.incrementAndGet()
                val oi = OrderItem(
                    id = orderItemId,
                    orderId = orderId,
                    menuItemId = menuItem.id!!,
                    quantity = cartItem.quantity,
                    priceAtTimeOfOrder = menuItem.price
                )
                orderItems[orderItemId] = oi
                createdOrderItems.add(oi)
            }
        }

        val taxAmount = calculatedTotal * 0.05
        val grandTotal = calculatedTotal + taxAmount

        val order = Order(
            id = orderId,
            tableId = tableId,
            customerId = customerId,
            orderType = orderType,
            totalAmount = grandTotal,
            status = "Paid",
            paymentMethod = paymentMethod
        )
        orders[orderId] = order

        // Create KOT
        val kotId = kotIdGen.incrementAndGet()
        val kot = KOT(id = kotId, orderId = orderId, status = "New")
        kots[kotId] = kot

        // Update customer points if linked
        if (customerId != null) {
            val points = (grandTotal / 100).toInt()
            updateCustomerPoints(customerId, points)
        }

        // If table linked, update table status to Occupied
        if (tableId != null) {
            updateTableStatus(tableId, "Occupied")
        }

        return order
    }

    fun getAllOrders(): List<Order> = orders.values.toList().sortedByDescending { it.id }

    // KOT operations
    fun getAllKOTs(): List<KOTResponse> {
        return kots.values.map { kot ->
            val itemsForOrder = orderItems.values.filter { it.orderId == kot.orderId }.map { oi ->
                val menuItem = menuItems[oi.menuItemId]
                KOTItemResponse(
                    id = oi.id,
                    orderId = oi.orderId,
                    menuItemId = oi.menuItemId,
                    menuItemName = menuItem?.name ?: "Unknown Item",
                    quantity = oi.quantity,
                    priceAtTimeOfOrder = oi.priceAtTimeOfOrder
                )
            }
            KOTResponse(
                id = kot.id,
                orderId = kot.orderId,
                status = kot.status,
                printedStatus = kot.printedStatus,
                updatedAt = kot.updatedAt,
                items = itemsForOrder
            )
        }.sortedBy { it.id }
    }

    fun updateKOTStatus(id: Long, newStatus: String): KOTResponse? {
        val kot = kots[id] ?: return null
        kot.status = newStatus
        kot.updatedAt = Instant.now().toString()

        val itemsForOrder = orderItems.values.filter { it.orderId == kot.orderId }.map { oi ->
            val menuItem = menuItems[oi.menuItemId]
            KOTItemResponse(
                id = oi.id,
                orderId = oi.orderId,
                menuItemId = oi.menuItemId,
                menuItemName = menuItem?.name ?: "Unknown Item",
                quantity = oi.quantity,
                priceAtTimeOfOrder = oi.priceAtTimeOfOrder
            )
        }
        return KOTResponse(
            id = kot.id,
            orderId = kot.orderId,
            status = kot.status,
            printedStatus = kot.printedStatus,
            updatedAt = kot.updatedAt,
            items = itemsForOrder
        )
    }

    // Analytics
    fun getAnalytics(): Map<String, Any> {
        val paidOrders = orders.values.filter { it.status == "Paid" }
        val totalRevenue = paidOrders.sumOf { it.totalAmount }
        val activeKOTs = kots.values.count { it.status != "Served" }
        val availableTables = diningTables.values.count { it.status == "Available" }
        val occupiedTables = diningTables.values.count { it.status == "Occupied" }

        return mapOf(
            "totalRevenue" to totalRevenue,
            "totalOrders" to orders.size,
            "activeKOTs" to activeKOTs,
            "availableTables" to availableTables,
            "occupiedTables" to occupiedTables,
            "totalCustomers" to customers.size
        )
    }
}
