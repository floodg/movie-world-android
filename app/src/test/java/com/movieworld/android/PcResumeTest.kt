package com.movieworld.android

import org.junit.Assert.assertEquals
import org.junit.Test

class PcResumeTest {
    @Test fun newerPcProgressWins() {
        assertEquals(400L, pcResumePosition(false, 50, 1000, 100, false, 400, 1000, 200))
    }
    @Test fun newerPhoneProgressWins() {
        assertEquals(50L, pcResumePosition(false, 50, 1000, 300, false, 400, 1000, 200))
    }
    @Test fun startOverIgnoresBothPositions() {
        assertEquals(0L, pcResumePosition(true, 50, 1000, 300, false, 400, 1000, 200))
    }
    @Test fun finishedPhoneDoesNotResurrectOldPcProgress() {
        assertEquals(0L, pcResumePosition(false, 990, 1000, 300, true, 400, 1000, 200))
    }
    @Test fun absentPhoneUsesPc() {
        assertEquals(400L, pcResumePosition(false, 0, 0, 0, false, 400, 1000, 200))
    }
    @Test fun almostFinishedPcStartsFromBeginning() {
        assertEquals(0L, pcResumePosition(false, 50, 1000, 100, false, 990, 1000, 200))
    }
}
