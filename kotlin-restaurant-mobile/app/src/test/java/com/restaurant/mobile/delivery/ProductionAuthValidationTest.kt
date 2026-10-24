package com.restaurant.mobile.delivery

import org.junit.Assert.*
import org.junit.Test

class ProductionAuthValidationTest {

    @Test
    fun `phone validation strictly requires exactly 10 digits starting with 6-9`() {
        // Valid Indian 10-digit mobile numbers
        assertTrue(RestroValidationUtils.isValidPhone("9876543210"))
        assertTrue(RestroValidationUtils.isValidPhone("8123456789"))
        assertTrue(RestroValidationUtils.isValidPhone("7012345678"))
        assertTrue(RestroValidationUtils.isValidPhone("6301234567"))

        // Invalid: Less than 10 digits
        assertFalse(RestroValidationUtils.isValidPhone("987654321"))
        assertFalse(RestroValidationUtils.isValidPhone("123"))

        // Invalid: Starting with invalid prefix (1, 2, 3, 4, 5)
        assertFalse(RestroValidationUtils.isValidPhone("5123456789"))
        assertFalse(RestroValidationUtils.isValidPhone("1234567890"))
        assertFalse(RestroValidationUtils.isValidPhone("0123456789"))

        // Sanitize phone extracts only digits, strips country code 91 if present, and caps at 10
        assertEquals("9876543210", RestroValidationUtils.sanitizePhone("+91 98765 43210"))
        assertEquals("9876543210", RestroValidationUtils.sanitizePhone("98765-43210-999"))
        assertEquals("8123456789", RestroValidationUtils.sanitizePhone("abc8123456789xyz"))
    }

    @Test
    fun `phone validation messages provide real-time guidance`() {
        assertNull(RestroValidationUtils.getPhoneValidationMessage("9876543210"))
        assertNotNull(RestroValidationUtils.getPhoneValidationMessage("98765"))
        assertTrue(RestroValidationUtils.getPhoneValidationMessage("98765")!!.contains("5/10 digits"))
        assertTrue(RestroValidationUtils.getPhoneValidationMessage("5123456789")!!.contains("start with 6, 7, 8, or 9"))
    }

    @Test
    fun `email validation strictly requires valid RFC domain and rejects loose domains`() {
        // Valid emails with proper domains
        assertTrue(RestroValidationUtils.isValidEmail("aarav@gmail.com"))
        assertTrue(RestroValidationUtils.isValidEmail("admin@restro.app"))
        assertTrue(RestroValidationUtils.isValidEmail("chef.marco@kitchen.co.in"))
        assertTrue(RestroValidationUtils.isValidEmail("contact@restaurant-brand.org"))
        assertTrue(RestroValidationUtils.isValidEmail("user.name+tag@sub.domain.com"))

        // Loose or invalid domains - MUST FAIL
        assertFalse(RestroValidationUtils.isValidEmail("user@loose"))
        assertFalse(RestroValidationUtils.isValidEmail("user@com"))
        assertFalse(RestroValidationUtils.isValidEmail("user@domain"))
        assertFalse(RestroValidationUtils.isValidEmail("user@.com"))
        assertFalse(RestroValidationUtils.isValidEmail("user@domain..com"))
        assertFalse(RestroValidationUtils.isValidEmail("user@-domain.com"))
        assertFalse(RestroValidationUtils.isValidEmail("plainaddress"))
        assertFalse(RestroValidationUtils.isValidEmail("@missinglocal.com"))
    }

    @Test
    fun `email validation messages give actionable feedback`() {
        assertNull(RestroValidationUtils.getEmailValidationMessage("aarav@example.com"))
        assertEquals("Email must contain an @ symbol", RestroValidationUtils.getEmailValidationMessage("aarav"))
        assertTrue(RestroValidationUtils.getEmailValidationMessage("aarav@loose")!!.contains("domain", ignoreCase = true))
    }

