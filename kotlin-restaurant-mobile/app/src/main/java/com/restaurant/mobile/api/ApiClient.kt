package com.restaurant.mobile.api

import com.restaurant.mobile.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ApiClient {
    // 10.0.2.2 points to host localhost from Android Emulator
    private const val BASE_URL = "http://10.0.2.2:8080/api"

    private fun httpGet(endpoint: String): String {
        val url = URL("$BASE_URL$endpoint")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 3000
        conn.readTimeout = 3000
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun httpPost(endpoint: String, jsonBody: String): String {
        val url = URL("$BASE_URL$endpoint")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 3000
        conn.readTimeout = 3000
        try {
            conn.outputStream.bufferedWriter().use { it.write(jsonBody) }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun httpPut(endpoint: String, jsonBody: String): String {
        val url = URL("$BASE_URL$endpoint")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "PUT"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 3000
        conn.readTimeout = 3000
        try {
            conn.outputStream.bufferedWriter().use { it.write(jsonBody) }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun getCategories(): List<Category> = withContext(Dispatchers.IO) {
        try {
            val res = httpGet("/categories")
            val arr = JSONArray(res)
            val list = mutableListOf<Category>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(Category(obj.getLong("id"), obj.getString("name")))
            }
            list
        } catch (e: Exception) {
            listOf(
                Category(1, "Starters"),
                Category(2, "Main Course"),
                Category(3, "Desserts"),
                Category(4, "Beverages")
            )
        }
    }

    suspend fun getMenuItems(): List<MenuItem> = withContext(Dispatchers.IO) {
        try {
            val res = httpGet("/menu-items")
            val arr = JSONArray(res)
            val list = mutableListOf<MenuItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    MenuItem(
                        id = obj.getLong("id"),
                        categoryId = obj.getLong("categoryId"),
                        name = obj.getString("name"),
                        price = obj.getDouble("price"),
                        taxRate = obj.optDouble("taxRate", 5.0),
                        isAvailable = obj.optBoolean("isAvailable", true)
                    )
                )
            }
            list
        } catch (e: Exception) {
            listOf(
                MenuItem(1, 1, "Paneer Tikka", 250.0),
                MenuItem(2, 2, "Chicken Biryani", 350.0),
                MenuItem(3, 2, "Garlic Naan", 60.0),
                MenuItem(4, 3, "Gulab Jamun", 90.0),
                MenuItem(5, 4, "Fresh Mint Mojito", 120.0)
            )
        }
    }

    suspend fun getTables(): List<DiningTable> = withContext(Dispatchers.IO) {
        try {
            val res = httpGet("/tables")
            val arr = JSONArray(res)
            val list = mutableListOf<DiningTable>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    DiningTable(
                        id = obj.getLong("id"),
                        tableNumber = obj.getString("tableNumber"),
                        seatingCapacity = obj.getInt("seatingCapacity"),
                        status = obj.getString("status")
                    )
                )
            }
            list
        } catch (e: Exception) {
            listOf(
                DiningTable(1, "T1", 2, "Available"),
                DiningTable(2, "T2", 2, "Occupied"),
                DiningTable(3, "T3", 4, "Available"),
                DiningTable(4, "T4", 4, "Reserved")
            )
        }
    }

    suspend fun updateTableStatus(tableId: Long, status: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("status", status).toString()
            httpPut("/tables/$tableId/status", body)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getKOTs(): List<KOT> = withContext(Dispatchers.IO) {
        try {
            val res = httpGet("/kots")
            val arr = JSONArray(res)
            val list = mutableListOf<KOT>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val itemsArr = obj.getJSONArray("items")
                val items = mutableListOf<KOTItem>()
                for (j in 0 until itemsArr.length()) {
                    val itemObj = itemsArr.getJSONObject(j)
                    items.add(
                        KOTItem(
                            id = itemObj.optLong("id", j.toLong()),
                            menuItemName = itemObj.getString("menuItemName"),
                            quantity = itemObj.getInt("quantity"),
                            priceAtTimeOfOrder = itemObj.optDouble("priceAtTimeOfOrder", 0.0)
                        )
                    )
                }
                list.add(
                    KOT(
                        id = obj.getLong("id"),
                        orderId = obj.getLong("orderId"),
                        status = obj.getString("status"),
                        items = items
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun updateKOTStatus(kotId: Long, newStatus: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("status", newStatus).toString()
            httpPut("/kots/$kotId/status", body)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun checkoutOrder(
        paymentMethod: String,
        items: List<CartItem>,
        customerId: Long? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonItems = JSONArray()
            items.forEach { cartItem ->
                jsonItems.put(
                    JSONObject()
                        .put("menuItemId", cartItem.menuItem.id)
                        .put("quantity", cartItem.quantity)
                )
            }
            val body = JSONObject()
                .put("orderType", "Dine-in")
                .put("paymentMethod", paymentMethod)
                .put("items", jsonItems)

            if (customerId != null) {
                body.put("customerId", customerId)
            }

            httpPost("/orders/checkout", body.toString())
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getAnalytics(): AnalyticsData = withContext(Dispatchers.IO) {
        try {
            val res = httpGet("/analytics")
            val obj = JSONObject(res)
            AnalyticsData(
                totalRevenue = obj.optDouble("totalRevenue", 0.0),
                totalOrders = obj.optInt("totalOrders", 0),
                activeKOTs = obj.optInt("activeKOTs", 0),
                availableTables = obj.optInt("availableTables", 0),
                occupiedTables = obj.optInt("occupiedTables", 0),
                totalCustomers = obj.optInt("totalCustomers", 0)
            )
        } catch (e: Exception) {
            AnalyticsData()
        }
    }
}
