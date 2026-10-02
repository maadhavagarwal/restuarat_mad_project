package com.restaurant.mobile.delivery

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class AuthResult(val token: String, val profile: Profile)

class DeliveryViewModel(app: Application) : AndroidViewModel(app) {
    private val session = SessionStore(app)
    val api = DeliveryApi()
    var starting by mutableStateOf(true)
        private set

    var busy by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
    var profile by mutableStateOf<Profile?>(null)
        private set

    var catalog by mutableStateOf(Catalog())
        private set

    var cart by mutableStateOf(Cart())
        private set

    var quote by mutableStateOf<Quote?>(null)
        private set

    var addresses by mutableStateOf<List<Address>>(emptyList())
        private set

    var orders by mutableStateOf<List<DeliveryOrder>>(emptyList())
        private set

    var favorites by mutableStateOf<List<String>>(emptyList())
        private set

    var reviews by mutableStateOf<List<Review>>(emptyList())
        private set

    var route by mutableStateOf("Home")
    var restaurantId by mutableStateOf("")
    var dishId by mutableStateOf("")
    var orderId by mutableStateOf("")
    var query by mutableStateOf("")
    var category by mutableStateOf("")
    var vegetarian by mutableStateOf(false)
    var sortPrice by mutableStateOf(false)
    private val checkoutPrefs =
        app.getSharedPreferences("restro-checkout", android.content.Context.MODE_PRIVATE)
    private var checkoutKey =
        checkoutPrefs.getString("key", null)
            ?: UUID.randomUUID().toString().also {
                checkoutPrefs.edit().putString("key", it).commit()
            }

    private fun resetCheckoutKey() {
        checkoutKey = UUID.randomUUID().toString()
        checkoutPrefs.edit().putString("key", checkoutKey).commit()
    }

    var editingAddress by mutableStateOf<Address?>(null)

    fun applyCoupon(code: String) = run {
        cart = api.call("/cart", "PUT", cart.copy(coupon = code))
        quote = null
        resetCheckoutKey()
    }

    init {
        run {
            api.token = session.load()
            catalog = api.call("/catalog")
            if (api.token != null) refreshAccount()
            starting = false
        }
    }

    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is ApiFailure && e.status == 409 && route == "Payment") {
                    runCatching {
                        cart = api.call("/cart")
                        orders = api.call("/orders")
                        if (cart.lines.isEmpty()) {
                            quote = null
                            route = "Orders"
                        } else {
                            quote = api.call("/quote")
                            route = "Checkout"
                        }
                    }
                }
                if (e is ApiFailure && e.status == 401) {
                    api.token = null
                    session.save(null)
                    profile = null
                    cart = Cart()
                    addresses = emptyList()
                    orders = emptyList()
                    favorites = emptyList()
                    quote = null
                    route = "Home"
                }
                error =
                    if (e is ApiFailure) e.message
                    else "Can't connect to Restro. Check your connection and try again."
            } finally {
                busy = false
                starting = false
            }
        }
    }

    private suspend fun refreshAccount() {
        profile = api.call("/profile")
        cart = api.call("/cart")
        addresses = api.call("/addresses")
        favorites = api.call("/favorites")
        orders = api.call("/orders")
    }

    fun refresh() = run {
        catalog = api.call("/catalog")
        if (api.token != null) refreshAccount()
    }

    fun auth(email: String, password: String, name: String, register: Boolean) = run {
        val response: AuthResult =
            api.call(
                if (register) "/auth/register" else "/auth/login",
                "POST",
                Credentials(email, password, name),
            )
        api.token = response.token
        session.save(response.token)
        profile = response.profile
        refreshAccount()
    }

    fun logout() = run {
        api.request("/auth/logout", "POST", null)
        session.save(null)
        api.token = null
        profile = null
        cart = Cart()
        quote = null
        addresses = emptyList()
        orders = emptyList()
        favorites = emptyList()
        route = "Home"
    }

    fun openRestaurant(id: String) {
        restaurantId = id
        category = ""
        query = ""
        route = "Restaurant"
        run { reviews = api.call("/reviews/$id") }
    }

    fun changeCart(lines: List<Line>, after: String? = null) = run {
        cart = api.call("/cart", "PUT", Cart(lines))
        quote = null
        resetCheckoutKey()
        if (after != null) route = after
    }

    fun add(dish: Dish, addons: List<String>, quantity: Int) {
        val match =
            cart.lines.indexOfFirst { it.dishId == dish.id && it.addons.toSet() == addons.toSet() }
        val lines = cart.lines.toMutableList()
        if (match >= 0)
            lines[match] =
                lines[match].copy(quantity = (lines[match].quantity + quantity).coerceAtMost(20))
        else lines.add(Line(dish.id, quantity, addons))
        changeCart(lines, "Cart")
    }

    fun loadQuote() = run {
        quote = api.call("/quote")
        addresses = api.call("/addresses")
        route = "Checkout"
    }

    fun checkout(addressId: String) = run {
        val total = quote?.total ?: return@run
        val order: DeliveryOrder =
            api.call("/orders", "POST", Checkout(addressId, "Cash on delivery", checkoutKey, total))
        cart = Cart()
        quote = null
        resetCheckoutKey()
        orderId = order.id
        orders = listOf(order) + orders.filterNot { it.id == order.id }
        route = "Confirmation"
    }

    fun track() = run {
        val updated: DeliveryOrder = api.call("/orders/$orderId")
        orders = orders.map { if (it.id == updated.id) updated else it }
    }

    fun saveAddress(a: Address) = run {
        api.call<Address>("/addresses", "PUT", a)
        addresses = api.call("/addresses")
        route = "Addresses"
    }

    fun deleteAddress(id: String) = run {
        api.request("/addresses/$id", "DELETE", null)
        addresses = api.call("/addresses")
    }

    fun favorite(id: String) = run {
        favorites = api.call("/favorites/$id", "PUT", mapOf("selected" to !favorites.contains(id)))
    }

    fun saveProfile(p: Profile) = run {
        profile = api.call("/profile", "PUT", p)
        if (route != "Settings") route = "Profile"
    }

    fun review(rating: Int, text: String) = run {
        api.call<Review>(
            "/reviews/$orderId",
            "POST",
            mapOf("rating" to rating.toString(), "text" to text),
        )
        route = "Orders"
    }

    fun back() {
        error = null
        route =
            when (route) {
                "Food",
                "Reviews" -> "Restaurant"
                "Payment" -> "Checkout"
                "Checkout" -> "Cart"
                "Tracking",
                "Confirmation",
                "Review" -> "Orders"
                "Edit address" -> "Addresses"
                "Addresses",
                "Settings",
                "Edit profile" -> "Profile"
                else -> "Home"
            }
    }
}
