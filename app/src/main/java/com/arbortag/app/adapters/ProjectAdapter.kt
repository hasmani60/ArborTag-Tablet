package com.arbortag.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arbortag.app.data.Project
import com.arbortag.app.databinding.ItemProjectBinding
import java.text.SimpleDateFormat
import java.util.*

class ProjectAdapter(
    private val onProjectClick: (Project) -> Unit
) : ListAdapter<Project, ProjectAdapter.ProjectViewHolder>(ProjectDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
        val binding = ItemProjectBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProjectViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ProjectViewHolder(
        private val binding: ItemProjectBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        fun bind(project: Project) {
            binding.tvProjectName.text = project.name
            binding.tvProjectDate.text = "Created: ${dateFormat.format(Date(project.createdDate))}"

            binding.root.setOnClickListener {
                onProjectClick(project)
            }
        }
    }

    private class ProjectDiffCallback : DiffUtil.ItemCallback<Project>() {
        override fun areItemsTheSame(oldItem: Project, newItem: Project): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Project, newItem: Project): Boolean {
            return oldItem == newItem
        }
    }
}