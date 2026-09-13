package com.yumeka.anime.engine.fragments

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.yumeka.anime.engine.R
import com.yumeka.anime.engine.adapters.ProjectAdapter
import com.yumeka.anime.engine.models.Project
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProjectsFragment : Fragment() {

    companion object {
        private const val YUMEKA_FOLDER = "Yumeka Animations"
    }

    private lateinit var recycler: RecyclerView
    private lateinit var emptyState: View
    private lateinit var countBadge: TextView
    private lateinit var fab: FloatingActionButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_projects, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recycler = view.findViewById(R.id.projects_list)
        emptyState = view.findViewById(R.id.empty_state)
        countBadge = view.findViewById(R.id.projects_count)
        fab = view.findViewById(R.id.fab_new_project)

        recycler.layoutManager = LinearLayoutManager(requireContext())

        // Garante que a pasta existe
        val yumekaDir = getOrCreateYumekaFolder()

        // Carrega os projetos
        val projects = scanProjects(yumekaDir)

        // Atualiza a interface
        countBadge.text = projects.size.toString()

        if (projects.isEmpty()) {
            recycler.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            recycler.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
            recycler.adapter = ProjectAdapter(projects) { project ->
                // TODO: abrir o editor
            }
        }

        fab.setOnClickListener {
            // TODO: criar novo projeto
        }
    }

    /**
     * Retorna (e cria se necessário) a pasta:
     * Documents/Yumeka Animations
     */
    private fun getOrCreateYumekaFolder(): File {
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val yumekaDir = File(docsDir, YUMEKA_FOLDER)
        if (!yumekaDir.exists()) {
            yumekaDir.mkdirs()
        }
        return yumekaDir
    }

    /**
     * Varre a pasta e retorna uma lista de Projects
     * Cada subpasta direta é tratada como um projeto
     */
    private fun scanProjects(dir: File): List<Project> {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale("pt", "BR"))

        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.lastModified() }
            ?.map { folder ->
                val lastMod = sdf.format(Date(folder.lastModified()))
                val version = readVersion(folder)
                Project(
                    id = folder.absolutePath,
                    name = folder.name,
                    path = folder.absolutePath,
                    version = version,
                    lastEdited = "Editado: $lastMod",
                    iconRes = 0
                )
            }
            ?: emptyList()
    }

    /**
     * Tenta ler um arquivo project.ymk ou project.json dentro da pasta
     * para extrair a versão (optional)
     */
    private fun readVersion(folder: File): String {
        val ymk = File(folder, "project.ymk")
        val json = File(folder, "project.json")
        return when {
            ymk.exists() -> {
                ymk.readLines().firstOrNull { it.startsWith("version") }
                    ?.split("=", ":")
                    ?.getOrNull(1)?.trim()?.let { "v$it" } ?: "v1.0"
            }
            json.exists() -> "v1.0"
            else -> "-"
        }
    }
}
