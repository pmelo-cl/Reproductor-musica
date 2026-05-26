package com.example.reproductormusica.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reproductormusica.R
import com.example.reproductormusica.ui.DownloadViewModel
import com.example.reproductormusica.utils.StreamingUrlUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel = viewModel(),
    downloadServiceReady: Boolean,
    onBack: () -> Unit,
    onDownloadComplete: (() -> Unit)? = null
) {
    var url by remember { mutableStateOf("") }
    var playlistMode by remember { mutableStateOf(false) }
    var playlistKindIsAlbum by remember { mutableStateOf(true) }
    var playlistOrAlbumName by remember { mutableStateOf("") }
    var singleTrackAlbumHint by remember { mutableStateOf("") }

    LaunchedEffect(url) {
        StreamingUrlUtils.playlistInferenceFromUrl(url)?.let { playlistMode = it }
    }

    val progress by viewModel.progress.collectAsState()
    val status by viewModel.status.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val downloadedSong by viewModel.downloadedSong.collectAsState()
    val downloadedPlaylistSongs by viewModel.downloadedPlaylistSongs.collectAsState()

    val effectivePlaylist = remember(url, playlistMode) {
        StreamingUrlUtils.effectivePlaylistMode(url, playlistMode)
    }

    LaunchedEffect(downloadedSong, downloadedPlaylistSongs) {
        if (downloadedSong != null || !downloadedPlaylistSongs.isNullOrEmpty()) {
            onDownloadComplete?.invoke()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.download_music)) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!downloadServiceReady) {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Conectando con el servicio de descarga...")
                    }
                }
                return@Scaffold
            }

            Text(
                text = stringResource(R.string.download_paste_url_hint),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.download_url_label)) },
                placeholder = { Text(stringResource(R.string.download_url_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isDownloading
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                stringResource(R.string.download_playlist_mode_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.download_playlist_mode_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = playlistMode,
                        onCheckedChange = { playlistMode = it },
                        enabled = !isDownloading &&
                            StreamingUrlUtils.playlistInferenceFromUrl(url) == null
                    )
                }
            }

            if (effectivePlaylist) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.download_playlist_kind_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = playlistKindIsAlbum,
                        onClick = { playlistKindIsAlbum = true },
                        enabled = !isDownloading
                    )
                    Text(
                        stringResource(R.string.download_kind_album),
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = !isDownloading) { playlistKindIsAlbum = true }
                    )
                    RadioButton(
                        selected = !playlistKindIsAlbum,
                        onClick = { playlistKindIsAlbum = false },
                        enabled = !isDownloading
                    )
                    Text(
                        stringResource(R.string.download_kind_playlist),
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = !isDownloading) { playlistKindIsAlbum = false }
                    )
                }
                OutlinedTextField(
                    value = playlistOrAlbumName,
                    onValueChange = { playlistOrAlbumName = it },
                    label = { Text(stringResource(R.string.download_playlist_name_label)) },
                    placeholder = { Text(stringResource(R.string.download_playlist_name_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isDownloading
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = singleTrackAlbumHint,
                    onValueChange = { singleTrackAlbumHint = it },
                    label = { Text(stringResource(R.string.download_single_album_hint_label)) },
                    placeholder = { Text(stringResource(R.string.download_single_album_hint_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isDownloading
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            val buttonLabel = when {
                isDownloading && effectivePlaylist -> stringResource(R.string.download_playlist_in_progress)
                isDownloading -> stringResource(R.string.download_in_progress)
                effectivePlaylist -> stringResource(R.string.download_audio_playlist)
                else -> stringResource(R.string.download_audio_single)
            }

            val canStart = url.isNotBlank() && !isDownloading && downloadServiceReady &&
                (!effectivePlaylist || playlistOrAlbumName.isNotBlank())

            Button(
                onClick = {
                    viewModel.startDownload(
                        url = url,
                        userWantsPlaylist = playlistMode,
                        playlistDisplayName = playlistOrAlbumName,
                        playlistIsAlbumType = playlistKindIsAlbum,
                        singleTrackAlbumHint = singleTrackAlbumHint
                    )
                },
                enabled = canStart,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(buttonLabel)
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
                        Text(
                            stringResource(R.string.download_completed_one),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(downloadedSong!!.title, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.clearDownloadedSong()
                                url = ""
                            }
                        ) {
                            Text(stringResource(R.string.download_another))
                        }
                    }
                }
            }

            val playlistSongs = downloadedPlaylistSongs
            if (!playlistSongs.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(R.string.download_completed_many),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.download_tracks_added, playlistSongs.size),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        playlistSongs.take(6).forEach { song ->
                            Text(
                                "• ${song.title}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (playlistSongs.size > 6) {
                            Text(
                                "… ${playlistSongs.size - 6} más",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.clearDownloadedSong()
                                url = ""
                            }
                        ) {
                            Text(stringResource(R.string.download_another))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.download_info_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        stringResource(R.string.download_info_body),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
