package com.example.reproductormusica.utils

import android.net.Uri

fun Uri.sanitizedFileName(): String {
    val path = this.path ?: this.toString()
    val fileName = path.substringAfterLast('/').substringBeforeLast('.')
    return fileName.ifEmpty { "Canción desconocida" }
}

fun String.sanitizedFileName(): String {
    val fileName = this.substringAfterLast('/').substringBeforeLast('.')
    return fileName.ifEmpty { "Canción desconocida" }
}