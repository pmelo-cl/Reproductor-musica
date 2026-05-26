package com.example.reproductormusica.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.reproductormusica.services.DownloadService
import com.example.reproductormusica.services.MusicService
import com.example.reproductormusica.ui.screens.*
import com.example.reproductormusica.ui.theme.ReproductorMusicaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val musicServiceState = mutableStateOf<MusicService?>(null)

    private val _downloadServiceReady = MutableStateFlow(false)
    val downloadServiceReady: StateFlow<Boolean> = _downloadServiceReady.asStateFlow()

    private val _downloadServiceFlow = MutableStateFlow<DownloadService?>(null)
    val downloadServiceFlow: StateFlow<DownloadService?> = _downloadServiceFlow.asStateFlow()

    private var isMusicBound = false
    private var isDownloadBound = false

    private val musicConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.LocalBinder
            musicServiceState.value = binder.getService()
            isMusicBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            musicServiceState.value = null
            isMusicBound = false
        }
    }

    private val downloadConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as DownloadService.LocalBinder
            val svc = binder.getService()
            _downloadServiceFlow.value = svc
            _downloadServiceReady.value = true
            isDownloadBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            _downloadServiceFlow.value = null
            _downloadServiceReady.value = false
            isDownloadBound = false
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Intent(this, MusicService::class.java).also { intent ->
            bindService(intent, musicConnection, Context.BIND_AUTO_CREATE)
        }
        Intent(this, DownloadService::class.java).also { intent ->
            bindService(intent, downloadConnection, Context.BIND_AUTO_CREATE)
        }

        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }

        setContent {
            ReproductorMusicaTheme {
                val navController = rememberNavController()
                val mainViewModel: MainViewModel = viewModel()
                val downloadViewModel: DownloadViewModel = viewModel()
                val playlistViewModel: PlaylistViewModel = viewModel()

                val musicService = musicServiceState.value
                val downloadReady by downloadServiceReady.collectAsState()
                val downloadSvc by downloadServiceFlow.collectAsState()
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    downloadViewModel.onSongDownloaded = { song ->
                        scope.launch {
                            playlistViewModel.addSongToDefaultPlaylist(song.id)
                            mainViewModel.updateAlbumsAndArtists(song)
                        }
                    }
                    downloadViewModel.onPlaylistDownloaded = { outcome ->
                        scope.launch {
                            playlistViewModel.createPlaylistFromDownload(
                                outcome.songs,
                                outcome.playlistTitleHint,
                                outcome.userPlaylistName
                            )
                            for (song in outcome.songs) {
                                playlistViewModel.addSongToDefaultPlaylist(song.id)
                                mainViewModel.updateAlbumsAndArtists(song)
                            }
                        }
                    }
                }

                LaunchedEffect(musicService) {
                    musicService?.let { mainViewModel.setMusicService(it) }
                }

                LaunchedEffect(downloadSvc) {
                    downloadSvc?.let { downloadViewModel.setDownloadService(it) }
                }

                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        MainScreen(
                            viewModel = mainViewModel,
                            playlistViewModel = playlistViewModel,
                            onNavigateToPlayer = { navController.navigate("player") },
                            onNavigateToDownload = { navController.navigate("download") },
                            onNavigateToPlaylistDetail = { playlistId ->
                                navController.navigate("playlist/$playlistId")
                            },
                            onNavigateToAlbum = { albumName, artistName ->
                                navController.navigate("album/$albumName/$artistName")
                            },
                            onNavigateToArtist = { artistName ->
                                navController.navigate("artist/$artistName")
                            },
                            onNavigateToAllPlaylists = { navController.navigate("playlists") },
                            onNavigateToAllAlbums = { navController.navigate("albums") },
                            onNavigateToAllArtists = { navController.navigate("artists") }
                        )
                    }
                    composable("player") {
                        PlayerScreen(
                            viewModel = mainViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("download") {
                        DownloadScreen(
                            viewModel = downloadViewModel,
                            downloadServiceReady = downloadReady,
                            onBack = { navController.popBackStack() },
                            onDownloadComplete = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = "playlist/{playlistId}",
                        arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: return@composable
                        PlaylistDetailScreen(
                            playlistId = playlistId,
                            playlistViewModel = playlistViewModel,
                            mainViewModel = mainViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("playlists") {
                        var showCreateDialog by remember { mutableStateOf(false) }
                        var newPlaylistName by remember { mutableStateOf("") }

                        AllPlaylistsScreen(
                            playlistViewModel = playlistViewModel,
                            onBack = { navController.popBackStack() },
                            onNavigateToPlaylist = { playlistId ->
                                navController.navigate("playlist/$playlistId")
                            },
                            onCreatePlaylist = { showCreateDialog = true }
                        )

                        if (showCreateDialog) {
                            CreatePlaylistDialog(
                                name = newPlaylistName,
                                onNameChange = { newPlaylistName = it },
                                onConfirm = {
                                    if (newPlaylistName.isNotBlank()) {
                                        playlistViewModel.createPlaylist(newPlaylistName.trim())
                                        newPlaylistName = ""
                                        showCreateDialog = false
                                    }
                                },
                                onDismiss = {
                                    showCreateDialog = false
                                    newPlaylistName = ""
                                }
                            )
                        }
                    }
                    composable("albums") {
                        AllAlbumsScreen(
                            mainViewModel = mainViewModel,
                            onBack = { navController.popBackStack() },
                            onNavigateToAlbum = { albumName, artistName ->
                                navController.navigate("album/$albumName/$artistName")
                            }
                        )
                    }
                    composable("artists") {
                        AllArtistsScreen(
                            mainViewModel = mainViewModel,
                            onBack = { navController.popBackStack() },
                            onNavigateToArtist = { artistName ->
                                navController.navigate("artist/$artistName")
                            }
                        )
                    }
                    composable(
                        route = "album/{albumName}/{artistName}",
                        arguments = listOf(
                            navArgument("albumName") { type = NavType.StringType },
                            navArgument("artistName") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val albumName = backStackEntry.arguments?.getString("albumName") ?: return@composable
                        val artistName = backStackEntry.arguments?.getString("artistName") ?: return@composable
                        AlbumDetailScreen(
                            albumName = albumName,
                            artistName = artistName,
                            mainViewModel = mainViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = "artist/{artistName}",
                        arguments = listOf(navArgument("artistName") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val artistName = backStackEntry.arguments?.getString("artistName") ?: return@composable
                        ArtistDetailScreen(
                            artistName = artistName,
                            mainViewModel = mainViewModel,
                            onBack = { navController.popBackStack() },
                            onNavigateToAlbum = { albumName, albumArtist ->
                                navController.navigate("album/$albumName/$albumArtist")
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isMusicBound) unbindService(musicConnection)
        if (isDownloadBound) unbindService(downloadConnection)
    }
}

@Composable
fun CreatePlaylistDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = name.isNotBlank()
            ) {
                Text("Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}