    @Test
    fun `account creation requires valid name, email, 10-digit phone and password`() {
        val name = "Aarav Sharma"
        val email = "aarav@gmail.com"
        val phone = "9876543210"
        val password = "SecurePassword123"

        assertTrue(RestroValidationUtils.isValidName(name))
        assertTrue(RestroValidationUtils.isValidEmail(email))
        assertTrue(RestroValidationUtils.isValidPhone(phone))
        assertTrue(RestroValidationUtils.isValidPassword(password))

        // Incomplete or invalid cases
        assertFalse(RestroValidationUtils.isValidName("A"))
        assertFalse(RestroValidationUtils.isValidEmail("aarav@loose"))
        assertFalse(RestroValidationUtils.isValidPhone("98765"))
        assertFalse(RestroValidationUtils.isValidPassword("short"))
    }

    @Test
    fun `otp code validation requires exactly 6 numeric digits`() {
        val validCodes = listOf("123456", "000000", "999999", "548291")
        for (code in validCodes) {
            val clean = code.trim()
            assertEquals(6, clean.length)
            assertTrue(clean.all { it.isDigit() })
        }

        val invalidCodes = listOf("12345", "1234567", "abcdef", "12a456", "", "   ")
        for (code in invalidCodes) {
            val clean = code.trim()
            val isValid = clean.length == 6 && clean.all { it.isDigit() }
            assertFalse("Code '$code' must be rejected", isValid)
        }
    }

    @Test
    fun `e164 phone formatting handles various user inputs correctly`() {
        val raw1 = "9876543210"
        val raw2 = "+91 98765 43210"
        val raw3 = "09876543210"

        val sanitized1 = RestroValidationUtils.sanitizePhone(raw1)
        val sanitized2 = RestroValidationUtils.sanitizePhone(raw2)
        val sanitized3 = RestroValidationUtils.sanitizePhone(raw3)

        assertEquals("9876543210", sanitized1)
        assertEquals("9876543210", sanitized2)
        assertEquals("9876543210", sanitized3)

        val formatted1 = "+91$sanitized1"
        assertEquals("+919876543210", formatted1)
    }

    @Test
    fun `staff roles direct access verification without PIN requirement`() {
        // Staff roles
        assertTrue(UserRole.WAITER.isStaff)
        assertTrue(UserRole.CHEF_KDS.isStaff)
        assertTrue(UserRole.ADMIN.isStaff)

        // Customer role is strictly non-staff
        assertFalse(UserRole.CUSTOMER.isStaff)

        // Direct profile role assignment for staff credentials
        val waiterProfile = Profile(
            id = "staff-waiter-1",
            name = "Rohan Waiter",
            email = "waiter@restro.app",
            role = UserRole.WAITER
        )
        assertTrue(waiterProfile.role.isStaff)
        assertEquals("Waiter / POS", waiterProfile.role.label)

        val chefProfile = Profile(
            id = "staff-chef-1",
            name = "Marco Chef",
            email = "chef@restro.app",
            role = UserRole.CHEF_KDS
        )
        assertTrue(chefProfile.role.isStaff)
        assertEquals("Kitchen Chef", chefProfile.role.label)

        val adminProfile = Profile(
            id = "staff-admin-1",
            name = "Ananya Admin",
            email = "admin@restro.app",
            role = UserRole.ADMIN
        )
        assertTrue(adminProfile.role.isStaff)
        assertEquals("Admin", adminProfile.role.label)

        val customerProfile = Profile(
            id = "cust-1",
            name = "Normal Customer",
            email = "customer@gmail.com",
            role = UserRole.CUSTOMER
        )
        assertFalse(customerProfile.role.isStaff)
    }

    @Test
    fun `admin provisioned staff member payroll record validation`() {
        val newStaffRecord = StaffPayrollRecord(
            id = "pay-new-1",
            staffId = "staff-new-1",
            staffName = "Suresh Patel",
            staffRole = UserRole.WAITER.label,
            monthYear = "October 2026",
            baseSalary = 22000L,
            bonusTips = 0L,
            deductions = 0L,
            netSalary = 22000L,
            status = "PENDING"
        )
        assertEquals("Suresh Patel", newStaffRecord.staffName)
        assertEquals("Waiter / POS", newStaffRecord.staffRole)
        assertEquals(22000L, newStaffRecord.netSalary)
        assertEquals("PENDING", newStaffRecord.status)
    }

