package com.queuego.ridernavigationmap

import android.content.Context
import com.valhalla.valhalla.Valhalla
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** One process-local actor, rebuilt from immutable APK assets after process death. */
object BundledOfflineRouting {
    private val lock = Any()
    private var engine: Valhalla? = null

    fun provider(context: Context, online: NavigationRouteProvider?): NavigationRouteProvider {
        val application = context.applicationContext
        // Only absence selects the existing online mode. A broken/partial offline package
        // must fail visibly, never switch to a hidden network dependency.
        val bundled = application.assets.list("routing").orEmpty().isNotEmpty()
        if (!bundled) return online ?: object : NavigationRouteProvider {
            override suspend fun route(from: NavigationMapPoint, stop: NavigationMapStop,
                segmentId: String): NavigationRouteLeg = error("ไม่ได้ติดตั้งข้อมูลเส้นทางออฟไลน์ และไม่มีบริการเส้นทางออนไลน์")
        }
        return ValhallaOfflineRouteProvider { request ->
            synchronized(lock) {
                val actor = engine ?: Valhalla(install(application).absolutePath).also { engine = it }
                actor.routeRaw(request)
            }
        }
    }

    /** Called only on Dispatchers.IO by the provider. Never load a graph from a shared directory. */
    private fun install(context: Context): File {
        val manifestText = context.assets.open("routing/manifest.json").use {
            readBoundedRoutingText(it, 64 * 1024)
        }
        val manifest = JSONObject(manifestText)
        require(manifest.getInt("format_version") == 1 &&
            manifest.getString("engine") == "valhalla" &&
            manifest.getString("profile") == "motorcycle" &&
            manifest.getString("licence") == "ODbL-1.0") { "Unsupported offline graph package" }
        val graph = manifest.getJSONObject("graph")
        val config = manifest.getJSONObject("config")
        require(config.getLong("bytes") in 1..64 * 1024L) { "Offline routing config is too large" }
        val graphHash = graph.getString("sha256")
        require(graphHash.matches(Regex("[a-f0-9]{64}"))) { "Invalid offline graph checksum" }
        val root = File(context.noBackupFilesDir, "routing/$graphHash")
        check(root.isDirectory || root.mkdirs()) { "Cannot create private offline graph directory" }
        // Verify cached bytes once per process too: never trust a marker after disk damage.
        copyVerified(context, "tiles.tar", File(root, "tiles.tar"), graph)
        copyVerified(context, "valhalla.json", File(root, "source.json"), config)
        val local = JSONObject(File(root, "source.json").readText())
        val mjolnir = local.getJSONObject("mjolnir")
        mjolnir.put("tile_extract", File(root, "tiles.tar").absolutePath)
        mjolnir.put("tile_dir", "")
        // This actor can ONLY use local tiles. No remote tile requests, traffic or services.
        mjolnir.put("tile_url", "").put("traffic_extract", "").put("admin", "")
        mjolnir.put("max_cache_size", 64 * 1024 * 1024)
        local.optJSONObject("additional_data")?.put("elevation", "")
        val target = File(root, "valhalla.json")
        FileOutputStream(target).use { output ->
            output.write(local.toString().toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        return target
    }

    private fun copyVerified(context: Context, name: String, target: File, specification: JSONObject) {
        val expectedBytes = specification.getLong("bytes")
        val expectedHash = specification.getString("sha256")
        require(expectedHash.matches(Regex("[a-f0-9]{64}")) && expectedBytes in 1..2_147_483_648L) {
            "Invalid offline graph file specification"
        }
        // A killed copy may leave a large partial file. Reclaim it before the
        // free-space check, including when a verified installed graph is reusable.
        val temporary = File(target.parentFile, target.name + ".part")
        check(!temporary.exists() || temporary.delete()) { "Cannot remove interrupted offline graph copy" }
        if (target.isFile && target.length() == expectedBytes && sha256(target) == expectedHash) return
        require(target.parentFile!!.usableSpace > expectedBytes + 32 * 1024 * 1024) {
            "พื้นที่จัดเก็บไม่พอสำหรับข้อมูลเส้นทางออฟไลน์"
        }
        try {
            context.assets.open("routing/$name").use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var count = 0L
                    while (true) {
                        val size = input.read(buffer)
                        if (size < 0) break
                        count += size
                        require(count <= expectedBytes) { "Offline graph file exceeds manifest size" }
                        output.write(buffer, 0, size)
                    }
                    output.fd.sync()
                    require(count == expectedBytes) { "Offline graph file is incomplete" }
                }
            }
            require(sha256(temporary) == expectedHash) { "Offline graph checksum mismatch" }
            check(temporary.renameTo(target)) { "Cannot install verified offline graph" }
        } finally {
            temporary.delete()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(128 * 1024).use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val size = input.read(buffer)
                if (size < 0) break
                digest.update(buffer, 0, size)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
\
internal fun readBoundedRoutingText(input: InputStream, maxBytes: Int): String {
    require(maxBytes > 0)
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        require(output.size().toLong() + count <= maxBytes) { "Offline graph manifest is too large" }
        output.write(buffer, 0, count)
    }
    return output.toString(Charsets.UTF_8.name())
}
