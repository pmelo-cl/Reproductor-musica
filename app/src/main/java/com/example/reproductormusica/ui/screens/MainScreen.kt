package com.example.reproductormusica.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
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
    onNavigateToPlayer: () -> Unit
) {
    val songs by viewModel.songs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var searchActive by remember { mutableStateOf(false) }

    var songForArtChange by remember { mutableStateOf<Song?>(null) }
    var songForDelete by remember { mutableStateOf<Song?>(null) }
    var songForEdit by remember { mutableStateOf<Song?>(null) }

    var editTitle by remember { mutableStateOf("") }
    var editArtist by remember { mutableStateOf("") }

    LaunchedEffect(songForEdit) {
        songForEdit?.let {
            editTitle = it.title
            editArtist = it.artist
        }
    }

    val pickAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? -> uri?.let { viewModel.addSongFromUri(it) } }
    )

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let { artUri ->
                songForArtChange?.let { song ->
                    viewModel.updateSongAlbumArt(song, artUri)
                }
            }
            songForArtChange = null
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Buscar...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                                    }
                                }
                            }
                        )
                    } else {
                        Text("Mi Reproductor")
                    }
                },
                actions = {
                    if (searchActive) {
                        IconButton(onClick = {
                            searchActive = false
                            viewModel.setSearchQuery("")
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Cerrar búsqueda")
                        }
                    } else {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar")
                        }
                        IconButton(onClick = { pickAudioLauncher.launch(arrayOf("audio/*")) }) {
                            Icon(Icons.Default.Add, contentDescription = "Añadir canción")
                        }
                        IconButton(onClick = { /* Acción comodín */ }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
                        }
                    }
                }
            )
        },
        bottomBar = {
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
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = { pickAudioLauncher.launch(arrayOf("audio/*")) }) {
                    Text("Añadir canción")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(songs, key = { it.id }) { song ->
                    SongListItem(
                        song = song,
                        isCurrentSong = song.id == currentSong?.id,
                        onPlay = { viewModel.playSong(song) },
                        onChangeAlbumArt = { songForArtChange = song },
                        onDelete = { songForDelete = song },
                        onEditInfo = { songForEdit = song }
                    )
                }
            }
        }
    }

    // Diálogo eliminar canción
    songForDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { songForDelete = null },
            title = { Text("Eliminar canción") },
            text = { Text("¿Seguro que quieres eliminar '${song.title}'?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSong(song)
                    songForDelete = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { songForDelete = null }) { Text("Cancelar") }
            }
        )
    }

    // Diálogo editar información
    songForEdit?.let { song ->
        AlertDialog(
            onDismissRequest = { songForEdit = null },
            title = { Text("Editar información") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Título") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editArtist,
                        onValueChange = { editArtist = it },
                        label = { Text("Artista") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editTitle.isNotBlank()) {
                        viewModel.updateSongInfo(song, editTitle, editArtist)
                    }
                    songForEdit = null
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { songForEdit = null }) { Text("Cancelar") }
            }
        )
    }

    LaunchedEffect(songForArtChange) {
        songForArtChange?.let { imagePickerLauncher.launch(arrayOf("image/*")) }
    }
}