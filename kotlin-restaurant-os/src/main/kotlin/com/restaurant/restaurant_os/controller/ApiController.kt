package com.restaurant.restaurant_os.controller

import com.restaurant.restaurant_os.model.*
import com.restaurant.restaurant_os.repository.RestaurantRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = ["*"])
class ApiController(private val repository: RestaurantRepository) {

    // Categories
    @GetMapping("/categories")
    fun getCategories(): List<Category> = repository.getAllCategories()

    @PostMapping("/categories")
    fun createCategory(@RequestBody body: Map<String, String>): Category {
        val name = body["name"] ?: "New Category"
        return repository.addCategory(name)
    }

    // Menu Items
    @GetMapping("/menu-items")
    fun getMenuItems(@RequestParam(required = false) categoryId: Long?): List<MenuItem> {
        return repository.getMenuItems(categoryId)
    }

    @PostMapping("/menu-items")
    fun createMenuItem(@RequestBody body: MenuItem): MenuItem {
        return repository.addMenuItem(
            categoryId = body.categoryId,
            name = body.name,
            price = body.price,
            taxRate = body.taxRate
        )
    }

    // Tables
    @GetMapping("/tables")
    fun getTables(): List<DiningTable> = repository.getAllDiningTables()

    @PutMapping("/tables/{id}/status")
    fun updateTableStatus(
        @PathVariable id: Long,
        @RequestBody request: StatusUpdateRequest
    ): ResponseEntity<DiningTable> {
        val updated = repository.updateTableStatus(id, request.status)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(updated)
    }

    // Customers
    @GetMapping("/customers")
    fun getCustomers(): List<Customer> = repository.customers.values.toList()

    @GetMapping("/customers/search")
    fun searchCustomerByPhone(@RequestParam phone: String): ResponseEntity<Customer> {
        val cust = repository.findCustomerByPhone(phone) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(cust)
    }

    @PostMapping("/customers")
    fun createCustomer(@RequestBody request: CustomerCreateRequest): Customer {
        return repository.addCustomer(request.name, request.phone)
    }

    // Orders & Checkout
    @GetMapping("/orders")
    fun getOrders(): List<Order> = repository.getAllOrders()

    @PostMapping("/orders/checkout")
    fun checkout(@RequestBody request: CheckoutRequest): Order {
        return repository.createOrder(
            orderType = request.orderType,
            tableId = request.tableId,
            customerId = request.customerId,
            paymentMethod = request.paymentMethod,
            items = request.items
        )
    }

    // KOTs
    @GetMapping("/kots")
    fun getKOTs(): List<KOTResponse> = repository.getAllKOTs()

    @PutMapping("/kots/{id}/status")
    fun updateKOTStatus(
        @PathVariable id: Long,
        @RequestBody request: StatusUpdateRequest
    ): ResponseEntity<KOTResponse> {
        val updated = repository.updateKOTStatus(id, request.status)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(updated)
    }

    // Analytics
    @GetMapping("/analytics")
    fun getAnalytics(): Map<String, Any> = repository.getAnalytics()
}
