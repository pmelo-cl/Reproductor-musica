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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.reproductormusica.R
import com.example.reproductormusica.models.Song
import com.example.reproductormusica.ui.MainActivity
import kotlinx.coroutines.*
import androidx.media3.common.PlaybackException

class MusicService : Service() {
    private val binder = LocalBinder()
    private lateinit var player: ExoPlayer
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentSong: Song? = null
    private var songQueue: List<Song> = emptyList()
    private var currentIndex = -1

    private var repeatMode = Player.REPEAT_MODE_OFF
    private var shuffleMode = false
    private val playbackHistory = mutableListOf<Song>()

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
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaId?.toLongOrNull()?.let { id ->
                    val song = songQueue.find { it.id == id }
                    if (song != null) {
                        currentSong = song
                        currentIndex = songQueue.indexOf(song)
                        onSongChanged?.invoke(song)
                        updateNotification()
                    }
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    handleSongEnd()
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                Log.e("MusicService", "ExoPlayer error", error)
            }
        })
    }

    private fun handleSongEnd() {
        when {
            repeatMode == Player.REPEAT_MODE_ONE -> {
                player.seekTo(0)
                player.play()
            }
            shuffleMode -> playRandomNext()
            else -> playNext()
        }
    }

    private fun playRandomNext() {
        val candidates = songQueue.filter { it.id != currentSong?.id }
        if (candidates.isNotEmpty()) {
            currentSong?.let { playbackHistory.add(it) }
            val next = candidates.random()
            playSong(next)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_PLAY -> player.play()
                ACTION_PAUSE -> player.pause()
                ACTION_NEXT -> playNext()
                ACTION_PREVIOUS -> playPrevious()
                ACTION_STOP -> stop()
            }
        }
        return START_STICKY
    }

    fun setQueue(songs: List<Song>) {
        songQueue = songs
        playbackHistory.clear()
        if (songs.isNotEmpty()) {
            val mediaItems = songs.map { song ->
                MediaItem.Builder()
                    .setUri(song.uri)
                    .setMediaId(song.id.toString())
                    .build()
            }
            player.setMediaItems(mediaItems)
            currentIndex = -1
        } else {
            player.clearMediaItems()
        }
    }

    fun playSong(song: Song) {
        val index = songQueue.indexOfFirst { it.id == song.id }
        if (index != -1) {
            currentIndex = index
            player.seekToDefaultPosition(index)
            player.prepare()
            player.play()
            currentSong = song
        } else {
            // Canción no está en la cola: reproducir directamente
            currentSong = song
            player.setMediaItem(MediaItem.fromUri(song.uri))
            player.prepare()
            player.play()
            currentIndex = -1
        }
        startForegroundServiceWithNotification()
        onSongChanged?.invoke(song)
    }

    fun playPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun playNext() {
        if (shuffleMode) {
            playRandomNext()
        } else {
            if (songQueue.isNotEmpty() && currentIndex < songQueue.size - 1) {
                currentIndex++
                val nextSong = songQueue[currentIndex]
                player.seekToDefaultPosition(currentIndex)
                player.play()
                currentSong = nextSong
                onSongChanged?.invoke(nextSong)
            } else {
                // Si está al final, volver al principio si el modo repetición total está activo
                if (repeatMode == Player.REPEAT_MODE_ALL) {
                    currentIndex = 0
                    val firstSong = songQueue.firstOrNull()
                    if (firstSong != null) {
                        player.seekToDefaultPosition(0)
                        player.play()
                        currentSong = firstSong
                        onSongChanged?.invoke(firstSong)
                    }
                }
            }
        }
    }

    fun playPrevious() {
        if (shuffleMode && playbackHistory.isNotEmpty()) {
            val previous = playbackHistory.removeLast()
            playSong(previous)
        } else {
            if (songQueue.isNotEmpty() && currentIndex > 0) {
                currentIndex--
                val prevSong = songQueue[currentIndex]
                player.seekToDefaultPosition(currentIndex)
                player.play()
                currentSong = prevSong
                onSongChanged?.invoke(prevSong)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun getCurrentPosition(): Long = player.currentPosition
    fun getDuration(): Long = player.duration
    fun isPlaying(): Boolean = player.isPlaying

    fun stop() {
        player.stop()
        currentSong = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun setRepeatMode(mode: Int) {
        repeatMode = mode
        player.repeatMode = mode
    }

    fun setShuffleMode(enabled: Boolean) {
        shuffleMode = enabled
        if (!enabled) playbackHistory.clear()
    }

    private fun startForegroundServiceWithNotification() {
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
        val notification = createNotification()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(): Notification {
        val channelId = "music_playback_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Reproducción de música",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val playPauseIcon = if (player.isPlaying)
            android.R.drawable.ic_media_pause
        else
            android.R.drawable.ic_media_play

        val playIntent = PendingIntent.getService(
            this, 0,
            Intent(this, MusicService::class.java).apply { action = ACTION_PLAY },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MusicService::class.java).apply { action = ACTION_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(currentSong?.title ?: "Reproduciendo")
            .setContentText(currentSong?.artist ?: "")
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_media_previous, "Anterior", prevIntent)
            .addAction(playPauseIcon, "Play/Pause", if (player.isPlaying) pauseIntent else playIntent)
            .addAction(android.R.drawable.ic_media_next, "Siguiente", nextIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        player.release()
    }

    companion object {
        const val NOTIFICATION_ID = 101
        const val ACTION_PLAY = "action_play"
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_NEXT = "action_next"
        const val ACTION_PREVIOUS = "action_previous"
        const val ACTION_STOP = "action_stop"
    }
}