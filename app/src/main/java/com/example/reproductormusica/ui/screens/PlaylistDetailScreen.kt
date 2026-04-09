package com.example.reproductormusica.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.reproductormusica.R
import com.example.reproductormusica.models.Song
import com.example.reproductormusica.ui.MainViewModel
import com.example.reproductormusica.ui.PlaylistViewModel
import com.example.reproductormusica.utils.advancedMatch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    playlistViewModel: PlaylistViewModel,
    mainViewModel: MainViewModel,
    onBack: () -> Unit
) {
    LaunchedEffect(playlistId) { playlistViewModel.selectPlaylist(playlistId) }

    val playlistWithSongs by playlistViewModel.selectedPlaylist.collectAsState()
    val allSongs by mainViewModel.allSongs.collectAsState()

    var showAddSongsDialog by remember { mutableStateOf(false) }
    var songToRemove by remember { mutableStateOf<Song?>(null) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val playlist = playlistWithSongs?.playlist ?: return
    val songsInPlaylist = playlistWithSongs?.songs ?: emptyList()
    val isDefaultPlaylist = playlist.name == "Canciones disponibles"

    val filteredSongs = remember(songsInPlaylist, searchQuery) {
        if (searchQuery.isBlank()) songsInPlaylist
        else songsInPlaylist.filter { song ->
            advancedMatch(searchQuery, song.title) || advancedMatch(searchQuery, song.artist)
        }
    }

    // Para editar canción desde el diálogo de añadir
    var songForEdit by remember { mutableStateOf<Song?>(null) }
    var songForArtChange by remember { mutableStateOf<Song?>(null) }
    var songForDelete by remember { mutableStateOf<Song?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editArtist by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let { artUri ->
                songForArtChange?.let { song -> mainViewModel.updateSongAlbumArt(song, artUri) }
            }
            songForArtChange = null
        }
    )

    LaunchedEffect(songsInPlaylist.size) {
        if (isDefaultPlaylist && songsInPlaylist.isEmpty()) {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar en playlist...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    } else {
                        Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
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
                        // Ocultar añadir canciones y opciones en la playlist por defecto
                        if (!isDefaultPlaylist) {
                            IconButton(onClick = { showAddSongsDialog = true }) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Añadir canciones")
                            }
                            IconButton(onClick = { /* Opciones editar playlist */ }) {
                                Icon(Icons.Default.MoreVert, "Opciones")
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (filteredSongs.isNotEmpty()) {
                FloatingActionButton(onClick = {
                    mainViewModel.playPlaylist(playlist.id, filteredSongs)
                }) {
                    Icon(Icons.Default.PlayArrow, "Reproducir playlist")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Cabecera
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Forzar portada por defecto si es la playlist especial
                if (isDefaultPlaylist) {
                    Image(
                        painter = painterResource(R.drawable.ic_default_album),
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else if (playlist.coverUriString != null) {
                    AsyncImage(
                        model = playlist.coverUriString,
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.ic_default_album),
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(playlist.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${filteredSongs.size} canción(es)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            if (filteredSongs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(if (searchQuery.isNotBlank()) "No se encontraron canciones" else "Playlist vacía")
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(filteredSongs, key = { it.id }) { song ->
                        PlaylistSongRow(
                            song = song,
                            onPlay = {
                                mainViewModel.playSong(song)
                                mainViewModel.setQueue(filteredSongs)
                            },
                            onRemove = if (isDefaultPlaylist) null else { { songToRemove = song } },
                            showOptions = true,
                            onEditInfo = { songForEdit = song },
                            onChangeAlbumArt = { songForArtChange = song },
                            onDelete = { songForDelete = song }
                        )
                    }
                }
            }
        }
    }

    // Diálogo añadir canciones (con búsqueda y opciones) - solo para playlists normales
    if (showAddSongsDialog && !isDefaultPlaylist) {
        AddSongsToPlaylistDialog(
            allSongs = allSongs,
            existingSongIds = songsInPlaylist.map { it.id }.toSet(),
            onAdd = { song -> playlistViewModel.addSongToPlaylist(playlist.id, song.id) },
            onDismiss = { showAddSongsDialog = false },
            onEditInfo = { songForEdit = it },
            onChangeAlbumArt = { songForArtChange = it },
            onDelete = { songForDelete = it }
        )
    }

    // Diálogo quitar canción (solo para playlists normales)
    if (!isDefaultPlaylist) {
        songToRemove?.let { song ->
            AlertDialog(
                onDismissRequest = { songToRemove = null },
                title = { Text("Quitar de la playlist") },
                text = { Text("¿Quitar '${song.title}' de '${playlist.name}'?") },
                confirmButton = {
                    TextButton(onClick = {
                        playlistViewModel.removeSongFromPlaylist(playlist.id, song.id)
                        songToRemove = null
                    }) { Text("Quitar") }
                },
                dismissButton = {
                    TextButton(onClick = { songToRemove = null }) { Text("Cancelar") }
                }
            )
        }
    }

    // Diálogo editar canción
    songForEdit?.let { song ->
        LaunchedEffect(song) {
            editTitle = song.title
            editArtist = song.artist
        }
        AlertDialog(
            onDismissRequest = { songForEdit = null },
            title = { Text("Editar información") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Título") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editArtist,
                        onValueChange = { editArtist = it },
                        label = { Text("Artista") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editTitle.isNotBlank()) {
                        mainViewModel.updateSongInfo(song, editTitle, editArtist)
                    }
                    songForEdit = null
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { songForEdit = null }) { Text("Cancelar") }
            }
        )
    }

    // Diálogo eliminar canción (con advertencia especial para "Canciones disponibles")
    songForDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { songForDelete = null },
            title = { Text("Eliminar canción") },
            text = {
                Text(
                    if (isDefaultPlaylist) {
                        "¿Eliminar '${song.title}' de la aplicación? Esta acción no se puede deshacer."
                    } else {
                        "¿Eliminar '${song.title}' de la aplicación? También se quitará de todas las playlists."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    mainViewModel.deleteSong(song)
                    songForDelete = null
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { songForDelete = null }) { Text("Cancelar") }
            }
        )
    }

    LaunchedEffect(songForArtChange) {
        songForArtChange?.let { imagePickerLauncher.launch(arrayOf("image/*")) }
    }
}

@Composable
private fun PlaylistSongRow(
    song: Song,
    onPlay: () -> Unit,
    onRemove: (() -> Unit)?,
    showOptions: Boolean,
    onEditInfo: () -> Unit,
    onChangeAlbumArt: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            if (song.albumArtUri != null) {
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_default_album),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onPlay) {
                    Icon(Icons.Default.PlayArrow, "Reproducir")
                }
                if (onRemove != null) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Remove, "Quitar", tint = MaterialTheme.colorScheme.error)
                    }
                }
                if (showOptions) {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, "Opciones")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Editar información") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { menuExpanded = false; onEditInfo() }
                            )
                            DropdownMenuItem(
                                text = { Text("Cambiar portada") },
                                leadingIcon = { Icon(Icons.Default.Image, null) },
                                onClick = { menuExpanded = false; onChangeAlbumArt() }
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
        },
        modifier = Modifier.padding(horizontal = 4.dp)
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun AddSongsToPlaylistDialog(
    allSongs: List<Song>,
    existingSongIds: Set<Long>,
    onAdd: (Song) -> Unit,
    onDismiss: () -> Unit,
    onEditInfo: (Song) -> Unit,
    onChangeAlbumArt: (Song) -> Unit,
    onDelete: (Song) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val availableSongs = allSongs.filter { it.id !in existingSongIds }
    val filteredSongs = remember(availableSongs, searchQuery) {
        if (searchQuery.isBlank()) availableSongs
        else availableSongs.filter { song ->
            advancedMatch(searchQuery, song.title) || advancedMatch(searchQuery, song.artist)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir canciones") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (filteredSongs.isEmpty()) {
                    Text("No se encontraron canciones")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 350.dp)) {
                        items(filteredSongs, key = { it.id }) { song ->
                            SongListItemWithOptions(
                                song = song,
                                onAdd = { onAdd(song) },
                                onEditInfo = { onEditInfo(song) },
                                onChangeAlbumArt = { onChangeAlbumArt(song) },
                                onDelete = { onDelete(song) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
private fun SongListItemWithOptions(
    song: Song,
    onAdd: () -> Unit,
    onEditInfo: () -> Unit,
    onChangeAlbumArt: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            if (song.albumArtUri != null) {
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_default_album),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, "Añadir")
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, "Opciones")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Editar información") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { menuExpanded = false; onEditInfo() }
                        )
                        DropdownMenuItem(
                            text = { Text("Cambiar portada") },
                            leadingIcon = { Icon(Icons.Default.Image, null) },
                            onClick = { menuExpanded = false; onChangeAlbumArt() }
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
    )
    HorizontalDivider()
}