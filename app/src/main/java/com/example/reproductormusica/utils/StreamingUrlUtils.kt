package com.example.reproductormusica.utils

import android.net.Uri

/**
 * Deduce si una URL apunta a una lista de reproducción (YouTube, SoundCloud, etc.) o a un solo ítem.
 * Devuelve null si no se puede deducir (el usuario puede usar el interruptor manual).
 */
object StreamingUrlUtils {

    private val youtubeListParam = Regex("[?&]list=([^&]+)", RegexOption.IGNORE_CASE)

    fun playlistInferenceFromUrl(rawUrl: String): Boolean? {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return null
        val uri = try {
            Uri.parse(trimmed)
        } catch (_: Exception) {
            return null
        }
        val host = uri.host?.lowercase().orEmpty()
        val path = uri.path?.lowercase().orEmpty()
        val full = trimmed.lowercase()

        when {
            host.contains("youtube.com") || host.contains("youtu.be") || host == "m.youtube.com" -> {
                when {
                    path.contains("/playlist") -> return true
                    else -> {
                        val listId = uri.getQueryParameter("list") ?: youtubeListParam.find(full)?.groupValues?.get(1)
                        if (listId.isNullOrBlank()) {
                            return if ("watch" in path || host.contains("youtu.be")) false else null
                        }
                        if (listId.startsWith("pl", ignoreCase = true)) return true
                        if (listId.startsWith("ol", ignoreCase = true)) return true
                        if (listId.startsWith("rd", ignoreCase = true)) return true
                        if (listId.startsWith("uu", ignoreCase = true)) return true
                        if (listId.length >= 13) return true
                        return true
                    }
                }
            }
            host.contains("soundcloud.com") && path.contains("/sets/") -> return true
            host.contains("bandcamp.com") && path.contains("/album/") -> return true
        }
        return null
    }

    fun effectivePlaylistMode(url: String, userWantsPlaylist: Boolean): Boolean {
        return playlistInferenceFromUrl(url) ?: userWantsPlaylist
    }
}
