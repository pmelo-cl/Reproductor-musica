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
import androidx.compose.runtime.*
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
    private var downloadService: DownloadService? = null

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
            downloadService = binder.getService()
            _downloadServiceReady.value = true
            isDownloadBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            downloadService = null
            _downloadServiceReady.value = false
            isDownloadBound = false
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* no es necesario hacer nada extra */ }

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
                val scope = rememberCoroutineScope()

                // Callback para añadir canciones descargadas a la playlist por defecto
                LaunchedEffect(Unit) {
                    downloadViewModel.onSongDownloaded = { song ->
                        scope.launch {
                            playlistViewModel.addSongToDefaultPlaylist(song.id)
                        }
                    }
                }

                LaunchedEffect(musicService) {
                    musicService?.let { mainViewModel.setMusicService(it) }
                }

                LaunchedEffect(downloadReady) {
                    if (downloadReady && downloadService != null) {
                        downloadViewModel.setDownloadService(downloadService!!)
                    }
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
                            onNavigateToSpotifyImport = { navController.navigate("spotify_import") }
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
                        val playlistId =
                            backStackEntry.arguments?.getLong("playlistId") ?: return@composable
                        PlaylistDetailScreen(
                            playlistId = playlistId,
                            playlistViewModel = playlistViewModel,
                            mainViewModel = mainViewModel,
                            onBack = { navController.popBackStack() }
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