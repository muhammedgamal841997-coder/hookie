package com.localwalkie.intercom.data

import android.content.Context
import com.localwalkie.intercom.model.PresenceStatus
import com.localwalkie.intercom.model.UserProfile
import java.util.UUID

/** تخزين محلي لهوية الموظف (الاسم، الوظيفة، الحالة) ومعرّف الجهاز. */
class ProfileStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("profile", Context.MODE_PRIVATE)

    val deviceId: String
        get() {
            val existing = prefs.getString(KEY_ID, null)
            if (existing != null) return existing
            val created = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_ID, created).apply()
            return created
        }

    fun load(): UserProfile? {
        val name = prefs.getString(KEY_NAME, null) ?: return null
        return UserProfile(
            name = name,
            role = prefs.getString(KEY_ROLE, "") ?: "",
            status = PresenceStatus.fromCode(prefs.getString(KEY_STATUS, null))
        )
    }

    fun save(profile: UserProfile) {
        prefs.edit()
            .putString(KEY_NAME, profile.name)
            .putString(KEY_ROLE, profile.role)
            .putString(KEY_STATUS, profile.status.code)
            .apply()
    }

    private companion object {
        const val KEY_ID = "device_id"
        const val KEY_NAME = "name"
        const val KEY_ROLE = "role"
        const val KEY_STATUS = "status"
    }
}
