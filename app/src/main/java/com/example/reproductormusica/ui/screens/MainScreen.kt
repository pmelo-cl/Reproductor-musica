package com.example.reproductormusica.ui.screens

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.reproductormusica.data.repository.MusicRepository
import com.example.reproductormusica.models.Song
import kotlinx.coroutines.launch

class MainScreen(application: Application) : AndroidViewModel(application) {
    private val repository = MusicRepository(application)
    val songs: LiveData<List<Song>> = repository.getAllSongs().asLiveData()

    fun insertSongFromUri(uri: Uri) {
        viewModelScope.launch {
            repository.insertSongFromUri(uri)
        }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            repository.deleteSong(song)
        }
    }

    fun updateAlbumArt(songId: Long, artUri: Uri) {
        viewModelScope.launch {
            repository.updateAlbumArt(songId, artUri)
        }
    }
}