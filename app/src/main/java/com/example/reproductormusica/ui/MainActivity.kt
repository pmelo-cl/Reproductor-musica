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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.reproductormusica.services.MusicService
import com.example.reproductormusica.ui.screens.MainScreen
import com.example.reproductormusica.ui.screens.PlayerScreen
import com.example.reproductormusica.ui.theme.ReproductorMusicaTheme

class MainActivity : ComponentActivity() {

    private var musicService: MusicService? = null
    private var isBound = false
    private var onServiceBound: ((MusicService) -> Unit)? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.LocalBinder
            musicService = binder.getService()
            isBound = true
            onServiceBound?.invoke(musicService!!)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    // Permiso de almacenamiento/audio
    private val requestStoragePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* La lista se actualizará automáticamente via Flow */ }

    // Permiso de notificaciones (Android 13+)
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Silencioso */ }

    // Selector de archivos de audio
    private var onAudioPicked: ((android.net.Uri) -> Unit)? = null
    private val pickAudioLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            // Persistir permiso de lectura para el URI
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            onAudioPicked?.invoke(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Iniciar y vincular el servicio
        Intent(this, MusicService::class.java).also { intent ->
            startService(intent)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        // Solicitar permisos
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestStoragePermission.launch(Manifest.permission.READ_MEDIA_AUDIO)
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestStoragePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        setContent {
            ReproductorMusicaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val mainViewModel: MainViewModel = viewModel()
                    val navController = rememberNavController()

                    // Vincular servicio al ViewModel cuando esté listo
                    LaunchedEffect(Unit) {
                        onServiceBound = { service ->
                            mainViewModel.setMusicService(service)
                        }
                        // Por si el servicio ya estaba enlazado antes del LaunchedEffect
                        musicService?.let { mainViewModel.setMusicService(it) }
                    }

                    // Configurar callbacks para el selector de archivos
                    onAudioPicked = { uri ->
                        mainViewModel.addSongFromUri(uri)
                    }

                    NavHost(navController = navController, startDestination = "main") {
                        composable("main") {
                            MainScreen(
                                viewModel = mainViewModel,
                                onPickAudio = {
                                    pickAudioLauncher.launch(arrayOf("audio/*"))
                                },
                                onNavigateToPlayer = {
                                    navController.navigate("player")
                                }
                            )
                        }
                        composable("player") {
                            PlayerScreen(
                                viewModel = mainViewModel,
                                onPickAudio = {
                                    pickAudioLauncher.launch(arrayOf("audio/*"))
                                },
                                onNavigateToPlayer = {
                                    navController.navigate("player")
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}