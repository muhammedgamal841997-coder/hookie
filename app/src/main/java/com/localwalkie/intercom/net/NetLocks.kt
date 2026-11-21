package com.localwalkie.intercom.net

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build

/** يمنع الواي فاي من النوم أثناء عمل الخدمة حتى تصل الحزم والإعلانات فوراً. */
class NetLocks(context: Context) {
    private val wifi = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var wifiLock: WifiManager.WifiLock? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    @Suppress("DEPRECATION")
    @Synchronized
    fun acquire() {
        if (wifiLock == null) {
            val mode = if (Build.VERSION.SDK_INT >= 29) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifi.createWifiLock(mode, "walkie:wifi").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        if (multicastLock == null) {
            multicastLock = wifi.createMulticastLock("walkie:mdns").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    @Synchronized
    fun release() {
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        runCatching { multicastLock?.takeIf { it.isHeld }?.release() }
        wifiLock = null
        multicastLock = null
    }
}
