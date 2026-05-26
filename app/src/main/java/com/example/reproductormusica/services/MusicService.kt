package com.example.reproductormusica.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.example.reproductormusica.R
import com.example.reproductormusica.models.Song
import com.example.reproductormusica.ui.MainActivity
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MusicService : MediaLibraryService() {

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var currentSong: Song? = null
    private var songQueue: List<Song> = emptyList()
    private var currentIndex = -1
    private var repeatMode = Player.REPEAT_MODE_OFF
    private var shuffleMode = false

    private val _queueState = MutableStateFlow<List<Song>>(emptyList())
    val queueState: StateFlow<List<Song>> = _queueState.asStateFlow()

    var onPlaybackStateChanged: ((Boolean) -> Unit)? = null
    var onSongChanged: ((Song) -> Unit)? = null

    private inner class AutoCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootItem = MediaItem.Builder()
                .setMediaId(AUTO_ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setTitle("Mi Música")
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        @OptIn(UnstableApi::class)
        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (parentId != AUTO_ROOT_ID) {
                return Futures.immediateFuture(
                    LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                )
            }
            val items = songQueue.map { it.toMediaItem() }
            return Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
            )
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                val song = songQueue.find { it.id.toString() == item.mediaId }
                song?.toMediaItem() ?: item
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        val activityIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaLibrarySession.Builder(this, player, AutoCallback())
            .setSessionActivity(activityIntent)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onPlaybackStateChanged?.invoke(isPlaying)
                if (isPlaying) {
                    updateForegroundNotification()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaId?.toLongOrNull()?.let { id ->
                    val song = songQueue.find { it.id == id }
                    if (song != null) {
                        currentSong = song
                        currentIndex = player.currentMediaItemIndex
                        onSongChanged?.invoke(song)
                        updateForegroundNotification()
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) handleSongEnd()
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("MusicService", "ExoPlayer error", error)
            }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = mediaSession

    override fun onBind(intent: Intent?): IBinder? {
        val superBinder = super.onBind(intent)
        return superBinder ?: binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        intent?.action?.let { action ->
            when (action) {
                ACTION_PLAY     -> player.play()
                ACTION_PAUSE    -> player.pause()
                ACTION_NEXT     -> playNext()
                ACTION_PREVIOUS -> playPrevious()
                ACTION_STOP     -> stop()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        mediaSession.release()
        player.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ---------- API pública ----------
    fun hasQueue(): Boolean = player.mediaItemCount > 0

    fun setQueue(songs: List<Song>) {
        songQueue = songs
        if (songs.isNotEmpty()) {
            val mediaItems = songs.map { it.toMediaItem() }
            player.setMediaItems(mediaItems)
            currentIndex = -1
        } else {
            player.clearMediaItems()
        }
        _queueState.value = songQueue
        mediaSession.notifyChildrenChanged(AUTO_ROOT_ID, songs.size, null)
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
            currentSong = song
            player.setMediaItem(song.toMediaItem())
            player.prepare()
            player.play()
            currentIndex = -1
        }
        onSongChanged?.invoke(song)
        updateForegroundNotification()
    }

    fun playPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun playNext() {
        if (shuffleMode) {
            player.seekToNextMediaItem()
        } else {
            if (songQueue.isNotEmpty() && currentIndex < songQueue.size - 1) {
                currentIndex++
                player.seekToDefaultPosition(currentIndex)
                player.play()
            } else if (repeatMode == Player.REPEAT_MODE_ALL && songQueue.isNotEmpty()) {
                currentIndex = 0
                player.seekToDefaultPosition(0)
                player.play()
            }
        }
    }

    fun playPrevious() {
        if (shuffleMode) {
            player.seekToPreviousMediaItem()
        } else {
            if (songQueue.isNotEmpty() && currentIndex > 0) {
                currentIndex--
                player.seekToDefaultPosition(currentIndex)
                player.play()
            }
        }
    }

    fun seekTo(positionMs: Long) { player.seekTo(positionMs) }
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
        player.shuffleModeEnabled = enabled
    }

    fun getRepeatMode(): Int = repeatMode
    fun getShuffleEnabled(): Boolean = shuffleMode

    // ---------- Gestión de cola dinámica ----------
    fun addToQueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        songQueue = songQueue + songs
        val mediaItems = songs.map { it.toMediaItem() }
        player.addMediaItems(mediaItems)
        _queueState.value = songQueue
        if (!player.isPlaying && player.mediaItemCount > 0) {
            player.prepare()
            player.play()
        }
    }

    fun removeFromQueue(index: Int) {
        if (index < 0 || index >= songQueue.size) return
        songQueue = songQueue.toMutableList().apply { removeAt(index) }
        player.removeMediaItem(index)
        _queueState.value = songQueue
        if (currentIndex == index) {
            // El player automáticamente pasará a la siguiente
        } else if (currentIndex > index) {
            currentIndex--
        }
    }

    fun moveQueueItem(from: Int, to: Int) {
        if (from < 0 || from >= songQueue.size || to < 0 || to >= songQueue.size) return
        val mutableList = songQueue.toMutableList()
        val item = mutableList.removeAt(from)
        mutableList.add(to, item)
        songQueue = mutableList
        player.moveMediaItem(from, to)
        _queueState.value = songQueue
        if (currentIndex == from) {
            currentIndex = to
        } else if (currentIndex in (minOf(from, to) + 1)..maxOf(from, to)) {
            currentIndex += if (from < to) -1 else 1
        }
    }

    // ---------- Helpers privados ----------
    private fun handleSongEnd() {
        when {
            repeatMode == Player.REPEAT_MODE_ONE -> {
                player.seekTo(0)
                player.play()
            }
            else -> {
                // ExoPlayer maneja automáticamente el avance
            }
        }
    }

    private fun Song.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setArtworkUri(albumArtUri)
                    .build()
            )
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reproducción de música",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    @OptIn(UnstableApi::class)
    private fun createMediaNotification(): Notification {
        createNotificationChannel()
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = if (isPlaying()) {
            NotificationCompat.Action(
                R.drawable.ic_pause,
                "Pausar",
                createPendingIntent(ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                R.drawable.ic_play,
                "Reproducir",
                createPendingIntent(ACTION_PLAY)
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentSong?.title ?: "Reproduciendo")
            .setContentText(currentSong?.artist ?: "")
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(pendingIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionCompatToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(R.drawable.ic_previous, "Anterior", createPendingIntent(ACTION_PREVIOUS))
            .addAction(playPauseAction)
            .addAction(R.drawable.ic_next, "Siguiente", createPendingIntent(ACTION_NEXT))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun updateForegroundNotification() {
        val notification = createMediaNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val NOTIFICATION_ID = 101
        const val ACTION_PLAY     = "action_play"
        const val ACTION_PAUSE    = "action_pause"
        const val ACTION_NEXT     = "action_next"
        const val ACTION_PREVIOUS = "action_previous"
        const val ACTION_STOP     = "action_stop"
        private const val AUTO_ROOT_ID = "auto_root"
        private const val CHANNEL_ID = "music_playback_channel"
    }
}