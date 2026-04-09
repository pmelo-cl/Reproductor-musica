package com.example.reproductormusica.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.reproductormusica.R
import com.example.reproductormusica.models.Playlist
import com.example.reproductormusica.models.PlaylistWithSongs
import com.example.reproductormusica.ui.MainViewModel
import com.example.reproductormusica.ui.PlaylistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistListScreen(
    playlistViewModel: PlaylistViewModel,
    mainViewModel: MainViewModel,
    onNavigateToPlaylist: (Long) -> Unit,
    onPickAudio: () -> Unit,
    onNavigateToDownload: () -> Unit
) {
    val playlistsWithSongs by playlistViewModel.playlistsWithSongs.collectAsState()
    val playingPlaylistId by mainViewModel.playingPlaylistId.collectAsState()

    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var playlistForCover by remember { mutableStateOf<Playlist?>(null) }
    var newName by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val sortedPlaylists = playlistsWithSongs.sortedWith(
        compareBy<PlaylistWithSongs> { it.playlist.name != "Canciones disponibles" }
            .thenBy { it.playlist.name }
    )

    val filteredPlaylists = remember(sortedPlaylists, searchQuery) {
        if (searchQuery.isBlank()) sortedPlaylists
        else sortedPlaylists.filter { it.playlist.name.contains(searchQuery, ignoreCase = true) }
    }

    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let { artUri ->
                playlistForCover?.let { pl -> playlistViewModel.updatePlaylistCover(pl, artUri) }
            }
            playlistForCover = null
        }
    )

    LaunchedEffect(playlistForCover) {
        playlistForCover?.let { coverPickerLauncher.launch(arrayOf("image/*")) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar playlist...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    } else {
                        Text("Biblioteca")
                    }
                },
                actions = {
                    if (searchActive) {
                        IconButton(onClick = {
                            searchActive = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.Default.Close, "Cerrar")
                        }
                    } else {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, "Buscar")
                        }
                        IconButton(onClick = onPickAudio) {
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
        if (filteredPlaylists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.LibraryMusic,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("No hay playlists", style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredPlaylists, key = { it.playlist.id }) { pw ->
                    PlaylistItem(
                        playlistWithSongs = pw,
                        onClick = { onNavigateToPlaylist(pw.playlist.id) },
                        onRename = {
                            newName = pw.playlist.name
                            playlistToRename = pw.playlist
                        },
                        onChangeCover = { playlistForCover = pw.playlist },
                        onDelete = { playlistToDelete = pw.playlist },
                        isPlaying = pw.playlist.id == playingPlaylistId
                    )
                }
            }
        }
    }

    playlistToRename?.let { pl ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Renombrar playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        playlistViewModel.renamePlaylist(pl, newName.trim())
                    },
                    enabled = newName.isNotBlank()
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { }) { Text("Cancelar") }
            }
        )
    }

    playlistToDelete?.let { pl ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Eliminar playlist") },
            text = { Text("¿Eliminar '${pl.name}'? Las canciones no se borrarán.") },
            confirmButton = {
                TextButton(onClick = {
                    playlistViewModel.deletePlaylist(pl)
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun PlaylistItem(
    playlistWithSongs: PlaylistWithSongs,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onChangeCover: () -> Unit,
    onDelete: () -> Unit,
    isPlaying: Boolean
) {
    val pl = playlistWithSongs.playlist
    val isDefault = pl.name == "Canciones disponibles"
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isDefault) {
                Image(
                    painter = painterResource(R.drawable.ic_default_album),
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else if (pl.coverUriString != null) {
                AsyncImage(
                    model = pl.coverUriString,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_default_album),
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(pl.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    "${playlistWithSongs.songs.size} canción(es)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isPlaying) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Reproduciendo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            if (!isDefault) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Renombrar") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { menuExpanded = false; onRename() }
                        )
                        DropdownMenuItem(
                            text = { Text("Cambiar portada") },
                            leadingIcon = { Icon(Icons.Default.Image, null) },
                            onClick = { menuExpanded = false; onChangeCover() }
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}