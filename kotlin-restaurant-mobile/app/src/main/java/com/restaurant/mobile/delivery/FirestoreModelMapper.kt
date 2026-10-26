package com.restaurant.mobile.delivery

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Instant

/**
 * Reusable, explicit document mappers for converting Firestore Document Maps into strongly-typed
 * domain entities and vice-versa, with robust fallback protection against malformed or partial fields.
 */
object FirestoreModelMapper {

    private val gson = Gson()

    // -------------------------------------------------------------
    // ORDERS
    // -------------------------------------------------------------

    fun mapToDeliveryOrder(docId: String, data: Map<String, Any?>): DeliveryOrder? {
        return try {
            val id = (data["id"] as? String)?.ifBlank { null } ?: docId
            val restId = (data["restaurantId"] as? String) ?: ""
            val restName = (data["restaurantName"] as? String) ?: ""
            val status = (data["status"] as? String) ?: DeliveryOrderStatus.CONFIRMED
            val paymentMethod = (data["paymentMethod"] as? String) ?: "Cash on delivery"
            val paymentStatus = (data["paymentStatus"] as? String) ?: "Due on delivery"
            val userId = (data["userId"] as? String) ?: ""

            // Address parsing
            val addrJson = data["address"] as? String
            val parsedAddress = if (!addrJson.isNullOrBlank()) {
                runCatching { gson.fromJson(addrJson, Address::class.java) }.getOrNull()
            } else null
            val address = parsedAddress ?: Address(
                id = "addr-default",
                label = "Home",
                line = (data["deliveryAddress"] as? String) ?: "Delivery Address",
                city = "Mumbai",
                postcode = "400062",
                phone = (data["userPhone"] as? String) ?: "+91 98765 43210"
            )

            // Quote parsing
            val quoteJson = data["quote"] as? String
            val totalRaw = (data["total"] as? Number)?.toLong() ?: 500L
            val parsedQuote = if (!quoteJson.isNullOrBlank()) {
                runCatching { gson.fromJson(quoteJson, Quote::class.java) }.getOrNull()
            } else null
            val quote = parsedQuote ?: Quote(
                restaurantId = restId,
                lines = emptyList(),
                subtotal = totalRaw * 100L,
                tax = 2500L,
                delivery = 3500L,
                discount = 0L,
                total = totalRaw * 100L
            )

            // Events parsing
            val eventsJson = data["events"] as? String
            val eventsType = object : TypeToken<List<Event>>() {}.type
            val events: List<Event> = if (!eventsJson.isNullOrBlank()) {
                runCatching { gson.fromJson<List<Event>>(eventsJson, eventsType) }.getOrNull() ?: listOf(Event(status))
            } else {
                listOf(Event(status))
            }

            DeliveryOrder(
                id = id,
                restaurantId = restId,
                restaurantName = restName,
                address = address,
                quote = quote,
                paymentMethod = paymentMethod,
                paymentStatus = paymentStatus,
                status = status,
                events = events,
                userId = userId,
                riderId = (data["riderId"] as? String) ?: "",
                riderName = (data["riderName"] as? String) ?: "",
                riderPhone = (data["riderPhone"] as? String) ?: "",
                riderVehicle = (data["riderVehicle"] as? String) ?: "",
                riderRating = (data["riderRating"] as? Number)?.toDouble() ?: 4.8,
                riderLatitude = (data["riderLatitude"] as? Number)?.toDouble() ?: 0.0,
                riderLongitude = (data["riderLongitude"] as? Number)?.toDouble() ?: 0.0
            )
        } catch (_: Exception) {
            null
        }
    }

    // -------------------------------------------------------------
    // TABLE BOOKINGS
    // -------------------------------------------------------------

    fun mapToTableBooking(docId: String, data: Map<String, Any?>): TableBooking? {
        return try {
            val id = (data["id"] as? String)?.ifBlank { null } ?: docId
            val restId = (data["restaurantId"] as? String) ?: ""
            val restName = (data["restaurantName"] as? String) ?: ""
            val tableNum = (data["tableNumber"] as? String) ?: "T1"
            val uid = (data["userId"] as? String) ?: "guest"
            val uName = (data["userName"] as? String) ?: "Guest"
            val uPhone = (data["userPhone"] as? String) ?: "+91 98765 43210"
            val party = (data["partySize"] as? Number)?.toInt() ?: 2
            val bDate = (data["bookingDate"] as? String) ?: "Today"
            val slot = (data["timeSlot"] as? String) ?: "7:00 PM"
            val area = (data["seatingArea"] as? String) ?: "Main Dining Room"
            val special = (data["specialRequests"] as? String) ?: ""
            val statusStr = (data["status"] as? String) ?: TableBookingStatus.CONFIRMED
            val createdAt = (data["createdAt"] as? String) ?: Instant.now().toString()

            TableBooking(
                id = id,
                restaurantId = restId,
                restaurantName = restName,
                tableNumber = tableNum,
                userId = uid,
                userName = uName,
                userPhone = uPhone,
                partySize = party,
                bookingDate = bDate,
                timeSlot = slot,
                seatingArea = area,
                specialRequests = special,
                status = statusStr,
                createdAt = createdAt
            )
        } catch (_: Exception) {
            null
        }
    }

