package com.example.reproductormusica.ui

import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.reproductormusica.models.Song
import com.example.reproductormusica.services.DownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private var downloadService: DownloadService? = null
    private val context = getApplication<Application>()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadedSong = MutableStateFlow<Song?>(null)
    val downloadedSong: StateFlow<Song?> = _downloadedSong.asStateFlow()

    // Callback para notificar a MainViewModel que una canción fue descargada
    var onSongDownloaded: ((Song) -> Unit)? = null

    fun setDownloadService(service: DownloadService) {
        downloadService = service
        service.onDownloadProgress = { progress, _ ->
            _progress.value = progress
            _status.value = "Descargando… ${(progress * 100).toInt()}%"
        }
        service.onDownloadComplete = { song ->
            _progress.value = 1f
            _status.value = "¡Descarga completada!"
            _isDownloading.value = false
            _downloadedSong.value = song
            onSongDownloaded?.invoke(song)   // <-- Notificar a MainViewModel
        }
        service.onDownloadError = { error ->
            _status.value = "Error: $error"
            _isDownloading.value = false
            viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(context, "Error en la descarga: $error", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun startDownload(url: String) {
        if (url.isBlank()) {
            _status.value = "La URL no puede estar vacía"
            return
        }
        if (downloadService == null) {
            _status.value = "Servicio de descarga no disponible"
            return
        }

        _isDownloading.value = true
        _progress.value = 0f
        _status.value = "Preparando descarga…"
        _downloadedSong.value = null

        val intent = Intent(context, DownloadService::class.java).apply {
            putExtra("url", url)
        }
        context.startService(intent)
    }

    fun clearDownloadedSong() {
        _downloadedSong.value = null
        _status.value = ""
        _progress.value = 0f
    }
}