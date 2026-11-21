package com.localwalkie.intercom.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.localwalkie.intercom.audio.AudioRoute
import com.localwalkie.intercom.audio.Capture
import com.localwalkie.intercom.audio.Playback
import com.localwalkie.intercom.data.ProfileStore
import com.localwalkie.intercom.model.CallState
import com.localwalkie.intercom.model.Peer
import com.localwalkie.intercom.model.PresenceStatus
import com.localwalkie.intercom.model.UiState
import com.localwalkie.intercom.model.UserProfile
import com.localwalkie.intercom.net.NetLocks
import com.localwalkie.intercom.net.PeerDiscovery
import com.localwalkie.intercom.net.Proto
import com.localwalkie.intercom.net.UdpTransport
import com.localwalkie.intercom.service.Notifications
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * قلب التطبيق: يربط الاكتشاف (NSD) + نقل UDP + الالتقاط/التشغيل + حالة المكالمات.
 *
 * قواعد الخيوط:
 *  - الدوال العامة تُستدعى من الخيط الرئيسي.
 *  - عمليات المقبس (start/stop/إرسال التحكم) تمر عبر خيط [io] لأن الشبكة ممنوعة على الرئيسي.
 *  - حزم التحكم تُعالَج على الخيط الرئيسي؛ حزم الصوت على خيط الاستقبال مباشرة.
 */
class IntercomController(private val app: Context) {

    private val store = ProfileStore(app)
    private val handler = Handler(Looper.getMainLooper())
    private val io: ExecutorService =
        Executors.newSingleThreadExecutor { r -> Thread(r, "wt-io").apply { isDaemon = true } }

