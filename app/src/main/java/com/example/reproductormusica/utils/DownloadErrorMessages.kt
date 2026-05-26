package com.example.reproductormusica.utils

import android.content.Context
import com.example.reproductormusica.R

/**
 * Convierte excepciones de yt-dlp / red en mensajes claros para el usuario.
 */
object DownloadErrorMessages {

    fun userMessage(context: Context, throwable: Throwable): String {
        val combined = buildString {
            append(throwable.message.orEmpty())
            var c = throwable.cause
            repeat(6) {
                if (c == null) return@repeat
                append(' ')
                append(c.message.orEmpty())
                c = c.cause
            }
        }.lowercase()

        if (indicatesDrm(combined)) {
            return context.getString(R.string.download_error_drm)
        }
        return throwable.message?.trim()?.takeIf { it.isNotEmpty() }
            ?: context.getString(R.string.download_error_unknown)
    }

    private fun indicatesDrm(text: String): Boolean {
        if ("drm" in text) return true
        if ("widevine" in text) return true
        if ("content protection" in text) return true
        if ("playready" in text) return true
        if ("fairplay" in text) return true
        return false
    }
}
