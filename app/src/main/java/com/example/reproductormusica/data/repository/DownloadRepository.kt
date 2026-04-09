package com.example.reproductormusica.data.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import com.example.reproductormusica.data.database.SongDao
import com.example.reproductormusica.models.Song
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DownloadRepository(
    private val songDao: SongDao,
    private val context: Context
) {
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities =
            connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * [onProgress] recibe un valor normalizado en [0f, 1f].
     */
    suspend fun downloadAudioFromUrl(
        url: String,
        onProgress: (Float, String) -> Unit
    ): Result<Song> = withContext(Dispatchers.IO) {
        try {
            YoutubeDL.getInstance().init(context)

            if (isNetworkAvailable()) {
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(context)
                    Log.d("DownloadRepo", "yt-dlp updated")
                } catch (e: Exception) {
                    Log.e("DownloadRepo", "yt-dlp update failed: ${e.message}")
                }
            }

            val downloadDir = context.getExternalFilesDir(null) ?: context.filesDir
            val outputTemplate = "$downloadDir/%(title)s.%(ext)s"

            // Obtener metadatos del video
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

            // Configurar descarga (formato M4A, sin conversión)
            val request = YoutubeDLRequest(url)
            request.addOption("-o", outputTemplate)
            request.addOption("-f", "bestaudio[ext=m4a]/bestaudio/best")
            request.addOption("--no-playlist")
            request.addOption("--force-ipv4")

            // Ejecutar descarga
            YoutubeDL.getInstance().execute(
                request,
                downloadDir.absolutePath
            ) { progressPercent: Float, _: Long, line: String ->
                val normalised = (progressPercent / 100f).coerceIn(0f, 1f)
                onProgress(normalised, line)
            }

            // Localizar archivo descargado
            val extensions = arrayOf("m4a", "mp4", "webm", "opus")
            val files = downloadDir.listFiles { file ->
                extensions.any { file.name.endsWith(it, ignoreCase = true) }
            }
            val downloadedFile = files?.maxByOrNull { it.lastModified() }

            if (downloadedFile != null) {
                val uri = Uri.fromFile(downloadedFile)
                val song = Song(
                    title = title,
                    artist = artist,
                    uriString = uri.toString(),
                    duration = 0L
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
}