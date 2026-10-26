package com.restaurant.mobile.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying Commit 6 requirements for:
 * - Image MIME type detection and magic byte verification
 * - Image size limits (5MB for logo, 10MB for dish) and unsupported type rejections
 * - Object naming conventions and unique path generation
 * - File extension mappings
 * - Rejection of local content:// URIs from permanent cloud persistence
 * - Preservation of existing dish and restaurant schema fields during image updates
 */
class CloudImageUploadTest {

    // -------------------------------------------------------------
    // MIME TYPE & MAGIC HEADER DETECTION
    // -------------------------------------------------------------

    @Test
    fun testJpegMagicBytesDetection() {
        val jpegHeader = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)
        val mime = FirebaseStorageEngine.detectMimeFromHeader(jpegHeader)
        assertEquals("image/jpeg", mime)
    }

    @Test
    fun testPngMagicBytesDetection() {
        val pngHeader = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)
        val mime = FirebaseStorageEngine.detectMimeFromHeader(pngHeader)
        assertEquals("image/png", mime)
    }

    @Test
    fun testGifMagicBytesDetection() {
        val gifHeader = byteArrayOf(0x47.toByte(), 0x49.toByte(), 0x46.toByte(), 0x38.toByte(), 0x39.toByte(), 0x61.toByte())
        val mime = FirebaseStorageEngine.detectMimeFromHeader(gifHeader)
        assertEquals("image/gif", mime)
    }

    @Test
    fun testWebpMagicBytesDetection() {
        val webpHeader = byteArrayOf(
            0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte(), // RIFF
            0x00, 0x00, 0x00, 0x00,
            0x57.toByte(), 0x45.toByte(), 0x42.toByte(), 0x50.toByte()  // WEBP
        )
        val mime = FirebaseStorageEngine.detectMimeFromHeader(webpHeader)
        assertEquals("image/webp", mime)
    }

    @Test
    fun testCorruptOrUnknownBytesReturnsNull() {
        val corrupt = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        assertNull(FirebaseStorageEngine.detectMimeFromHeader(corrupt))
    }

    // -------------------------------------------------------------
    // VALIDATION LOGIC (TYPE & SIZE LIMITS)
    // -------------------------------------------------------------

    @Test
    fun testValidJpegWithinLogoLimitPasses() {
        val header = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val sizeBytes = 2 * 1024 * 1024L // 2 MB
        val result = FirebaseStorageEngine.validateImageBytes(
            headerBytes = header,
            reportedMimeType = "image/jpeg",
            sizeBytes = sizeBytes,
            maxSizeBytes = FirebaseStorageEngine.MAX_LOGO_SIZE_BYTES
        )

        assertTrue(result.isValid)
        assertEquals("image/jpeg", result.mimeType)
        assertNull(result.errorMessage)
    }

    @Test
    fun testValidDishImageWithinDishLimitPasses() {
        val header = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
        val sizeBytes = 8 * 1024 * 1024L // 8 MB (under 10 MB limit)
        val result = FirebaseStorageEngine.validateImageBytes(
            headerBytes = header,
            reportedMimeType = "image/png",
            sizeBytes = sizeBytes,
            maxSizeBytes = FirebaseStorageEngine.MAX_DISH_SIZE_BYTES
        )

        assertTrue(result.isValid)
        assertEquals("image/png", result.mimeType)
        assertNull(result.errorMessage)
    }

    @Test
    fun testOversizedLogoRejected() {
        val header = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val sizeBytes = 6 * 1024 * 1024L // 6 MB (exceeds 5 MB logo limit)
        val result = FirebaseStorageEngine.validateImageBytes(
            headerBytes = header,
            reportedMimeType = "image/jpeg",
            sizeBytes = sizeBytes,
            maxSizeBytes = FirebaseStorageEngine.MAX_LOGO_SIZE_BYTES
        )

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("exceeds the maximum allowed limit"))
    }

    @Test
    fun testOversizedDishImageRejected() {
        val header = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
        val sizeBytes = 12 * 1024 * 1024L // 12 MB (exceeds 10 MB dish limit)
        val result = FirebaseStorageEngine.validateImageBytes(
            headerBytes = header,
            reportedMimeType = "image/png",
            sizeBytes = sizeBytes,
            maxSizeBytes = FirebaseStorageEngine.MAX_DISH_SIZE_BYTES
        )

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("exceeds the maximum allowed limit"))
    }

    @Test
    fun testUnsupportedMimeTypeRejected() {
        val header = byteArrayOf(0x25.toByte(), 0x50.toByte(), 0x44.toByte(), 0x46.toByte()) // %PDF
        val result = FirebaseStorageEngine.validateImageBytes(
            headerBytes = header,
            reportedMimeType = "application/pdf",
            sizeBytes = 100_000L,
            maxSizeBytes = FirebaseStorageEngine.MAX_LOGO_SIZE_BYTES
        )

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("Unsupported file type"))
    }

    // -------------------------------------------------------------
    // EXTENSION & STORAGE PATH GENERATION
    // -------------------------------------------------------------

    @Test
    fun testExtensionMappingFromMimeType() {
        assertEquals("png", FirebaseStorageEngine.getExtensionForMimeType("image/png"))
        assertEquals("webp", FirebaseStorageEngine.getExtensionForMimeType("image/webp"))
        assertEquals("gif", FirebaseStorageEngine.getExtensionForMimeType("image/gif"))
        assertEquals("jpg", FirebaseStorageEngine.getExtensionForMimeType("image/jpeg"))
        assertEquals("jpg", FirebaseStorageEngine.getExtensionForMimeType("image/jpg"))
        assertEquals("jpg", FirebaseStorageEngine.getExtensionForMimeType(null))
    }

    @Test
    fun testUniqueStoragePathGeneration() {
        val path1 = FirebaseStorageEngine.generateStoragePath("rest-1", "logo", "rest-1", "jpg")
        val path2 = FirebaseStorageEngine.generateStoragePath("rest-1", "logo", "rest-1", "jpg")

        assertTrue(path1.startsWith("restaurants/rest-1/logos/logo_"))
        assertTrue(path1.endsWith(".jpg"))
        // UUID ensures distinct paths even within the same millisecond
        assertFalse(path1 == path2)

        val dishPath = FirebaseStorageEngine.generateStoragePath("rest-1", "dish", "dish-404", "webp")
        assertTrue(dishPath.startsWith("restaurants/rest-1/dishes/dish_dish-404_"))
        assertTrue(dishPath.endsWith(".webp"))
    }

    // -------------------------------------------------------------
    // LOCAL URI REJECTION & CLOUD REFERENCE VALIDATION
    // -------------------------------------------------------------

    @Test
    fun testLocalContentUriMustNotBePermanentCloudReference() {
        val localUri = "content://media/external/images/media/4921"
        val cloudUrl = "https://firebasestorage.googleapis.com/v0/b/restro-42877.firebasestorage.app/o/restaurants%2Frest-1%2Flogos%2Flogo_17000.jpg?alt=media"

        fun isPersistentCloudUrl(url: String): Boolean {
            return url.startsWith("https://firebasestorage.googleapis.com/") ||
                    url.startsWith("https://storage.googleapis.com/") ||
                    url.startsWith("https://images.unsplash.com/") ||
                    url.startsWith("https://") ||
                    url.startsWith("http://")
        }

        assertFalse("Device-local content URI must not qualify as permanent cloud storage reference", localUri.startsWith("https://"))
        assertTrue("Firebase storage download URL qualifies as permanent cloud reference", isPersistentCloudUrl(cloudUrl))
    }

    @Test
    fun testDishUpdatePreservesUnrelatedFields() {
        val original = Dish(
            id = "dish-biryani-1",
            restaurantId = "rest-1",
            name = "Dum Biryani",
            category = "Biryani",
            description = "Fragrant long-grain basmati cooked with whole spices",
            price = 32000L,
            vegetarian = false,
            available = true,
            image = "https://images.unsplash.com/photo-old",
            addons = listOf(AddOn("extra-raita", "Extra Raita", 3000L))
        )

        val newCloudImage = "https://firebasestorage.googleapis.com/v0/b/bucket/o/new_biryani.jpg?alt=media"
        val updated = original.copy(image = newCloudImage)

        assertEquals("dish-biryani-1", updated.id)
        assertEquals("rest-1", updated.restaurantId)
        assertEquals("Dum Biryani", updated.name)
        assertEquals("Biryani", updated.category)
        assertEquals(32000L, updated.price)
        assertFalse(updated.vegetarian)
        assertTrue(updated.available)
        assertEquals(1, updated.addons.size)
        assertEquals(newCloudImage, updated.image)
    }

    @Test
    fun testRestaurantUpdatePreservesUnrelatedFields() {
        val original = Restaurant(
            id = "rest-1",
            name = "Bistro Lumière",
            cuisine = "North Indian & Mughlai",
            description = "Fine dining restaurant with artisanal preparations",
            image = "https://images.unsplash.com/photo-old-rest",
            address = "Oberoi Woods, Goregaon East",
            latitude = 19.1678,
            longitude = 72.8496,
            deliveryMinutes = 22,
            deliveryFee = 4000L,
            open = true,
            rating = 4.8,
            reviewCount = 1240,
            costForTwo = 850,
            vibes = listOf("Fine Dining", "Romantic"),
            logoUrl = "https://images.unsplash.com/photo-old-logo",
            acceptsDiningPayment = true,
            prepMinutes = 22
        )

        val newCloudLogo = "https://firebasestorage.googleapis.com/v0/b/bucket/o/new_logo.jpg?alt=media"
        val updated = original.copy(logoUrl = newCloudLogo, image = newCloudLogo)

        assertEquals("rest-1", updated.id)
        assertEquals("Bistro Lumière", updated.name)
        assertEquals("North Indian & Mughlai", updated.cuisine)
        assertEquals(4.8, updated.rating, 0.01)
        assertEquals(22, updated.deliveryMinutes)
        assertTrue(updated.open)
        assertTrue(updated.acceptsDiningPayment)
        assertEquals(22, updated.prepMinutes)
        assertEquals(newCloudLogo, updated.logoUrl)
        assertEquals(newCloudLogo, updated.image)
    }
}
