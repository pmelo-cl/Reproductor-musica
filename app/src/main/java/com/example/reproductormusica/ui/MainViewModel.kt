package com.example.reproductormusica.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.reproductormusica.data.database.AppDatabase
import com.example.reproductormusica.data.repository.MusicRepository
import com.example.reproductormusica.models.Song
import com.example.reproductormusica.services.MusicService
import com.example.reproductormusica.utils.advancedMatch
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = MusicRepository(
        songDao = database.songDao(),
        contentResolver = application.contentResolver
    )

    private val playlistViewModel: PlaylistViewModel by lazy { PlaylistViewModel(application) }

    private var musicService: MusicService? = null

    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    private val _searchQuery = MutableStateFlow("")

    val songs: StateFlow<List<Song>> = combine(_allSongs, _searchQuery) { songs, query ->
        if (query.isBlank()) songs
        else songs.filter { song ->
            advancedMatch(query, song.title) || advancedMatch(query, song.artist)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allSongs: StateFlow<List<Song>> = _allSongs.asStateFlow()
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playingPlaylistId = MutableStateFlow<Long?>(null)
    val playingPlaylistId: StateFlow<Long?> = _playingPlaylistId.asStateFlow()

    init {
        loadSongs()
        viewModelScope.launch {
            while (true) {
                musicService?.let { svc ->
                    _playbackPosition.value = svc.getCurrentPosition()
                    _duration.value = svc.getDuration().coerceAtLeast(0L)
                    _isPlaying.value = svc.isPlaying()
                }
                kotlinx.coroutines.delay(500)
            }
        }
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun setMusicService(service: MusicService) {
        musicService = service
        service.onPlaybackStateChanged = { playing -> _isPlaying.value = playing }
        service.onSongChanged = { song -> _currentSong.value = song }
        service.setQueue(_allSongs.value)
    }

    fun setQueue(songs: List<Song>) {
        musicService?.setQueue(songs)
    }

    private fun loadSongs() {
        viewModelScope.launch {
            repository.getAllSongs().collect { list ->
                _allSongs.value = list
                musicService?.setQueue(list)
            }
        }
    }

    fun playSong(song: Song) { musicService?.playSong(song) }
    fun playPause()          { musicService?.playPause() }
    fun playNext()           { musicService?.playNext() }
    fun playPrevious()       { musicService?.playPrevious() }
    fun seekTo(position: Long) { musicService?.seekTo(position) }

    fun setRepeatMode(mode: Int) { musicService?.setRepeatMode(mode) }
    fun setShuffleMode(enabled: Boolean) { musicService?.setShuffleMode(enabled) }

    fun addSongFromUri(uri: Uri) {
        viewModelScope.launch {
            val songId = repository.addSongFromUri(uri, getApplication())
            if (songId > 0) {
                playlistViewModel.addSongToDefaultPlaylist(songId)
            }
        }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            if (_currentSong.value?.id == song.id) {
                musicService?.stop()
                _currentSong.value = null
                _isPlaying.value = false
            }
            repository.deleteSong(song)
            playlistViewModel.cleanupDefaultPlaylistIfEmpty()
        }
    }

    fun updateSongAlbumArt(song: Song, artUri: Uri) {
        viewModelScope.launch {
            repository.updateSongAlbumArt(song, artUri)
            if (_currentSong.value?.id == song.id) {
                _currentSong.value = _currentSong.value?.copy(albumArtUriString = artUri.toString())
            }
        }
    }

    fun updateSongInfo(song: Song, newTitle: String, newArtist: String) {
        viewModelScope.launch {
            repository.updateSongInfo(song.id, newTitle, newArtist)
            if (_currentSong.value?.id == song.id) {
                _currentSong.value = _currentSong.value?.copy(title = newTitle, artist = newArtist)
            }
        }
    }

    fun playPlaylist(playlistId: Long, songs: List<Song>) {
        _playingPlaylistId.value = playlistId
        setQueue(songs)
        if (songs.isNotEmpty()) {
            playSong(songs.first())
        }
    }

    fun stopPlaylist() {
        _playingPlaylistId.value = null
    }
}