package com.restaurant.mobile.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.restaurant.mobile.delivery.AddOn
import com.restaurant.mobile.delivery.Dish
import com.restaurant.mobile.delivery.MockCatalog
import com.restaurant.mobile.delivery.Restaurant
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * Dedicated authoritative Cloud Firestore repository for Restaurant & Dish data.
 * Serves as the single shared source of truth across Customer, Waiter, Kitchen, and Admin clients.
 */
class FirebaseMenuRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    init {
        // Optimize Firestore client settings for offline persistence & cache
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()
        firestore.firestoreSettings = settings
    }

    /**
     * Seeds initial restaurant catalog and dishes into Firestore if the collection is empty.
     * Uses Dispatchers.IO to keep network and disk writes off the main thread.
     */
    suspend fun seedIfEmpty(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val existing = firestore.collection(COLLECTION_RESTAURANTS).limit(1).get().await()
            if (existing.isEmpty) {
                val sample = MockCatalog.sample
                for (restaurant in sample.restaurants) {
                    saveRestaurant(restaurant).getOrThrow()
                }
                for (dish in sample.dishes) {
                    addDish(dish).getOrThrow()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // RESTAURANT QUERIES & REAL-TIME LISTENERS
    // -------------------------------------------------------------

    /**
     * Real-time Firestore snapshot listener for all available restaurants.
     * Unregisters the listener automatically when the calling coroutine scope is cancelled.
     */
    fun observeRestaurants(): Flow<List<Restaurant>> = callbackFlow {
        val listenerRegistration = firestore.collection(COLLECTION_RESTAURANTS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val restaurants = snapshot.documents.mapNotNull { doc -> docToRestaurant(doc) }
                    trySend(restaurants)
                }
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }

    suspend fun getRestaurants(): List<Restaurant> = withContext(Dispatchers.IO) {
        val snapshot = firestore.collection(COLLECTION_RESTAURANTS).get().await()
        snapshot.documents.mapNotNull { docToRestaurant(it) }
    }

    suspend fun saveRestaurant(restaurant: Restaurant): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val data = hashMapOf(
                "id" to restaurant.id,
                "name" to restaurant.name,
                "cuisine" to restaurant.cuisine,
                "description" to restaurant.description,
                "image" to restaurant.image,
                "address" to restaurant.address,
                "latitude" to restaurant.latitude,
                "longitude" to restaurant.longitude,
                "deliveryMinutes" to restaurant.deliveryMinutes,
                "deliveryFee" to restaurant.deliveryFee,
                "open" to restaurant.open,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection(COLLECTION_RESTAURANTS)
                .document(restaurant.id)
                .set(data)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // DISH QUERIES & REAL-TIME LISTENERS
    // -------------------------------------------------------------

    /**
     * Genuine real-time Firestore snapshot listener for dishes under a specific restaurant.
     * Automatically emits updates whenever an administrator adds, edits, reprices,
     * enables, or disables a dish.
     */
    fun observeDishes(restaurantId: String): Flow<List<Dish>> = callbackFlow {
        val listenerRegistration = firestore.collection(COLLECTION_RESTAURANTS)
            .document(restaurantId)
            .collection(COLLECTION_DISHES)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val dishes = snapshot.documents.mapNotNull { doc -> docToDish(doc) }
                    trySend(dishes)
                }
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }

    /**
     * Real-time listener for all dishes across all restaurants (useful for Staff POS / Global Catalog).
     */
    fun observeAllDishes(): Flow<List<Dish>> = callbackFlow {
        val listenerRegistration = firestore.collectionGroup(COLLECTION_DISHES)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val dishes = snapshot.documents.mapNotNull { doc -> docToDish(doc) }
                    trySend(dishes)
                }
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }

    suspend fun getAllDishesOnce(): List<Dish> = withContext(Dispatchers.IO) {
        try {
            val snap = firestore.collectionGroup("dishes").get().await()
            snap.documents.mapNotNull { docToDish(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getDishes(restaurantId: String): List<Dish> = withContext(Dispatchers.IO) {
        val snapshot = firestore.collection(COLLECTION_RESTAURANTS)
            .document(restaurantId)
            .collection(COLLECTION_DISHES)
            .get()
            .await()
        snapshot.documents.mapNotNull { docToDish(it) }
    }

    // -------------------------------------------------------------
    // AUTHORITATIVE MENU MUTATIONS (ADMIN ONLY)
    // -------------------------------------------------------------

    suspend fun addDish(dish: Dish): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val data = dishToMap(dish)
            firestore.collection(COLLECTION_RESTAURANTS)
                .document(dish.restaurantId)
                .collection(COLLECTION_DISHES)
                .document(dish.id)
                .set(data)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateDish(dish: Dish): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val data = dishToMap(dish)
            firestore.collection(COLLECTION_RESTAURANTS)
                .document(dish.restaurantId)
                .collection(COLLECTION_DISHES)
                .document(dish.id)
                .set(data)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateDishAvailability(restaurantId: String, dishId: String, available: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                firestore.collection(COLLECTION_RESTAURANTS)
                    .document(restaurantId)
                    .collection(COLLECTION_DISHES)
                    .document(dishId)
                    .update(
                        mapOf(
                            "available" to available,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    .await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun updateDishPrice(restaurantId: String, dishId: String, pricePaise: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                require(pricePaise >= 0) { "Price cannot be negative" }
                firestore.collection(COLLECTION_RESTAURANTS)
                    .document(restaurantId)
                    .collection(COLLECTION_DISHES)
                    .document(dishId)
                    .update(
                        mapOf(
                            "price" to pricePaise,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    .await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun deleteDish(restaurantId: String, dishId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                firestore.collection(COLLECTION_RESTAURANTS)
                    .document(restaurantId)
                    .collection(COLLECTION_DISHES)
                    .document(dishId)
                    .delete()
                    .await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // -------------------------------------------------------------
    // SERIALIZATION & DESERIALIZATION HELPERS
    // -------------------------------------------------------------

    companion object {
        const val COLLECTION_RESTAURANTS = "restaurants"
        const val COLLECTION_DISHES = "dishes"

        fun docToRestaurant(doc: DocumentSnapshot): Restaurant? {
            if (!doc.exists()) return null
            val id = doc.getString("id") ?: doc.id
            val name = doc.getString("name") ?: return null
            val cuisine = doc.getString("cuisine") ?: ""
            val description = doc.getString("description") ?: ""
            val image = doc.getString("image") ?: ""
            val address = doc.getString("address") ?: ""
            val latitude = doc.getDouble("latitude") ?: 0.0
            val longitude = doc.getDouble("longitude") ?: 0.0
            val deliveryMinutes = doc.getLong("deliveryMinutes")?.toInt() ?: 30
            val deliveryFee = doc.getLong("deliveryFee") ?: 3000L
            val open = doc.getBoolean("open") ?: true

            return Restaurant(
                id = id,
                name = name,
                cuisine = cuisine,
                description = description,
                image = image,
                address = address,
                latitude = latitude,
                longitude = longitude,
                deliveryMinutes = deliveryMinutes,
                deliveryFee = deliveryFee,
                open = open
            )
        }

        fun docToDish(doc: DocumentSnapshot): Dish? {
            if (!doc.exists()) return null
            val id = doc.getString("id") ?: doc.id
            val restaurantId = doc.getString("restaurantId") ?: ""
            val category = doc.getString("category") ?: "Main Course"
            val name = doc.getString("name") ?: return null
            val description = doc.getString("description") ?: ""
            val price = doc.getLong("price") ?: 0L
            val image = doc.getString("image") ?: ""
            val vegetarian = doc.getBoolean("vegetarian") ?: false
            val available = doc.getBoolean("available") ?: true

            @Suppress("UNCHECKED_CAST")
            val rawAddons = doc.get("addons") as? List<Map<String, Any>> ?: emptyList()
            val addons = rawAddons.mapNotNull { m ->
                val addonId = m["id"] as? String ?: return@mapNotNull null
                val addonName = m["name"] as? String ?: ""
                val addonPrice = (m["price"] as? Number)?.toLong() ?: 0L
                AddOn(addonId, addonName, addonPrice)
            }

            return Dish(
                id = id,
                restaurantId = restaurantId,
                category = category,
                name = name,
                description = description,
                price = price,
                image = image,
                vegetarian = vegetarian,
                available = available,
                addons = addons
            )
        }

        fun dishToMap(dish: Dish): Map<String, Any> {
            val addonsList = dish.addons.map {
                mapOf(
                    "id" to it.id,
                    "name" to it.name,
                    "price" to it.price
                )
            }
            return hashMapOf(
                "id" to dish.id,
                "restaurantId" to dish.restaurantId,
                "category" to dish.category,
                "name" to dish.name,
                "description" to dish.description,
                "price" to dish.price,
                "image" to dish.image,
                "vegetarian" to dish.vegetarian,
                "available" to dish.available,
                "addons" to addonsList,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        }
    }
}
