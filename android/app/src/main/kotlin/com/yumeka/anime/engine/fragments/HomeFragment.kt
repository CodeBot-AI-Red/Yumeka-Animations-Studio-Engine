package com.yumeka.anime.engine.fragments

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
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

    // Android 10- - request popup permission
    private val requestLegacyPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.any { it }) initYumekaFolder()
        else showPermissionDeniedDialog()
    }

    // Android 11+ - volta da tela de Settings
    private val requestManagerPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            initYumekaFolder()
        } else {
            showPermissionDeniedDialog()
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

        recyclerView.layoutManager = LinearLayoutManager(context)

        view.findViewById<TextView>(R.id.btn_refresh).setOnClickListener { checkPermissionsAndLoad() }
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

    override fun onResume() {
        super.onResume()
        // Rele ao voltar da tela de Settings
        if (hasStoragePermission()) initYumekaFolder()
    }

    private fun checkPermissionsAndLoad() {
        when {
            hasStoragePermission() -> initYumekaFolder()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> requestManagerAccess()
            else -> requestLegacyPermission.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Android 11+: abre a tela de Settings para MANAGE_ALL_FILES
    private fun requestManagerAccess() {
        AlertDialog.Builder(requireContext())
            .setTitle("Storage Permission Required")
            .setMessage(
                "Yumeka needs access to 'All Files' to read your projects from:\n\n" +
                "Documents/Yumeka Animations\n\n" +
                "Tap 'Allow' then enable 'Allow access to manage all files'."
            )
            .setPositiveButton("Allow") { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${requireContext().packageName}")
                requestManagerPermission.launch(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Permission Denied")
            .setMessage("Storage access is required to read projects. Please enable it in App Settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${requireContext().packageName}")
                })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun initYumekaFolder() {
        val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val yumekaDir = File(docs, YUMEKA_FOLDER)
        if (!yumekaDir.exists()) yumekaDir.mkdirs()
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
