package com.example.reproductormusica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.reproductormusica.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onPickAudio: () -> Unit,
    onNavigateToDownload: () -> Unit,
    onNavigateToSpotifyImport: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Feed") },
                actions = {
                    IconButton(onClick = onPickAudio) {
                        Icon(Icons.Default.Add, contentDescription = "Añadir canción")
                    }
                    IconButton(onClick = onNavigateToDownload) {
                        Icon(Icons.Default.Download, contentDescription = "Descargar música")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Feed", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Próximamente: novedades, tendencias y más",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}