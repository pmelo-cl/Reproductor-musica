package com.example.reproductormusica.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "artists")
data class Artist(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val coverUriString: String? = null
)