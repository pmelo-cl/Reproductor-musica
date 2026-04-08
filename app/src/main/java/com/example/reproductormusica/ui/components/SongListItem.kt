package com.example.reproductormusica.ui.components

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.example.reproductormusica.models.Song

class SongListItem(
    private val onItemClick: (Song) -> Unit,
    private val onOptionsClick: (Song, View) -> Unit
) : ListAdapter<Song, SongListItem.SongViewHolder>(SongDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view, onItemClick, onOptionsClick)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class SongViewHolder(
        itemView: View,
        private val onItemClick: (Song) -> Unit,
        private val onOptionsClick: (Song, View) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val imageViewAlbumArt: ImageView = itemView.findViewById(R.id.imageViewAlbumArt)
        private val textViewTitle: TextView = itemView.findViewById(R.id.textViewTitle)
        private val textViewArtist: TextView = itemView.findViewById(R.id.textViewArtist)
        private val buttonOptions: ImageView = itemView.findViewById(R.id.buttonOptions)

        fun bind(song: Song) {
            textViewTitle.text = song.title
            textViewArtist.text = song.artist ?: "Artista desconocido"

            // Cargar portada con Glide
            Glide.with(itemView.context)
                .load(song.albumArtUriParsed ?: R.drawable.ic_music_note_placeholder)
                .placeholder(R.drawable.ic_music_note_placeholder)
                .into(imageViewAlbumArt)

            itemView.setOnClickListener { onItemClick(song) }
            buttonOptions.setOnClickListener { onOptionsClick(song, it) }
        }
    }

    class SongDiffCallback : DiffUtil.ItemCallback<Song>() {
        override fun areItemsTheSame(oldItem: Song, newItem: Song): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Song, newItem: Song): Boolean {
            return oldItem == newItem
        }
    }
}