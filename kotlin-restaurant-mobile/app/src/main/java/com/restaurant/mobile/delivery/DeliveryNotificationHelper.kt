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

enum class DineInNotificationType {
    RESERVATION_CONFIRMED,
    RESERVATION_REMINDER,
    TABLE_BILL_READY,
    BILL_SETTLED_RECEIPT
}

data class DineInNotificationRecord(
    val type: DineInNotificationType,
    val tableNumber: String,
    val title: String,
    val message: String,
    val bigText: String,
    val destination: String,
    val amountPaise: Long = 0L,
    val formattedAmount: String = "",
    val slot: String = "",
    val minutesRemaining: Int = 0,
    val invoiceId: String = "",
    val notificationId: Int = 0
)

/**
 * Android Native Notification Channel and Trigger Service for Food Delivery & Dine-In.
 * Manages high-importance delivery and dine-in notification channels and dispatches real-time updates.
 */
open class DeliveryNotificationHelper(private val context: Context? = null) {

    companion object {
        const val CHANNEL_ID = "food_delivery_channel"
        const val CHANNEL_NAME = "Live Order & Delivery Updates"

        const val DINE_IN_CHANNEL_ID = "dine_in_notifications_channel"
        const val DINE_IN_CHANNEL_NAME = "Table Reservations & Dining Updates"

        const val NOTIFICATION_ID_PLACED = 1001
        const val NOTIFICATION_ID_PROXIMITY = 1002
        const val NOTIFICATION_ID_DELIVERED = 1003
        const val NOTIFICATION_ID_DINE_IN_SETTLED = 1004
        const val NOTIFICATION_ID_BILL_SETTLED_RECEIPT = 1004
        const val NOTIFICATION_ID_RESERVATION_CONFIRMED = 1005
        const val NOTIFICATION_ID_RESERVATION_REMINDER = 1006
        const val NOTIFICATION_ID_TABLE_BILL_READY = 1007

        const val EXTRA_DESTINATION = "EXTRA_DESTINATION"
        const val EXTRA_TABLE_NUMBER = "EXTRA_TABLE_NUMBER"
        const val EXTRA_INVOICE_ID = "EXTRA_INVOICE_ID"

        fun formatDineInSettlementMessage(total: String, tableNumber: String): String =
            "Bill Settled! Payment of $total for Table $tableNumber verified. Thank you for dining with us!"
    }

    // In-memory record tracking for verification, testing, and deduplication
    var lastDispatchedRecord: DineInNotificationRecord? = null
        internal set
    val dispatchedRecords = mutableListOf<DineInNotificationRecord>()
    private val notifiedSettledInvoices = mutableSetOf<String>()

    init {
        createNotificationChannel()
    }

    /**
     * Creates notification channels with IMPORTANCE_HIGH, custom vibration and sound.
     */
    private fun createNotificationChannel() {
        val ctx = context ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // 1. Delivery Channel
            val deliveryChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time updates for order confirmation, rider proximity alerts, and delivery completion."
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(deliveryChannel)

            // 2. Dine-In Channel
            val dineInChannel = NotificationChannel(
                DINE_IN_CHANNEL_ID,
                DINE_IN_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time updates for table reservations, arrival reminders, bills, and payment receipts."
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(dineInChannel)
        }
    }

    private fun getPendingIntent(
        destination: String? = null,
        tableNumber: String? = null,
        invoiceId: String? = null,
        requestCode: Int = 0
    ): PendingIntent? {
        val ctx = context ?: return null
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            destination?.let { putExtra(EXTRA_DESTINATION, it) }
            tableNumber?.let { putExtra(EXTRA_TABLE_NUMBER, it) }
            invoiceId?.let { putExtra(EXTRA_INVOICE_ID, it) }
        }
        return PendingIntent.getActivity(
            ctx,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // -------------------------------------------------------------
    // DINE-IN NOTIFICATION METHODS (PROMPT 19 SPECIFICATION)
    // -------------------------------------------------------------

    /**
     * A. Displays a notification confirming table reservation.
     * Tapping opens the reservation details/bookings screen ("MyBookings").
     */
    fun showReservationConfirmed(tableNumber: String, slot: String) {
        val cleanTable = tableNumber.trim()
        val cleanSlot = slot.trim()
        val title = "Reservation Confirmed! \uD83C\uDF7D\uFE0F"
        val message = "Table $cleanTable reserved for $cleanSlot."
        val bigText = "Your reservation for Table $cleanTable at $cleanSlot has been confirmed. Tap to view your booking details."
        val destination = "MyBookings"
        val notificationId = NOTIFICATION_ID_RESERVATION_CONFIRMED

        val record = DineInNotificationRecord(
            type = DineInNotificationType.RESERVATION_CONFIRMED,
            tableNumber = cleanTable,
            title = title,
            message = message,
            bigText = bigText,
            destination = destination,
            slot = cleanSlot,
            notificationId = notificationId
        )
        recordDispatch(record)

        val ctx = context ?: return
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val pendingIntent = getPendingIntent(destination, cleanTable, requestCode = notificationId)

        val builder = NotificationCompat.Builder(ctx, DINE_IN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_restro)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setAutoCancel(true)

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }

        sendNotification("res_$cleanTable", notificationId, builder)
    }

