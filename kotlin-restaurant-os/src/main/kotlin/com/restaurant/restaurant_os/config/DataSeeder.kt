package com.restaurant.restaurant_os.config

import com.restaurant.restaurant_os.model.CartItemRequest
import com.restaurant.restaurant_os.repository.RestaurantRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class DataSeeder(private val repository: RestaurantRepository) : CommandLineRunner {

    override fun run(vararg args: String?) {
        println("🌱 Seeding initial Restaurant OS Kotlin data...")

        // Categories
        val starters = repository.addCategory("Starters")
        val mainCourse = repository.addCategory("Main Course")
        val desserts = repository.addCategory("Desserts")
        val beverages = repository.addCategory("Beverages")

        // Menu Items
        val tikka = repository.addMenuItem(starters.id!!, "Paneer Tikka", 250.0)
        val Manchurian = repository.addMenuItem(starters.id!!, "Veg Manchurian", 210.0)
        
        val biryani = repository.addMenuItem(mainCourse.id!!, "Chicken Biryani", 350.0)
        val butterChicken = repository.addMenuItem(mainCourse.id!!, "Butter Chicken", 380.0)
        val naan = repository.addMenuItem(mainCourse.id!!, "Garlic Naan", 60.0)
        val dosa = repository.addMenuItem(mainCourse.id!!, "Masala Dosa", 160.0)

        val gulabJamun = repository.addMenuItem(desserts.id!!, "Gulab Jamun", 90.0)
        val iceCream = repository.addMenuItem(desserts.id!!, "Sizzling Brownie", 180.0)

        val mojito = repository.addMenuItem(beverages.id!!, "Fresh Mint Mojito", 120.0)
        val lassi = repository.addMenuItem(beverages.id!!, "Mango Lassi", 100.0)

        // Dining Tables
        repository.addDiningTable("T1", 2, "Available")
        repository.addDiningTable("T2", 2, "Occupied")
        repository.addDiningTable("T3", 4, "Available")
        repository.addDiningTable("T4", 4, "Reserved")
        repository.addDiningTable("T5", 6, "Available")
        repository.addDiningTable("T6", 8, "Available")
        repository.addDiningTable("T7", 2, "Available")
        repository.addDiningTable("T8", 4, "Occupied")

        // Customers
        val cust1 = repository.addCustomer("Rahul Sharma", "9876543210")
        val cust2 = repository.addCustomer("Priya Patel", "9123456789")

        // Sample initial orders for KDS preview
        val sampleOrder = repository.createOrder(
            orderType = "Dine-in",
            tableId = 2L,
            customerId = cust1.id,
            paymentMethod = "UPI",
            items = listOf(
                CartItemRequest(biryani.id!!, 2),
                CartItemRequest(naan.id!!, 3),
                CartItemRequest(mojito.id!!, 2)
            )
        )

        println("✅ Seeding completed successfully!")
    }
}
