package com.yumeka.anime.engine.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yumeka.anime.engine.R
import com.yumeka.anime.engine.models.Project

class ProjectAdapter(
    private val projects: List<Project>,
    private val onProjectClick: (Project) -> Unit
) : RecyclerView.Adapter<ProjectAdapter.ProjectViewHolder>() {

    inner class ProjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: TextView     = itemView.findViewById(R.id.project_icon)
        private val nameView: TextView     = itemView.findViewById(R.id.project_name)
        private val dateView: TextView     = itemView.findViewById(R.id.project_date)
        private val versionView: TextView  = itemView.findViewById(R.id.project_version)
        private val pathView: TextView     = itemView.findViewById(R.id.project_path)
        private val favView: TextView      = itemView.findViewById(R.id.project_favorite)
        private val errorMsg: TextView     = itemView.findViewById(R.id.project_error_message)

        fun bind(project: Project) {
            // Emoji dinamico por nome
            iconView.text = when {
                project.name.contains("hero",  ignoreCase = true) -> "\uD83E\uDD88"
                project.name.contains("scene", ignoreCase = true) -> "\uD83C\uDFAA"
                project.name.contains("fight", ignoreCase = true) -> "\uD83E\uDD4A"
                project.name.contains("magic", ignoreCase = true) -> "\u2728"
                project.name.contains("plugin",ignoreCase = true) -> "\uD83E\uDD4F"
                else -> "\uD83C\uDFC8"
  }

            nameView.text   = project.name
            dateView.text   = project.lastEdited
            versionView.text = project.version
            pathView.text   = project.path

            favView.visibility = if (project.isFavorite) View.VISIBLE else View.GONE

            if (project.hasError) {
                errorMsg.visibility = View.VISIBLE
                errorMsg.text       = project.errorMessage ?: "Erro desconhecido"
            } else {
                errorMsg.visibility = View.GONE
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
