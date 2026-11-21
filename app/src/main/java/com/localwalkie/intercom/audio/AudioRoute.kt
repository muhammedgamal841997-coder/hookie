package com.localwalkie.intercom.audio

import android.content.Context
import android.media.AudioManager

/**
 * يحوّل وضع الصوت إلى "اتصال" مع تشغيل السماعة الخارجية طالما هناك إرسال أو استقبال،
 * ثم يعيد الإعدادات السابقة عند انتهاء آخر مستخدم (عدّاد مراجع).
 */
class AudioRoute(context: Context) {
    private val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var count = 0
    private var previousMode = AudioManager.MODE_NORMAL
    private var previousSpeaker = false

    @Suppress("DEPRECATION")
    @Synchronized
    fun acquire() {
        if (count++ == 0) {
            previousMode = am.mode
            previousSpeaker = am.isSpeakerphoneOn
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = true
        }
    }

    @Suppress("DEPRECATION")
    @Synchronized
    fun release() {
        if (count > 0 && --count == 0) {
            am.isSpeakerphoneOn = previousSpeaker
            am.mode = previousMode
        }
    }
}
