package com.restaurant.mobile.delivery

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * Intelligent Rider Dispatch Engine.
 *
 * Implements:
 * 1. Fleet management with 5 live delivery riders in the service zone.
 * 2. Haversine nearest-rider matching algorithm from restaurant coordinates.
 * 3. Dynamic assignment with calculated ETAs and vehicle telemetry.
 * 4. Real-time Firebase Cloud Firestore synchronization.
 */
class RiderDispatchEngine(
    private val cloudEngine: FirebaseCloudEngine? = null
) {
    companion object {
        private const val TAG = "RiderDispatchEngine"

        /** Pre-seeded active fleet in Mumbai / Service Radius */
        val DEFAULT_FLEET = listOf(
            Rider(
                id = "rider-101",
                name = "Ramesh Kumar",
                phone = "+91 98112 34567",
                vehicleNumber = "MH-02-EM-8821",
                vehicleModel = "Hero Splendor Electric",
                rating = 4.9,
                totalDeliveries = 480,
                latitude = 19.1672,
                longitude = 72.8518,
                isAvailable = true,
                batteryPercent = 94
            ),
            Rider(
                id = "rider-102",
                name = "Vikram Singh",
                phone = "+91 98223 45678",
                vehicleNumber = "MH-02-BK-4412",
                vehicleModel = "Honda Activa 6G",
                rating = 4.8,
                totalDeliveries = 320,
                latitude = 19.1550,
                longitude = 72.8420,
                isAvailable = true,
                batteryPercent = 88
            ),
            Rider(
                id = "rider-103",
                name = "Suresh Patil",
                phone = "+91 98334 56789",
                vehicleNumber = "MH-02-EV-9011",
                vehicleModel = "Ather 450X Gen 3",
                rating = 4.95,
                totalDeliveries = 512,
                latitude = 19.1780,
                longitude = 72.8600,
                isAvailable = true,
                batteryPercent = 98
            ),
            Rider(
                id = "rider-104",
                name = "Amit Sharma",
                phone = "+91 98445 67890",
                vehicleNumber = "MH-02-TR-3390",
                vehicleModel = "TVS Jupiter ZX",
                rating = 4.7,
                totalDeliveries = 195,
                latitude = 19.1420,
                longitude = 72.8350,
                isAvailable = true,
                batteryPercent = 76
            ),
            Rider(
                id = "rider-105",
                name = "Pooja Verma",
                phone = "+91 98556 78901",
                vehicleNumber = "MH-02-ZE-1288",
                vehicleModel = "Ola S1 Pro",
                rating = 5.0,
                totalDeliveries = 245,
                latitude = 19.1850,
                longitude = 72.8680,
                isAvailable = true,
                batteryPercent = 91
            )
        )
    }

    private var localFleet: MutableList<Rider> = DEFAULT_FLEET.toMutableList()
    var lastDispatchLog: String = "Engine initialized with ${DEFAULT_FLEET.size} active riders."
        private set

    init {
        // Sync fleet to Cloud Firestore on initialization
        cloudEngine?.seedRidersToCloud(DEFAULT_FLEET)
        cloudEngine?.observeRidersRealTime { remoteRiders ->
            if (remoteRiders.isNotEmpty()) {
                localFleet = remoteRiders.toMutableList()
                Log.d(TAG, "Synced ${remoteRiders.size} riders from Cloud Firestore")
            }
        }
    }

    /**
     * Calculates Haversine distance between two GPS coordinates in kilometers.
     */
    fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val dist = r * c
        return (dist * 10.0).roundToInt() / 10.0
    }

    /**
     * Dynamically finds and assigns the nearest available rider to an order based on restaurant location.
     */
    fun assignNearestRider(
        order: DeliveryOrder,
        restaurantLat: Double,
        restaurantLng: Double,
        prepMinutes: Int = 22
    ): RiderAssignmentResult {
        // Filter available riders; if all are occupied, select from entire fleet
        val pool = localFleet.filter { it.isAvailable }.ifEmpty { localFleet }

        // Find rider with minimum Haversine distance to the restaurant
        val nearestRider = pool.minByOrNull { rider ->
            calculateDistanceKm(rider.latitude, rider.longitude, restaurantLat, restaurantLng)
        } ?: DEFAULT_FLEET.first()

        val destLat = order.address.latitude ?: restaurantLat
        val destLng = order.address.longitude ?: restaurantLng
        val routeDistKm = calculateDistanceKm(
            restaurantLat,
            restaurantLng,
            destLat,
            destLng
        ).coerceAtLeast(0.8)

        val clampedPrep = prepMinutes.coerceIn(20, 25)
        val travelMins = (routeDistKm * 3.5).roundToInt().coerceAtLeast(3)
        // Dynamic ETA calculation: 20-25 minutes restaurant prep time + user-to-restaurant travel time
        val etaMinutes = clampedPrep + travelMins
        val distanceKm = routeDistKm

        // Mark rider as occupied
        val updatedRider = nearestRider.copy(
            isAvailable = false,
            activeOrderId = order.id,
            lastUpdated = System.currentTimeMillis()
        )

        // Update local fleet state
        val idx = localFleet.indexOfFirst { it.id == nearestRider.id }
        if (idx >= 0) {
            localFleet[idx] = updatedRider
        }

        lastDispatchLog = "[DISPATCH SUCCESS] Assigned nearest partner ${updatedRider.name} " +
                "(${distanceKm} km away, ${updatedRider.vehicleModel} ${updatedRider.vehicleNumber}) " +
                "to Order #${order.id} with ETA ${etaMinutes} mins."

        Log.i(TAG, lastDispatchLog)

        // Sync assignment to Cloud Firestore
        cloudEngine?.updateRiderStatusInCloud(
            riderId = updatedRider.id,
            isAvailable = false,
            activeOrderId = order.id
        )

        return RiderAssignmentResult(
            rider = updatedRider,
            distanceKm = distanceKm,
            etaMinutes = etaMinutes,
            candidateCount = pool.size,
            dispatchLog = lastDispatchLog
        )
    }

    /**
     * Releases a rider when the delivery is completed.
     */
    fun releaseRider(riderId: String) {
        val idx = localFleet.indexOfFirst { it.id == riderId }
        if (idx >= 0) {
            val rider = localFleet[idx].copy(
                isAvailable = true,
                activeOrderId = null,
                lastUpdated = System.currentTimeMillis()
            )
            localFleet[idx] = rider
            cloudEngine?.updateRiderStatusInCloud(
                riderId = riderId,
                isAvailable = true,
                activeOrderId = null
            )
            Log.d(TAG, "Rider $riderId marked available.")
        }
    }

    /**
     * Returns current fleet snapshot with live availability.
     */
    fun getFleet(): List<Rider> = localFleet.toList()

    /**
     * Resets all riders to available.
     */
    fun resetFleet() {
        localFleet = DEFAULT_FLEET.toMutableList()
        localFleet.forEach { r ->
            cloudEngine?.updateRiderStatusInCloud(r.id, isAvailable = true, activeOrderId = null)
        }
    }
}
