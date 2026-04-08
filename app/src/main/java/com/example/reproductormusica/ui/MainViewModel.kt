package com.example.reproductormusica.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.reproductormusica.data.repository.MusicRepository
import com.example.reproductormusica.models.Song
import com.example.reproductormusica.services.MusicService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)

    private var musicService: MusicService? = null

    // Estado de la lista de canciones
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    // Estado del reproductor
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    init {
        loadSongs()
        // Actualizar posición periódicamente
        viewModelScope.launch {
            while (true) {
                musicService?.let {
                    _playbackPosition.value = it.getCurrentPosition()
                    _duration.value = it.getDuration().coerceAtLeast(0L)
                    _isPlaying.value = it.isPlaying()
                }
                kotlinx.coroutines.delay(500)
            }
        }
    }

    fun setMusicService(service: MusicService) {
        musicService = service
        service.onPlaybackStateChanged = { playing ->
            _isPlaying.value = playing
        }
        service.onSongChanged = { song ->
            _currentSong.value = song
        }
        // Cargar la cola de reproducción en el servicio
        viewModelScope.launch {
            val queue = repository.getSongsSnapshot()
            service.setQueue(queue)
        }
    }

    private fun loadSongs() {
        viewModelScope.launch {
            repository.getAllSongs().collect { list ->
                _songs.value = list
                // Actualizar la cola en el servicio cuando la lista cambia
                musicService?.setQueue(list)
            }
        }
    }

    fun playSong(song: Song) {
        musicService?.playSong(song, _songs.value)
    }

    fun playPause() {
        musicService?.playPause()
    }

    fun playNext() {
        musicService?.playNext()
    }

    fun playPrevious() {
        musicService?.playPrevious()
    }

    fun seekTo(position: Long) {
        musicService?.seekTo(position)
    }

    fun addSongFromUri(uri: Uri) {
        viewModelScope.launch {
            repository.insertSongFromUri(uri)
        }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            // Si es la canción actual, parar reproducción
            if (_currentSong.value?.id == song.id) {
                musicService?.stop()
                _currentSong.value = null
                _isPlaying.value = false
            }
            repository.deleteSong(song)
        }
    }

    fun updateSongAlbumArt(song: Song, artUri: Uri) {
        viewModelScope.launch {
            repository.updateAlbumArt(song.id, artUri)
        }
    }
}