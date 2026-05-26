package com.example.reproductormusica.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QueueMusic
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
import com.example.reproductormusica.models.PlaylistWithSongs
import com.example.reproductormusica.ui.PlaylistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllPlaylistsScreen(
    playlistViewModel: PlaylistViewModel,
    onBack: () -> Unit,
    onNavigateToPlaylist: (Long) -> Unit,
    onCreatePlaylist: () -> Unit
) {
    val playlistsWithSongs by playlistViewModel.playlistsWithSongs.collectAsState()
    val userPlaylists = playlistsWithSongs.filter { it.playlist.name != "Canciones disponibles" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Todas las playlists") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = onCreatePlaylist) {
                        Icon(Icons.Default.Add, "Nueva playlist")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (userPlaylists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No hay playlists", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onCreatePlaylist) {
                        Text("Crear playlist")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                items(userPlaylists, key = { it.playlist.id }) { pw ->
                    PlaylistListItem(
                        playlistWithSongs = pw,
                        onClick = { onNavigateToPlaylist(pw.playlist.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistListItem(
    playlistWithSongs: PlaylistWithSongs,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (playlistWithSongs.playlist.coverUriString != null) {
                AsyncImage(
                    model = playlistWithSongs.playlist.coverUriString,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_default_album),
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    playlistWithSongs.playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${playlistWithSongs.songs.size} canciones",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}