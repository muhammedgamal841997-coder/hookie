package com.localwalkie.intercom.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localwalkie.intercom.model.CallState
import com.localwalkie.intercom.model.Peer
import com.localwalkie.intercom.model.PresenceStatus
import com.localwalkie.intercom.model.UiState
import com.localwalkie.intercom.model.UserProfile
import kotlinx.coroutines.delay

// ======================================================================= الأذونات

@Composable
fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🎙", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "نحتاج إذن الميكروفون",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "حتى تتمكن من التحدث مع زملائك عبر الشبكة المحلية. " +
                "لا يتم إرسال أي صوت خارج شبكة الشركة ولا نحتاج إنترنت.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) { Text("السماح") }
    }
}

// ======================================================================= الملف الشخصي

@Composable
fun ProfileScreen(
    initial: UserProfile?,
    onSave: (UserProfile) -> Unit,
    onBack: (() -> Unit)?
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var role by rememberSaveable { mutableStateOf(initial?.role ?: "") }
    var status by remember { mutableStateOf(initial?.status ?: PresenceStatus.AVAILABLE) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
            }
        }
        Text(
            if (initial == null) "أهلاً بك 👋" else "الملف الشخصي",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "هذه البيانات تظهر لزملائك على الشبكة المحلية فقط.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            label = { Text("الاسم") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            )
        )
        OutlinedTextField(
            value = role,
            onValueChange = { role = it.take(40) },
            label = { Text("المسمى الوظيفي") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )

        Text("الحالة", style = MaterialTheme.typography.titleSmall)
        StatusChips(selected = status, onSelect = { status = it })

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onSave(UserProfile(name.trim(), role.trim(), status)) },
            enabled = name.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(if (initial == null) "ابدأ" else "حفظ")
        }
    }
}

@Composable
private fun StatusChips(selected: PresenceStatus, onSelect: (PresenceStatus) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PresenceStatus.entries.forEach { st ->
            FilterChip(
                selected = st == selected,
                onClick = { onSelect(st) },
                label = { Text(st.label) }
            )
        }
    }
}

// ======================================================================= قائمة الموظفين

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    state: UiState,
    onOpenTalk: (Peer?) -> Unit,
    onCall: (Peer) -> Unit,
    onRefresh: () -> Unit,
    onStartService: () -> Unit,
    onEditProfile: () -> Unit,
    onStatusChange: (PresenceStatus) -> Unit
) {
    val profile = state.profile ?: return
    val speaker = state.peers.firstOrNull { it.id == state.speakingPeerId }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("الاتصال الداخلي", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث القائمة")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!state.serviceRunning) {
                item(key = "stopped") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("الخدمة متوقفة", modifier = Modifier.weight(1f))
                            Button(onClick = onStartService) { Text("تشغيل") }
                        }
                    }
                }
            }

            if (state.speakingPeerId != null) {
                item(key = "speaking") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "🔊 ${speaker?.name ?: "أحد الزملاء"} يتحدث الآن",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            item(key = "me") {
                MyCard(profile, onStatusChange, onEditProfile)
            }

            item(key = "broadcast") {
                Button(
                    onClick = { onOpenTalk(null) },
                    enabled = state.peers.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text("📢  بث لجميع المتصلين (${state.peers.size})")
                }
            }

            item(key = "header") {
                Text(
                    "المتصلون الآن (${state.peers.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (state.peers.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "لا يوجد أحد متصل حالياً.\n" +
                            "تأكد أن جميع الأجهزة على نفس شبكة الواي فاي وأن التطبيق يعمل عندهم، ثم اضغط تحديث.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            items(state.peers, key = { it.id }) { peer ->
                PeerCard(
                    peer = peer,
                    speaking = peer.id == state.speakingPeerId,
                    onClick = { onOpenTalk(peer) },
                    onCall = { onCall(peer) }
                )
            }
        }
    }
}

