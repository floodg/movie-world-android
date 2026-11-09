package com.movieworld.android

import org.junit.Assert.*
import org.junit.Test

class SetupErrorsTest {
    @Test fun requestHeadersNeverReachTheSettingsScreen() {
        val raw = "invalid_credentials\nURL: https://project.supabase.co/auth/v1/token\nHeaders: Authorization=Bearer secret apikey=secret"
        val message = safeSetupError(IllegalStateException(raw))
        assertFalse(message.contains("secret"))
        assertFalse(message.contains("supabase.co"))
        assertFalse(message.contains("Headers"))
    }
    @Test fun exportDoesNotOverwriteEqualTimeLocalEdits() {
        val local = PersonalRecord("1305", 10, "0abcdef-phone", removed = 10)
        val old = PersonalRecord("1305", 10, "!supabase")
        assertEquals(local, SnapshotCodec.merge(listOf(local, old)).single())
    }
}
