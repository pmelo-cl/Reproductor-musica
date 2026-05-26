package com.example.reproductormusica.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import com.example.reproductormusica.data.database.SongDao
import com.example.reproductormusica.models.PlaylistDownloadOutcome
import com.example.reproductormusica.models.Song
import com.example.reproductormusica.utils.MetadataFetcher
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

class DownloadRepository(
    private val songDao: SongDao,
    private val context: Context
) {
    private val metadataFetcher by lazy { MetadataFetcher.getInstance(context) }
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities =
            connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun initYoutubeDl() {
        YoutubeDL.getInstance().init(context)
        if (isNetworkAvailable()) {
            try {
                YoutubeDL.getInstance().updateYoutubeDL(context)
                Log.d("DownloadRepo", "yt-dlp updated")
            } catch (e: Exception) {
                Log.e("DownloadRepo", "yt-dlp update failed: ${e.message}")
            }
        }
    }

    private val audioExtensions =
        arrayOf("m4a", "mp4", "webm", "opus", "mp3", "ogg", "oga")

    private val audioFormatSelector =
        "bestaudio[ext=m4a]/bestaudio[ext=mp3]/bestaudio/best"

    private data class ParsedTrackTags(
        val title: String,
        val artist: String,
        val album: String?,
        val durationMs: Long,
        val embeddedArtUri: String?
    )

    private fun saveEmbeddedArtBytes(bytes: ByteArray, uniqueKey: String): String {
        val dir = File(context.cacheDir, "album_art").apply { mkdirs() }
        val name = "dl_${abs(uniqueKey.hashCode())}_${abs(bytes.contentHashCode())}.jpg"
        val f = File(dir, name)
        f.writeBytes(bytes)
        return Uri.fromFile(f).toString()
    }

    /** Lee metadatos incrustados tras `--embed-metadata` / miniatura. */
    private fun readTagsFromDownloadedFile(
        file: File,
        fallbackTitle: String,
        fallbackArtist: String
    ): ParsedTrackTags {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            val t = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.trim()?.takeIf { it.isNotBlank() } ?: fallbackTitle
            val a = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.trim()?.takeIf { it.isNotBlank() }
                ?: r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    ?.trim()?.takeIf { it.isNotBlank() }
                ?: fallbackArtist
            val album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.trim()?.takeIf { it.isNotBlank() }
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val pic = r.embeddedPicture
            val artUri = if (pic != null && pic.isNotEmpty()) {
                saveEmbeddedArtBytes(pic, file.name)
            } else null
            ParsedTrackTags(t, a, album, duration, artUri)
        } catch (e: Exception) {
            Log.w("DownloadRepo", "readTags: ${e.message}")
            ParsedTrackTags(fallbackTitle, fallbackArtist, null, 0L, null)
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Muchos vídeos usan "Artista - Canción"; si el artista sigue siendo el canal/uploader,
     * intenta separar título real e intérprete.
     */
    private fun refineTitleArtistFromYoutubeLine(
        rawLine: String,
        tagTitle: String,
        tagArtist: String,
        channelOrUploader: String
    ): Pair<String, String> {
        val line = (if (tagTitle.isNotBlank()) tagTitle else rawLine).trim()
        if (tagArtist.isNotBlank() && tagArtist != channelOrUploader) {
            return line to tagArtist
        }
        val m = Regex("^(.+?)\\s*-\\s*(.+)$").find(line) ?: return line to tagArtist
        val left = m.groupValues[1].trim()
        val right = m.groupValues[2].trim()
        if (left.isBlank() || right.isBlank()) return line to tagArtist
        val topicLike = left.endsWith("- Topic", ignoreCase = true) ||
            left.contains("VEVO", ignoreCase = true)
        val artist = if (topicLike) {
            left.removeSuffix("- Topic").removeSuffix(" - Topic").trim()
        } else {
            left
        }
        return right to artist
    }

    private suspend fun applyOnlineMetadata(
        title: String,
        artist: String,
        album: String?,
        existingArtUri: String?
    ): Triple<String?, String?, String?> {
        if (existingArtUri != null) return Triple(existingArtUri, album, null)
        if (!isNetworkAvailable()) return Triple(null, album, null)
        return try {
            val meta = metadataFetcher.fetchTrackMetadata(title, artist, album)
                ?: return Triple(null, album, null)
            Triple(
                meta.albumArtUriString,
                meta.albumTitle ?: album,
                meta.artistDisplayName
            )
        } catch (e: Exception) {
            Log.e("DownloadRepo", "Metadata: ${e.message}")
            Triple(null, album, null)
        }
    }

    /** yt-dlp JSON maps `playlist_title`; nested `playlist` objects may hold the title. */
    private fun playlistTitleFromVideoInfo(info: Any, depth: Int = 0): String? {
        if (depth > 2) return null
        val fieldNames =
            if (depth == 0) listOf("playlistTitle", "playlist_title")
            else listOf("playlistTitle", "playlist_title", "title")
        for (fieldName in fieldNames) {
            try {
                var cl: Class<*>? = info.javaClass
                while (cl != null) {
                    val f = cl.declaredFields.find { it.name == fieldName }
                    if (f != null) {
                        f.isAccessible = true
                        val v = f.get(info) as? String
                        if (!v.isNullOrBlank()) return v.trim()
                    }
                    cl = cl.superclass
                }
            } catch (_: Exception) {
            }
        }
        var c: Class<*>? = info.javaClass
        while (c != null) {
            try {
                val m = c.methods.find { it.name == "getPlaylistTitle" && it.parameterCount == 0 }
                if (m != null) {
                    val v = m.invoke(info) as? String
                    if (!v.isNullOrBlank()) return v.trim()
                }
            } catch (_: Exception) {
            }
            c = c.superclass
        }
        try {
            val getPl = info.javaClass.methods.find { it.name == "getPlaylist" && it.parameterCount == 0 }
            if (getPl != null) {
                val nested = getPl.invoke(info) ?: return null
                playlistTitleFromVideoInfo(nested, depth + 1)?.let { return it }
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun parseTitleFromPlaylistOutputFile(name: String): String {
        val base = name.substringBeforeLast('.')
        val match = Regex("^\\d+\\s*-\\s*(.+)$").find(base)
        return match?.groupValues?.get(1)?.trim() ?: base.trim()
    }

    /**
     * yt-dlp reports 0–100% for each playlist entry; this maps to a single 0–1 value using
     * "N of M" lines when present, otherwise segment resets (progress drops after a high value).
     */
    private class PlaylistProgressAggregator {
        private var totalEntries: Int = 0
        private var currentIndex: Int = 0
        private var lastP: Float = -1f
        private var lastOverall: Float = 0f
        private val itemOfPattern =
            Regex("""(\d+)\s+(?:of|de)\s+(\d+)""", RegexOption.IGNORE_CASE)

        fun aggregate(progressPercent: Float, line: String): Float {
            val p = (progressPercent / 100f).coerceIn(0f, 1f)
            var matched = false
            itemOfPattern.find(line)?.let { m ->
                matched = true
                val cur = m.groupValues[1].toIntOrNull() ?: return@let
                val tot = m.groupValues[2].toIntOrNull() ?: return@let
                if (tot > 0) totalEntries = tot
                if (cur >= 1) {
                    val maxIdx = if (totalEntries > 0) (totalEntries - 1).coerceAtLeast(0) else Int.MAX_VALUE
                    currentIndex = (cur - 1).coerceIn(0, maxIdx)
                }
            }
            if (!matched && lastP >= 0f && lastP > 0.75f && p < 0.2f) {
                if (totalEntries > 0) {
                    currentIndex = (currentIndex + 1).coerceAtMost(totalEntries - 1)
                } else {
                    currentIndex++
                }
            }
            lastP = p
            val denom =
                if (totalEntries > 0) totalEntries else (currentIndex + 1).coerceAtLeast(1)
            val raw = ((currentIndex + p) / denom).coerceIn(0f, 1f)
            lastOverall = maxOf(lastOverall, raw)
            return lastOverall
        }
    }

    suspend fun downloadAudioFromUrl(
        url: String,
        onProgress: (Float, String) -> Unit,
        metadataAlbumHint: String? = null
    ): Result<Song> = withContext(Dispatchers.IO) {
        try {
            initYoutubeDl()

            val downloadDir = context.getExternalFilesDir(null) ?: context.filesDir
            val outputTemplate = "$downloadDir/%(title)s.%(ext)s"

            val infoRequest = YoutubeDLRequest(url)
            infoRequest.addOption("--dump-json")

            val videoInfo = try {
                YoutubeDL.getInstance().getInfo(infoRequest)
            } catch (e: Exception) {
                Log.e("DownloadRepo", "Error fetching info: ${e.message}")
                return@withContext Result.failure(
                    Exception("No se pudo obtener información del video")
                )
            }

            val title  = videoInfo.title?.takeIf    { it.isNotBlank() } ?: "Título desconocido"
            val artist = videoInfo.uploader?.takeIf { it.isNotBlank() } ?: "Artista desconocido"

            val request = YoutubeDLRequest(url)
            request.addOption("-o", outputTemplate)
            request.addOption("-f", audioFormatSelector)
            request.addOption("--no-playlist")
            request.addOption("--force-ipv4")

            YoutubeDL.getInstance().execute(
                request,
                downloadDir.absolutePath
            ) { progressPercent: Float, _: Long, line: String ->
                val normalised = (progressPercent / 100f).coerceIn(0f, 1f)
                onProgress(normalised, line)
            }

            val files = downloadDir.listFiles { file ->
                audioExtensions.any { file.name.endsWith(it, ignoreCase = true) }
            }
            val downloadedFile = files?.maxByOrNull { it.lastModified() }

            if (downloadedFile != null) {
                val uri = Uri.fromFile(downloadedFile)
                val tags = readTagsFromDownloadedFile(downloadedFile, title, artist)
                val (refTitle, refArtist) = refineTitleArtistFromYoutubeLine(
                    videoInfo.title.orEmpty(),
                    tags.title,
                    tags.artist,
                    artist
                )
                val albumForMeta = metadataAlbumHint?.trim()?.takeIf { it.isNotBlank() } ?: tags.album
                val (albumArtUri, album, mbArtist) = applyOnlineMetadata(
                    refTitle,
                    refArtist,
                    albumForMeta,
                    tags.embeddedArtUri
                )
                val finalArtist = (mbArtist ?: refArtist).ifBlank { artist }
                val song = Song(
                    title = refTitle,
                    artist = finalArtist,
                    album = album,
                    uriString = uri.toString(),
                    albumArtUriString = albumArtUri,
                    duration = tags.durationMs
                )
                val id = songDao.insert(song)
                Result.success(song.copy(id = id))
            } else {
                Result.failure(Exception("No se encontró el archivo descargado"))
            }
        } catch (e: Exception) {
            Log.e("DownloadRepo", "Download error", e)
            Result.failure(e)
        }
    }

    /**
     * Descarga todas las entradas de una URL de lista de reproducción (sin `--no-playlist`).
     * Usa el mismo formato de audio y flags de red que la descarga simple.
     */
    suspend fun downloadPlaylistFromUrl(
        url: String,
        onProgress: (Float, String) -> Unit,
        userPlaylistName: String? = null,
        playlistAsAlbumMetadata: Boolean = false,
        playlistMetadataAlbumName: String? = null
    ): Result<PlaylistDownloadOutcome> = withContext(Dispatchers.IO) {
        try {
            initYoutubeDl()

            val downloadDir = context.getExternalFilesDir(null) ?: context.filesDir
            val existingPaths = downloadDir.listFiles()
                ?.filter { it.isFile }
                ?.map { it.absolutePath }
                ?.toSet()
                ?: emptySet()

            val infoRequest = YoutubeDLRequest(url)
            infoRequest.addOption("--dump-json")
            infoRequest.addOption("--playlist-items", "1")

            val videoInfo = try {
                YoutubeDL.getInstance().getInfo(infoRequest)
            } catch (e: Exception) {
                Log.e("DownloadRepo", "Error fetching playlist info: ${e.message}")
                return@withContext Result.failure(
                    Exception("No se pudo leer la lista. Comprueba que sea una URL de playlist válida.")
                )
            }

            val defaultArtist =
                videoInfo.uploader?.takeIf { it.isNotBlank() } ?: "Artista desconocido"
            val playlistTitleHint = playlistTitleFromVideoInfo(videoInfo)

            val outputTemplate = "$downloadDir/%(playlist_index)03d - %(title)s.%(ext)s"
            val request = YoutubeDLRequest(url)
            request.addOption("-o", outputTemplate)
            request.addOption("-f", audioFormatSelector)
            request.addOption("--force-ipv4")

            val playlistProgress = PlaylistProgressAggregator()
            YoutubeDL.getInstance().execute(
                request,
                downloadDir.absolutePath
            ) { progressPercent: Float, _: Long, line: String ->
                val normalised = playlistProgress.aggregate(progressPercent, line)
                onProgress(normalised, line)
            }

            val newFiles = downloadDir.listFiles { file ->
                file.isFile &&
                    audioExtensions.any { file.name.endsWith(it, ignoreCase = true) } &&
                    file.absolutePath !in existingPaths
            }?.sortedBy { it.name } ?: emptyList()

            if (newFiles.isEmpty()) {
                return@withContext Result.failure(
                    Exception("No se encontraron archivos de audio nuevos tras la descarga")
                )
            }

            val forcedAlbum = playlistMetadataAlbumName?.trim()?.takeIf { it.isNotBlank() }
            val songs = mutableListOf<Song>()
            for (file in newFiles) {
                val fallbackTitle = parseTitleFromPlaylistOutputFile(file.name)
                val tags = readTagsFromDownloadedFile(file, fallbackTitle, defaultArtist)
                val (refTitle, refArtist) = refineTitleArtistFromYoutubeLine(
                    fallbackTitle,
                    tags.title,
                    tags.artist,
                    defaultArtist
                )
                val albumForMeta = if (playlistAsAlbumMetadata && forcedAlbum != null) {
                    forcedAlbum
                } else {
                    tags.album
                }
                val (albumArtUri, album, mbArtist) = applyOnlineMetadata(
                    refTitle,
                    refArtist,
                    albumForMeta,
                    tags.embeddedArtUri
                )
                val finalArtist = (mbArtist ?: refArtist).ifBlank { defaultArtist }
                val uri = Uri.fromFile(file)
                val song = Song(
                    title = refTitle,
                    artist = finalArtist,
                    album = album,
                    uriString = uri.toString(),
                    albumArtUriString = albumArtUri,
                    duration = tags.durationMs
                )
                val id = songDao.insert(song)
                if (id != -1L) songs.add(song.copy(id = id))
            }

            if (songs.isEmpty()) {
                return@withContext Result.failure(
                    Exception("No se pudieron guardar las canciones en la biblioteca")
                )
            }

            val nameForPlaylist = userPlaylistName?.trim()?.takeIf { it.isNotBlank() }
            Result.success(
                PlaylistDownloadOutcome(
                    songs = songs,
                    playlistTitleHint = playlistTitleHint,
                    userPlaylistName = nameForPlaylist
                )
            )
        } catch (e: Exception) {
            Log.e("DownloadRepo", "Playlist download error", e)
            Result.failure(e)
        }
    }
}