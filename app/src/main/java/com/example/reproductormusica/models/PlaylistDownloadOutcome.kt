package com.example.reproductormusica.models

/** Result of a full playlist download (yt-dlp): tracks plus naming hints for the new playlist. */
data class PlaylistDownloadOutcome(
    val songs: List<Song>,
    val playlistTitleHint: String?,
    val userPlaylistName: String? = null
)
