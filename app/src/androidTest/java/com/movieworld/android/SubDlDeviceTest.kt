package com.movieworld.android
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
class SubDlDeviceTest {
    @Test fun glassSearchAndDownload() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("subdlLive") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = SubtitleProvider(context)
        val matches = provider.search(SubtitleIdentity("Glass", "2019", "tt6823368", "450465"))
        assertTrue("Glass should have subtitle matches", matches.isNotEmpty())
        assertTrue(matches.all { !it.url.contains('?') })
        val bytes = provider.download(matches.first())
        assertTrue(SrtSubtitles.prepare(bytes.inputStream()).contains(" --> "))
        println("SubDL Glass: ${matches.size} matches, valid SRT downloaded")
    }
}
