package com.example.reproductormusica.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.reproductormusica.models.Song

@Composable
fun SongOptionsDialog(
    song: Song,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onUpdateAlbumArt: (Uri) -> Unit
) {
    val context = LocalContext.current

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // Persistir permiso de lectura
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            onUpdateAlbumArt(it)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = song.title, maxLines = 1) },
        text = {
            Text("¿Qué deseas hacer con esta canción?")
        },
        confirmButton = {
            TextButton(onClick = { pickImageLauncher.launch(arrayOf("image/*")) }) {
                Text("Cambiar portada")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Eliminar")
            }
        }
    )
}