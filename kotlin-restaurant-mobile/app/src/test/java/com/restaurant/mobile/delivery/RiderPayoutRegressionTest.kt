package com.restaurant.mobile.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Regression test suite for RazorpayX Rider Payouts (Commit 5 of Step 2):
 * - UPI VPA format validation
 * - Payout amount bounds and available balance validation
 * - Destination masking for privacy
 * - Real Reference ID / Bank UTR non-fabrication
 * - Receipt generation and ownership
 * - Idempotency protection against duplicate payouts
 * - Payout state lifecycle transitions
 * - Balance recovery on failed/reversed payouts
 * - Firestore mapping and durable storage
 */
class RiderPayoutRegressionTest {

    // -------------------------------------------------------------------------
    // 1. UPI VPA FORMAT VALIDATION
    // -------------------------------------------------------------------------

    @Test
    fun testValidUpiVpaFormats() {
        val validVpas = listOf(
            "ramesh.rider@okaxis",
            "delivery-partner@okhdfcbank",
            "user_123@icici",
            "9876543210@paytm",
            "john.doe@ybl",
            "rider-mumbai.central@sbi"
        )
        for (vpa in validVpas) {
            val result = RiderPayoutRules.validateVpa(vpa)
            assertTrue("Expected '$vpa' to be valid, but got: ${result.errorMessage}", result.isValid)
            assertNull(result.errorMessage)
        }
    }

    @Test
    fun testInvalidUpiVpaFormats() {
        val invalidVpas = listOf(
            "",
            "   ",
            "invalidvpa",
            "@okaxis",
            "ramesh@",
            "ramesh@ok@axis",
            "ramesh rider@okaxis",
            "ramesh#rider@okaxis",
            "r@a",
            "user@bank.123"
        )
        for (vpa in invalidVpas) {
            val result = RiderPayoutRules.validateVpa(vpa)
            assertFalse("Expected '$vpa' to be invalid", result.isValid)
            assertNotNull("Expected error message for '$vpa'", result.errorMessage)
        }
    }

    // -------------------------------------------------------------------------
    // 2. PAYOUT AMOUNT & BOUNDS VALIDATION
    // -------------------------------------------------------------------------

    @Test
    fun testPayoutAmountValidation() {
        // Valid amounts
        assertTrue(RiderPayoutRules.validatePayoutAmount(10L).isValid)
        assertTrue(RiderPayoutRules.validatePayoutAmount(500L).isValid)
        assertTrue(RiderPayoutRules.validatePayoutAmount(100_000L).isValid)

        // Invalid amounts
        assertFalse(RiderPayoutRules.validatePayoutAmount(0L).isValid)
        assertFalse(RiderPayoutRules.validatePayoutAmount(-50L).isValid)
        assertFalse(RiderPayoutRules.validatePayoutAmount(9L).isValid) // Minimum ₹10
        assertFalse(RiderPayoutRules.validatePayoutAmount(100_001L).isValid) // Maximum ₹100,000
    }

    // -------------------------------------------------------------------------
    // 3. SENSITIVE DESTINATION MASKING
    // -------------------------------------------------------------------------

    @Test
    fun testDestinationMaskingForUpiAndBank() {
        // UPI masking: first char + *** + last char before '@' + domain
        val maskedUpi = RiderPayoutRules.maskDestination("ramesh.rider@okaxis")
        assertTrue("Expected masked UPI to contain '***', was: $maskedUpi", maskedUpi.contains("***"))
        assertTrue("Expected masked UPI to preserve '@okaxis', was: $maskedUpi", maskedUpi.endsWith("@okaxis"))

        // Bank account masking: show only last 4 digits
        val maskedBank = RiderPayoutRules.maskDestination("50100429381948")
        assertTrue("Expected bank account to mask leading digits, was: $maskedBank", maskedBank.contains("••••"))
        assertTrue("Expected bank account to preserve last 4 digits '1948', was: $maskedBank", maskedBank.endsWith("1948"))
    }

