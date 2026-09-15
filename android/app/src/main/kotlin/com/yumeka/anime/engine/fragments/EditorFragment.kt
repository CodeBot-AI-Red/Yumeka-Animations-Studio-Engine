package com.yumeka.anime.engine.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.aniforge.engine.components.FloatingHub
import com.yumeka.anime.engine.R

class EditorFragment : Fragment() {

    private var projectName: String = "Projeto sem titulo"

    companion object {
        private const val ARG_PROJECT_NAME = "project_name"

        fun newInstance(projectName: String): EditorFragment {
            return EditorFragment().apply {
                arguments = Bundle().also { it.putString(ARG_PROJECT_NAME, projectName) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectName = arguments?.getString(ARG_PROJECT_NAME) ?: "Projeto sem titulo"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_editor, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.txt_project_name).text = projectName

        view.findViewById<TextView>(R.id.btn_editor_back).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val hub = view.findViewById<FloatingHub>(R.id.floating_hub)

        hub.onLayersClick = {
            Toast.makeText(context, "Layers - em breve", Toast.LENGTH_SHORT).show()
        }
        hub.onTimelineClick = {
            Toast.makeText(context, "Timeline - em breve", Toast.LENGTH_SHORT).show()
        }
        hub.onUndoClick = {
            Toast.makeText(context, "Undo", Toast.LENGTH_SHORT).show()
        }
        hub.onRedoClick = {
            Toast.makeText(context, "Redo", Toast.LENGTH_SHORT).show()
        }
        hub.onPlayClick = {
            Toast.makeText(context, "Reproduzindo preview...", Toast.LENGTH_SHORT).show()
        }
        hub.onExportClick = {
            Toast.makeText(context, "Export - em breve", Toast.LENGTH_SHORT).show()
        }
    }
}