@Composable
private fun MyCard(
    profile: UserProfile,
    onStatusChange: (PresenceStatus) -> Unit,
    onEdit: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(profile.name, profile.status)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.name, fontWeight = FontWeight.Bold)
                    if (profile.role.isNotBlank()) {
                        Text(
                            profile.role,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "تعديل الملف الشخصي")
                }
            }
            StatusChips(selected = profile.status, onSelect = onStatusChange)
            if (profile.status == PresenceStatus.IN_MEETING) {
                Text(
                    "في وضع \"في اجتماع\" لا يُشغَّل صوت النداءات الواردة وتُرفض المكالمات.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PeerCard(
    peer: Peer,
    speaking: Boolean,
    onClick: () -> Unit,
    onCall: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (speaking) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            if (speaking) 2.dp else 1.dp,
            if (speaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(peer.name, peer.status)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(peer.name, fontWeight = FontWeight.SemiBold)
                if (peer.role.isNotBlank()) {
                    Text(
                        peer.role,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(peer.status)
            }
            FilledTonalIconButton(onClick = onCall) {
                Icon(Icons.Default.Call, contentDescription = "مكالمة صوتية")
            }
        }
    }
}

// ======================================================================= شاشة التحدث (PTT)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalkScreen(
    peer: Peer?,
    broadcast: Boolean,
    state: UiState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCall: () -> Unit
) {
    // إذا غادر المستخدم الشاشة وهو ما زال ضاغطاً — نوقف الإرسال
    DisposableEffect(Unit) { onDispose { onStop() } }

    val offline = !broadcast && peer == null
    val canTalk = !offline && (!broadcast || state.peers.isNotEmpty())
    val title = when {
        broadcast -> "بث لجميع المتصلين"
        peer != null -> peer.name
        else -> "غير متصل"
    }
    val peerSpeaking = peer != null && state.speakingPeerId == peer.id

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (peer != null) {
                Avatar(peer.name, peer.status, size = 88.dp)
                Spacer(Modifier.height(12.dp))
                if (peer.role.isNotBlank()) {
                    Text(peer.role, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                StatusBadge(peer.status)
            } else if (broadcast) {
                Text("📢", fontSize = 56.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "سيسمعك ${state.peers.size} من الزملاء المتصلين",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "هذا الزميل لم يعد متصلاً بالشبكة.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = when {
                    state.transmitting -> "🔴 جارٍ الإرسال"
                    peerSpeaking -> "🔊 ${peer?.name} يتحدث الآن"
                    state.speakingPeerId != null -> "🔊 أحد الزملاء يتحدث الآن"
                    else -> " "
                },
                fontWeight = FontWeight.SemiBold
            )

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                PttButton(
                    active = state.transmitting,
                    enabled = canTalk,
                    onPress = onStart,
                    onRelease = onStop
                )
            }

            if (peer != null) {
                OutlinedButton(onClick = onCall, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Call, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("مكالمة صوتية ثنائية")
                }
            }
        }
    }
}

// ======================================================================= شاشة المكالمة

@Composable
fun CallScreen(
    call: CallState,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onEnd: () -> Unit
) {
    val peer = when (call) {
        is CallState.Outgoing -> call.peer
        is CallState.Incoming -> call.peer
        is CallState.Active -> call.peer
        CallState.Idle -> return
    }

    var seconds by remember(call is CallState.Active) { mutableIntStateOf(0) }
    LaunchedEffect(call is CallState.Active) {
        if (call is CallState.Active) {
            while (true) {
                delay(1000)
                seconds++
            }
        }
    }

    val statusText = when (call) {
        is CallState.Outgoing -> "جارٍ الاتصال…"
        is CallState.Incoming -> "مكالمة واردة"
        is CallState.Active -> "%02d:%02d".format(seconds / 60, seconds % 60)
        CallState.Idle -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Avatar(peer.name, peer.status, size = 120.dp)
        Spacer(Modifier.height(20.dp))
        Text(peer.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (peer.role.isNotBlank()) {
            Text(peer.role, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Text(statusText, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)

        Spacer(Modifier.weight(1f))

        if (call is CallState.Incoming) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CircleAction(RecordRed, Icons.Default.Close, "رفض", onReject)
                CircleAction(AcceptGreen, Icons.Default.Call, "رد", onAccept)
            }
        } else {
            CircleAction(RecordRed, Icons.Default.Close, "إنهاء", onEnd)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CircleAction(
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = color,
                contentColor = Color.White
            )
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label)
    }
}
