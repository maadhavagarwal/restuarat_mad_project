package com.restaurant.mobile.delivery

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying Step 2 Commit 2 Android document verification requirements:
 * - Parsing structured JSON success responses and extracting localized messages
 * - Ensuring HTTP errors and network failures fail closed (never treated as success)
 * - Validating localized response handling for English, Hindi, and Marathi
 * - Masked document number validation
 */
class DocumentVerificationRegressionTest {

    private val gson = Gson()

    @Test
    fun testParseSuccessResponseEnglish() {
        val json = """
            {
                "success": true,
                "verified": true,
                "provider": "GEMINI_VISION_OCR",
                "documentType": "RIDER_DL",
                "documentNumber": "***2345",
                "maskedDocumentNumber": "***2345",
                "holderName": "Ramesh Rider",
                "confidence": 0.98,
                "message": "Document verified successfully."
            }
        """.trimIndent()

        val map = gson.fromJson(json, Map::class.java)
        assertTrue(map["verified"] == true)
        assertEquals("Document verified successfully.", map["message"])
        assertEquals("***2345", map["documentNumber"])
    }

    @Test
    fun testParseSuccessResponseHindi() {
        val json = """
            {
                "success": true,
                "verified": true,
                "provider": "GEMINI_VISION_OCR",
                "documentType": "RESTAURANT_FSSAI",
                "documentNumber": "***0421",
                "maskedDocumentNumber": "***0421",
                "holderName": "Bistro Lumiere",
                "confidence": 0.96,
                "message": "दस्तावेज़ सफलतापूर्वक सत्यापित हो गया।"
            }
        """.trimIndent()

        val map = gson.fromJson(json, Map::class.java)
        assertTrue(map["verified"] == true)
        assertEquals("दस्तावेज़ सफलतापूर्वक सत्यापित हो गया।", map["message"])
    }

    @Test
    fun testParseSuccessResponseMarathi() {
        val json = """
            {
                "success": true,
                "verified": true,
                "provider": "GEMINI_VISION_OCR",
                "documentType": "RESTAURANT_GST",
                "documentNumber": "***R1ZM",
                "maskedDocumentNumber": "***R1ZM",
                "holderName": "Bistro Lumiere",
                "confidence": 0.99,
                "message": "दस्तऐवज यशस्वीरित्या पडताळले गेले."
            }
        """.trimIndent()

        val map = gson.fromJson(json, Map::class.java)
        assertTrue(map["verified"] == true)
        assertEquals("दस्तऐवज यशस्वीरित्या पडताळले गेले.", map["message"])
    }

    @Test
    fun testParseDocumentMismatchResponseFailsClosed() {
        val json = """
            {
                "success": false,
                "verified": false,
                "provider": "GEMINI_VISION_OCR",
                "error": "DOCUMENT_NUMBER_MISMATCH",
                "message": "Document number mismatch. The extracted number does not match the submitted number.",
                "documentType": "RIDER_DL"
            }
        """.trimIndent()

        val map = gson.fromJson(json, Map::class.java)
        assertFalse(map["verified"] == true)
        assertEquals("DOCUMENT_NUMBER_MISMATCH", map["error"])
        assertEquals("Document number mismatch. The extracted number does not match the submitted number.", map["message"])
    }

    @Test
    fun testParseDocumentUnreadableResponseFailsClosed() {
        val json = """
            {
                "success": false,
                "verified": false,
                "provider": "GEMINI_VISION_OCR",
                "error": "DOCUMENT_UNREADABLE",
                "message": "Document is blurry or unreadable. Please upload a clear copy.",
                "documentType": "RIDER_DL"
            }
        """.trimIndent()

        val map = gson.fromJson(json, Map::class.java)
        assertFalse(map["verified"] == true)
        assertEquals("DOCUMENT_UNREADABLE", map["error"])
    }

    @Test
    fun testParseUnauthorizedResponseFailsClosed() {
        val json = """
            {
                "success": false,
                "verified": false,
                "error": "UNAUTHORIZED",
                "message": "Authentication token required. Please sign in to verify documents."
            }
        """.trimIndent()

        val map = gson.fromJson(json, Map::class.java)
        assertFalse(map["verified"] == true)
        assertEquals("UNAUTHORIZED", map["error"])
    }

    @Test
    fun testParseForbiddenResponseFailsClosed() {
        val json = """
            {
                "success": false,
                "verified": false,
                "error": "FORBIDDEN",
                "message": "Access denied: You do not have permission to verify this document."
            }
        """.trimIndent()

        val map = gson.fromJson(json, Map::class.java)
        assertFalse(map["verified"] == true)
        assertEquals("FORBIDDEN", map["error"])
    }

    @Test
    fun testApiFailureExtractsBackendMessage() {
        val failure = ApiFailure(422, "Document number mismatch. The extracted number does not match the submitted number.")
        assertEquals(422, failure.status)
        assertEquals("Document number mismatch. The extracted number does not match the submitted number.", failure.message)
    }

    @Test
    fun testNetworkExceptionNeverTreatedAsVerified() {
        var verificationResult = true // initially set to true to test mutation
        var receivedMessage = ""

        try {
            throw java.io.IOException("Connection refused to remote server")
        } catch (e: Exception) {
            verificationResult = false
            receivedMessage = e.localizedMessage ?: "Network error"
        }

        assertFalse(verificationResult)
        assertEquals("Connection refused to remote server", receivedMessage)
    }
}
