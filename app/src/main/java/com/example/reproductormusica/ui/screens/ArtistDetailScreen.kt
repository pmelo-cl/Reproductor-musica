package com.example.reproductormusica.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
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
import com.example.reproductormusica.models.Song
import com.example.reproductormusica.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistName: String,
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (String, String) -> Unit
) {
    val songs by mainViewModel.getSongsFromArtist(artistName).collectAsState(initial = emptyList())

    val songsByAlbum = remember(songs) {
        songs.groupBy { it.album ?: "Sin álbum" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(artistName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                }
            )
        },
        floatingActionButton = {
            if (songs.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        mainViewModel.playFromQueue(null, songs, songs.first())
                    }
                ) {
                    Icon(Icons.Default.PlayArrow, "Reproducir todo")
                }
            }
        }
    ) { paddingValues ->
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("No hay canciones de este artista", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val coverUri = songs.firstOrNull()?.albumArtUri
                        if (coverUri != null) {
                            AsyncImage(
                                model = coverUri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(160.dp)
                                    .clip(RoundedCornerShape(80.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(160.dp)
                                    .padding(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            artistName,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            "${songs.size} canciones · ${songsByAlbum.size} álbumes",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }

                songsByAlbum.forEach { (albumName, albumSongs) ->
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (albumName != "Sin álbum") {
                                        onNavigateToAlbum(albumName, artistName)
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                albumName,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            if (albumName != "Sin álbum") {
                                Text(
                                    "Ver álbum",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    items(albumSongs, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            onPlay = { mainViewModel.playFromQueue(null, albumSongs, song) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SongRow(
    song: Song,
    onPlay: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .clickable(onClick = onPlay)
            .padding(horizontal = 8.dp),
        headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            if (song.albumArtUri != null) {
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_default_album),
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onPlay) {
                Icon(Icons.Default.PlayArrow, "Reproducir")
            }
        }
    )
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
}