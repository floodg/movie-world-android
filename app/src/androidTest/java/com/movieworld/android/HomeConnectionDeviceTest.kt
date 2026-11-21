package com.movieworld.android

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeConnectionDeviceTest {
    @Test fun savedPcIsVerifiedOffMainThreadAndMissingTvDefaultsToDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val net = TvNetwork(context)
        val prefs = context.getSharedPreferences("tv", Context.MODE_PRIVATE)
        val keys = listOf("pc-server", "pc-name", "description", "pc-disabled")
        val before = keys.associateWith { prefs.all[it] }
        val server = ServerSocket().apply { bind(InetSocketAddress(net.address, 0)); soTimeout = 1000 }
        val executor = Executors.newSingleThreadExecutor()
        executor.submit {
            while (!server.isClosed) try {
                server.accept().use { socket ->
                    socket.soTimeout = 2000
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrBlank()) { }
                    val bytes = "{\"enabled\":true,\"subtitles\":true}".toByteArray()
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(bytes); flush()
                    }
                }
            } catch (_: java.io.IOException) { }
        }
        val address = "http://${net.address.hostAddress}:${server.localPort}"
        val home = HomeConnection.get(context)
        try {
            prefs.edit().putString("pc-server", address).putString("pc-name", "Test PC")
                .remove("description").putBoolean("pc-disabled", false).commit()
            // Exercise the same main-thread caller as Compose; HTTP must be dispatched to IO.
            runBlocking(kotlinx.coroutines.Dispatchers.Main) { home.refresh(force = true) }
            assertEquals(address, home.state.value.address)
            assertEquals(PlaybackDestination.DEVICE, home.state.value.destination)
            assertTrue(home.state.value.wifi)
            assertFalse(home.state.value.searching)
            assertNotNull(home.client())
        } finally {
            val edit = prefs.edit()
            before.forEach { (key, value) -> when (value) {
                null -> edit.remove(key)
                is String -> edit.putString(key, value)
                is Boolean -> edit.putBoolean(key, value)
            } }
            edit.commit()
            server.close(); executor.shutdownNow()
            runBlocking { home.refresh(force = true) }
        }
    }
}
