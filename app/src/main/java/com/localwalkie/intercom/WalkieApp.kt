package com.localwalkie.intercom

import android.app.Application
import com.localwalkie.intercom.core.IntercomController
import com.localwalkie.intercom.service.Notifications

class WalkieApp : Application() {
    lateinit var controller: IntercomController
        private set

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        controller = IntercomController(this)
    }
}
