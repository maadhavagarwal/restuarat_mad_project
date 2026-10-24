package com.restaurant.mobile.delivery

import kotlin.math.min

/**
 * Authoritative Coupon Business Rules and Calculation Engine.
 * Single source of truth for discount validation, minimum order requirements,
 * and currency-safe monetary representation across the application.
 */
object CouponRules {

    val VALID_COUPONS = setOf("WELCOME50", "FEAST", "FEAST100", "FREEDEL")

    /**
     * Calculates the discount in paise for a given coupon code, subtotal, and delivery fee.
     * All values in paise (₹1.00 = 100 paise).
     */
    fun calculateDiscount(couponCode: String, subtotalPaise: Long, deliveryFeePaise: Long): Long {
        if (subtotalPaise <= 0L) return 0L
        return when (couponCode.trim().uppercase()) {
            "WELCOME50" -> {
                // 50% discount up to max ₹50 (5000 paise)
                min(5000L, subtotalPaise / 2L)
            }
            "FEAST" -> {
                // Flat ₹50 off (5000 paise) on orders of ₹200 (20000 paise) or more
                if (subtotalPaise >= 20000L) 5000L else 0L
            }
            "FEAST100" -> {
                // Flat ₹100 off (10000 paise) on orders of ₹400 (40000 paise) or more;
                // or ₹50 off (5000 paise) on orders between ₹200 and ₹400
                if (subtotalPaise >= 40000L) 10000L
                else if (subtotalPaise >= 20000L) 5000L
                else 0L
            }
            "FREEDEL" -> {
                // Delivery fee waiver discount exactly equals the delivery charge
                deliveryFeePaise.coerceAtLeast(0L)
            }
            else -> 0L
        }
    }

    /**
     * Validates if a coupon code can be applied given the current subtotal.
     * Returns a pair of (isValid, errorMessage).
     */
    fun validateCoupon(couponCode: String, subtotalPaise: Long): Pair<Boolean, String?> {
        val clean = couponCode.trim().uppercase()
        if (clean.isBlank()) {
            return Pair(false, "Coupon code cannot be empty")
        }
        if (clean !in VALID_COUPONS) {
            return Pair(false, "Invalid coupon code '$clean'. Available: WELCOME50, FEAST, FEAST100, FREEDEL")
        }
        if (clean == "FEAST" && subtotalPaise < 20000L) {
            val shortBy = (20000L - subtotalPaise) / 100L
            return Pair(false, "Coupon 'FEAST' requires a minimum order of ₹200. Add ₹$shortBy more to apply.")
        }
        if (clean == "FEAST100" && subtotalPaise < 20000L) {
            val shortBy = (20000L - subtotalPaise) / 100L
            return Pair(false, "Coupon 'FEAST100' requires min order of ₹200 (₹400 for ₹100 off). Add ₹$shortBy more.")
        }
        return Pair(true, null)
    }
}
