package com.yumeka.anime.engine.fragments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yumeka.anime.engine.R
import com.yumeka.anime.engine.adapters.ProjectAdapter
import com.yumeka.anime.engine.models.Project
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProjectsFragment : Fragment() {

    companion object {
        private const val TAG            = "ProjectsFragment"
        private const val YUMEKA_FOLDER  = "Yumeka Animations"
    }

    // Views nullable — zero risco de UninitializedPropertyAccessException
    private var recycler:    RecyclerView? = null
    private var emptyState:  View?         = null
    private var countBadge:  TextView?     = null
    private var pathSub:     TextView?     = null
    private var loaded = false

    // Launcher para Android < 11 (dialogo normal READ_EXTERNAL_STORAGE)
    private val legacyPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadProjects()
        else Log.w(TAG, "PERMISSION READ_EXTERNAL_STORAGE negada")
    }

    // Launcher para Android 11+ (abre tela de configuracao do sistema)
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            && Environment.isExternalStorageManager()) {
            loadProjects()
        } else {
            Log.w(TAG, "PERMISSION MANAGE_EXTERNAL_STORAGE nao concedida")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_projects, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recycler    = view.findViewById(R.id.projects_list)
        emptyState  = view.findViewById(R.id.empty_state)
        countBadge  = view.findViewById(R.id.projects_count)
        pathSub     = view.findViewById(R.id.path_subtitle)

        recycler?.layoutManager = GridLayoutManager(requireContext(), 2)

        view.findViewById<View>(R.id.fab_new_project)?.setOnClickListener { /* TODO */ }

        checkPermissionAndLoad()
    }

    override fun onResume() {
        super.onResume()
        if (loaded) loadProjects()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        recycler    = null
        emptyState  = null
        countBadge  = null
        pathSub     = null
    }

    // ----------------------------------------------------------------
    // Permissoes
    // ----------------------------------------------------------------

    private fun checkPermissionAndLoad() {
        when {
            // Android 11+: precisa de MANAGE_EXTERNAL_STORAGE via Intent especial
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                if (Environment.isExternalStorageManager()) {
                    loadProjects()
                } else {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                    ).apply {
                        data = Uri.parse("package:${requireContext().packageName}")
                    }
                    manageStorageLauncher.launch(intent)
                }
            }

            // Android 6-10: dialogo normal de permissao
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                val perm = Manifest.permission.READ_EXTERNAL_STORAGE
                if (ContextCompat.checkSelfPermission(requireContext(), perm)
                    == PackageManager.PERMISSION_GRANTED) {
                    loadProjects()
                } else {
                    legacyPermLauncher.launch(perm)
                }
            }

            // Android 5 e abaixo: sem runtime permission
            else -> loadProjects()
        }
    }

    // ----------------------------------------------------------------
    // Carregar projetos
    // ----------------------------------------------------------------

    private fun loadProjects() {
        if (!isAdded) return
        try {
            val dir = getOrCreateYumekaFolder()
            pathSub?.text = "Documents/$YUMEKA_FOLDER"

            val projects = scanProjects(dir)
            countBadge?.text = projects.size.toString()

            if (projects.isEmpty()) {
                recycler?.visibility   = View.GONE
                emptyState?.visibility = View.VISIBLE
            } else {
                emptyState?.visibility = View.GONE
                recycler?.visibility   = View.VISIBLE
                recycler?.adapter      = ProjectAdapter(projects) { }
            }
            loaded = true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao carregar projetos", e)
        }
    }

    private fun getOrCreateYumekaFolder(): File {
        val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val dir  = File(docs, YUMEKA_FOLDER)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun scanProjects(dir: File): List<Project> {
        if (!dir.exists() || !dir.canRead()) return emptyList()
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale("pt", "BR"))
        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.lastModified() }
            ?.map { folder ->
                Project(
                    id          = folder.absolutePath,
                    name        = folder.name,
                    path        = folder.absolutePath,
                    version     = readVersion(folder),
                    lastEdited  = sdf.format(Date(folder.lastModified())),
                    sizeLabel   = folderSize(folder),
                    iconRes     = 0
                )
            }
            ?: emptyList()
    }

    private fun readVersion(folder: File): String {
        return try {
            val ymk  = File(folder, "project.ymk")
            val json = File(folder, "project.json")
            when {
                ymk.exists() -> ymk.readLines()
                    .firstOrNull { it.startsWith("version") }
                    ?.split("=", ":")?.getOrNull(1)?.trim()?.let { "v$it" } ?: "v1.0"
                json.exists() -> "v1.0"
                else -> "-"
            }
        } catch (e: Exception) { "-" }
    }

    private fun folderSize(folder: File): String {
        return try {
            val bytes = folder.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            when {
                bytes < 1024L        -> "${bytes} B"
                bytes < 1048576L     -> "${bytes / 1024} KB"
                else                 -> "${bytes / 1048576} MB"
            }
        } catch (e: Exception) { "-" }
    }
}
