package com.example.reproductormusica.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.example.reproductormusica.R
import com.example.reproductormusica.ui.MainViewModel
import com.example.reproductormusica.ui.PlaylistViewModel
import com.example.reproductormusica.ui.components.MiniPlayerBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    playlistViewModel: PlaylistViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToDownload: () -> Unit,
    onNavigateToPlaylistDetail: (Long) -> Unit,
    onNavigateToSpotifyImport: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    val pickAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? -> uri?.let { viewModel.addSongFromUri(it) } }
    )

    Scaffold(
        floatingActionButton = {
            if (selectedTab == 1) {
                FloatingActionButton(
                    onClick = { showCreatePlaylistDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nueva playlist")
                }
            }
        },
        bottomBar = {
            Column {
                currentSong?.let { song ->
                    MiniPlayerBar(
                        song = song,
                        isPlaying = isPlaying,
                        onPlayPause = { viewModel.playPause() },
                        onNext = { viewModel.playNext() },
                        onBarClick = onNavigateToPlayer,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(painterResource(R.drawable.ic_feed), contentDescription = null) },
                        label = { Text("Feed") },
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                    NavigationBarItem(
                        icon = { Icon(painterResource(R.drawable.ic_library), contentDescription = null) },
                        label = { Text("Biblioteca") },
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> FeedScreen(
                    onPickAudio = { pickAudioLauncher.launch(arrayOf("audio/*")) },
                    onNavigateToDownload = onNavigateToDownload,
                    onNavigateToSpotifyImport = onNavigateToSpotifyImport
                )
                1 -> PlaylistListScreen(
                    playlistViewModel = playlistViewModel,
                    mainViewModel = viewModel,
                    onNavigateToPlaylist = onNavigateToPlaylistDetail,
                    onPickAudio = { pickAudioLauncher.launch(arrayOf("audio/*")) },
                    onNavigateToDownload = onNavigateToDownload
                )
            }
        }
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Nueva playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            playlistViewModel.createPlaylist(newPlaylistName.trim())
                            newPlaylistName = ""
                            showCreatePlaylistDialog = false
                        }
                    },
                    enabled = newPlaylistName.isNotBlank()
                ) {
                    Text("Crear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}