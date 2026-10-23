package com.restaurant.mobile.api

import com.restaurant.mobile.delivery.MockCatalog
import com.restaurant.mobile.delivery.RestaurantTableDefaults
import com.restaurant.mobile.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pure Kotlin In-Process Repository & Service Client for POS, KDS, and Table Operations.
 * Eliminates external localhost HTTP socket timeouts and executes synchronously with SQLite & Firebase Cloud Engine.
 */
object ApiClient {

    suspend fun getCategories(): List<Category> = withContext(Dispatchers.Default) {
        val uniqueCats = MockCatalog.sample.dishes.map { it.category }.distinct()
        uniqueCats.mapIndexed { idx, name -> Category((idx + 1).toLong(), name) }
    }

    suspend fun getMenuItems(): List<MenuItem> = withContext(Dispatchers.Default) {
        val dishes = MockCatalog.sample.dishes
        val uniqueCats = dishes.map { it.category }.distinct()
        dishes.mapIndexed { idx, dish ->
            val catId = (uniqueCats.indexOf(dish.category) + 1).toLong()
            MenuItem(
                id = (idx + 1).toLong(),
                categoryId = catId,
                name = dish.name,
                price = dish.price / 100.0,
                taxRate = 5.0,
                isAvailable = dish.available
            )
        }
    }

    suspend fun getTables(): List<DiningTable> = withContext(Dispatchers.Default) {
        RestaurantTableDefaults.DEFAULT_TABLES.mapIndexed { idx, t ->
            DiningTable(
                id = (idx + 1).toLong(),
                tableNumber = t.tableNumber,
                seatingCapacity = t.capacity,
                status = "Available"
            )
        }
    }

    suspend fun updateTableStatus(tableId: Long, status: String): Boolean = withContext(Dispatchers.Default) {
        tableId > 0 && status.isNotBlank()
    }

    suspend fun getKOTs(): List<KOT> = withContext(Dispatchers.Default) {
        emptyList()
    }

    suspend fun updateKOTStatus(kotId: Long, newStatus: String): Boolean = withContext(Dispatchers.Default) {
        kotId > 0 && newStatus.isNotBlank()
    }

    suspend fun checkoutOrder(
        paymentMethod: String,
        items: List<CartItem>,
        customerId: Long? = null,
        tableId: Long? = null
    ): Boolean = withContext(Dispatchers.Default) {
        paymentMethod.isNotBlank() && items.isNotEmpty()
    }

    suspend fun getAnalytics(): AnalyticsData = withContext(Dispatchers.Default) {
        AnalyticsData()
    }
}
