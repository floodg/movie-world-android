package com.movieworld.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.io.ByteArrayInputStream
import java.net.*
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

data class Renderer(val name: String, val id: String, val description: String, val control: String, val service: String)
data class TvPosition(val seconds: Long, val duration: Long, val state: String)

class TvNetwork(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val network: Network = connectivity.allNetworks.firstOrNull {
        connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    } ?: error("Connect the phone and TV to the same Wi-Fi network.")
    val address: InetAddress = connectivity.getLinkProperties(network)?.linkAddresses
        ?.map { it.address }?.firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
        ?: error("Wi-Fi does not have a local IPv4 address.")
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)

    fun validate(url: String): URL {
        val parsed = URL(url)
        require(parsed.protocol == "http" && parsed.userInfo == null &&
            parsed.ref == null && parsed.host.isNotBlank()) { "Enter a local HTTP TV description URL." }
        val addresses = network.getAllByName(parsed.host)
        require(addresses.isNotEmpty() && addresses.all {
            it is Inet4Address && it.isSiteLocalAddress && !it.isLoopbackAddress && !it.isMulticastAddress
        }) { "The TV address must be on a private local network." }
        return parsed
    }

    fun http(url: String, action: String? = null, body: String? = null): String {
        val connection = network.openConnection(validate(url)) as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 6_000
            connection.readTimeout = 6_000
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
                connection.setRequestProperty("SOAPACTION", action)
                val bytes = body.toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val code = connection.responseCode
            check(code in 200..299) { "TV returned HTTP $code. Check that it supports DLNA remote playback." }
            return connection.inputStream.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= 1024 * 1024) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                val bytes = output.toByteArray()
                check(bytes.size <= 1024 * 1024) { "TV response was too large." }
                bytes.toString(Charsets.UTF_8)
            }
        } finally { connection.disconnect() }
    }

    fun renderer(description: String): Renderer {
        val document = xml(http(description))
        val service = document.getElementsByTagNameNS("*", "service").let { nodes ->
            (0 until nodes.length).map { nodes.item(it) as Element }
                .firstOrNull { it.text("serviceType").startsWith("urn:schemas-upnp-org:service:AVTransport:") }
        } ?: error("This TV does not advertise DLNA remote playback.")
        val base = document.text("URLBase").ifBlank { description }
        val control = URL(URL(base), service.text("controlURL")).toString()
        validate(control)
        return Renderer(document.text("friendlyName").ifBlank { "TV" },
            document.text("UDN").ifBlank { description }, description, control, service.text("serviceType"))
    }

    fun discover(): List<Renderer> {
        val lock = wifi.createMulticastLock("movie-world-discovery").apply { setReferenceCounted(false); acquire() }
        try {
            val locations = linkedSetOf<String>()
            DatagramSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(address, 0))
                network.bindSocket(socket)
                socket.soTimeout = 700
                listOf("urn:schemas-upnp-org:device:MediaRenderer:1", "ssdp:all").forEach { target ->
                    val bytes = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $target\r\n\r\n").toByteArray()
                    socket.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName("239.255.255.250"), 1900))
                }
                val until = android.os.SystemClock.elapsedRealtime() + 4_000
                while (android.os.SystemClock.elapsedRealtime() < until) {
                    try {
                        val packet = DatagramPacket(ByteArray(16_384), 16_384)
                        socket.receive(packet)
                        String(packet.data, 0, packet.length).lineSequence().forEach { line ->
                            if (line.substringBefore(':').equals("location", true))
                                locations += line.substringAfter(':').trim()
                        }
                    } catch (_: SocketTimeoutException) { }
                }
            }
            return locations.take(24).mapNotNull {
                try { renderer(it) } catch (_: Exception) { null }
            }.distinctBy { it.id }
        } finally { lock.release() }
    }

    fun action(tv: Renderer, name: String, args: Map<String, String> = emptyMap()): Element {
        val params = (mapOf("InstanceID" to "0") + args).entries.joinToString("") {
            "<${it.key}>${escapeXml(it.value)}</${it.key}>"
        }
        return xml(http(tv.control, "\"${tv.service}#$name\"",
            """<?xml version="1.0" encoding="utf-8"?>
               <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
               <s:Body><u:$name xmlns:u="${tv.service}">$params</u:$name></s:Body></s:Envelope>"""))
    }

    fun play(tv: Renderer, url: String, title: String, mime: String) {
        val metadata = """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
            <item id="1" parentID="0" restricted="1"><dc:title>${escapeXml(title)}</dc:title>
            <upnp:class>object.item.videoItem.movie</upnp:class>
            <res protocolInfo="http-get:*:$mime:DLNA.ORG_OP=01;DLNA.ORG_CI=0">${escapeXml(url)}</res></item></DIDL-Lite>"""
        try { action(tv, "Stop") } catch (_: Exception) { }
        action(tv, "SetAVTransportURI", mapOf("CurrentURI" to url, "CurrentURIMetaData" to metadata))
        action(tv, "Play", mapOf("Speed" to "1"))
    }

    fun position(tv: Renderer): TvPosition {
        val transport = action(tv, "GetTransportInfo").text("CurrentTransportState")
        val position = action(tv, "GetPositionInfo")
        return TvPosition(parseTime(position.text("RelTime")), parseTime(position.text("TrackDuration")), transport)
    }

    fun seek(tv: Renderer, seconds: Long) {
        action(tv, "Seek", mapOf("Unit" to "REL_TIME", "Target" to formatTime(seconds)))
    }
}

internal fun xml(value: String): Element {
    require(!value.contains("<!DOCTYPE", ignoreCase = true) && !value.contains("<!ENTITY", ignoreCase = true)) {
        "TV XML contains a prohibited document type."
    }
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true



        isExpandEntityReferences = false
    }
    val builder = factory.newDocumentBuilder()
    builder.setEntityResolver { _, _ -> throw org.xml.sax.SAXException("External entities are prohibited") }
    return builder.parse(ByteArrayInputStream(value.toByteArray())).documentElement
}
internal fun Element.text(name: String): String = getElementsByTagNameNS("*", name).item(0)?.textContent.orEmpty()
internal fun escapeXml(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;")
    .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
internal fun parseTime(value: String): Long {
    val parts = value.substringBefore('.').split(":").map { it.toLongOrNull() ?: return -1 }
    return if (parts.size == 3) parts[0] * 3600 + parts[1] * 60 + parts[2] else -1
}
internal fun formatTime(seconds: Long): String {
    val value = seconds.coerceAtLeast(0)
    return "%02d:%02d:%02d".format(java.util.Locale.US, value / 3600, value / 60 % 60, value % 60)
}
