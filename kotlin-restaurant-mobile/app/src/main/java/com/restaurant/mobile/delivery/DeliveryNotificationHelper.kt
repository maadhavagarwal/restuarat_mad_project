package com.restaurant.mobile.delivery

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.restaurant.mobile.MainActivity
import com.restaurant.mobile.R

/**
 * Android Native Notification Channel and Trigger Service for Food Delivery.
 * Manages the high-importance delivery channel and dispatches live order updates.
 */
class DeliveryNotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "food_delivery_channel"
        const val CHANNEL_NAME = "Live Order & Delivery Updates"
        const val NOTIFICATION_ID_PLACED = 1001
        const val NOTIFICATION_ID_PROXIMITY = 1002
        const val NOTIFICATION_ID_DELIVERED = 1003
        const val NOTIFICATION_ID_DINE_IN_SETTLED = 1004

        fun formatDineInSettlementMessage(total: String, tableNumber: String): String =
            "Bill Settled! Payment of $total for Table $tableNumber verified. Thank you for dining with us!"
    }

    init {
        createNotificationChannel()
    }

    /**
     * Creates the food_delivery_channel with IMPORTANCE_HIGH, custom vibration and sound.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val descriptionText = "Real-time updates for order confirmation, rider proximity alerts, and delivery completion."
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                setShowBadge(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Notification 1 (Order Placed):
     * "Order Confirmed! Bistro Lumière is preparing your food. ETA: 28 mins."
     */
    fun notifyOrderPlaced(orderId: String, restaurantName: String, etaMinutes: Int = 28) {
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_restro)
            .setContentTitle("Order Confirmed! 🍲")
            .setContentText("$restaurantName is preparing your food. ETA: $etaMinutes mins.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Order #$orderId has been accepted! $restaurantName is preparing your food. Estimated delivery time: $etaMinutes mins.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 250, 150, 250))
            .setContentIntent(getPendingIntent())
            .setAutoCancel(true)

        sendNotification(NOTIFICATION_ID_PLACED, builder)
    }

    /**
     * Notification 2 (Proximity Alert):
     * Triggered when rider is < 500 m away:
     * "🛵 Ramesh is at your gate! Please meet your delivery partner." (with sound and vibration)
     */
    fun notifyRiderNearby(orderId: String, riderName: String = "Ramesh") {
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_restro)
            .setContentTitle("🛵 $riderName is at your gate!")
            .setContentText("Please meet your delivery partner. Rider is less than 500m away.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("🛵 $riderName is at your gate! Please meet your delivery partner. Arrived with hot food.")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVibrate(longArrayOf(0, 400, 200, 400, 200, 400))
            .setSound(soundUri)
            .setContentIntent(getPendingIntent())
            .setAutoCancel(true)

        sendNotification(NOTIFICATION_ID_PROXIMITY, builder)
    }

    /**
     * Notification 3 (Delivered):
     * "🎉 Order Delivered! Enjoy your hot meal. Tap to rate your food."
     */
    fun notifyOrderDelivered(orderId: String) {
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_restro)
            .setContentTitle("🎉 Order Delivered!")
            .setContentText("Enjoy your hot meal. Tap to rate your food.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("🎉 Order #$orderId has been delivered! Enjoy your hot meal. Tap to rate your food and delivery partner.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
            .setContentIntent(getPendingIntent())
            .setAutoCancel(true)

        sendNotification(NOTIFICATION_ID_DELIVERED, builder)
    }

    /**
     * Notification 4 (Dine-In Bill Settled):
     * "Bill Settled! Payment of {total} for Table {tableNumber} verified. Thank you for dining with us!"
     */
    fun notifyDineInBillSettled(total: String, tableNumber: String) {
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val message = formatDineInSettlementMessage(total, tableNumber)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_restro)
            .setContentTitle("Bill Settled! 🎉")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(soundUri)
            .setContentIntent(getPendingIntent())
            .setAutoCancel(true)

        sendNotification(NOTIFICATION_ID_DINE_IN_SETTLED, builder)
    }

    fun notifyDineInBillSettled(totalPaise: Long, tableNumber: String) {
        notifyDineInBillSettled(money(totalPaise), tableNumber)
    }

    private fun sendNotification(notificationId: Int, builder: NotificationCompat.Builder) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            // On Android 13+, check permission before posting
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasPermission) {
                    return
                }
            }
            notificationManager.notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Permission not granted or restricted in sandbox
        } catch (_: Exception) {
            // Fallback gracefully
        }
    }
}
