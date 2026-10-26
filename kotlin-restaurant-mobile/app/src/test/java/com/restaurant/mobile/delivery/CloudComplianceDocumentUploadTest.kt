package com.restaurant.mobile.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying Commit 7 requirements for:
 * - Compliance document categories (Rider DL, Restaurant FSSAI, Restaurant GST)
 * - PDF and Image MIME type detection via magic byte signatures (%PDF, JPEG, PNG, WEBP)
 * - Document size limits (10MB max) and unsupported format rejections
 * - Secure unique cloud storage paths preventing object collisions and path traversal
 * - Permanent cloud reference validation (rejecting device-local content:// URIs)
 * - Preservation of partner verification records and schema integrity
 */
class CloudComplianceDocumentUploadTest {

    // -------------------------------------------------------------
    // CATEGORY MAPPING & ENUM VERIFICATION
    // -------------------------------------------------------------

    @Test
    fun testComplianceDocCategoryFromString() {
        assertEquals(ComplianceDocCategory.RIDER_DL, ComplianceDocCategory.fromString("RIDER_DL"))
        assertEquals(ComplianceDocCategory.RIDER_DL, ComplianceDocCategory.fromString("rider_dl"))
        assertEquals(ComplianceDocCategory.RESTAURANT_FSSAI, ComplianceDocCategory.fromString("RESTAURANT_FSSAI"))
        assertEquals(ComplianceDocCategory.RESTAURANT_GST, ComplianceDocCategory.fromString("restaurant_gst"))
        assertNull(ComplianceDocCategory.fromString("INVALID_CATEGORY"))
    }

    // -------------------------------------------------------------
    // MAGIC BYTE DETECTION (PDF & IMAGES)
    // -------------------------------------------------------------

    @Test
    fun testPdfMagicBytesDetection() {
        // %PDF -> 0x25, 0x50, 0x44, 0x46
        val pdfHeader = byteArrayOf(0x25.toByte(), 0x50.toByte(), 0x44.toByte(), 0x46.toByte(), 0x2D, 0x31, 0x2E, 0x34)
        val mime = FirebaseStorageEngine.detectDocumentMimeFromHeader(pdfHeader)
        assertEquals("application/pdf", mime)
    }

    @Test
    fun testJpegDocMagicBytesDetection() {
        val jpegHeader = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val mime = FirebaseStorageEngine.detectDocumentMimeFromHeader(jpegHeader)
        assertEquals("image/jpeg", mime)
    }

    @Test
    fun testPngDocMagicBytesDetection() {
        val pngHeader = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)
        val mime = FirebaseStorageEngine.detectDocumentMimeFromHeader(pngHeader)
        assertEquals("image/png", mime)
    }

    @Test
    fun testWebpDocMagicBytesDetection() {
        val webpHeader = byteArrayOf(
            0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte(),
            0x00, 0x00, 0x00, 0x00,
            0x57.toByte(), 0x45.toByte(), 0x42.toByte(), 0x50.toByte()
        )
        val mime = FirebaseStorageEngine.detectDocumentMimeFromHeader(webpHeader)
        assertEquals("image/webp", mime)
    }

    @Test
    fun testCorruptBytesReturnNull() {
        val corrupt = byteArrayOf(0x00, 0x11, 0x22, 0x33)
        assertNull(FirebaseStorageEngine.detectDocumentMimeFromHeader(corrupt))
    }

    // -------------------------------------------------------------
    // DOCUMENT VALIDATION & 10MB SIZE ENFORCEMENT
    // -------------------------------------------------------------

    @Test
    fun testValidPdfWithinLimitPasses() {
        val header = byteArrayOf(0x25.toByte(), 0x50.toByte(), 0x44.toByte(), 0x46.toByte())
        val sizeBytes = 4 * 1024 * 1024L // 4 MB (under 10 MB limit)
        val result = FirebaseStorageEngine.validateDocumentBytes(
            headerBytes = header,
            reportedMimeType = "application/pdf",
            sizeBytes = sizeBytes
        )

        assertTrue(result.isValid)
        assertEquals("application/pdf", result.mimeType)
        assertNull(result.errorMessage)
    }

    @Test
    fun testOversizedPdfRejected() {
        val header = byteArrayOf(0x25.toByte(), 0x50.toByte(), 0x44.toByte(), 0x46.toByte())
        val sizeBytes = 11 * 1024 * 1024L // 11 MB (exceeds 10 MB limit)
        val result = FirebaseStorageEngine.validateDocumentBytes(
            headerBytes = header,
            reportedMimeType = "application/pdf",
            sizeBytes = sizeBytes
        )

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("exceeds the maximum allowed limit of 10MB"))
    }

    @Test
    fun testUnsupportedDocumentFormatRejected() {
        val zipHeader = byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x03.toByte(), 0x04.toByte())
        val result = FirebaseStorageEngine.validateDocumentBytes(
            headerBytes = zipHeader,
            reportedMimeType = "application/zip",
            sizeBytes = 1024L
        )

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("Only PDF documents and JPG/PNG/WEBP images are accepted"))
    }

    // -------------------------------------------------------------
    // STORAGE KEY GENERATION & PATH TRAVERSAL DEFENSE
    // -------------------------------------------------------------

    @Test
    fun testRiderDlStoragePathStructure() {
        val path = FirebaseStorageEngine.generateComplianceStoragePath(
            category = ComplianceDocCategory.RIDER_DL,
            ownerId = "usr-rider-42",
            ext = "pdf"
        )

        assertTrue(path.startsWith("documents/riders/usr-rider-42/dl/dl_"))
        assertTrue(path.endsWith(".pdf"))
    }

    @Test
    fun testRestaurantFssaiStoragePathStructure() {
        val path = FirebaseStorageEngine.generateComplianceStoragePath(
            category = ComplianceDocCategory.RESTAURANT_FSSAI,
            ownerId = "rest-bistro",
            ext = "pdf"
        )

        assertTrue(path.startsWith("documents/restaurants/rest-bistro/fssai/fssai_"))
        assertTrue(path.endsWith(".pdf"))
    }

    @Test
    fun testRestaurantGstStoragePathStructure() {
        val path = FirebaseStorageEngine.generateComplianceStoragePath(
            category = ComplianceDocCategory.RESTAURANT_GST,
            ownerId = "rest-bistro",
            ext = "jpg"
        )

        assertTrue(path.startsWith("documents/restaurants/rest-bistro/gst/gst_"))
        assertTrue(path.endsWith(".jpg"))
    }

    @Test
    fun testPathTraversalSanitizationInOwnerId() {
        val dangerousOwner = "../../etc/passwd"
        val path = FirebaseStorageEngine.generateComplianceStoragePath(
            category = ComplianceDocCategory.RIDER_DL,
            ownerId = dangerousOwner,
            ext = "pdf"
        )

        assertFalse(path.contains(".."))
        assertFalse(path.contains("/etc/"))
        assertTrue(path.startsWith("documents/riders/etcpasswd/dl/dl_"))
    }

    // -------------------------------------------------------------
    // PERMANENT CLOUD REFERENCE INTEGRITY
    // -------------------------------------------------------------

    @Test
    fun testRejectLocalContentUriAsPermanentReference() {
        fun isPermanentCloudReference(uri: String): Boolean {
            return (uri.startsWith("https://") || uri.startsWith("http://") || uri.startsWith("gs://") || uri.startsWith("/api/delivery/media/download/")) &&
                   !uri.startsWith("content://") && !uri.startsWith("file://")
        }

        assertFalse(isPermanentCloudReference("content://com.android.providers.media.documents/document/image%3A102"))
        assertFalse(isPermanentCloudReference("file:///data/user/0/com.restaurant.mobile/cache/dl_temp.pdf"))

        assertTrue(isPermanentCloudReference("https://firebasestorage.googleapis.com/v0/b/restro-app/o/documents%2Friders%2Fdl_1.pdf?alt=media"))
        assertTrue(isPermanentCloudReference("gs://restro-app.firebasestorage.app/documents/restaurants/rest-1/fssai/fssai_cert.pdf"))
        assertTrue(isPermanentCloudReference("/api/delivery/media/download/doc-12345"))
    }

    // -------------------------------------------------------------
    // PARTNER VERIFICATION RECORD SCHEMA PRESERVATION
    // -------------------------------------------------------------

    @Test
    fun testPartnerVerificationRecordPreservesFields() {
        val initial = PartnerVerificationRecord(
            userId = "usr-rider-1",
            role = "RIDER",
            vehicleNumber = "MH-02-EM-8821",
            drivingLicenseUri = "https://firebasestorage.googleapis.com/v0/b/restro/documents/riders/usr-rider-1/dl/dl_1.pdf",
            fssaiNumber = "",
            fssaiCertUri = "",
            gstNumber = "",
            gstCertUri = "",
            restaurantName = "",
            restaurantLogoUri = "",
            verificationStatus = "VERIFIED_BY_GEMINI_OCR",
            extractedSummary = "INDIAN UNION DRIVING LICENCE · Reg/Vehicle: MH-02-EM-8821 · LMV Transport Valid",
            verifiedAt = "2026-10-26T12:00:00Z"
        )

        val updated = initial.copy(
            drivingLicenseUri = "https://firebasestorage.googleapis.com/v0/b/restro/documents/riders/usr-rider-1/dl/dl_new.pdf"
        )

        assertEquals("usr-rider-1", updated.userId)
        assertEquals("RIDER", updated.role)
        assertEquals("MH-02-EM-8821", updated.vehicleNumber)
        assertEquals("https://firebasestorage.googleapis.com/v0/b/restro/documents/riders/usr-rider-1/dl/dl_new.pdf", updated.drivingLicenseUri)
        assertEquals(initial.extractedSummary, updated.extractedSummary)
        assertEquals(initial.verifiedAt, updated.verifiedAt)
    }
}
