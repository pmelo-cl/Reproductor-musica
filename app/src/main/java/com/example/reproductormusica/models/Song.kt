package com.example.reproductormusica.models

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val duration: Long = 0,
    val dataUri: String,               // URI del archivo de audio
    val albumArtUri: String? = null,   // URI de la portada personalizada o del sistema
    val dateAdded: Long = System.currentTimeMillis()
) {
    // Propiedad computada para obtener URI
    val uri: Uri
        get() = Uri.parse(dataUri)

    val albumArtUriParsed: Uri?
        get() = albumArtUri?.let { Uri.parse(it) }
}