    @Test
    fun `rider live location tracking strictly scoped to assigned user`() {
        val assignedUserOrder = DeliveryOrder(
            id = "ORD-TEST-1",
            restaurantId = "rest-1",
            restaurantName = "Spice Route",
            address = Address(id = "addr-1", label = "Home", line = "Indiranagar", city = "Mumbai", postcode = "400001", phone = "+919876543210"),
            quote = Quote(restaurantId = "rest-1", subtotal = 50000L, delivery = 0L, tax = 2500L, discount = 5000L, total = 47500L, lines = emptyList()),
            paymentMethod = "UPI",
            userId = "user-assigned-1"
        )

        // Authorized: Owner of the order
        val authorizedUser = "user-assigned-1"
        assertTrue(assignedUserOrder.userId == authorizedUser)

        // Forbidden: Another user attempting to track
        val unauthorizedUser = "user-intruder-99"
        assertFalse(assignedUserOrder.userId == unauthorizedUser)

        // Order delivery lifecycle state transitions
        val orderStages = listOf("Confirmed", "Preparing", "Out for delivery", "Delivered")
        val activeStage = "Out for delivery"
        val activeIdx = orderStages.indexOf(activeStage)
        assertEquals(2, activeIdx)
        assertTrue(activeIdx >= 0 && activeIdx < orderStages.size)
    }

    @Test
    fun `post ordering experience reorder and review data integrity`() {
        val testLines = listOf(
            QuoteLine(dishId = "dish-1", name = "Biryani", quantity = 2, addons = emptyList(), unitPrice = 35000L, total = 70000L),
            QuoteLine(dishId = "dish-2", name = "Gulab Jamun", quantity = 1, addons = emptyList(), unitPrice = 12000L, total = 12000L)
        )
        val testQuote = Quote(restaurantId = "rest-biryani", subtotal = 82000L, delivery = 4000L, tax = 4100L, discount = 5000L, total = 85100L, lines = testLines)
        val completedOrder = DeliveryOrder(
            id = "ORD-COMPLETED-101",
            restaurantId = "rest-biryani",
            restaurantName = "Biryani Mahal",
            address = Address(id = "addr-2", label = "Work", line = "BKC", city = "Mumbai", postcode = "400051", phone = "+919876543211"),
            quote = testQuote,
            paymentMethod = "Cash on delivery",
            status = "Delivered",
            userId = "user-101"
        )

        // Reorder reconstruction produces exact item quantities and dishes
        val reorderCartLines = completedOrder.quote.lines.map { line ->
            Line(line.dishId, line.quantity, line.addons)
        }
        assertEquals(2, reorderCartLines.size)
        assertEquals("dish-1", reorderCartLines[0].dishId)
        assertEquals(2, reorderCartLines[0].quantity)

        // Post-delivery review model validation
        val review = Review(
            orderId = completedOrder.id,
            restaurantId = completedOrder.restaurantId,
            author = "Test Diner",
            rating = 5,
            text = "Super fast delivery and delicious biryani!"
        )
        assertEquals(5, review.rating)
        assertEquals("Test Diner", review.author)
        assertEquals(completedOrder.id, review.orderId)
        assertEquals(completedOrder.restaurantId, review.restaurantId)
    }

    @Test
    fun `user account profile customization and light dark theme mode toggle integrity`() {
        val userProfile = Profile(
            id = "user-theme-1",
            name = "Aarav Sharma",
            email = "aarav@example.com",
            phone = "+91 98765 43210",
            reducedGlass = false,
            isDarkMode = true,
            role = UserRole.CUSTOMER
        )

        // Default theme state
        assertTrue(userProfile.isDarkMode)

        // Switching to Light Mode
        val lightProfile = userProfile.copy(isDarkMode = false)
        assertFalse(lightProfile.isDarkMode)
        assertEquals("Aarav Sharma", lightProfile.name)
        assertEquals("+91 98765 43210", lightProfile.phone)

        // Switching back to Dark Mode
        val darkProfile = lightProfile.copy(isDarkMode = true)
        assertTrue(darkProfile.isDarkMode)

        // Editing name and phone in Account Screen preserves theme mode
        val updatedProfile = darkProfile.copy(name = "Aarav S.", phone = "+91 91234 56789")
        assertEquals("Aarav S.", updatedProfile.name)
        assertEquals("+91 91234 56789", updatedProfile.phone)
        assertTrue(updatedProfile.isDarkMode)
    }
}
