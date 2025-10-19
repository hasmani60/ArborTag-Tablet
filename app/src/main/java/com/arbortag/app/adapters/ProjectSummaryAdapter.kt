package com.arbortag.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arbortag.app.data.ProjectSummary
import com.arbortag.app.databinding.ItemProjectSummaryBinding

class ProjectSummaryAdapter(
    private val onProjectClick: (ProjectSummary) -> Unit
) : ListAdapter<ProjectSummary, ProjectSummaryAdapter.SummaryViewHolder>(SummaryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SummaryViewHolder {
        val binding = ItemProjectSummaryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SummaryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SummaryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SummaryViewHolder(
        private val binding: ItemProjectSummaryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(summary: ProjectSummary) {
            binding.tvProjectName.text = summary.project.name
            binding.tvTreeCount.text = "${summary.treeCount} trees"
            binding.tvAvgHeight.text = "Avg: ${String.format("%.2f", summary.avgHeight)} m"
            binding.tvAvgWidth.text = "Width: ${String.format("%.2f", summary.avgWidth)} m"
            binding.tvTotalCarbon.text = "${String.format("%.2f", summary.totalCarbon)} kg CO₂"

            binding.root.setOnClickListener {
                onProjectClick(summary)
            }
        }
    }

    private class SummaryDiffCallback : DiffUtil.ItemCallback<ProjectSummary>() {
        override fun areItemsTheSame(oldItem: ProjectSummary, newItem: ProjectSummary): Boolean {
            return oldItem.project.id == newItem.project.id
        }

        override fun areContentsTheSame(oldItem: ProjectSummary, newItem: ProjectSummary): Boolean {
            return oldItem == newItem
        }
    }
}