package com.localwalkie.intercom.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import com.localwalkie.intercom.net.Proto
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * تشغيل الصوت الوارد. الـ AudioTrack يُنشأ عند بداية كل "كلمة" ويُحرَّر بعد ثانية من الصمت،
 * والطابور محدود حتى لا يتراكم التأخير عند ازدحام الشبكة.
 */
class Playback(private val route: AudioRoute) {
    private val queue = LinkedBlockingQueue<ByteArray>()
    @Volatile private var running = false
    private var worker: Thread? = null

    @Synchronized
    fun start() {
        if (running) return
        running = true
        queue.clear()
        worker = Thread({ loop() }, "wt-playback").also { it.isDaemon = true; it.start() }
    }

    @Synchronized
    fun stop() {
        running = false
        worker?.interrupt()
        worker = null
        queue.clear()
    }

    fun enqueue(pcm: ByteArray) {
        if (!running) return
        while (queue.size >= MAX_QUEUED_FRAMES) queue.poll()
        queue.offer(pcm)
    }

    private fun loop() {
        var track: AudioTrack? = null
        var lastFrameAt = 0L
        try {
            while (running) {
                val frame = queue.poll(250, TimeUnit.MILLISECONDS)
                if (frame == null) {
                    if (track != null && SystemClock.elapsedRealtime() - lastFrameAt > IDLE_RELEASE_MS) {
                        closeTrack(track)
                        track = null
                    }
                    continue
                }
                if (track == null) {
                    route.acquire()
                    track = buildTrack()
                    track.play()
                    // مخزن صغير (60ms صمت) لامتصاص تذبذب الشبكة في بداية الكلام
                    val prime = ByteArray(Proto.FRAME_BYTES * 3)
                    track.write(prime, 0, prime.size)
                }
                track.write(frame, 0, frame.size)
                lastFrameAt = SystemClock.elapsedRealtime()
            }
        } catch (_: InterruptedException) {
            // إيقاف طبيعي
        } finally {
            track?.let { closeTrack(it) }
        }
    }

    private fun closeTrack(track: AudioTrack) {
        runCatching { track.stop() }
        track.release()
        route.release()
    }

    private fun buildTrack(): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            Proto.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(Proto.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(max(minBuf, Proto.FRAME_BYTES * 6))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
    }

    private companion object {
        const val MAX_QUEUED_FRAMES = 15      // ~300ms
        const val IDLE_RELEASE_MS = 1000L
    }
}
