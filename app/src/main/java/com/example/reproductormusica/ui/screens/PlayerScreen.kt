package com.example.reproductormusica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
fun PlayerScreen(
    viewModel: MainViewModel,
    onPickAudio: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val songs by viewModel.songs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    // Estado para el diálogo de opciones
    var songForOptions by remember { mutableStateOf<Song?>(null) }

    // Mostrar diálogo de opciones si hay canción seleccionada
    songForOptions?.let { song ->
        SongOptionsDialog(
            song = song,
            onDismiss = { songForOptions = null },
            onDelete = {
                viewModel.deleteSong(song)
                songForOptions = null
            },
            onUpdateAlbumArt = { uri ->
                viewModel.updateSongAlbumArt(song, uri)
                songForOptions = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi Reproductor") },
                actions = {
                    IconButton(onClick = { /* Futuro: búsqueda */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar")
                    }
                    IconButton(onClick = onPickAudio) {
                        Icon(Icons.Default.Add, contentDescription = "Añadir archivo")
                    }
                }
            )
        },
        bottomBar = {
            currentSong?.let { song ->
                // No mostrar si la canción es el centinela de "sin canción"
                if (song.uriString.isNotEmpty()) {
                    MiniPlayerBar(
                        song = song,
                        isPlaying = isPlaying,
                        onPlayPause = { viewModel.playPause() },
                        onNext = { viewModel.playNext() },
                        onBarClick = onNavigateToPlayer
                    )
                }
            }
        }
    ) { paddingValues ->
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No hay canciones",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Pulsa + para añadir archivos de audio",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
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
                        onOptions = { songForOptions = song }
                    )
                }
            }
        }
    }
}