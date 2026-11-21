package com.localwalkie.intercom.model

enum class PresenceStatus(val code: String, val label: String) {
    AVAILABLE("available", "متاح"),
    IN_MEETING("meeting", "في اجتماع"),
    BUSY("busy", "مشغول"),
    AWAY("away", "غير متواجد");

    companion object {
        fun fromCode(code: String?): PresenceStatus =
            entries.firstOrNull { it.code == code } ?: AVAILABLE
    }
}

data class UserProfile(
    val name: String,
    val role: String,
    val status: PresenceStatus = PresenceStatus.AVAILABLE
)

/** موظف آخر مكتشف على الشبكة المحلية. */
data class Peer(
    val id: String,
    val name: String,
    val role: String,
    val status: PresenceStatus,
    val host: String,
    val port: Int
)

sealed interface CallState {
    data object Idle : CallState
    data class Outgoing(val peer: Peer) : CallState
    data class Incoming(val peer: Peer) : CallState
    data class Active(val peer: Peer) : CallState
}

data class UiState(
    val profile: UserProfile? = null,
    val serviceRunning: Boolean = false,
    val peers: List<Peer> = emptyList(),
    val speakingPeerId: String? = null,
    val transmitting: Boolean = false,
    val call: CallState = CallState.Idle,
    val message: String? = null
)