    /**
     * B. Displays a reminder that the reservation is approaching.
     * Safely handles zero or negative minutes without scheduling repeated reminders.
     * Tapping opens the reservation screen ("MyBookings").
     */
    fun showReservationReminder(tableNumber: String, minutesRemaining: Int) {
        if (minutesRemaining < 0) return
        val cleanTable = tableNumber.trim()
        val title = if (minutesRemaining == 0) "Reservation Starting Now! \u23F0" else "Upcoming Reservation Reminder \u23F0"
        val message = if (minutesRemaining == 0) {
            "Your reservation for Table $cleanTable is starting now."
        } else {
            "Your table $cleanTable reservation is in $minutesRemaining minutes."
        }
        val bigText = if (minutesRemaining == 0) {
            "Your reservation for Table $cleanTable is starting right now. We look forward to serving you!"
        } else {
            "Reminder: Your reservation for Table $cleanTable is coming up in $minutesRemaining minutes. Tap to view details."
        }
        val destination = "MyBookings"
        val notificationId = NOTIFICATION_ID_RESERVATION_REMINDER

        val record = DineInNotificationRecord(
            type = DineInNotificationType.RESERVATION_REMINDER,
            tableNumber = cleanTable,
            title = title,
            message = message,
            bigText = bigText,
            destination = destination,
            minutesRemaining = minutesRemaining,
            notificationId = notificationId
        )
        recordDispatch(record)

        val ctx = context ?: return
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val pendingIntent = getPendingIntent(destination, cleanTable, requestCode = notificationId)

        val builder = NotificationCompat.Builder(ctx, DINE_IN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_restro)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 250, 150, 250))
            .setAutoCancel(true)

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }

        sendNotification("rem_$cleanTable", notificationId, builder)
    }

    /**
     * C. Notifies the customer that their dine-in bill is ready for payment.
     * Displays formatted currency amount.
     * Tapping opens the active dining-tab/bill payment screen ("Home").
     */
    fun showTableBillReady(tableNumber: String, amount: Long) {
        val cleanTable = tableNumber.trim()
        val formattedAmount = money(amount)
        val title = "Bill Ready for Payment \uD83E\uDDFE"
        val message = "Your bill for Table $cleanTable is ready: $formattedAmount."
        val bigText = "Your dining bill for Table $cleanTable is ready. Total: $formattedAmount. Tap to review items and pay securely."
        val destination = "Home"
        val notificationId = NOTIFICATION_ID_TABLE_BILL_READY

        val record = DineInNotificationRecord(
            type = DineInNotificationType.TABLE_BILL_READY,
            tableNumber = cleanTable,
            title = title,
            message = message,
            bigText = bigText,
            destination = destination,
            amountPaise = amount,
            formattedAmount = formattedAmount,
            notificationId = notificationId
        )
        recordDispatch(record)

        val ctx = context ?: return
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val pendingIntent = getPendingIntent(destination, cleanTable, requestCode = notificationId)

        val builder = NotificationCompat.Builder(ctx, DINE_IN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_restro)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setAutoCancel(true)

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }

        sendNotification("bill_$cleanTable", notificationId, builder)
    }

    /**
     * D. Displays a payment-success notification after authoritative settlement.
     * Contains table number, formatted amount paid, and invoice identifier.
     * Tapping opens the order/receipt details screen ("Orders").
     */
    fun showBillSettledReceipt(tableNumber: String, amount: Long, invoiceId: String) {
        val cleanInvoice = invoiceId.trim()
        if (cleanInvoice.isNotBlank() && !notifiedSettledInvoices.add(cleanInvoice)) {
            // Deduplicate: exact same invoice already notified
            return
        }

        val cleanTable = tableNumber.trim()
        val formattedAmount = money(amount)
        val title = "Bill Settled! \uD83C\uDF89"
        val message = "Payment of $formattedAmount for Table $cleanTable verified. Receipt: $cleanInvoice."
        val bigText = "Payment of $formattedAmount for Table $cleanTable verified. Invoice #$cleanInvoice. Thank you for dining with us! Tap to view your receipt."
        val destination = "Orders"
        val notificationId = NOTIFICATION_ID_BILL_SETTLED_RECEIPT

        val record = DineInNotificationRecord(
            type = DineInNotificationType.BILL_SETTLED_RECEIPT,
            tableNumber = cleanTable,
            title = title,
            message = message,
            bigText = bigText,
            destination = destination,
            amountPaise = amount,
            formattedAmount = formattedAmount,
            invoiceId = cleanInvoice,
            notificationId = notificationId
        )
        recordDispatch(record)

        val ctx = context ?: return
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val pendingIntent = getPendingIntent(destination, cleanTable, cleanInvoice, requestCode = notificationId)

        val builder = NotificationCompat.Builder(ctx, DINE_IN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_restro)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 250, 150, 250))
            .setAutoCancel(true)

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }

        sendNotification("settled_$cleanInvoice", notificationId, builder)
    }

    // -------------------------------------------------------------
    // EXISTING DELIVERY NOTIFICATIONS (PRESERVED)
    // -------------------------------------------------------------

    fun notifyOrderPlaced(orderId: String, restaurantName: String, etaMinutes: Int = 28) {
        val ctx = context ?: return
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_restro)
            .setContentTitle("Order Confirmed! \uD83C\uDF72")
            .setContentText("$restaurantName is preparing your food. ETA: $etaMinutes mins.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Order #$orderId has been accepted! $restaurantName is preparing your food. Estimated delivery time: $etaMinutes mins.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 250, 150, 250))
            .setAutoCancel(true)

        getPendingIntent(destination = "Orders", requestCode = NOTIFICATION_ID_PLACED)?.let {
            builder.setContentIntent(it)
        }
        sendNotification(NOTIFICATION_ID_PLACED, builder)
    }

    fun notifyRiderNearby(orderId: String, riderName: String = "Ramesh") {
        val ctx = context ?: return
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_restro)
            .setContentTitle("\uD83D\uDEF5 $riderName is at your gate!")
            .setContentText("Please meet your delivery partner. Rider is less than 500m away.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("\uD83D\uDEF5 $riderName is at your gate! Please meet your delivery partner. Arrived with hot food.")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVibrate(longArrayOf(0, 400, 200, 400, 200, 400))
            .setSound(soundUri)
            .setAutoCancel(true)

        getPendingIntent(destination = "Tracking", requestCode = NOTIFICATION_ID_PROXIMITY)?.let {
            builder.setContentIntent(it)
        }
        sendNotification(NOTIFICATION_ID_PROXIMITY, builder)
    }

    fun notifyOrderDelivered(orderId: String) {
        val ctx = context ?: return
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_restro)
            .setContentTitle("\uD83C\uDF89 Order Delivered!")
            .setContentText("Enjoy your hot meal. Tap to rate your food.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("\uD83C\uDF89 Order #$orderId has been delivered! Enjoy your hot meal. Tap to rate your food and delivery partner.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
            .setAutoCancel(true)

        getPendingIntent(destination = "Orders", requestCode = NOTIFICATION_ID_DELIVERED)?.let {
            builder.setContentIntent(it)
        }
        sendNotification(NOTIFICATION_ID_DELIVERED, builder)
    }

    fun notifyDineInBillSettled(total: String, tableNumber: String) {
        showBillSettledReceipt(tableNumber, 0L, "TXN-DIN-PREV")
    }

    fun notifyDineInBillSettled(totalPaise: Long, tableNumber: String) {
        showBillSettledReceipt(tableNumber, totalPaise, "TXN-DIN-PREV")
    }

    private fun recordDispatch(record: DineInNotificationRecord) {
        lastDispatchedRecord = record
        dispatchedRecords.add(record)
    }

    fun clearHistoryForTest() {
        lastDispatchedRecord = null
        dispatchedRecords.clear()
        notifiedSettledInvoices.clear()
    }

    private fun sendNotification(notificationId: Int, builder: NotificationCompat.Builder) {
        sendNotification(null, notificationId, builder)
    }

    private fun sendNotification(tag: String? = null, notificationId: Int, builder: NotificationCompat.Builder) {
        val ctx = context ?: return
        try {
            val notificationManager = NotificationManagerCompat.from(ctx)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                    ctx,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasPermission) {
                    return
                }
            }
            if (tag != null) {
                notificationManager.notify(tag, notificationId, builder.build())
            } else {
                notificationManager.notify(notificationId, builder.build())
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }
}
