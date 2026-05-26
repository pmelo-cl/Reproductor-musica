package com.example.reproductormusica.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.reproductormusica.models.Album
import com.example.reproductormusica.models.Artist
import com.example.reproductormusica.models.PlaylistWithSongs
import com.example.reproductormusica.ui.MainViewModel
import com.example.reproductormusica.ui.PlaylistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    mainViewModel: MainViewModel,
    playlistViewModel: PlaylistViewModel,
    onNavigateToPlaylist: (Long) -> Unit,
    onNavigateToAlbum: (String, String) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onPickAudio: () -> Unit,
    onNavigateToDownload: () -> Unit
) {
    val playlistsWithSongs by playlistViewModel.playlistsWithSongs.collectAsState()
    val albums by mainViewModel.albums.collectAsState()
    val artists by mainViewModel.artists.collectAsState()

    val recentPlaylists = playlistsWithSongs
        .filter { it.playlist.name != "Canciones disponibles" }
        .sortedByDescending { it.playlist.id }
        .take(3)

    val recentAlbums = albums.sortedByDescending { it.id }.take(3)
    val recentArtists = artists.sortedByDescending { it.id }.take(3)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Feed") },
                actions = {
                    IconButton(onClick = onPickAudio) {
                        Icon(Icons.Default.Add, "Añadir")
                    }
                    IconButton(onClick = onNavigateToDownload) {
                        Icon(Icons.Default.Download, "Descargar")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (recentPlaylists.isNotEmpty()) {
                Text("Playlists recientes", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recentPlaylists) { pw ->
                        PlaylistGridItem(pw, onClick = { onNavigateToPlaylist(pw.playlist.id) })
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (recentAlbums.isNotEmpty()) {
                Text("Álbumes recientes", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recentAlbums) { album ->
                        AlbumGridItem(album, onClick = { onNavigateToAlbum(album.name, album.artist) })
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (recentArtists.isNotEmpty()) {
                Text("Artistas recientes", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recentArtists) { artist ->
                        ArtistGridItem(artist, onClick = { onNavigateToArtist(artist.name) })
                    }
                }
            }

            if (recentPlaylists.isEmpty() && recentAlbums.isEmpty() && recentArtists.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Añade música para comenzar", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun PlaylistGridItem(playlistWithSongs: PlaylistWithSongs, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (playlistWithSongs.playlist.coverUriString != null) {
                    AsyncImage(
                        model = playlistWithSongs.playlist.coverUriString,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(playlistWithSongs.playlist.name, maxLines = 1, style = MaterialTheme.typography.bodyMedium, overflow = TextOverflow.Ellipsis)
                Text("${playlistWithSongs.songs.size} canciones", maxLines = 1, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AlbumGridItem(album: Album, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                if (album.coverUriString != null) {
                    AsyncImage(
                        model = album.coverUriString,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.ic_default_album),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(album.name, maxLines = 1, style = MaterialTheme.typography.bodyMedium, overflow = TextOverflow.Ellipsis)
                Text(album.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ArtistGridItem(artist: Artist, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(50.dp))
            ) {
                if (artist.coverUriString != null) {
                    AsyncImage(
                        model = artist.coverUriString,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(artist.name, maxLines = 1, style = MaterialTheme.typography.bodyMedium, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}