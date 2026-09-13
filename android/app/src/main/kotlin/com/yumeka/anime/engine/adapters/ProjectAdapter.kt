package com.yumeka.anime.engine.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yumeka.anime.engine.R
import com.yumeka.anime.engine.models.Project

class ProjectAdapter(
    private val projects: List<Project>,
    private val onProjectClick: (Project) -> Unit
) : RecyclerView.Adapter<ProjectAdapter.ProjectViewHolder>() {

    inner class ProjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.project_icon)
        private val nameView: TextView = itemView.findViewById(R.id.project_name)
        private val pathView: TextView = itemView.findViewById(R.id.project_path)
        private val versionView: TextView = itemView.findViewById(R.id.project_version)
        private val dateView: TextView = itemView.findViewById(R.id.project_date)
        private val favoriteView: ImageView = itemView.findViewById(R.id.project_favorite)
        private val errorView: ImageView = itemView.findViewById(R.id.project_error)
        private val errorMessageView: TextView = itemView.findViewById(R.id.project_error_message)

        fun bind(project: Project) {
            iconView.setImageResource(project.iconRes)
            nameView.text = project.name
            pathView.text = project.path
            versionView.text = project.version
            dateView.text = project.lastEdited
            favoriteView.visibility = if (project.isFavorite) View.VISIBLE else View.GONE
            if (project.hasError) {
                errorView.visibility = View.VISIBLE
                errorMessageView.visibility = View.VISIBLE
                errorMessageView.text = project.errorMessage ?: "Erro desconhecido"
            } else {
                errorView.visibility = View.GONE
                errorMessageView.visibility = View.GONE
            }
            itemView.setOnClickListener { onProjectClick(project) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_project, parent, false)
        return ProjectViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
        holder.bind(projects[position])
    }

    override fun getItemCount(): Int = projects.size
}
