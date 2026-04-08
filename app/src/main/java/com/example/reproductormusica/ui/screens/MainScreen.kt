package com.example.reproductormusica.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.reproductormusica.models.Song
import com.example.reproductormusica.ui.MainViewModel
import com.example.reproductormusica.ui.components.MiniPlayerBar
import com.example.reproductormusica.ui.components.SongListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onPickAudio: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val songs by viewModel.songs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi Reproductor") },
                actions = {
                    IconButton(onClick = { /* Buscar en dispositivo */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar")
                    }
                    IconButton(onClick = onPickAudio) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Añadir archivo")
                    }
                }
            )
        },
        bottomBar = {
            // Mini reproductor persistente (solo se muestra si hay canción actual)
            currentSong?.let { song ->
                MiniPlayerBar(
                    song = song,
                    isPlaying = isPlaying,
                    onPlayPause = { viewModel.playPause() },
                    onNext = { viewModel.playNext() },
                    onBarClick = onNavigateToPlayer
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(songs, key = { it.id }) { song ->
                SongListItem(
                    song = song,
                    isCurrentSong = song.id == currentSong?.id,
                    onPlay = { viewModel.playSong(song) },
                    onOptions = { /* mostrar diálogo de opciones */ }
                )
            }
        }
    }
}