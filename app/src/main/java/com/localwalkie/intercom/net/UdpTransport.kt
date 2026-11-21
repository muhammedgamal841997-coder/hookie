package com.localwalkie.intercom.net

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/** ثوابت البروتوكول: أول بايت في كل حزمة UDP هو نوعها. */
object Proto {
    const val DEFAULT_PORT = 50505
    const val SERVICE_TYPE = "_walkietalkie._tcp."

    const val T_AUDIO: Byte = 1
    const val T_CALL_REQ: Byte = 2
    const val T_CALL_ACCEPT: Byte = 3
    const val T_CALL_REJECT: Byte = 4
    const val T_CALL_END: Byte = 5

    const val SAMPLE_RATE = 16_000
    const val FRAME_MS = 20
    /** 20ms من صوت PCM 16-bit أحادي = 640 بايت (أقل بكثير من MTU الشبكة). */
    const val FRAME_BYTES = SAMPLE_RATE / 1000 * FRAME_MS * 2
}

/**
 * مقبس UDP واحد للإرسال والاستقبال معاً.
 * يجب استدعاء start/stop و send الخاصة بالتحكم من خيط خلفي (ليس الخيط الرئيسي).
 */
class UdpTransport(
    private val onPacket: (type: Byte, data: ByteArray, len: Int, host: String, port: Int) -> Unit
) {
    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var running = false

    @Volatile
    var port: Int = 0
        private set

    private val addressCache = ConcurrentHashMap<String, InetAddress>()

    @Synchronized
    fun start(): Int {
        if (running) return port
        val s = openSocket()
        socket = s
        port = s.localPort
        running = true
        thread(name = "wt-udp-rx", isDaemon = true) { receiveLoop(s) }
        return port
    }

    @Synchronized
    fun stop() {
        running = false
        socket?.close()
        socket = null
        addressCache.clear()
    }

    /** payload[0 until len] يُرسل بعد بايت النوع. */
    fun send(type: Byte, payload: ByteArray, len: Int, host: String, port: Int) {
        val s = socket ?: return
        try {
            val addr = addressCache.getOrPut(host) { InetAddress.getByName(host) }
            val buf = ByteArray(len + 1)
            buf[0] = type
            if (len > 0) System.arraycopy(payload, 0, buf, 1, len)
            s.send(DatagramPacket(buf, buf.size, addr, port))
        } catch (_: Exception) {
            // الشبكة غير متاحة مؤقتاً — نتجاهل الحزمة (UDP لا يضمن التسليم أصلاً)
        }
    }

    private fun openSocket(): DatagramSocket {
        var s: DatagramSocket? = null
        try {
            s = DatagramSocket(null as SocketAddress?)
            s.reuseAddress = true
            s.bind(InetSocketAddress(Proto.DEFAULT_PORT))
        } catch (_: Exception) {
            // المنفذ مشغول — نأخذ أي منفذ حر وننشره عبر NSD
            runCatching { s?.close() }
            s = DatagramSocket(0)
        }
        val result = s!!
        runCatching { result.trafficClass = 0xB8 } // أولوية صوت (DSCP EF)
        return result
    }

    private fun receiveLoop(s: DatagramSocket) {
        val buf = ByteArray(2048)
        val pkt = DatagramPacket(buf, buf.size)
        while (running && !s.isClosed) {
            try {
                pkt.length = buf.size
                s.receive(pkt)
                if (pkt.length >= 1) {
                    val host = pkt.address?.hostAddress ?: continue
                    onPacket(buf[0], buf, pkt.length, host, pkt.port)
                }
            } catch (_: Exception) {
                if (!running || s.isClosed) break
                try { Thread.sleep(50) } catch (_: InterruptedException) { break }
            }
        }
    }
}
