package com.localwalkie.intercom.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.localwalkie.intercom.MainActivity
import com.localwalkie.intercom.R

object Notifications {
    private const val CH_SERVICE = "intercom_service"
    private const val CH_CALLS = "intercom_calls"
    const val ID_SERVICE = 1
    private const val ID_CALL = 2

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "حالة الاتصال الداخلي", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_CALLS, "المكالمات الواردة", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // الرنين يُشغَّل من التطبيق نفسه
                enableVibration(true)
            }
        )
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun foreground(ctx: Context): Notification {
        val stop = PendingIntent.getService(
            ctx, 1,
            Intent(ctx, IntercomService::class.java).setAction(IntercomService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(ctx, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("الاتصال الداخلي يعمل")
            .setContentText("متصل بالشبكة المحلية ويستقبل النداءات")
            .setContentIntent(openApp(ctx))
            .addAction(0, "إيقاف", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun incomingCall(ctx: Context, callerName: String) {
        val n = NotificationCompat.Builder(ctx, CH_CALLS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("مكالمة واردة")
            .setContentText(callerName)
            .setContentIntent(openApp(ctx))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_CALL, n)
    }

    fun cancelIncoming(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).cancel(ID_CALL)
    }
}
