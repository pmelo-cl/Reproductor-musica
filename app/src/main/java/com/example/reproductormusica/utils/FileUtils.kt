package com.example.reproductormusica.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

object FileUtils {

    /**
     * Obtiene el nombre de archivo visible (sin extensión) desde un URI del SAF.
     * Útil como título de respaldo si los metadatos ID3 no contienen título.
     */
    fun getDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    cursor.getString(nameIndex)
                        ?.substringBeforeLast(".")  // Eliminar extensión
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Obtiene el tamaño en bytes de un archivo apuntado por un URI del SAF.
     */
    fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex >= 0) {
                    cursor.getLong(sizeIndex)
                } else 0L
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Formatea milisegundos a cadena "m:ss".
     */
    fun formatDuration(millis: Long): String {
        val totalSeconds = (millis / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
}