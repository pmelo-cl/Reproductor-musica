package com.example.reproductormusica.models

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.core.net.toUri

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val artist: String = "Artista desconocido",
    val album: String? = null,
    val duration: Long = 0,
    val uriString: String,                   // URI del archivo de audio (antes "dataUri")
    val albumArtUriString: String? = null,   // URI de la portada personalizada
    val dateAdded: Long = System.currentTimeMillis()
) {
    val uri: Uri
        get() = uriString.toUri()

    val albumArtUri: Uri?
        get() = albumArtUriString?.toUri()
}