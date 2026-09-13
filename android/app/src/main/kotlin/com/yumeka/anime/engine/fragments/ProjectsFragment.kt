package com.yumeka.anime.engine.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
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
        private const val YUMEKA_FOLDER = "Yumeka Animations Studio Engine"
    }

    private lateinit var recycler: RecyclerView
    private lateinit var emptyState: View
    private lateinit var countBadge: TextView
    private lateinit var fab: FloatingActionButton
    private lateinit var pathSubtitle: TextView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadProjects()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_projects, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recycler      = view.findViewById(R.id.projects_list)
        emptyState    = view.findViewById(R.id.empty_state)
        countBadge    = view.findViewById(R.id.projects_count)
        fab           = view.findViewById(R.id.fab_new_project)
        pathSubtitle  = view.findViewById(R.id.path_subtitle)

        recycler.layoutManager = GridLayoutManager(requireContext(), 2)

        fab.setOnClickListener {
            // TODO: criar novo projeto
        }

        checkStoragePermissionAndLoad()
    }

    override fun onResume() {
        super.onResume()
        loadProjects()
    }

    private fun checkStoragePermissionAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            loadProjects()
        } else {
            val perm = Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(requireContext(), perm) == PackageManager.PERMISSION_GRANTED) {
                loadProjects()
            } else {
                permissionLauncher.launch(perm)
            }
        }
    }

    private fun loadProjects() {
        val yumekaDirFile = getOrCreateYumekaFolder()
        pathSubtitle.text = "Documents/$YUMEKA_FOLDER"

        val projects = scanProjects(yumekaDirFile)
        countBadge.text = projects.size.toString()

        if (projects.isEmpty()) {
            recycler.visibility   = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            recycler.visibility   = View.VISIBLE
            emptyState.visibility = View.GONE
            recycler.adapter = ProjectAdapter(projects) { project ->
                // TODO: abrir o editor
            }
        }
    }

    private fun getOrCreateYumekaFolder(): File {
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val yumekaDirFile = File(docsDir, YUMEKA_FOLDER)
        if (!yumekaDirFile.exists()) {
            yumekaDirFile.mkdirs()
        }
        return yumekaDirFile
    }

    private fun scanProjects(dir: File): List<Project> {
        if (!dir.exists() || !dir.canRead()) return emptyList()
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale("pt", "BR"))
        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.lastModified() }
            ?.map { folder ->
                val lastMod = sdf.format(Date(folder.lastModified()))
                Project(
                    id         = folder.absolutePath,
                    name       = folder.name,
                    path       = folder.absolutePath,
                    version    = readVersion(folder),
                    lastEdited = "Editado: $lastMod",
                    sizeLabel  = folderSizeKb(folder),
                    iconRes    = 0
                )
            }
            ?: emptyList()
    }

    private fun readVersion(folder: File): String {
        val ymk  = File(folder, "project.ymk")
        val json = File(folder, "project.json")
        return when {
            ymk.exists() -> ymk.readLines().firstOrNull { it.startsWith("version") }
                ?.split("=", ":")
                ?.getOrNull(1)?.trim()?.let { "v$it" } ?: "v1.0"
            json.exists() -> "v1.0"
            else -> "-"
        }
    }

    private fun folderSizeKb(folder: File): String {
        val bytes = folder.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return when {
            bytes < 1024          -> "${bytes} B"
            bytes < 1024 * 1024   -> "${bytes / 1024} KB"
            else                  -> "${bytes / (1024 * 1024)} MB"
        }
    }
}
