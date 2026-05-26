package com.example.reproductormusica.models

import androidx.room.Entity

@Entity(
    tableName = "playlist_song_cross_ref",
    primaryKeys = ["playlistId", "songId"]
)
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: Long,
    val position: Int = 0,          // Campo de orden
    val addedAt: Long = System.currentTimeMillis()
)