package com.queuego.ridermaplibreosm

import android.content.Context
import java.io.File

data class OfflineMapPackage(
    val id: String,
    val downloadUrl: String,
    val sha256: String? = null
) {
    init {
        require(id.matches(Regex("[A-Za-z0-9._-]{1,64}"))) {
            "Offline map id may contain only letters, numbers, dot, underscore and dash"
        }
        require(downloadUrl.startsWith("https://")) {
            "Offline map downloadUrl must use https"
        }
        require(sha256 == null || sha256.matches(Regex("[A-Fa-f0-9]{64}"))) {
            "Offline map sha256 must be a 64-character hex digest"
        }
    }

    fun directory(context: Context): File =
        File(context.filesDir, "queuego-map-packages").apply { mkdirs() }

    fun partialFile(context: Context): File =
        File(directory(context), id + ".pmtiles.part")

    fun finalFile(context: Context): File =
        File(directory(context), id + ".pmtiles")

    fun installedSourceUri(context: Context): String =
        "pmtiles://file://" + finalFile(context).absolutePath
}
