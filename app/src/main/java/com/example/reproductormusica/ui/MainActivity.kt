package com.example.reproductormusica.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.reproductormusica.services.MusicService
import com.example.reproductormusica.ui.screens.MainScreen
import com.example.reproductormusica.ui.screens.PlayerScreen
import com.example.reproductormusica.ui.theme.ReproductorMusicaTheme

class MainActivity : ComponentActivity() {

    // Estado observable para el servicio
    private val musicServiceState = mutableStateOf<MusicService?>(null)
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.LocalBinder
            musicServiceState.value = binder.getService()
            isBound = true
            Log.d("MainActivity", "✅ Servicio conectado")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicServiceState.value = null
            isBound = false
            Log.d("MainActivity", "❌ Servicio desconectado")
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Opcional: recargar canciones */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Iniciar y vincular el servicio
        Intent(this, MusicService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        // Solicitar permiso de audio
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        setContent {
            ReproductorMusicaTheme {
                val navController = rememberNavController()
                val mainViewModel: MainViewModel = viewModel()

                // Obtener el valor actual del servicio
                val service = musicServiceState.value

                // Cuando el servicio cambie (de null a objeto), lo pasamos al ViewModel
                LaunchedEffect(service) {
                    service?.let {
                        mainViewModel.setMusicService(it)
                        Log.d("MainActivity", "✅ Servicio pasado al ViewModel")
                    }
                }

                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        MainScreen(
                            viewModel = mainViewModel,
                            onNavigateToPlayer = { navController.navigate("player") }
                        )
                    }
                    composable("player") {
                        PlayerScreen(
                            viewModel = mainViewModel,
                            onBack = { navController.popBackStack() }
                        )
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