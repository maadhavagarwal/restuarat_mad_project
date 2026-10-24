package com.restaurant.mobile.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.restaurant.mobile.delivery.Profile
import com.restaurant.mobile.delivery.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Dedicated authoritative Firebase Authentication and Role Resolution Repository.
 *
 * Security Guarantee:
 * Role resolution queries the server-side Cloud Firestore collection `user_roles/{uid}`.
 * Users NEVER receive elevated privileges (WAITER, CHEF, ADMIN) through email substrings,
 * client-supplied parameters, or unsecured PINs.
 * Newly registered users default unconditionally to [UserRole.CUSTOMER].
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    /**
     * Resolves the authoritative role for a given Firebase User ID from Cloud Firestore.
     * If no document exists in `user_roles/{uid}`, defaults strictly to [UserRole.CUSTOMER].
     */
    suspend fun resolveAuthoritativeRole(uid: String): UserRole = withContext(Dispatchers.IO) {
        try {
            val roleDoc = firestore.collection(COLLECTION_USER_ROLES).document(uid).get().await()
            if (roleDoc.exists()) {
                val roleStr = roleDoc.getString("role")
                UserRole.fromString(roleStr)
            } else {
                UserRole.CUSTOMER
            }
        } catch (_: Exception) {
            UserRole.CUSTOMER
        }
    }

    /**
     * Authenticates an existing user via Firebase Auth email & password,
     * and securely resolves their role from Firestore.
     */
    suspend fun signInWithEmail(email: String, password: String): Result<Profile> =
        withContext(Dispatchers.IO) {
            try {
                val authResult = auth.signInWithEmailAndPassword(email.trim(), password).await()
                val user = authResult.user ?: throw IllegalStateException("Firebase user is null after sign in")
                val role = resolveAuthoritativeRole(user.uid)

                val profile = Profile(
                    id = user.uid,
                    name = user.displayName ?: email.substringBefore("@"),
                    email = user.email ?: email,
                    phone = user.phoneNumber ?: "",
                    role = role
                )
                Result.success(profile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Registers a new user with Firebase Auth.
     * New users are unconditionally assigned [UserRole.CUSTOMER] to prevent privilege escalation.
     */
    suspend fun registerWithEmail(email: String, password: String, name: String, phone: String = ""): Result<Profile> =
        withContext(Dispatchers.IO) {
            try {
                val authResult = auth.createUserWithEmailAndPassword(email.trim(), password).await()
                val user = authResult.user ?: throw IllegalStateException("Firebase user is null after registration")

                // Update display name
                val profileUpdate = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                    .setDisplayName(name.trim())
                    .build()
                user.updateProfile(profileUpdate).await()

                // Crucial: New users NEVER receive staff/admin roles automatically
                val profile = Profile(
                    id = user.uid,
                    name = name.trim().ifBlank { email.substringBefore("@") },
                    email = user.email ?: email,
                    phone = phone.trim(),
                    role = UserRole.CUSTOMER
                )

                // Persist base user document in users collection
                firestore.collection(COLLECTION_USERS).document(user.uid).set(
                    mapOf(
                        "uid" to user.uid,
                        "email" to user.email,
                        "displayName" to profile.name,
                        "phone" to profile.phone,
                        "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                ).await()

                Result.success(profile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Signs in anonymously if credentials are not provided (guest mode).
     */
    suspend fun signInAnonymously(): Result<Profile> = withContext(Dispatchers.IO) {
        try {
            val authResult = auth.signInAnonymously().await()
            val user = authResult.user ?: throw IllegalStateException("Anonymous user is null")
            val profile = Profile(
                id = user.uid,
                name = "Guest Diner",
                email = "guest_${user.uid.take(6)}@restaurant.firebase.io",
                role = UserRole.CUSTOMER
            )
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        auth.signOut()
    }

    companion object {
        const val COLLECTION_USER_ROLES = "user_roles"
        const val COLLECTION_USERS = "users"
    }
}