    private val _state = MutableStateFlow(UiState(profile = store.load()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val route = AudioRoute(app)
    private val locks = NetLocks(app)
    private val playback = Playback(route)
    private val capture = Capture(route, this::sendAudio) { msg -> handler.post { onCaptureError(msg) } }
    private val transport = UdpTransport(this::onPacket)
    private val discovery = PeerDiscovery(app) { peers -> _state.update { it.copy(peers = peers) } }

    @Volatile private var running = false
    @Volatile private var targets: List<Peer> = emptyList()

    // قفل المتحدث: أول متحدث يأخذ القناة حتى يصمت 600ms
    @Volatile private var activeHost: String? = null
    @Volatile private var lastAudioAt = 0L
    @Volatile private var callAudioAt = 0L

    private var ringtone: Ringtone? = null

    // ------------------------------------------------------------------ دورة التشغيل

    fun start() {
        if (running) return
        val profile = _state.value.profile ?: return
        running = true
        locks.acquire()
        playback.start()
        _state.update { it.copy(serviceRunning = true) }
        io.execute {
            val port = transport.start()
            handler.post {
                if (running) discovery.start(profile, store.deviceId, port)
            }
        }
    }

    fun stop() {
        if (!running) return
        running = false

        val call = _state.value.call
        if (call is CallState.Active) sendControl(Proto.T_CALL_END, call.peer)
        if (call is CallState.Outgoing) sendControl(Proto.T_CALL_END, call.peer)
        if (call is CallState.Incoming) sendControl(Proto.T_CALL_REJECT, call.peer)

        capture.stop()
        targets = emptyList()
        resetCallTimers()
        stopRing()
        Notifications.cancelIncoming(app)
        discovery.stop()
        playback.stop()
        locks.release()
        io.execute { transport.stop() } // بعد رسائل التحكم الجارية في نفس الخيط

        _state.update {
            it.copy(
                serviceRunning = false, peers = emptyList(), speakingPeerId = null,
                transmitting = false, call = CallState.Idle
            )
        }
    }

    fun refreshPeers() {
        if (running) discovery.refresh()
    }

    // ------------------------------------------------------------------ الملف الشخصي

    fun saveProfile(profile: UserProfile) {
        val clean = profile.copy(name = profile.name.trim().take(40), role = profile.role.trim().take(40))
        store.save(clean)
        _state.update { it.copy(profile = clean) }
        if (running) discovery.updateProfile(clean, store.deviceId, transport.port)
    }

    fun setStatus(status: PresenceStatus) {
        _state.value.profile?.let { saveProfile(it.copy(status = status)) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    // ------------------------------------------------------------------ Push-To-Talk

    /** [peer] = null يعني بث لجميع المتصلين. */
    fun startTalking(peer: Peer?) {
        val s = _state.value
        if (!running || s.call != CallState.Idle || s.transmitting) return
        if (!hasMic()) {
            toast("إذن الميكروفون غير ممنوح")
            return
        }
        val list = if (peer != null) listOf(peer) else s.peers
        if (list.isEmpty()) {
            toast("لا يوجد أحد متصل للتحدث معه")
            return
        }
        targets = list
        _state.update { it.copy(transmitting = true) }
        capture.start()
    }

    fun stopTalking() {
        if (!_state.value.transmitting) return
        capture.stop()
        targets = emptyList()
        _state.update { it.copy(transmitting = false) }
    }

    // ------------------------------------------------------------------ المكالمات

    fun requestCall(peer: Peer) {
        val s = _state.value
        if (!running || s.call != CallState.Idle || s.transmitting) return
        if (!hasMic()) {
            toast("إذن الميكروفون غير ممنوح")
            return
        }
        sendControl(Proto.T_CALL_REQ, peer)
        _state.update { it.copy(call = CallState.Outgoing(peer)) }
        handler.removeCallbacks(callTimeout)
        handler.postDelayed(callTimeout, CALL_TIMEOUT_MS)
    }

    fun acceptCall() {
        val c = _state.value.call as? CallState.Incoming ?: return
        sendControl(Proto.T_CALL_ACCEPT, c.peer)
        beginActive(c.peer)
    }

    fun rejectCall() {
        val c = _state.value.call as? CallState.Incoming ?: return
        sendControl(Proto.T_CALL_REJECT, c.peer)
        finishCall(null)
    }

    fun endCall() {
        when (val c = _state.value.call) {
            is CallState.Outgoing -> sendControl(Proto.T_CALL_END, c.peer)
            is CallState.Active -> sendControl(Proto.T_CALL_END, c.peer)
            is CallState.Incoming -> sendControl(Proto.T_CALL_REJECT, c.peer)
            CallState.Idle -> return
        }
        finishCall(null)
    }

    private fun beginActive(peer: Peer) {
        resetCallTimers()
        stopRing()
        Notifications.cancelIncoming(app)
        callAudioAt = SystemClock.elapsedRealtime()
        targets = listOf(peer)
        _state.update { it.copy(call = CallState.Active(peer), transmitting = false) }
        capture.start()
        handler.postDelayed(watchdog, WATCHDOG_MS)
    }

    private fun finishCall(message: String?) {
        resetCallTimers()
        capture.stop()
        targets = emptyList()
        stopRing()
        Notifications.cancelIncoming(app)
        _state.update { it.copy(call = CallState.Idle, transmitting = false, message = message ?: it.message) }
    }

    private fun resetCallTimers() {
        handler.removeCallbacks(callTimeout)
        handler.removeCallbacks(watchdog)
    }

    private val callTimeout = Runnable {
        when (val c = _state.value.call) {
            is CallState.Outgoing -> {
                sendControl(Proto.T_CALL_END, c.peer)
                finishCall("لم يرد ${c.peer.name}")
            }
            is CallState.Incoming -> finishCall("مكالمة فائتة من ${c.peer.name}")
            else -> Unit
        }
    }

    /** المكالمة الثنائية ترسل صوتاً باستمرار؛ انقطاع الاستقبال 10 ثوانٍ يعني سقوط الاتصال. */
    private val watchdog = object : Runnable {
        override fun run() {
            val c = _state.value.call as? CallState.Active ?: return
            if (SystemClock.elapsedRealtime() - callAudioAt > CALL_SILENCE_LIMIT_MS) {
                sendControl(Proto.T_CALL_END, c.peer)
                finishCall("انقطع الاتصال مع ${c.peer.name}")
            } else {
                handler.postDelayed(this, WATCHDOG_MS)
            }
        }
    }

    // ------------------------------------------------------------------ الشبكة

    private fun sendAudio(frame: ByteArray, len: Int) {
        for (p in targets) transport.send(Proto.T_AUDIO, frame, len, p.host, p.port)
    }

    private fun sendControl(type: Byte, peer: Peer) {
        io.execute {
            // نرسلها مرتين لتعويض فقدان UDP؛ المستقبل يتجاهل التكرار
            repeat(2) {
                transport.send(type, EMPTY, 0, peer.host, peer.port)
                try { Thread.sleep(120) } catch (_: InterruptedException) { return@execute }
            }
        }
    }

    private fun onPacket(type: Byte, data: ByteArray, len: Int, host: String, port: Int) {
        if (type == Proto.T_AUDIO) {
            onAudio(data, len, host)
        } else {
            handler.post { onControl(type, host, port) }
        }
    }

    private fun onAudio(data: ByteArray, len: Int, host: String) {
        val payload = len - 1
        if (!running || payload <= 0 || payload % 2 != 0 || payload > MAX_PAYLOAD) return

        val s = _state.value
        val profile = s.profile ?: return

        when (val c = s.call) {
            is CallState.Active -> {
                if (!sameHost(c.peer.host, host)) return
                callAudioAt = SystemClock.elapsedRealtime()
            }
            CallState.Idle -> {
                // أثناء الضغط على PTT أو في حالة "في اجتماع" لا نشغّل الوارد
                if (s.transmitting || profile.status == PresenceStatus.IN_MEETING) return
            }
            else -> return
        }

        val now = SystemClock.elapsedRealtime()
        val current = activeHost
        if (current != null && current != host && now - lastAudioAt < SPEAKER_HOLD_MS) return
        activeHost = host
        lastAudioAt = now

        playback.enqueue(data.copyOfRange(1, len))

        val peer = findPeer(host)
        _state.update { it.copy(speakingPeerId = peer?.id ?: UNKNOWN_SPEAKER) }
        handler.removeCallbacks(clearSpeaking)
        handler.postDelayed(clearSpeaking, 700)
    }

    private val clearSpeaking = Runnable { _state.update { it.copy(speakingPeerId = null) } }

    private fun onControl(type: Byte, host: String, port: Int) {
        if (!running) return
        val s = _state.value
        val peer = findPeer(host)
            ?: Peer("ip:$host", host, "", PresenceStatus.AVAILABLE, host, port)

        when (type) {
            Proto.T_CALL_REQ -> {
                val c = s.call
                if (c is CallState.Incoming && sameHost(c.peer.host, host)) return // تكرار
                if (c is CallState.Outgoing && sameHost(c.peer.host, host)) {
                    // اتصلنا ببعض في نفس اللحظة → نعتبرها مقبولة
                    sendControl(Proto.T_CALL_ACCEPT, c.peer)
                    beginActive(c.peer)
                    return
                }
                val busy = c != CallState.Idle || s.transmitting ||
                    s.profile?.status == PresenceStatus.IN_MEETING
                if (busy) {
                    sendControl(Proto.T_CALL_REJECT, peer)
                    return
                }
                _state.update { it.copy(call = CallState.Incoming(peer)) }
                startRing()
                Notifications.incomingCall(app, peer.name)
                handler.removeCallbacks(callTimeout)
                handler.postDelayed(callTimeout, CALL_TIMEOUT_MS)
            }

            Proto.T_CALL_ACCEPT -> {
                val c = s.call
                if (c is CallState.Outgoing && sameHost(c.peer.host, host)) beginActive(c.peer)
            }

            Proto.T_CALL_REJECT -> {
                val c = s.call
                if (c is CallState.Outgoing && sameHost(c.peer.host, host)) {
                    finishCall("${c.peer.name} غير متاح للمكالمة الآن")
                }
            }

            Proto.T_CALL_END -> {
                val other = when (val c = s.call) {
                    is CallState.Outgoing -> c.peer
                    is CallState.Incoming -> c.peer
                    is CallState.Active -> c.peer
                    CallState.Idle -> null
                }
                if (other != null && sameHost(other.host, host)) {
                    finishCall(if (s.call is CallState.Active) "انتهت المكالمة" else null)
                }
            }
        }
    }

    private fun onCaptureError(msg: String) {
        val c = _state.value.call
        if (c is CallState.Active) sendControl(Proto.T_CALL_END, c.peer)
        if (c != CallState.Idle) {
            finishCall(msg)
        } else {
            capture.stop()
            targets = emptyList()
            _state.update { it.copy(transmitting = false, message = msg) }
        }
    }

    // ------------------------------------------------------------------ أدوات مساعدة

    private fun findPeer(host: String): Peer? =
        _state.value.peers.firstOrNull { sameHost(it.host, host) }

    private fun sameHost(a: String, b: String) = a.substringBefore('%') == b.substringBefore('%')

    private fun hasMic() = ContextCompat.checkSelfPermission(
        app, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    private fun toast(msg: String) {
        _state.update { it.copy(message = msg) }
    }

    private fun startRing() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(app, uri)?.also { it.play() }
        }
    }

    private fun stopRing() {
        runCatching { ringtone?.stop() }
        ringtone = null
    }

    private companion object {
        val EMPTY = ByteArray(0)
        const val UNKNOWN_SPEAKER = "unknown"
        const val MAX_PAYLOAD = 4096
        const val SPEAKER_HOLD_MS = 600L
        const val CALL_TIMEOUT_MS = 30_000L
        const val WATCHDOG_MS = 2_000L
        const val CALL_SILENCE_LIMIT_MS = 10_000L
    }
}
