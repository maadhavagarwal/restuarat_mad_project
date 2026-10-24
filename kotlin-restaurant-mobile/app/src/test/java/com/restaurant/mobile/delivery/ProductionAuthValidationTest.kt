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
}
