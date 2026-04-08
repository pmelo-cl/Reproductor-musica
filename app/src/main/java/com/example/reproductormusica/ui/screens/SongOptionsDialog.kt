package com.example.reproductormusica.ui.screens

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.example.reproductormusica.models.Song
import com.example.reproductormusica.ui.screens.MainScreen

class SongOptionsDialog : DialogFragment() {
    private var _binding: DialogSongOptionsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainScreen by activityViewModels()
    private lateinit var song: Song

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.updateAlbumArt(song.id, it)
            Toast.makeText(requireContext(), "Portada actualizada", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        song = arguments?.getParcelable(ARG_SONG) ?: throw IllegalArgumentException("Song required")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogSongOptionsBinding.inflate(layoutInflater)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(song.title)
            .setView(binding.root)
            .setNegativeButton("Cancelar", null)
            .create()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonEditAlbumArt.setOnClickListener {
            pickImageLauncher.launch(arrayOf("image/*"))
        }

        binding.buttonDeleteSong.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Eliminar canción")
                .setMessage("¿Estás seguro de que quieres eliminar '${song.title}'?")
                .setPositiveButton("Eliminar") { _, _ ->
                    viewModel.deleteSong(song)
                    dismiss()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_SONG = "song"
        fun newInstance(song: Song): SongOptionsDialog {
            return SongOptionsDialog().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_SONG, song)
                }
            }
        }
    }
}