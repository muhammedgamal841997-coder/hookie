package com.localwalkie.intercom.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localwalkie.intercom.model.PresenceStatus

/** دائرة بحرف الاسم الأول مع نقطة حالة الاتصال. */
@Composable
fun Avatar(name: String, status: PresenceStatus, size: Dp = 48.dp) {
    val initial = name.trim().firstOrNull()?.toString() ?: "؟"
    Box(modifier = Modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp
            )
        }
        Box(
            modifier = Modifier
                .size(size * 0.3f)
                .align(Alignment.BottomEnd)
                .clip(CircleShape)
                .background(statusColor(status))
                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
        )
    }
}

@Composable
fun StatusBadge(status: PresenceStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(statusColor(status))
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** زر Push-To-Talk: يرسل طالما الإصبع مضغوط عليه. */
@Composable
fun PttButton(
    active: Boolean,
    enabled: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val transition = rememberInfiniteTransition(label = "ptt-pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(animation = tween(650), repeatMode = RepeatMode.Reverse),
        label = "ptt-scale"
    )
    val color = when {
        !enabled -> MaterialTheme.colorScheme.outline
        active -> RecordRed
        else -> MaterialTheme.colorScheme.primary
    }
    val content = if (enabled && !active) MaterialTheme.colorScheme.onPrimary else Color.White

    Box(
        modifier = Modifier
            .size(220.dp)
            .scale(if (active) pulse else 1f)
            .clip(CircleShape)
            .background(color)
            .pointerInput(enabled) {
                detectTapGestures(
                    onPress = {
                        if (enabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnPress()
                            try {
                                tryAwaitRelease()
                            } finally {
                                currentOnRelease()
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("🎙", fontSize = 56.sp)
            Text(
                text = if (active) "جارٍ الإرسال…" else "اضغط باستمرار للتحدث",
                color = content,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
