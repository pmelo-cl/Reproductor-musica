package com.example.reproductormusica.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
    onNavigateToAlbum: (String, String) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToAllPlaylists: () -> Unit,
    onNavigateToAllAlbums: () -> Unit,
    onNavigateToAllArtists: () -> Unit
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
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (selectedTab == 1) {
                FloatingActionButton(
                    onClick = { showCreatePlaylistDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_playlist))
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
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        icon = { Icon(painterResource(R.drawable.ic_feed), contentDescription = null) },
                        label = { Text(stringResource(R.string.feed)) },
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(painterResource(R.drawable.ic_library), contentDescription = null) },
                        label = { Text(stringResource(R.string.library)) },
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> FeedScreen(
                    mainViewModel = viewModel,
                    playlistViewModel = playlistViewModel,
                    onNavigateToPlaylist = onNavigateToPlaylistDetail,
                    onNavigateToAlbum = onNavigateToAlbum,
                    onNavigateToArtist = onNavigateToArtist,
                    onPickAudio = { pickAudioLauncher.launch(arrayOf("audio/*")) },
                    onNavigateToDownload = onNavigateToDownload
                )
                1 -> LibraryScreen(
                    mainViewModel = viewModel,
                    playlistViewModel = playlistViewModel,
                    onNavigateToPlaylist = onNavigateToPlaylistDetail,
                    onNavigateToAlbum = onNavigateToAlbum,
                    onNavigateToArtist = onNavigateToArtist,
                    onNavigateToAllPlaylists = onNavigateToAllPlaylists,
                    onNavigateToAllAlbums = onNavigateToAllAlbums,
                    onNavigateToAllArtists = onNavigateToAllArtists,
                    onPickAudio = { pickAudioLauncher.launch(arrayOf("audio/*")) },
                    onNavigateToDownload = onNavigateToDownload
                )
            }
        }
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text(stringResource(R.string.new_playlist)) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text(stringResource(R.string.name)) },
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
                    Text(stringResource(R.string.create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}