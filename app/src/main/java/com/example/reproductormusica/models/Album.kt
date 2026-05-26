package com.example.reproductormusica.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "albums")
data class Album(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val artist: String,
    val coverUriString: String? = null,
    val year: Int? = null
)