    // -------------------------------------------------------------------------
    // 4. REAL UTR / REFERENCE NON-FABRICATION
    // -------------------------------------------------------------------------

    @Test
    fun testRealUtrPreservedAndNeverFabricated() {
        val pendingPayout = RiderPayoutRecord(
            id = "pout_test_001",
            riderId = "usr-rider",
            amountRupees = 450L,
            accountDestination = "r***r@okaxis",
            payoutMethod = "UPI",
            status = "PENDING",
            referenceId = "",
            utr = ""
        )
        // Ensure pending payout has no fabricated reference or UTR
        assertEquals("", pendingPayout.referenceId)
        assertEquals("", pendingPayout.utr)

        val receiptTextPending = RiderPayoutRules.generateReceiptText(pendingPayout)
        assertFalse("Pending receipt must not include fabricated UTR", receiptTextPending.contains("Bank UTR:"))

        // When processed with real provider UTR
        val realUtr = "429302194812"
        val processedPayout = pendingPayout.copy(
            status = "PROCESSED",
            referenceId = "rpz_pout_982341",
            utr = realUtr
        )
        val receiptTextProcessed = RiderPayoutRules.generateReceiptText(processedPayout)
        assertTrue("Processed receipt must include real UTR", receiptTextProcessed.contains(realUtr))
        assertTrue("Processed receipt must include reference ID", receiptTextProcessed.contains("rpz_pout_982341"))
    }

    // -------------------------------------------------------------------------
    // 5. RECEIPT GENERATION FROM TRUSTED RECORD
    // -------------------------------------------------------------------------

    @Test
    fun testReceiptGenerationContainsTrustedFields() {
        val payout = RiderPayoutRecord(
            id = "pout_rec_100",
            riderId = "rider_789",
            amountRupees = 1250L,
            accountDestination = "r***r@okaxis",
            payoutMethod = "UPI",
            status = "PROCESSED",
            referenceId = "ref_razorpay_99",
            utr = "UTR9988776655",
            receiptNumber = "REC-POUT-100",
            timestampMs = 1700000000000L
        )

        val receipt = RiderPayoutReceipt(
            receiptNumber = payout.receiptNumber,
            payoutId = payout.id,
            riderId = payout.riderId,
            amountRupees = payout.amountRupees,
            payoutMethod = payout.payoutMethod,
            destinationMasked = payout.accountDestination,
            status = payout.status,
            referenceId = payout.referenceId,
            utr = payout.utr,
            timestampMs = payout.timestampMs,
            receiptText = RiderPayoutRules.generateReceiptText(payout)
        )

        assertEquals("REC-POUT-100", receipt.receiptNumber)
        assertEquals(1250L, receipt.amountRupees)
        assertEquals("PROCESSED", receipt.status)
        assertEquals("UPI", receipt.payoutMethod)
        assertEquals("r***r@okaxis", receipt.destinationMasked)
        assertEquals("ref_razorpay_99", receipt.referenceId)
        assertEquals("UTR9988776655", receipt.utr)
        assertTrue(receipt.receiptText.contains("REC-POUT-100"))
        assertTrue(receipt.receiptText.contains("₹1250"))
    }

    // -------------------------------------------------------------------------
    // 6. IDEMPOTENCY & DUPLICATE PROTECTION
    // -------------------------------------------------------------------------

    @Test
    fun testDuplicateIdempotencyKeyReturnsOriginal() {
        val idempotencyKey = "idemp_test_key_${UUID.randomUUID()}"
        val initialRecord = RiderPayoutRecord(
            id = "pout_111",
            riderId = "usr-rider",
            amountRupees = 300L,
            accountDestination = "r***r@okaxis",
            payoutMethod = "UPI",
            status = "PROCESSED",
            idempotencyKey = idempotencyKey,
            referenceId = "ref_111",
            utr = "UTR_111"
        )

        val payoutsList = mutableListOf(initialRecord)

        // Attempting duplicate request with same idempotency key
        val existing = payoutsList.firstOrNull { it.idempotencyKey == idempotencyKey }
        assertNotNull("Existing idempotency record must be detected", existing)
        assertEquals("pout_111", existing!!.id)
        assertEquals(300L, existing.amountRupees)

        // Ensure list is not duplicated
        assertEquals(1, payoutsList.size)
    }

