package com.example.reproductormusica.models

/** Result of a full playlist download (yt-dlp): tracks plus optional source playlist title. */
data class PlaylistDownloadOutcome(
    val songs: List<Song>,
    val playlistTitleHint: String?
)
