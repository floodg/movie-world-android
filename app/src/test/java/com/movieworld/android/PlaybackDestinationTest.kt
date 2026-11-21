package com.movieworld.android

import com.movieworld.android.ui.playLabel
import org.junit.Assert.*
import org.junit.Test

class PlaybackDestinationTest {
    @Test fun tvIsDefaultOnlyWhenPcAndRendererAreBothAvailable() {
        assertEquals(PlaybackDestination.TV, defaultDestination(true, true))
        assertEquals(PlaybackDestination.DEVICE, defaultDestination(true, false))
        assertEquals(PlaybackDestination.DEVICE, defaultDestination(false, true))
        assertEquals(PlaybackDestination.DEVICE, defaultDestination(false, false))
    }
    @Test fun noWifiAndMissingPcHaveUsablePhoneDefaults() {
        assertEquals(PlaybackDestination.DEVICE, HomeConnectionState().destination)
        assertEquals(PlaybackDestination.DEVICE, HomeConnectionState(wifi = true).destination)
        assertEquals(PlaybackDestination.DEVICE, HomeConnectionState(wifi = true, address = "http://192.168.1.2").destination)
    }
    @Test fun labelsIdentifyPhonePlaybackAndResume() {
        assertEquals("Play on this device", playLabel(0, PlaybackDestination.DEVICE))
        assertEquals("Resume on this device · 00:02:00", playLabel(120, PlaybackDestination.DEVICE))
        assertEquals("Play on TV", playLabel(0, PlaybackDestination.TV))
    }
}
