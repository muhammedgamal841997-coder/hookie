package com.localwalkie.intercom.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.localwalkie.intercom.model.Peer
import com.localwalkie.intercom.model.PresenceStatus
import com.localwalkie.intercom.model.UserProfile
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * اكتشاف الموظفين على نفس الشبكة المحلية عبر NSD (mDNS).
 * كل جهاز ينشر خدمة بها بيانات TXT: المعرّف، الاسم، الوظيفة، الحالة.
 */
class PeerDiscovery(
    context: Context,
    private val onPeers: (List<Peer>) -> Unit
) {
    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val byServiceName = ConcurrentHashMap<String, Peer>()
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private val lock = Any()
    private var resolving = false

    private var regListener: NsdManager.RegistrationListener? = null
    private var discListener: NsdManager.DiscoveryListener? = null

    @Volatile private var selfId = ""

    fun start(profile: UserProfile, deviceId: String, port: Int) {
        stop()
        selfId = deviceId
        register(profile, deviceId, port)
        startDiscovery()
    }

    /** إعادة نشر الخدمة عند تغيير الاسم/الوظيفة/الحالة. */
    fun updateProfile(profile: UserProfile, deviceId: String, port: Int) {
        regListener?.let { runCatching { nsd.unregisterService(it) } }
        regListener = null
        register(profile, deviceId, port)
    }

    /** مسح القائمة وإعادة البحث (زر التحديث). */
    fun refresh() {
        discListener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discListener = null
        synchronized(lock) { resolveQueue.clear(); resolving = false }
        byServiceName.clear()
        publish()
        startDiscovery()
    }

    fun stop() {
        regListener?.let { runCatching { nsd.unregisterService(it) } }
        regListener = null
        discListener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discListener = null
        synchronized(lock) { resolveQueue.clear(); resolving = false }
        byServiceName.clear()
        publish()
    }

    // ---------------------------------------------------------------- تسجيل

    private fun register(profile: UserProfile, deviceId: String, port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = "WT-" + deviceId.take(8)
            serviceType = Proto.SERVICE_TYPE
            setPort(port)
            setAttribute("id", deviceId)
            setAttribute("name", profile.name.take(40))
            setAttribute("role", profile.role.take(40))
            setAttribute("status", profile.status.code)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "registered: ${serviceInfo.serviceName}")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "registration failed: $errorCode")
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        regListener = listener
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    // ---------------------------------------------------------------- اكتشاف

    private fun startDiscovery() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.contains("_walkietalkie")) enqueueResolve(serviceInfo)
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                byServiceName.remove(serviceInfo.serviceName)
                publish()
            }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "start discovery failed: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        discListener = listener
        nsd.discoverServices(Proto.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    /** NSD لا يدعم أكثر من resolve في نفس الوقت على أغلب الإصدارات، لذلك نستخدم طابوراً. */
    private fun enqueueResolve(info: NsdServiceInfo) {
        synchronized(lock) {
            resolveQueue.addLast(info)
            if (resolving) return
            resolving = true
        }
        resolveNext()
    }

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        val next = synchronized(lock) {
            resolveQueue.removeFirstOrNull().also { if (it == null) resolving = false }
        } ?: return

        try {
            nsd.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    resolveNext()
                }
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    handleResolved(serviceInfo)
                    resolveNext()
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "resolve error", e)
            resolveNext()
        }
    }

    @Suppress("DEPRECATION")
    private fun handleResolved(info: NsdServiceInfo) {
        val attrs = info.attributes
        fun attr(key: String): String? = attrs[key]?.toString(Charsets.UTF_8)

        val id = attr("id") ?: return
        if (id == selfId) return

        val address: InetAddress? = if (Build.VERSION.SDK_INT >= 34) {
            info.hostAddresses.firstOrNull { it is Inet4Address } ?: info.hostAddresses.firstOrNull()
        } else {
            info.host
        }
        val host = address?.hostAddress ?: return

        byServiceName[info.serviceName] = Peer(
            id = id,
            name = attr("name") ?: info.serviceName,
            role = attr("role") ?: "",
            status = PresenceStatus.fromCode(attr("status")),
            host = host,
            port = info.port
        )
        publish()
    }

    private fun publish() {
        onPeers(byServiceName.values.distinctBy { it.id }.sortedBy { it.name })
    }

    private companion object {
        const val TAG = "PeerDiscovery"
    }
}
