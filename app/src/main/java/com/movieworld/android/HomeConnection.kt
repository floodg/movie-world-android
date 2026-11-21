package com.movieworld.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

data class HomePc(val name: String, val address: String)
data class HomeConnectionState(
    val wifi: Boolean = false,
    val searching: Boolean = false,
    val address: String? = null,
    val tvAvailable: Boolean = false,
    val candidates: List<HomePc> = emptyList(),
) {
    val destination get() = defaultDestination(address != null, tvAvailable)
    val message get() = when {
        !wifi -> "Away · play on this device"
        searching -> "Checking home connection…"
        address != null && tvAvailable -> "Home PC connected · TV ready"
        address != null -> "Home PC connected · play on this device"
        candidates.size > 1 -> "Choose your home PC below"
        else -> "PC unavailable · play on this device"
    }
}

/** The only source of PC availability, shared by UI, playback and durable workers. */
class HomeConnection private constructor(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("tv", Context.MODE_PRIVATE)
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private val nsd = app.getSystemService(NsdManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(HomeConnectionState())
    val state = mutableState.asStateFlow()
    @Volatile private var generation = 0L
    private var checkedAt = 0L
    private var refreshJob: Job? = null

    init {
        connectivity.registerNetworkCallback(NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = changed()
            override fun onLost(network: Network) = changed()
            override fun onLinkPropertiesChanged(network: Network, properties: android.net.LinkProperties) = changed()
        })
        changed()
    }

    @Synchronized private fun changed() {
        generation++
        checkedAt = 0
        mutableState.value = HomeConnectionState(wifi = wifiPresent())
        refreshJob?.cancel()
        refreshJob = scope.launch { refresh(force = true) }
    }

    private fun wifiPresent() = connectivity.allNetworks.any {
        connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    suspend fun refresh(force: Boolean = false) = withContext(Dispatchers.IO) { mutex.withLock {
        if (!force && android.os.SystemClock.elapsedRealtime() - checkedAt < 15_000) return@withLock
        val revision = generation
        if (!wifiPresent()) {
            mutableState.value = HomeConnectionState()
            return@withLock
        }
        mutableState.value = mutableState.value.copy(wifi = true, searching = true)
        if (prefs.getBoolean("pc-disabled", false)) {
            mutableState.value = HomeConnectionState(wifi = true)
            return@withLock
        }
        try { withTimeout(5000) {
            val net = TvNetwork(app)
            val saved = prefs.getString("pc-server", "").orEmpty()
            val candidates = coroutineScope {
                val discovery = async { discover(net.network) }
                val known = saved.takeIf { it.isNotBlank() && verified(net, it) }
                if (known != null) {
                    discovery.cancelAndJoin()
                    listOf(HomePc(prefs.getString("pc-name", "Home PC").orEmpty(), known))
                } else discovery.await().filter {
                    currentCoroutineContext().ensureActive()
                    verified(net, it.address)
                }.distinctBy { it.address }
            }
            val preferredName = prefs.getString("pc-name", null)
            val selected = candidates.firstOrNull { it.name == preferredName } ?: candidates.singleOrNull()
            val tv = selected != null && rendererReady(net)
            currentCoroutineContext().ensureActive()
            if (generation == revision) {
                if (selected != null) prefs.edit().putString("pc-server", selected.address).putString("pc-name", selected.name).apply()
                mutableState.value = HomeConnectionState(wifi = true, address = selected?.address,
                    tvAvailable = tv, candidates = candidates)
            }
        } } catch (_: TimeoutCancellationException) {
            if (generation == revision) mutableState.value = HomeConnectionState(wifi = wifiPresent())
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (generation == revision) mutableState.value = HomeConnectionState(wifi = wifiPresent())
        } finally {
            if (generation == revision) {
                checkedAt = android.os.SystemClock.elapsedRealtime()
                mutableState.value = mutableState.value.copy(searching = false)
            }
        }
    } }

    private fun verified(net: TvNetwork, address: String): Boolean = try {
        PcPlayback(net, address).check(timeout = 800); true
    } catch (_: Exception) { false }

    private fun rendererReady(net: TvNetwork): Boolean = try {
        val description = prefs.getString("description", null)
        val expected = prefs.getString("renderer-id", null)
        description != null && expected != null && net.renderer(description, timeout = 800).id == expected
    } catch (_: Exception) { false }

    @Suppress("DEPRECATION")
    private suspend fun discover(network: Network): List<HomePc> = coroutineScope {
        val found = ConcurrentHashMap<String, HomePc>()
        val services = Channel<NsdServiceInfo>(16)
        val resolver = launch {
            for (info in services) {
                val resolved = withTimeoutOrNull(1000) {
                    suspendCancellableCoroutine<NsdServiceInfo?> { continuation ->
                        try { nsd.resolveService(info, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                if (continuation.isActive) continuation.resume(null)
                            }
                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                if (continuation.isActive) continuation.resume(serviceInfo)
                            }
                        }) } catch (_: Exception) { if (continuation.isActive) continuation.resume(null) }
                    }
                } ?: continue
                val host = resolved.host?.takeIf { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress ?: continue
                if (resolved.port !in 1..65535) continue
                if (resolved.attributes["app"]?.toString(Charsets.UTF_8) != "movie-world" ||
                    resolved.attributes["scheme"]?.toString(Charsets.UTF_8) != "http") continue
                found[resolved.serviceName] = HomePc(resolved.serviceName, "http://$host:${resolved.port}")
            }
        }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) { }
            override fun onDiscoveryStopped(type: String) { }
            override fun onStartDiscoveryFailed(type: String, code: Int) { }
            override fun onStopDiscoveryFailed(type: String, code: Int) { }
            override fun onServiceFound(info: NsdServiceInfo) { services.trySend(info) }
            override fun onServiceLost(info: NsdServiceInfo) { found.remove(info.serviceName) }
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                nsd.discoverServices("_movieworld._tcp.", NsdManager.PROTOCOL_DNS_SD, network, app.mainExecutor, listener)
            } else nsd.discoverServices("_movieworld._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
            delay(4000)
        } finally {
            try { nsd.stopServiceDiscovery(listener) } catch (_: Exception) { }
            services.close()
            resolver.cancelAndJoin()
        }
        found.values.sortedBy { it.name }
    }

    /** Call only from IO threads. Never silently reuses a stale saved endpoint away from home. */
    fun client(): PcPlayback {
        check(wifiPresent()) { "Home PC unavailable. Connect to home Wi-Fi to use PC features." }
        val address = state.value.address ?: error("Home PC unavailable. Retry discovery in settings.")
        return PcPlayback(TvNetwork(app), address)
    }

    suspend fun choose(pc: HomePc) {
        withContext(Dispatchers.IO) { PcPlayback(TvNetwork(app), pc.address).check() }
        prefs.edit().putString("pc-server", pc.address).putString("pc-name", pc.name).putBoolean("pc-disabled", false).apply()
        refresh(force = true)
    }

    fun usePhoneOnly() {
        prefs.edit().putBoolean("pc-disabled", true).apply()
        changed()
    }

    fun enableDiscovery() { prefs.edit().putBoolean("pc-disabled", false).apply(); changed() }

    companion object {
        @Volatile private var instance: HomeConnection? = null
        fun get(context: Context): HomeConnection = instance ?: synchronized(this) {
            instance ?: HomeConnection(context).also { instance = it }
        }
    }
}
