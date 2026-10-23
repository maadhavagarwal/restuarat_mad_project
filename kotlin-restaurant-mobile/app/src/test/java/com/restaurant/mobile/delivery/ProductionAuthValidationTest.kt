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
}
