package com.localwalkie.intercom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.localwalkie.intercom.ui.IntercomApp
import com.localwalkie.intercom.ui.WalkieTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val controller = (application as WalkieApp).controller
        setContent {
            WalkieTheme {
                IntercomApp(controller)
            }
        }
    }
}
