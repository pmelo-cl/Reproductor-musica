package com.example.reproductormusica.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.example.reproductormusica.R
import com.example.reproductormusica.models.Song
import com.example.reproductormusica.ui.MainViewModel
import kotlin.math.floor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val position by viewModel.playbackPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()

    var showOptionsMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var editTitle by remember { mutableStateOf("") }
    var editArtist by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let { artUri ->
                currentSong?.let { song ->
                    viewModel.updateSongAlbumArt(song, artUri)
                }
            }
        }
    )

    currentSong?.let { song ->
        LaunchedEffect(song) {
            editTitle = song.title
            editArtist = song.artist
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Reproduciendo") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showOptionsMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                        }
                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Editar información") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = {
                                    showOptionsMenu = false
                                    showEditDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Cambiar portada") },
                                leadingIcon = { Icon(Icons.Default.Image, null) },
                                onClick = {
                                    showOptionsMenu = false
                                    imagePickerLauncher.launch(arrayOf("image/*"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showOptionsMenu = false
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (song.albumArtUri != null) {
                    AsyncImage(
                        model = song.albumArtUri,
                        contentDescription = "Portada",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.ic_default_album),
                        contentDescription = "Portada",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(text = song.title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                Slider(
                    value = position.toFloat(),
                    onValueChange = { viewModel.seekTo(it.toLong()) },
                    valueRange = 0f..duration.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatTime(position))
                    Text(text = formatTime(duration))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Dentro de PlayerScreen
                var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
                var shuffleEnabled by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        shuffleEnabled = !shuffleEnabled
                        viewModel.setShuffleMode(shuffleEnabled)
                    }) {
                        Icon(
                            Icons.Default.Shuffle,
                            null,
                            tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { viewModel.playPrevious() }) {
                        Icon(Icons.Default.SkipPrevious, "Anterior")
                    }
                    FloatingActionButton(
                        onClick = { viewModel.playPause() },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (isPlaying) "Pausar" else "Reproducir",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.playNext() }) {
                        Icon(Icons.Default.SkipNext, "Siguiente")
                    }
                    IconButton(onClick = {
                        repeatMode = when (repeatMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                            else -> Player.REPEAT_MODE_OFF
                        }
                        viewModel.setRepeatMode(repeatMode)
                    }) {
                        Icon(
                            Icons.Default.Repeat,
                            null,
                            tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Diálogo de edición
        if (showEditDialog) {
            AlertDialog(
                onDismissRequest = { showEditDialog = false },
                title = { Text("Editar información") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = editTitle,
                            onValueChange = { editTitle = it },
                            label = { Text("Título") },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
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
                            viewModel.updateSongInfo(song, editTitle, editArtist)
                        }
                        showEditDialog = false
                    }) { Text("Guardar") }
                },
                dismissButton = {
                    TextButton(onClick = { showEditDialog = false }) { Text("Cancelar") }
                }
            )
        }

        // Diálogo de eliminación
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Eliminar canción") },
                text = { Text("¿Seguro que quieres eliminar '${song.title}'?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteSong(song)
                        showDeleteDialog = false
                        onBack()
                    }) { Text("Eliminar") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
                }
            )
        }
    } ?: run {
        LaunchedEffect(Unit) { onBack() }
    }

}

private fun formatTime(millis: Long): String {
    val totalSeconds = floor(millis / 1000.0).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

