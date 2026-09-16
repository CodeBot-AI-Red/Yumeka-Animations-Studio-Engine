package com.yumeka.anime.engine.fragments

import android.Manifest
import android.app.Dialog
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
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yumeka.anime.engine.MainActivity
import com.yumeka.anime.engine.R
import com.yumeka.anime.engine.adapters.ProjectAdapter
import com.yumeka.anime.engine.models.Project
import com.yumeka.anime.engine.services.ProjectCreator
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
    private var yumekaDir: File? = null
    private var permDialog: Dialog? = null
    private var newProjectDialog: Dialog? = null

    private val requestLegacyPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.any { it }) initYumekaFolder() else showDeniedToast()
    }

    private val requestManagerPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager())
            initYumekaFolder() else showDeniedToast()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView   = view.findViewById(R.id.projects_recycler_view)
        emptyState     = view.findViewById(R.id.empty_state)
        txtCount       = view.findViewById(R.id.txt_project_count)
        txtFolderPath  = view.findViewById(R.id.txt_folder_path)

        recyclerView.layoutManager = LinearLayoutManager(context)

        view.findViewById<TextView>(R.id.btn_refresh).setOnClickListener { checkPermissions() }

        view.findViewById<TextView>(R.id.btn_new_project).setOnClickListener {
            if (hasStoragePermission()) showNewProjectDialog()
            else Toast.makeText(context, "Permita o acesso ao armazenamento primeiro.", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<TextView>(R.id.btn_import_project).setOnClickListener {
            Toast.makeText(context, "Import - em breve", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<TextView>(R.id.btn_search).setOnClickListener {
            Toast.makeText(context, "Busca - em breve", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<TextView>(R.id.btn_settings).setOnClickListener {
            Toast.makeText(context, "Configuracoes - em breve", Toast.LENGTH_SHORT).show()
        }

        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        if (hasStoragePermission()) initYumekaFolder()
    }

    /**
     * Aplica largura de 92% da tela e deixa a altura livre (WRAP_CONTENT).
     * Os layouts dos Hubs foram redesenhados compactos para caber em 16:9 landscape
     * sem precisar de scroll nem de altura fixa — igual ao comportamento do Godot.
     */
    private fun applyDialogSize(dialog: Dialog) {
        val screenW = requireContext().resources.displayMetrics.widthPixels
        dialog.window?.setLayout(
            (screenW * 0.92).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showNewProjectDialog() {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar)
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_new_project, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        applyDialogSize(dialog)

        val edtName = view.findViewById<EditText>(R.id.edt_anime_name)
        val txtError = view.findViewById<TextView>(R.id.txt_error_name)

        view.findViewById<TextView>(R.id.btn_criar).setOnClickListener {
            val name = edtName.text.toString().trim()
            if (name.isEmpty()) {
                txtError.text = "Digite o nome do anime."
                txtError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            val base = yumekaDir ?: return@setOnClickListener
            val result = ProjectCreator.createProject(base, name)
            when (result.status) {
                ProjectCreator.Result.SUCCESS -> {
                    hideKeyboard(edtName)
                    dialog.dismiss()
                    loadProjectsFrom(base)
                    (activity as? MainActivity)?.openEditor(name)
                }
                ProjectCreator.Result.ALREADY_EXISTS -> {
                    txtError.text = result.message
                    txtError.visibility = View.VISIBLE
                }
                ProjectCreator.Result.ERROR -> {
                    txtError.text = result.message
                    txtError.visibility = View.VISIBLE
                }
            }
        }
        view.findViewById<TextView>(R.id.btn_cancelar).setOnClickListener {
            hideKeyboard(edtName)
            dialog.dismiss()
        }
        newProjectDialog = dialog
        dialog.show()
        edtName.requestFocus()
    }

    private fun hideKeyboard(view: View) {
        val imm = requireContext().getSystemService(InputMethodManager::class.java)
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun checkPermissions() {
        when {
            hasStoragePermission() -> initYumekaFolder()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> showPermissionDialog(true)
            else -> showPermissionDialog(false)
        }
    }

    private fun showPermissionDialog(isManager: Boolean) {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar)
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_permission, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        applyDialogSize(dialog)

        view.findViewById<TextView>(R.id.btn_allow).setOnClickListener {
            dialog.dismiss()
            if (isManager) {
                val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                i.data = Uri.parse("package:${requireContext().packageName}")
                requestManagerPermission.launch(i)
            } else {
                requestLegacyPermission.launch(arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ))
            }
        }
        view.findViewById<TextView>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
        permDialog = dialog
        dialog.show()
    }

    private fun showDeniedToast() {
        Toast.makeText(context, "Permissao necessaria para acessar os projetos.", Toast.LENGTH_LONG).show()
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun initYumekaFolder() {
        val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val dir = File(docs, YUMEKA_FOLDER)
        if (!dir.exists()) dir.mkdirs()
        yumekaDir = dir
        txtFolderPath.text = dir.absolutePath
        loadProjectsFrom(dir)
    }

    fun loadProjectsFrom(dir: File) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val projects = dir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.lastModified() }
            ?.map { folder ->
                val isYase = File(folder, "yase.project").exists()
                val fileCount = folder.listFiles()?.size ?: 0
                val version = if (isYase) "YASE" else "$fileCount files"
                Project(
                    id = folder.name, name = folder.name,
                    path = folder.absolutePath, version = version,
                    lastEdited = sdf.format(Date(folder.lastModified())),
                    iconRes = R.mipmap.ic_launcher,
                    hasError = !folder.canRead(),
                    errorMessage = if (!folder.canRead()) "Nao e possivel ler" else null
                )
            } ?: emptyList()

        txtCount.text = if (projects.size == 1) "1 projeto" else "${projects.size} projetos"

        if (projects.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            emptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.adapter = ProjectAdapter(projects) { project ->
                (activity as? MainActivity)?.openEditor(project.name)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        permDialog?.dismiss()
        permDialog = null
        newProjectDialog?.dismiss()
        newProjectDialog = null
    }
}
