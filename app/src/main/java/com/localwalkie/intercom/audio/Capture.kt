package com.localwalkie.intercom.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import com.localwalkie.intercom.net.Proto
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * التقاط الميكروفون وتقطيعه إلى إطارات 20ms.
 * كل جلسة (start → stop) لها علم مستقل، فلا يحدث تداخل لو ضغط المستخدم PTT بسرعة متكررة.
 */
class Capture(
    private val route: AudioRoute,
    private val onFrame: (frame: ByteArray, len: Int) -> Unit,
    private val onError: (String) -> Unit
) {
    private var session: AtomicBoolean? = null
    private var worker: Thread? = null

    @Synchronized
    fun start() {
        if (session?.get() == true) return
        val flag = AtomicBoolean(true)
        session = flag
        val previous = worker
        worker = Thread({
            val interrupted = try {
                previous?.join()
                false
            } catch (_: InterruptedException) {
                true
            }
            if (!interrupted && flag.get()) loop(flag)
        }, "wt-capture").also { it.start() }
    }

    @Synchronized
    fun stop() {
        session?.set(false)
        session = null
    }

    @SuppressLint("MissingPermission")
    private fun loop(flag: AtomicBoolean) {
        val minBuf = AudioRecord.getMinBufferSize(
            Proto.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            onError("الميكروفون غير مدعوم على هذا الجهاز")
            return
        }

        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                Proto.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBuf, Proto.FRAME_BYTES * 4)
            )
        } catch (e: Exception) {
            onError("تعذر فتح الميكروفون")
            return
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            onError("تعذر فتح الميكروفون (قد يكون مستخدماً في تطبيق آخر)")
            return
        }

        route.acquire()
        var aec: AcousticEchoCanceler? = null
        var ns: NoiseSuppressor? = null
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(rec.audioSessionId)?.also { it.setEnabled(true) }
            }
            if (NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(rec.audioSessionId)?.also { it.setEnabled(true) }
            }

            rec.startRecording()
            val frame = ByteArray(Proto.FRAME_BYTES)
            while (flag.get()) {
                var off = 0
                while (off < frame.size && flag.get()) {
                    val n = rec.read(frame, off, frame.size - off)
                    if (n < 0) {
                        onError("حدث خطأ أثناء قراءة الميكروفون")
                        return
                    }
                    off += n
                }
                if (off == frame.size && flag.get()) onFrame(frame, off)
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
            aec?.release()
            ns?.release()
            route.release()
        }
    }
}
