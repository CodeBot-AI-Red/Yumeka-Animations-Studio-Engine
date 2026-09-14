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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yumeka.anime.engine.R
import com.yumeka.anime.engine.adapters.ProjectAdapter
import com.yumeka.anime.engine.models.Project
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    companion object {
        const val YUMEKA_FOLDER = "Yumeka Animations"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: View
    private lateinit var txtCount: TextView
    private lateinit var txtFolderPath: TextView
    private lateinit var btnRefresh: TextView

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.all { it }) {
            initYumekaFolder()
        } else {
            Toast.makeText(context, "Storage permission needed to read projects", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.projects_recycler_view)
        emptyState = view.findViewById(R.id.empty_state)
        txtCount = view.findViewById(R.id.txt_project_count)
        txtFolderPath = view.findViewById(R.id.txt_folder_path)
        btnRefresh = view.findViewById(R.id.btn_refresh)

        recyclerView.layoutManager = LinearLayoutManager(context)

        btnRefresh.setOnClickListener { checkPermissionsAndLoad() }
        view.findViewById<TextView>(R.id.btn_new_project).setOnClickListener {
            Toast.makeText(context, "New project coming soon", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<TextView>(R.id.btn_import_project).setOnClickListener {
            Toast.makeText(context, "Import coming soon", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<TextView>(R.id.btn_search).setOnClickListener {
            Toast.makeText(context, "Search coming soon", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<TextView>(R.id.btn_settings).setOnClickListener {
            Toast.makeText(context, "Settings coming soon", Toast.LENGTH_SHORT).show()
        }

        checkPermissionsAndLoad()
    }

    private fun checkPermissionsAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                initYumekaFolder()
            } else if (hasReadPermission()) {
                initYumekaFolder()
            } else {
                requestPermission.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
            }
        } else {
            if (hasReadPermission()) {
                initYumekaFolder()
            } else {
                requestPermission.launch(arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ))
            }
        }
    }

    private fun hasReadPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

    private fun initYumekaFolder() {
        val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val yumekaDir = File(docs, YUMEKA_FOLDER)
        if (!yumekaDir.exists()) {
            yumekaDir.mkdirs()
        }
        txtFolderPath.text = yumekaDir.absolutePath
        loadProjectsFrom(yumekaDir)
    }

    private fun loadProjectsFrom(dir: File) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val projects = dir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.lastModified() }
            ?.map { folder ->
                val fileCount = folder.listFiles()?.size ?: 0
                val versionFile = File(folder, "version.txt")
                val version = if (versionFile.exists()) versionFile.readText().trim().take(10)
                              else "$fileCount files"
                Project(
                    id = folder.name,
                    name = folder.name,
                    path = folder.absolutePath,
                    version = version,
                    lastEdited = sdf.format(Date(folder.lastModified())),
                    iconRes = R.mipmap.ic_launcher,
                    isFavorite = false,
                    hasError = !folder.canRead(),
                    errorMessage = if (!folder.canRead()) "Cannot read folder" else null
                )
            } ?: emptyList()

        val count = projects.size
        txtCount.text = if (count == 1) "1 project" else "$count projects"

        if (projects.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            emptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.adapter = ProjectAdapter(projects) { project ->
                Toast.makeText(context, "Opening: ${project.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
