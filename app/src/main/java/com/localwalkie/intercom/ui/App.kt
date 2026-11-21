package com.localwalkie.intercom.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localwalkie.intercom.core.IntercomController
import com.localwalkie.intercom.model.CallState
import com.localwalkie.intercom.model.UiState
import com.localwalkie.intercom.service.IntercomService

private sealed interface Screen {
    data object Contacts : Screen
    data object Profile : Screen
    data class Talk(val peerId: String?) : Screen
}

private fun hasMicPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.RECORD_AUDIO)
    }

@Composable
fun IntercomApp(controller: IntercomController) {
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var micGranted by remember { mutableStateOf(hasMicPermission(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { micGranted = hasMicPermission(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { micGranted = hasMicPermission(context) }

    // تشغيل الخدمة الأمامية بمجرد توفر الإذن والملف الشخصي
    val hasProfile = state.profile != null
    LaunchedEffect(micGranted, hasProfile) {
        if (micGranted && hasProfile) IntercomService.start(context)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            controller.clearMessage()
        }
    }

    // الواجهة عربية بالكامل → نفرض اتجاه RTL بغض النظر عن لغة الجهاز
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when {
                !micGranted -> PermissionScreen(
                    onRequest = { permissionLauncher.launch(requiredPermissions()) }
                )
                state.profile == null -> ProfileScreen(
                    initial = null,
                    onSave = controller::saveProfile,
                    onBack = null
                )
                else -> MainFlow(state, controller)
            }
        }
    }
}

@Composable
private fun MainFlow(state: UiState, controller: IntercomController) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Contacts) }

    if (state.call != CallState.Idle) {
        CallScreen(
            call = state.call,
            onAccept = controller::acceptCall,
            onReject = controller::rejectCall,
            onEnd = controller::endCall
        )
        return
    }

    BackHandler(enabled = screen != Screen.Contacts) { screen = Screen.Contacts }

    when (val s = screen) {
        Screen.Contacts -> ContactsScreen(
            state = state,
            onOpenTalk = { peer -> screen = Screen.Talk(peer?.id) },
            onCall = controller::requestCall,
            onRefresh = controller::refreshPeers,
            onStartService = { IntercomService.start(context) },
            onEditProfile = { screen = Screen.Profile },
            onStatusChange = controller::setStatus
        )

        Screen.Profile -> ProfileScreen(
            initial = state.profile,
            onSave = {
                controller.saveProfile(it)
                screen = Screen.Contacts
            },
            onBack = { screen = Screen.Contacts }
        )

        is Screen.Talk -> {
            val peer = s.peerId?.let { id -> state.peers.firstOrNull { it.id == id } }
            TalkScreen(
                peer = peer,
                broadcast = s.peerId == null,
                state = state,
                onBack = { screen = Screen.Contacts },
                onStart = { controller.startTalking(peer) },
                onStop = controller::stopTalking,
                onCall = { peer?.let(controller::requestCall) }
            )
        }
    }
}