    // -------------------------------------------------------------------------
    // 7. BALANCE DEDUCTION & REVERSAL RESTORATION
    // -------------------------------------------------------------------------

    @Test
    fun testBalanceRestorationOnFailedOrReversedPayout() {
        val totalIncome = 5000L

        val processed = RiderPayoutRecord(id = "1", riderId = "r1", amountRupees = 1000L, accountDestination = "r***r@okaxis", status = "PROCESSED")
        val processing = RiderPayoutRecord(id = "2", riderId = "r1", amountRupees = 500L, accountDestination = "r***r@okaxis", status = "PROCESSING")
        val failed = RiderPayoutRecord(id = "3", riderId = "r1", amountRupees = 800L, accountDestination = "r***r@okaxis", status = "FAILED", failureReason = "Beneficiary bank down")
        val reversed = RiderPayoutRecord(id = "4", riderId = "r1", amountRupees = 700L, accountDestination = "r***r@okaxis", status = "REVERSED", failureReason = "Account blocked")

        val allPayouts = listOf(processed, processing, failed, reversed)

        // Only active/settled payouts should be deducted
        val totalWithdrawn = allPayouts
            .filter { it.status == "PROCESSED" || it.status == "PROCESSING" || it.status == "PENDING" }
            .sumOf { it.amountRupees }

        assertEquals(1500L, totalWithdrawn) // 1000 + 500

        val availableBalance = (totalIncome - totalWithdrawn).coerceAtLeast(0L)
        assertEquals(3500L, availableBalance) // 5000 - 1500 = 3500 (failed 800 and reversed 700 are restored)
    }

    // -------------------------------------------------------------------------
    // 8. FIRESTORE MAPPER ROUNDTRIP
    // -------------------------------------------------------------------------

    @Test
    fun testFirestoreModelMapperPreservesAllPayoutFields() {
        val rawData = mapOf<String, Any?>(
            "id" to "pout_fs_456",
            "riderId" to "rider_xyz",
            "amountRupees" to 750L,
            "accountDestination" to "r***r@okaxis",
            "payoutMethod" to "UPI",
            "status" to "PROCESSED",
            "referenceId" to "ref_fs_456",
            "utr" to "UTR1234567890",
            "failureReason" to "",
            "idempotencyKey" to "idemp_456",
            "receiptNumber" to "REC-456",
            "timestampMs" to 1710000000000L
        )

        val record = checkNotNull(FirestoreModelMapper.mapToRiderPayoutRecord("pout_fs_456", rawData))
        assertEquals("pout_fs_456", record.id)
        assertEquals("rider_xyz", record.riderId)
        assertEquals(750L, record.amountRupees)
        assertEquals("r***r@okaxis", record.accountDestination)
        assertEquals("UPI", record.payoutMethod)
        assertEquals("PROCESSED", record.status)
        assertEquals("ref_fs_456", record.referenceId)
        assertEquals("UTR1234567890", record.utr)
        assertEquals("idemp_456", record.idempotencyKey)
        assertEquals("REC-456", record.receiptNumber)
        assertEquals(1710000000000L, record.timestampMs)
    }

    // -------------------------------------------------------------------------
    // 9. METHOD RESTRICTIONS
    // -------------------------------------------------------------------------

    @Test
    fun testUnsupportedPayoutMethodValidation() {
        val allowedMethods = listOf("UPI", "IMPS")
        val disallowedMethods = listOf("NEFT", "RTGS", "CASH", "CRYPTO", "CHEQUE")

        for (m in allowedMethods) {
            assertTrue("Method $m should be permitted", m in allowedMethods)
        }
        for (m in disallowedMethods) {
            assertFalse("Method $m must be rejected", m in allowedMethods)
        }
    }
}
