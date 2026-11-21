package com.movieworld.android.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import com.movieworld.android.CastState
import com.movieworld.android.PlaybackDestination

/** Remember user dismissal across rotation, and automatically open only a new session. */
internal class DevicePlayerPresentation(
    openedSession: String = "",
    expanded: Boolean = false,
    stoppedSession: String = "",
) {
    private var openedSession by mutableStateOf(openedSession)
    private var stoppedSession by mutableStateOf(stoppedSession)
    var expanded by mutableStateOf(expanded)
        private set

    fun observe(state: CastState) {
        if (!state.active || state.destination != PlaybackDestination.DEVICE) {
            expanded = false
        } else if (state.sessionId.isNotBlank() && openedSession != state.sessionId) {
            openedSession = state.sessionId
            expanded = true
        }
    }

    fun open(state: CastState) {
        if (state.active && state.destination == PlaybackDestination.DEVICE && stoppedSession != state.sessionId) {
            openedSession = state.sessionId
            expanded = true
        }
    }

    fun close() { expanded = false }
    fun stop(state: CastState) {
        openedSession = state.sessionId
        stoppedSession = state.sessionId
        expanded = false
    }

    fun showMiniPlayer(state: CastState) =
        state.destination != PlaybackDestination.DEVICE || state.sessionId.isBlank() || stoppedSession != state.sessionId

    companion object {
        val Saver = listSaver<DevicePlayerPresentation, Any>(
            save = { listOf(it.openedSession, it.expanded, it.stoppedSession) },
            restore = { DevicePlayerPresentation(it[0] as String, it[1] as Boolean, it[2] as String) },
        )
    }
}
