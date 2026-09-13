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
        private val iconEmoji: TextView   = itemView.findViewById(R.id.project_icon_emoji)
        private val nameView: TextView    = itemView.findViewById(R.id.project_name)
        private val dateView: TextView    = itemView.findViewById(R.id.project_date)
        private val versionView: TextView = itemView.findViewById(R.id.project_version)
        private val sizeView: TextView    = itemView.findViewById(R.id.project_size)
        private val errorMsg: TextView    = itemView.findViewById(R.id.project_error_message)

        fun bind(project: Project) {
            // Emoji baseado no nome para diferenciar visualmente
            iconEmoji.text = when {
                project.name.contains("fight", ignoreCase = true) -> "\uD83E\uDD4A"
                project.name.contains("magic", ignoreCase = true) -> "\u2728"
                project.name.contains("hero",  ignoreCase = true) -> "\uD83E\uDDB8"
                project.name.contains("scene", ignoreCase = true) -> "\uD83C\uDFAC"
                project.name.contains("char",  ignoreCase = true) -> "\uD83D\uDC64"
                else -> "\uD83C\uDFA8"
            }

            nameView.text    = project.name
            dateView.text    = project.lastEdited
            versionView.text = project.version
            sizeView.text    = project.sizeLabel

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
