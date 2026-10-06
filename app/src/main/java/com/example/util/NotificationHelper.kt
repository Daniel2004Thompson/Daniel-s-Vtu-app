package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import java.text.NumberFormat
import java.util.Locale

object NotificationHelper {
    const val CHANNEL_ID = "vtu_transactions"
    private const val CHANNEL_NAME = "VTU Transactions & Bills"
    private const val CHANNEL_DESC = "Real-time alerts for airtime, data purchases, token receipts, and wallet updates."

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                setShowBadge(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showTransactionNotification(
        context: Context,
        title: String,
        message: String,
        reference: String,
        amount: Double? = null,
        token: String? = null
    ) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("EXTRA_TX_REF", reference)
            }
            val pendingIntent: PendingIntent = PendingIntent.getActivity(
                context,
                reference.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val formattedAmount = amount?.let {
                val format = NumberFormat.getCurrencyInstance(Locale("en", "NG"))
                format.maximumFractionDigits = 0
                format.format(it)
            }

            val bigText = buildString {
                append(message)
                if (formattedAmount != null) {
                    append("\nAmount: ").append(formattedAmount)
                }
                append("\nRef: ").append(reference)
                if (!token.isNullOrBlank()) {
                    append("\nToken: ").append(token)
                }
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.img_vtu_icon)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)

            val notificationManager = NotificationManagerCompat.from(context)
            if (notificationManager.areNotificationsEnabled()) {
                val notificationId = (System.currentTimeMillis() % 100000).toInt()
                notificationManager.notify(notificationId, builder.build())
            }
        } catch (e: SecurityException) {
            // Permission not granted or notification disabled
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
