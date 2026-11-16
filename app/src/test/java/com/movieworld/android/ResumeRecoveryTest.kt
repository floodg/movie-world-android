package com.movieworld.android

import org.junit.Assert.*
import org.junit.Test

class ResumeRecoveryTest {
    @Test fun normalStopDoesNotRestartMovie() {
        assertEquals(ResumeRecovery.Decision.STOP, ResumeRecovery().stopped(100))
    }
    @Test fun briefAndLongPausesBothAllowResumeRecovery() {
        for (pauseLength in listOf(500L, 600_000L)) {
            val recovery = ResumeRecovery()
            recovery.pause(100)
            recovery.resume(100 + pauseLength, 5269)
            assertEquals(5269L, recovery.position)
            assertEquals(ResumeRecovery.Decision.WAIT, recovery.stopped(100 + pauseLength + 2000))
            assertEquals(ResumeRecovery.Decision.RELOAD, recovery.stopped(100 + pauseLength + 6000))
        }
    }
    @Test fun recoveryIsBoundedAndDoesNotLoop() {
        val recovery = ResumeRecovery()
        recovery.resume(100, 300)
        assertEquals(ResumeRecovery.Decision.RELOAD, recovery.stopped(6100))
        assertEquals(ResumeRecovery.Decision.WAIT, recovery.stopped(8100))
        assertEquals(ResumeRecovery.Decision.STOP, recovery.stopped(21100))
        assertEquals(ResumeRecovery.Decision.STOP, recovery.stopped(30000))
    }
    @Test fun laterUnrelatedStopDoesNotRestartMovie() {
        val recovery = ResumeRecovery()
        recovery.resume(100, 300)
        assertEquals(ResumeRecovery.Decision.STOP, recovery.stopped(30101))
    }
    @Test fun pauseCancelsPendingRecovery() {
        val recovery = ResumeRecovery()
        recovery.resume(100, 300)
        recovery.pause(200)
        assertEquals(ResumeRecovery.Decision.WAIT, recovery.stopped(1000))
        assertEquals(ResumeRecovery.Decision.STOP, recovery.stopped(6200))
    }
    @Test fun successfulResumeDisarmsRecoveryBeforeTvStop() {
        val recovery = ResumeRecovery()
        recovery.resume(100, 300)
        assertFalse(recovery.observe("PLAYING", 2100))
        assertEquals(ResumeRecovery.Decision.STOP, recovery.stopped(10100))
        assertFalse(recovery.expired(60100))
    }
    @Test fun stalledReloadExpiresForEveryTransportState() {
        for (state in listOf("TRANSITIONING", "STOPPED", "NO_MEDIA_PRESENT", "UNKNOWN", "PAUSED_PLAYBACK")) {
            val recovery = ResumeRecovery()
            recovery.resume(100, 300)
            assertEquals(ResumeRecovery.Decision.RELOAD, recovery.stopped(6100))
            assertFalse(recovery.observe(state, 21099))
            assertTrue(state, recovery.observe(state, 21100))
        }
    }
    @Test fun initialResumeAlsoExpiresWhenTvNeverStopsOrPlays() {
        val recovery = ResumeRecovery()
        recovery.resume(100, 300)
        assertFalse(recovery.observe("TRANSITIONING", 30099))
        assertTrue(recovery.observe("TRANSITIONING", 30100))
    }
    @Test fun restoredPlaybackIsConfirmedOnlyAfterSeekCompletes() {
        val recovery = ResumeRecovery()
        recovery.resume(100, 300)
        recovery.stopped(6100)
        assertFalse(recovery.observe("PLAYING", 8100, seeking = true))
        assertTrue(recovery.expired(21100))
        assertFalse(recovery.observe("PLAYING", 21100, seeking = false))
        assertEquals(ResumeRecovery.Decision.STOP, recovery.stopped(22100))
        assertFalse(recovery.expired(60000))
    }
}
