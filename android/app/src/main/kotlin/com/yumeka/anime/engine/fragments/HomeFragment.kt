package com.yumeka.anime.engine.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yumeka.anime.engine.R
import com.yumeka.anime.engine.adapters.ProjectAdapter
import com.yumeka.anime.engine.models.Project

class HomeFragment : Fragment() {

    private lateinit var projectsRecyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var projectAdapter: ProjectAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        projectsRecyclerView = view.findViewById(R.id.projects_recycler_view)
        emptyState = view.findViewById(R.id.empty_state)

        val btnNewProject = view.findViewById<Button>(R.id.btn_new_project)
        val btnImportProject = view.findViewById<Button>(R.id.btn_import_project)
        val btnSearch = view.findViewById<Button>(R.id.btn_search)
        val btnSettings = view.findViewById<Button>(R.id.btn_settings)

        loadProjects()

        btnNewProject.setOnClickListener {
            Toast.makeText(context, "Criar novo projeto", Toast.LENGTH_SHORT).show()
        }
        btnImportProject.setOnClickListener {
            Toast.makeText(context, "Importar projeto", Toast.LENGTH_SHORT).show()
        }
        btnSearch.setOnClickListener {
            Toast.makeText(context, "Pesquisar projetos", Toast.LENGTH_SHORT).show()
        }
        btnSettings.setOnClickListener {
            Toast.makeText(context, "Abrir configuracoes", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadProjects() {
        val projects = listOf(
            Project(id="1", name="Plugins de fora", path="/storage/emulated/0/Documents/plugins-de-fora", version="4.7", lastEdited="2026-08-11 16:13:56", iconRes=R.mipmap.ic_launcher, isFavorite=false),
            Project(id="2", name="Criar plugins", path="/storage/emulated/0/Documents/criar-plugins", version="4.7", lastEdited="2026-08-10 21:23:37", iconRes=R.mipmap.ic_launcher, isFavorite=false),
            Project(id="3", name="Zone Hero", path="/storage/emulated/0/Documents/zone-hero", version="4.7", lastEdited="2026-08-10 00:16:50", iconRes=R.mipmap.ic_launcher, isFavorite=true),
            Project(id="4", name="Trailer", path="/storage/emulated/0/Documents/trailer", version="4.6", lastEdited="2026-05-20 22:29:31", iconRes=R.mipmap.ic_launcher, isFavorite=false),
            Project(id="5", name="Github Flash", path="/storage/emulated/0/Documents/github-flash", version="4.6", lastEdited="2026-05-18 20:31:46", iconRes=R.mipmap.ic_launcher, isFavorite=false),
            Project(id="6", name="Projeto Inexistente", path="/storage/emulated/0/Documents/Otupos Max", version="4.5", lastEdited="2026-05-15 10:00:00", iconRes=R.mipmap.ic_launcher, hasError=true, errorMessage="Dados perdida")
        )

        if (projects.isEmpty()) {
            projectsRecyclerView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            projectsRecyclerView.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
            projectAdapter = ProjectAdapter(projects) { project ->
                Toast.makeText(context, "Abrindo: ${project.name}", Toast.LENGTH_SHORT).show()
            }
            projectsRecyclerView.layoutManager = LinearLayoutManager(context)
            projectsRecyclerView.adapter = projectAdapter
        }
    }
}