    // -------------------------------------------------------------
    // RESTAURANTS
    // -------------------------------------------------------------

    fun mapToRestaurant(docId: String, data: Map<String, Any?>): Restaurant? {
        return try {
            val id = (data["id"] as? String)?.ifBlank { null } ?: docId
            val name = (data["name"] as? String) ?: return null
            val cuisine = (data["cuisine"] as? String) ?: ""
            val desc = (data["description"] as? String) ?: ""
            val image = (data["image"] as? String) ?: ""
            val address = (data["address"] as? String) ?: "Mumbai"
            val lat = (data["latitude"] as? Number)?.toDouble() ?: 19.1663
            val lng = (data["longitude"] as? Number)?.toDouble() ?: 72.8526
            val delMins = (data["deliveryMinutes"] as? Number)?.toInt() ?: 28
            val delFee = (data["deliveryFee"] as? Number)?.toLong() ?: 4000L
            val open = (data["open"] as? Boolean) ?: true
            val rating = (data["rating"] as? Number)?.toDouble() ?: 4.8
            val tableBooking = (data["tableBookingAvailable"] as? Boolean) ?: true
            val vibes = (data["vibes"] as? List<*>)?.mapNotNull { it as? String } ?: listOf("Fine Dining", "Romantic")

            Restaurant(
                id = id,
                name = name,
                cuisine = cuisine,
                description = desc,
                image = image,
                address = address,
                latitude = lat,
                longitude = lng,
                deliveryMinutes = delMins,
                deliveryFee = delFee,
                open = open,
                rating = rating,
                vibes = vibes,
                tableBookingAvailable = tableBooking
            )
        } catch (_: Exception) {
            null
        }
    }

    // -------------------------------------------------------------
    // DISHES
    // -------------------------------------------------------------

    fun mapToDish(docId: String, data: Map<String, Any?>): Dish? {
        return try {
            val id = (data["id"] as? String)?.ifBlank { null } ?: docId
            val restId = (data["restaurantId"] as? String) ?: "rest-1"
            val category = (data["category"] as? String) ?: "Mains"
            val name = (data["name"] as? String) ?: return null
            val desc = (data["description"] as? String) ?: ""
            val price = (data["price"] as? Number)?.toLong() ?: 24900L
            val image = (data["image"] as? String) ?: ""
            val veg = (data["vegetarian"] as? Boolean) ?: true
            val avail = (data["available"] as? Boolean) ?: true
            val addonsJson = data["addons"] as? String
            val addons: List<AddOn> = if (!addonsJson.isNullOrBlank()) {
                val listType = object : TypeToken<List<AddOn>>() {}.type
                runCatching { gson.fromJson<List<AddOn>>(addonsJson, listType) }.getOrNull() ?: emptyList()
            } else emptyList()

            Dish(
                id = id,
                restaurantId = restId,
                category = category,
                name = name,
                description = desc,
                price = price,
                image = image,
                vegetarian = veg,
                available = avail,
                addons = addons
            )
        } catch (_: Exception) {
            null
        }
    }

    // -------------------------------------------------------------
    // ADDRESSES
    // -------------------------------------------------------------

    fun mapToAddress(docId: String, data: Map<String, Any?>): Address? {
        return try {
            val id = (data["id"] as? String)?.ifBlank { null } ?: docId
            val label = (data["label"] as? String) ?: "Home"
            val line = (data["line"] as? String) ?: ""
            val city = (data["city"] as? String) ?: ""
            val postcode = (data["postcode"] as? String) ?: ""
            val phone = (data["phone"] as? String) ?: ""
            val lat = (data["latitude"] as? Number)?.toDouble()
            val lng = (data["longitude"] as? Number)?.toDouble()
            Address(id, label, line, city, postcode, phone, lat, lng)
        } catch (_: Exception) {
            null
        }
    }
}
