package com.example.reproductormusica.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.reproductormusica.models.Song
import com.example.reproductormusica.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import com.example.reproductormusica.R

class MusicService : Service() {
    private val binder = LocalBinder()
    private lateinit var player: ExoPlayer
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var queue: List<Song> = emptyList()
    private var currentIndex: Int = -1
    private var currentSong: Song? = null

    var onPlaybackStateChanged: ((Boolean) -> Unit)? = null
    var onSongChanged: ((Song) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onPlaybackStateChanged?.invoke(isPlaying)
                updateNotification()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                // Auto-avanzar al siguiente cuando termina la canción
                if (playbackState == Player.STATE_ENDED) {
                    playNext()
                }
            }
        })
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_PLAY -> player.play()
                ACTION_PAUSE -> player.pause()
                ACTION_NEXT -> playNext()
                ACTION_PREVIOUS -> playPrevious()
                ACTION_STOP -> {
                    stop()
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    // Actualizar la cola (llamado desde ViewModel cuando cambia la lista)
    fun setQueue(songs: List<Song>) {
        queue = songs
        // Reajustar índice si la canción actual sigue en la lista
        currentSong?.let { song ->
            currentIndex = queue.indexOfFirst { it.id == song.id }
        }
    }

    fun playSong(song: Song, songList: List<Song> = queue) {
        queue = songList
        currentIndex = queue.indexOfFirst { it.id == song.id }
        currentSong = song

        val mediaItem = MediaItem.Builder()
            .setUri(song.uri)
            .setMediaId(song.id.toString())
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        startForegroundServiceWithNotification()
        onSongChanged?.invoke(song)
    }

    fun playPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun playNext() {
        if (queue.isEmpty()) return
        val nextIndex = (currentIndex + 1) % queue.size
        playSong(queue[nextIndex])
    }

    fun playPrevious() {
        if (queue.isEmpty()) return
        // Si llevamos más de 3 segundos, reinicia la canción actual
        if (player.currentPosition > 3000L) {
            player.seekTo(0)
            return
        }
        val prevIndex = if (currentIndex - 1 < 0) queue.size - 1 else currentIndex - 1
        playSong(queue[prevIndex])
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun stop() {
        player.stop()
        currentSong = null
        currentIndex = -1
        onPlaybackStateChanged?.invoke(false)
        onSongChanged?.invoke(Song(title = "", uriString = ""))  // señal de "sin canción"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    fun getCurrentPosition(): Long = if (::player.isInitialized) player.currentPosition else 0L
    fun getDuration(): Long = if (::player.isInitialized) player.duration else 0L
    fun isPlaying(): Boolean = if (::player.isInitialized) player.isPlaying else false

    private fun startForegroundServiceWithNotification() {
        createNotificationChannel()
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        if (currentSong == null) return
        val notification = createNotification()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reproducción de música",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val playPauseAction: Int
        val playPauseIntent: PendingIntent
        if (player.isPlaying) {
            playPauseAction = android.R.drawable.ic_media_pause
            playPauseIntent = PendingIntent.getService(
                this, 1,
                Intent(this, MusicService::class.java).apply { action = ACTION_PAUSE },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            playPauseAction = android.R.drawable.ic_media_play
            playPauseIntent = PendingIntent.getService(
                this, 0,
                Intent(this, MusicService::class.java).apply { action = ACTION_PLAY },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val nextIntent = PendingIntent.getService(
            this, 2,
            Intent(this, MusicService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val prevIntent = PendingIntent.getService(
            this, 3,
            Intent(this, MusicService::class.java).apply { action = ACTION_PREVIOUS },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentSong?.title ?: "Reproduciendo")
            .setContentText(currentSong?.artist ?: "")
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_media_previous, "Anterior", prevIntent)
            .addAction(playPauseAction, "Play/Pause", playPauseIntent)
            .addAction(android.R.drawable.ic_media_next, "Siguiente", nextIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        player.release()
    }

    companion object {
        const val NOTIFICATION_ID = 101
        const val CHANNEL_ID = "music_playback_channel"
        const val ACTION_PLAY = "action_play"
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_NEXT = "action_next"
        const val ACTION_PREVIOUS = "action_previous"
        const val ACTION_STOP = "action_stop"
    }
}