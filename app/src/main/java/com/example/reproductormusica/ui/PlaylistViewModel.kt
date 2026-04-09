package com.example.reproductormusica.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.reproductormusica.data.database.AppDatabase
import com.example.reproductormusica.data.repository.PlaylistRepository
import com.example.reproductormusica.models.Playlist
import com.example.reproductormusica.models.PlaylistWithSongs
import com.example.reproductormusica.models.Song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PlaylistViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = PlaylistRepository(database.playlistDao())
    private val prefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    val playlistsWithSongs: StateFlow<List<PlaylistWithSongs>> =
        repository.getAllPlaylistsWithSongs()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedPlaylistId = MutableStateFlow<Long?>(null)
    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedPlaylist: StateFlow<PlaylistWithSongs?> =
        _selectedPlaylistId.flatMapLatest { id ->
            if (id == null) flowOf(null) else repository.getPlaylistWithSongs(id)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            ensureDefaultPlaylistExists()
        }
    }

    private suspend fun ensureDefaultPlaylistExists() {
        val existing = repository.getPlaylistByName("Canciones disponibles")
        if (existing == null) {
            repository.createPlaylist("Canciones disponibles")
        }
    }

    fun selectPlaylist(playlistId: Long) { _selectedPlaylistId.value = playlistId }
    fun clearSelection() { _selectedPlaylistId.value = null }

    fun createPlaylist(name: String, coverUri: Uri? = null) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.createPlaylist(name.trim(), coverUri) }
    }

    fun renamePlaylist(playlist: Playlist, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch { repository.updatePlaylist(playlist.copy(name = newName.trim())) }
    }

    fun updatePlaylistCover(playlist: Playlist, coverUri: Uri) {
        viewModelScope.launch { repository.updatePlaylistCover(playlist, coverUri) }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch { repository.deletePlaylist(playlist) }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch { repository.addSongToPlaylist(playlistId, songId) }
    }

    suspend fun addSongToDefaultPlaylist(songId: Long) {
        ensureDefaultPlaylistExists()
        val default = repository.getPlaylistByName("Canciones disponibles")
        default?.let { repository.addSongToPlaylist(it.id, songId) }
    }

    fun createPlaylistFromDownload(songs: List<Song>, titleHint: String?) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            repository.createPlaylistWithSongsFromDownload(titleHint, songs.map { it.id })
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
            val playlist = repository.getPlaylistById(playlistId)
            if (playlist?.name == "Canciones disponibles") {
                val songs = repository.getPlaylistWithSongs(playlistId).first()?.songs ?: emptyList()
                if (songs.isEmpty()) {
                    repository.deletePlaylist(playlist)
                }
            }
        }
    }

    suspend fun cleanupDefaultPlaylistIfEmpty() {
        val default = repository.getPlaylistByName("Canciones disponibles")
        if (default != null) {
            val songs = repository.getPlaylistWithSongs(default.id).first()?.songs ?: emptyList()
            if (songs.isEmpty()) {
                repository.deletePlaylist(default)
            }
        }
    }
}