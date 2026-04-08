package com.example.reproductormusica.ui.components

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.reproductormusica.models.Song

class MiniPlayerBar : Fragment() {
    private var _binding: FragmentMiniPlayerBinding? = null
    private val binding get() = _binding!!

    var onClickListener: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMiniPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.root.setOnClickListener {
            onClickListener?.invoke()
        }

        binding.btnMiniPlayPause.setOnClickListener {
            // Conectar con servicio
        }

        binding.btnMiniNext.setOnClickListener {
            // Conectar con servicio
        }
    }

    fun updateSongInfo(song: Song) {
        binding.textViewMiniTitle.text = song.title
        binding.textViewMiniArtist.text = song.artist ?: ""

        Glide.with(requireContext())
            .load(song.albumArtUriParsed ?: R.drawable.ic_music_note_placeholder)
            .placeholder(R.drawable.ic_music_note_placeholder)
            .into(binding.imageViewMiniAlbumArt)
    }

    fun setOnClickListener(listener: () -> Unit) {
        onClickListener = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}