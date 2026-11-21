package com.localwalkie.intercom.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.localwalkie.intercom.WalkieApp

/**
 * خدمة أمامية (Foreground Service) تُبقي التطبيق يستقبل النداءات والمكالمات
 * حتى لو كانت الشاشة مطفأة أو التطبيق في الخلفية.
 */
class IntercomService : Service() {

    private val controller get() = (application as WalkieApp).controller

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            controller.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val type = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        try {
            ServiceCompat.startForeground(this, Notifications.ID_SERVICE, Notifications.foreground(this), type)
        } catch (e: Exception) {
            // مثلاً: إذن الميكروفون غير ممنوح أو بدء الخدمة من الخلفية
            stopSelf()
            return START_NOT_STICKY
        }

        controller.start()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        controller.stop()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.localwalkie.intercom.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, IntercomService::class.java))
        }
    }
}
