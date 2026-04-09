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
import androidx.core.app.NotificationCompat
import com.example.reproductormusica.R
import com.example.reproductormusica.data.database.AppDatabase
import com.example.reproductormusica.data.download.DownloadRepository
import com.example.reproductormusica.models.PlaylistDownloadOutcome
import com.example.reproductormusica.models.Song
import com.example.reproductormusica.ui.MainActivity
import kotlinx.coroutines.*

class DownloadService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: DownloadRepository

    var onDownloadProgress: ((Float, String) -> Unit)? = null
    var onDownloadComplete: ((Song) -> Unit)? = null
    var onPlaylistDownloadComplete: ((PlaylistDownloadOutcome) -> Unit)? = null
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
        val playlist = intent?.getBooleanExtra("playlist", false) == true
        startDownload(url, playlist)
        return START_STICKY
    }

    private fun startDownload(url: String, playlist: Boolean) {
        startForeground(
            NOTIFICATION_ID_PROGRESS,
            createProgressNotification(
                if (playlist) "Iniciando descarga de playlist…" else "Iniciando descarga…",
                0
            )
        )

        serviceScope.launch {
            if (playlist) {
                val result = repository.downloadPlaylistFromUrl(url) { progress, line ->
                    val percent = (progress * 100).toInt().coerceIn(0, 100)
                    updateProgressNotification("Descargando playlist… $percent%", percent)
                    onDownloadProgress?.invoke(progress, line)
                }
                result.onSuccess { outcome ->
                    stopForeground(Service.STOP_FOREGROUND_REMOVE)
                    val songs = outcome.songs
                    val subtitle = if (songs.size == 1) {
                        getString(R.string.download_notification_single_subtitle, songs.first().title)
                    } else {
                        getString(R.string.download_notification_playlist_subtitle, songs.size)
                    }
                    showFinishedNotification(subtitle)
                    onPlaylistDownloadComplete?.invoke(outcome)
                    stopSelf()
                }.onFailure { error ->
                    stopForeground(Service.STOP_FOREGROUND_REMOVE)
                    showErrorNotification(error.message ?: "Error desconocido")
                    onDownloadError?.invoke(error.message ?: "Error desconocido")
                    stopSelf()
                }
            } else {
                val result = repository.downloadAudioFromUrl(url) { progress, line ->
                    val percent = (progress * 100).toInt().coerceIn(0, 100)
                    updateProgressNotification("Descargando… $percent%", percent)
                    onDownloadProgress?.invoke(progress, line)
                }

                result.onSuccess { song ->
                    stopForeground(Service.STOP_FOREGROUND_REMOVE)
                    showFinishedNotification(
                        getString(R.string.download_notification_single_subtitle, song.title)
                    )
                    onDownloadComplete?.invoke(song)
                    stopSelf()
                }.onFailure { error ->
                    stopForeground(Service.STOP_FOREGROUND_REMOVE)
                    showErrorNotification(error.message ?: "Error desconocido")
                    onDownloadError?.invoke(error.message ?: "Error desconocido")
                    stopSelf()
                }
            }
        }
    }

    private fun ensureProgressChannel() {
        val channelId = "download_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.download_music),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun ensureFinishedChannel() {
        val channelId = "download_complete_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.download_notification_channel_complete),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun mainContentIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun createProgressNotification(content: String, progress: Int): Notification {
        ensureProgressChannel()
        val channelId = "download_channel"
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.download_music))
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(mainContentIntent())
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateProgressNotification(content: String, progress: Int) {
        val notification = createProgressNotification(content, progress)
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID_PROGRESS, notification)
    }

    private fun showFinishedNotification(subtitle: String) {
        ensureFinishedChannel()
        val notification = NotificationCompat.Builder(this, "download_complete_channel")
            .setContentTitle(getString(R.string.download_notification_complete_title))
            .setContentText(subtitle)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(mainContentIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID_COMPLETE, notification)
    }

    private fun showErrorNotification(message: String) {
        ensureFinishedChannel()
        val notification = NotificationCompat.Builder(this, "download_complete_channel")
            .setContentTitle(getString(R.string.download_notification_error_title))
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(mainContentIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID_COMPLETE, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID_PROGRESS = 202
        private const val NOTIFICATION_ID_COMPLETE = 203
    }
}