package com.restaurant.mobile.delivery

import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import java.util.concurrent.ConcurrentHashMap

/**
 * Reusable Subscription Registry and Sync Foundation for Cloud Firestore.
 *
 * Responsibilities:
 * 1. Safe lifecycle management of [ListenerRegistration] tokens (keyed by unique channel id).
 * 2. Deduplication of snapshot listeners: cancels previous subscription before rebinding.
 * 3. Scope isolation & cancellation: supports clearing user-specific subscriptions on logout/switch
 *    while preserving global/catalog subscriptions if desired, or cancelling all.
 * 4. Document-level change tracking (Added, Modified, Removed) with change suppression to prevent
 *    redundant processing of unchanged payloads.
 * 5. Error handling and safe fallback routing without crashing the UI.
 * 6. Thread-safe concurrency.
 */
class FirestoreSubscriptionManager {

    companion object {
        private const val TAG = "FirestoreSubMgr"

        private fun logD(tag: String, msg: String) {
            runCatching { Log.d(tag, msg) }
        }

        private fun logI(tag: String, msg: String) {
            runCatching { Log.i(tag, msg) }
        }

        private fun logW(tag: String, msg: String) {
            runCatching { Log.w(tag, msg) }
        }
    }

    /**
     * Scope of a synchronization channel:
     * - [GLOBAL]: Catalog, restaurants, dishes, public metadata (survives account logout)
     * - [USER_SCOPED]: Orders, table bookings, addresses, profile (cancelled on logout / account switch)
     */
    enum class ChannelScope {
        GLOBAL,
        USER_SCOPED
    }

    /**
     * Typed description of document modifications emitted by Firestore.
     */
    data class DocumentSyncEvent<T>(
        val type: ChangeType,
        val id: String,
        val item: T?,
        val isFromCache: Boolean
    ) {
        enum class ChangeType {
            ADDED,
            MODIFIED,
            REMOVED
        }
    }

    private data class SubscriptionRecord(
        val channelId: String,
        val scope: ChannelScope,
        val registration: ListenerRegistration
    )

    private val activeSubscriptions = ConcurrentHashMap<String, SubscriptionRecord>()
    private val lastProcessedPayloadHashes = ConcurrentHashMap<String, Int>()

    /**
     * Returns the number of currently active subscriptions.
     */
    val activeSubscriptionCount: Int
        get() = activeSubscriptions.size

    /**
     * Checks if a channel currently has an active listener.
     */
    fun isSubscribed(channelId: String): Boolean = activeSubscriptions.containsKey(channelId)

    /**
     * Returns the tracked payload hash for a given channel and document ID (useful for testing loop suppression).
     */
    fun getTrackedPayloadHash(channelId: String, docId: String): Int? =
        lastProcessedPayloadHashes["$channelId:$docId"]

    val trackedHashCount: Int
        get() = lastProcessedPayloadHashes.size

    /**
     * Evaluates whether a modification payload has changed compared to previously processed hash.
     * If changed, records the new hash and returns true. If identical (write-echo), returns false.
     */
    fun shouldProcessModification(channelId: String, docId: String, dataMap: Map<String, Any?>): Boolean {
        val trackingKey = "$channelId:$docId"
        val payloadHash = dataMap.hashCode()
        val previousHash = lastProcessedPayloadHashes[trackingKey]
        if (previousHash == payloadHash) {
            return false
        }
        lastProcessedPayloadHashes[trackingKey] = payloadHash
        return true
    }

    fun recordPayloadHash(channelId: String, docId: String, dataMap: Map<String, Any?>) {
        lastProcessedPayloadHashes["$channelId:$docId"] = dataMap.hashCode()
    }

    fun removePayloadHash(channelId: String, docId: String) {
        lastProcessedPayloadHashes.remove("$channelId:$docId")
    }

    /**
     * Test helper for unit tests verifying subscription scoping and channel cancellation.
     */
    fun registerTestSubscription(channelId: String, scope: ChannelScope) {
        val dummyRegistration = ListenerRegistration { }
        activeSubscriptions[channelId] = SubscriptionRecord(channelId, scope, dummyRegistration)
    }

    /**
     * Attaches a query listener with deduplication, lifecycle tracking, and error handling.
     * If [channelId] is already active, the previous listener is cleanly detached first.
     */
    fun attachQueryListener(
        channelId: String,
        scope: ChannelScope,
        query: Query,
        onSnapshot: (snapshot: QuerySnapshot, isFromCache: Boolean) -> Unit,
        onError: (FirebaseFirestoreException) -> Unit = {}
    ): ListenerRegistration {
        cancelSubscription(channelId)

        val registration = query.addSnapshotListener(EventListener<QuerySnapshot> { snapshot, error ->
            if (error != null) {
                logW(TAG, "Subscription error on channel '$channelId': ${error.message}")
                onError(error)
                return@EventListener
            }
            if (snapshot == null) return@EventListener

            val isFromCache = snapshot.metadata.isFromCache
            onSnapshot(snapshot, isFromCache)
        })

        val record = SubscriptionRecord(channelId, scope, registration)
        activeSubscriptions[channelId] = record
        logD(TAG, "Subscribed channel '$channelId' (scope: $scope). Total active: ${activeSubscriptions.size}")
        return registration
    }

