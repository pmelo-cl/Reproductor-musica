package com.example.reproductormusica.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.reproductormusica.R
import com.example.reproductormusica.data.database.AppDatabase
import com.example.reproductormusica.data.download.DownloadRepository
import com.example.reproductormusica.models.Song
import com.example.reproductormusica.ui.MainActivity
import kotlinx.coroutines.*

class DownloadService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: DownloadRepository

    // Callbacks used by DownloadViewModel
    var onDownloadProgress: ((Float, String) -> Unit)? = null
    var onDownloadComplete: ((Song) -> Unit)? = null
    var onDownloadError: ((String) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getDatabase(this)
        repository = DownloadRepository(database.songDao(), this)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("url")
        if (url.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        startDownload(url)
        return START_STICKY
    }

    private fun startDownload(url: String) {
        startForeground(NOTIFICATION_ID, createNotification("Iniciando descarga…", 0))

        serviceScope.launch {
            // DownloadRepository already delivers normalised 0..1 progress.
            val result = repository.downloadAudioFromUrl(url) { progress, line ->
                val percent = (progress * 100).toInt().coerceIn(0, 100)
                updateNotification("Descargando… $percent%", percent)
                onDownloadProgress?.invoke(progress, line)
            }

            result.onSuccess { song ->
                updateNotification("Descarga completada: ${song.title}", 100)
                onDownloadComplete?.invoke(song)
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }.onFailure { error ->
                updateNotification("Error en la descarga", 0)
                onDownloadError?.invoke(error.message ?: "Error desconocido")
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    private fun createNotification(content: String, progress: Int): Notification {
        val channelId = "download_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Descargas de música", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Descargando música")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(contentIntent)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String, progress: Int) {
        val notification = createNotification(content, progress)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val NOTIFICATION_ID = 202
    }
}