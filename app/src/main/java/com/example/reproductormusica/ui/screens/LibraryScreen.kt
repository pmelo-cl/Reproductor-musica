package com.example.reproductormusica.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.reproductormusica.models.Song
import com.example.reproductormusica.ui.MainViewModel
import com.example.reproductormusica.ui.components.SongListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToDownload: () -> Unit
) {
    val songs by viewModel.songs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var searchActive by remember { mutableStateOf(false) }
    var songForArtChange by remember { mutableStateOf<Song?>(null) }
    var songForDelete by remember { mutableStateOf<Song?>(null) }
    var songForEdit by remember { mutableStateOf<Song?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editArtist by remember { mutableStateOf("") }

    val currentSong by viewModel.currentSong.collectAsState()

    LaunchedEffect(songForEdit) {
        songForEdit?.let {
            editTitle = it.title
            editArtist = it.artist
        }
    }

    val pickAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { viewModel.addSongFromUri(it) } }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { artUri ->
            songForArtChange?.let { song -> viewModel.updateSongAlbumArt(song, artUri) }
        }
        songForArtChange = null
    }

    LaunchedEffect(songForArtChange) {
        songForArtChange?.let { imagePickerLauncher.launch(arrayOf("image/*")) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Buscar…") },
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
                                        Icon(Icons.Default.Clear, "Limpiar")
                                    }
                                }
                            }
                        )
                    } else {
                        Text("Biblioteca")
                    }
                },
                actions = {
                    if (searchActive) {
                        IconButton(onClick = {
                            searchActive = false
                            viewModel.setSearchQuery("")
                        }) {
                            Icon(Icons.Default.Clear, "Cerrar búsqueda")
                        }
                    } else {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, "Buscar")
                        }
                        IconButton(onClick = { pickAudioLauncher.launch(arrayOf("audio/*")) }) {
                            Icon(Icons.Default.Add, "Añadir canción")
                        }
                        IconButton(onClick = onNavigateToDownload) {
                            Icon(Icons.Default.Download, "Descargar música")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No hay canciones", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { pickAudioLauncher.launch(arrayOf("audio/*")) }) {
                        Text("Añadir canción")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onNavigateToDownload) { Text("Descargar música") }
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
                    Spacer(Modifier.height(8.dp))
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
                    if (editTitle.isNotBlank()) viewModel.updateSongInfo(song, editTitle, editArtist)
                    songForEdit = null
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { songForEdit = null }) { Text("Cancelar") }
            }
        )
    }
}