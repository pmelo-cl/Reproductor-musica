package com.example.reproductormusica.ui

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.reproductormusica.data.database.AppDatabase
import com.example.reproductormusica.data.repository.AlbumRepository
import com.example.reproductormusica.data.repository.ArtistRepository
import com.example.reproductormusica.data.repository.MusicRepository
import com.example.reproductormusica.models.Album
import com.example.reproductormusica.models.Artist
import com.example.reproductormusica.models.Song
import com.example.reproductormusica.services.MusicService
import com.example.reproductormusica.utils.advancedMatch
import androidx.media3.common.Player
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = MusicRepository(
        songDao = database.songDao(),
        contentResolver = application.contentResolver
    )

    private val albumRepository = AlbumRepository(database.albumDao())
    private val artistRepository = ArtistRepository(database.artistDao())

    init {
        repository.setAlbumAndArtistRepos(albumRepository, artistRepository)
    }

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

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    // Álbumes y artistas
    val albums: StateFlow<List<Album>> = albumRepository.getAllAlbums()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val artists: StateFlow<List<Artist>> = artistRepository.getAllArtists()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
        _repeatMode.value = service.getRepeatMode()
        _shuffleEnabled.value = service.getShuffleEnabled()
        service.onPlaybackStateChanged = { playing -> _isPlaying.value = playing }
        service.onSongChanged = { song -> _currentSong.value = song }
        // Solo establecer cola si no tiene ya
        if (!service.hasQueue()) {
            service.setQueue(_allSongs.value)
        }
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

    fun playSong(song: Song) {
        musicService?.playSong(song)
    }

    fun playFromQueue(playlistId: Long?, queue: List<Song>, start: Song) {
        if (queue.isEmpty()) return
        _playingPlaylistId.value = playlistId
        setQueue(queue)
        playSong(start)
    }

    fun playPause()          { musicService?.playPause() }
    fun playNext()           { musicService?.playNext() }
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun playPrevious()       { musicService?.playPrevious() }
    fun seekTo(position: Long) { musicService?.seekTo(position) }

    fun setRepeatMode(mode: Int) {
        musicService?.setRepeatMode(mode)
        _repeatMode.value = mode
    }

    fun setShuffleMode(enabled: Boolean) {
        musicService?.setShuffleMode(enabled)
        _shuffleEnabled.value = enabled
    }

    fun addSongFromUri(uri: Uri) {
        viewModelScope.launch {
            val songId = repository.addSongFromUri(uri, getApplication())
            if (songId > 0) {
                // Notificar al PlaylistViewModel (se hace externamente)
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
            // Notificar limpieza de playlist por defecto (externo)
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

    // Métodos para obtener canciones de álbum/artista
    fun getSongsFromAlbum(albumName: String, artist: String): Flow<List<Song>> =
        albumRepository.getSongsFromAlbum(albumName, artist)

    fun getSongsFromArtist(artistName: String): Flow<List<Song>> =
        artistRepository.getSongsFromArtist(artistName)

    fun updateAlbumsAndArtists(song: Song) {
        viewModelScope.launch {
            if (song.artist.isNotBlank() && song.artist != "Artista desconocido" && song.artist != "Desconocido") {
                artistRepository.getOrCreateArtist(song.artist, song.albumArtUriString)
            }
            if (song.album != null && song.artist.isNotBlank()) {
                albumRepository.getOrCreateAlbum(song.album, song.artist, song.albumArtUriString)
            }
        }
    }
}