    /**
     * Attaches a document snapshot listener with deduplication and lifecycle tracking.
     */
    fun attachDocumentListener(
        channelId: String,
        scope: ChannelScope,
        docRef: DocumentReference,
        onSnapshot: (snapshot: DocumentSnapshot, isFromCache: Boolean) -> Unit,
        onError: (FirebaseFirestoreException) -> Unit = {}
    ): ListenerRegistration {
        cancelSubscription(channelId)

        val registration = docRef.addSnapshotListener(EventListener<DocumentSnapshot> { snapshot, error ->
            if (error != null) {
                logW(TAG, "Subscription error on document channel '$channelId': ${error.message}")
                onError(error)
                return@EventListener
            }
            if (snapshot == null) return@EventListener

            val isFromCache = snapshot.metadata.isFromCache
            onSnapshot(snapshot, isFromCache)
        })

        val record = SubscriptionRecord(channelId, scope, registration)
        activeSubscriptions[channelId] = record
        logD(TAG, "Subscribed document channel '$channelId' (scope: $scope). Total active: ${activeSubscriptions.size}")
        return registration
    }

    /**
     * Reusable helper for tracking document changes (Add/Modify/Delete) with payload deduplication.
     */
    fun <T> processDocumentChanges(
        channelId: String,
        snapshot: QuerySnapshot,
        mapper: (docId: String, data: Map<String, Any?>) -> T?,
        onAdded: (T) -> Unit,
        onModified: (T) -> Unit,
        onRemoved: (docId: String) -> Unit
    ) {
        val isFromCache = snapshot.metadata.isFromCache
        for (dc in snapshot.documentChanges) {
            val doc = dc.document
            val docId = doc.id
            val dataMap = doc.data
            val docTrackingKey = "$channelId:$docId"

            when (dc.type) {
                DocumentChange.Type.ADDED -> {
                    val parsed = runCatching { mapper(docId, dataMap) }.getOrNull()
                    if (parsed != null) {
                        recordPayloadHash(channelId, docId, dataMap)
                        onAdded(parsed)
                    }
                }
                DocumentChange.Type.MODIFIED -> {
                    if (!shouldProcessModification(channelId, docId, dataMap)) {
                        // Suppress duplicate echo modifications (e.g. metadata-only or write-echo)
                        continue
                    }
                    val parsed = runCatching { mapper(docId, dataMap) }.getOrNull()
                    if (parsed != null) {
                        onModified(parsed)
                    }
                }
                DocumentChange.Type.REMOVED -> {
                    removePayloadHash(channelId, docId)
                    onRemoved(docId)
                }
            }
        }
    }

    /**
     * Cancels a specific subscription channel and frees its Firestore listener.
     */
    fun cancelSubscription(channelId: String) {
        activeSubscriptions.remove(channelId)?.let { record ->
            try {
                record.registration.remove()
                logD(TAG, "Removed subscription on channel '$channelId'")
            } catch (e: Exception) {
                logW(TAG, "Error removing listener for channel '$channelId': ${e.message}")
            }
        }
        // Cleanup tracking hashes for this channel
        lastProcessedPayloadHashes.keys.removeIf { it.startsWith("$channelId:") }
    }

    /**
     * Cancels all user-scoped subscriptions (e.g. during logout, session revocation, or user switch).
     * Global catalog subscriptions are preserved.
     */
    fun cancelUserScopedSubscriptions() {
        val toRemove = activeSubscriptions.filterValues { it.scope == ChannelScope.USER_SCOPED }.keys
        for (channelId in toRemove) {
            cancelSubscription(channelId)
        }
        logI(TAG, "Cancelled ${toRemove.size} user-scoped subscriptions. Remaining: ${activeSubscriptions.size}")
    }

    /**
     * Cancels all active subscriptions (both global and user-scoped).
     */
    fun cancelAll() {
        val allKeys = activeSubscriptions.keys.toList()
        for (channelId in allKeys) {
            cancelSubscription(channelId)
        }
        activeSubscriptions.clear()
        lastProcessedPayloadHashes.clear()
        logI(TAG, "All Firestore subscriptions cancelled.")
    }
}
