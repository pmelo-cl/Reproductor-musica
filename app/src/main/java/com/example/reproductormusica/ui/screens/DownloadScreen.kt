package com.example.reproductormusica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reproductormusica.ui.DownloadViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel = viewModel(),
    downloadServiceReady: Boolean,
    onBack: () -> Unit,
    onDownloadComplete: (() -> Unit)? = null
) {
    var url by remember { mutableStateOf("") }
    val progress by viewModel.progress.collectAsState()
    val status by viewModel.status.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val downloadedSong by viewModel.downloadedSong.collectAsState()

    LaunchedEffect(downloadedSong) {
        if (downloadedSong != null) {
            onDownloadComplete?.invoke()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Descargar música") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
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
            if (!downloadServiceReady) {
                // Mostrar loading mientras el servicio no está listo
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Conectando con el servicio de descarga...")
                    }
                }
                return@Scaffold
            }

            Text(
                text = "Pega la URL de YouTube, SoundCloud u otro sitio compatible",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL del video/audio") },
                placeholder = { Text("https://...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isDownloading
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.startDownload(url) },
                enabled = url.isNotBlank() && !isDownloading && downloadServiceReady,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isDownloading) "Descargando..." else "Descargar Audio")
            }

            if (progress > 0f) {
                Spacer(modifier = Modifier.height(24.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = status)
            }

            if (downloadedSong != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("¡Descarga completada!", style = MaterialTheme.typography.titleMedium)
                        Text(downloadedSong!!.title, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.clearDownloadedSong()
                                url = ""
                            }
                        ) {
                            Text("Descargar otra")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ℹ️ Información", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "El audio se descargará en formato MP3 en la carpeta Downloads de tu dispositivo.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}