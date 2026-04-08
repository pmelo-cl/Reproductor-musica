package com.example.reproductormusica.ui.screens

import android.R
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.lifecycle.lifecycleScope
import com.example.reproductormusica.models.Song
import com.example.reproductormusica.ui.screens.MainScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlayerScreen : Fragment() {
    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private lateinit var musicServiceConnection: MusicServiceConnection
    private val viewModel: MainScreen by activityViewModels()
    private var currentSong: Song? = null
    private var isTracking = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        musicServiceConnection = MusicServiceConnection(requireContext())
        musicServiceConnection.bindService()

        setupListeners()
        observeService()
        startProgressUpdater()
    }

    private fun setupListeners() {
        binding.btnPlayPause.setOnClickListener {
            musicServiceConnection.getService()?.playPause()
        }

        binding.btnNext.setOnClickListener {
            musicServiceConnection.getService()?.playNext()
        }

        binding.btnPrevious.setOnClickListener {
            musicServiceConnection.getService()?.playPrevious()
        }

        binding.btnShuffle.setOnClickListener {
            // Implementar modo aleatorio
        }

        binding.btnRepeat.setOnClickListener {
            // Implementar modo repetición
        }

        binding.btnPlayerOptions.setOnClickListener {
            currentSong?.let { song ->
                SongOptionsDialog.newInstance(song).show(parentFragmentManager, "SongOptions")
            }
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.textViewCurrentTime.text = formatTime(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isTracking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isTracking = false
                seekBar?.progress?.let { progress ->
                    musicServiceConnection.getService()?.seekTo(progress.toLong())
                }
            }
        })
    }

    private fun observeService() {
        lifecycleScope.launch {
            musicServiceConnection.currentSong.collect { song ->
                song?.let {
                    currentSong = it
                    updateSongInfo(it)
                }
            }
        }

        lifecycleScope.launch {
            musicServiceConnection.isPlaying.collect { isPlaying ->
                binding.btnPlayPause.setImageResource(
                    if (isPlaying) R.drawable.ic_media_pause
                    else R.drawable.ic_media_play
                )
            }
        }
    }

    fun updateSongInfo(song: Song) {
        currentSong = song
        binding.textViewTitleLarge.text = song.title
        binding.textViewArtistLarge.text = song.artist ?: "Artista desconocido"

        Glide.with(requireContext())
            .load(song.albumArtUriParsed ?: R.drawable.ic_music_note_placeholder)
            .placeholder(R.drawable.ic_music_note_placeholder)
            .into(binding.imageViewAlbumArtLarge)

        // Configurar duración
        binding.seekBar.max = song.duration.toInt()
        binding.textViewDuration.text = formatTime(song.duration)
    }

    private fun startProgressUpdater() {
        lifecycleScope.launch {
            while (true) {
                val service = musicServiceConnection.getService()
                if (service != null && !isTracking) {
                    val pos = service.getCurrentPosition()
                    binding.seekBar.progress = pos.toInt()
                    binding.textViewCurrentTime.text = formatTime(pos)
                }
                delay(500)
            }
        }
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        musicServiceConnection.unbindService()
    }
}