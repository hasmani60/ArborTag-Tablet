package com.arbortag.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.arbortag.app.adapters.ProjectSummaryAdapter
import com.arbortag.app.data.ArborTagDatabase
import com.arbortag.app.data.ProjectSummary
import com.arbortag.app.databinding.ActivityDataCurationBinding
import com.arbortag.app.utils.CsvExporter
import kotlinx.coroutines.launch
import java.io.File

class DataCurationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDataCurationBinding
    private lateinit var database: ArborTagDatabase
    private lateinit var projectSummaryAdapter: ProjectSummaryAdapter
    private var selectedProjectId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDataCurationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = ArborTagDatabase.getInstance(this)

        setupRecyclerView()
        loadProjects()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        projectSummaryAdapter = ProjectSummaryAdapter { projectSummary ->
            selectedProjectId = projectSummary.project.id
            showProjectDetails(projectSummary)
        }

        binding.rvProjects.apply {
            layoutManager = LinearLayoutManager(this@DataCurationActivity)
            adapter = projectSummaryAdapter
        }
    }

    private fun loadProjects() {
        lifecycleScope.launch {
            val projects = database.projectDao().getAllProjects()
            val summaries = mutableListOf<ProjectSummary>()

            for (project in projects) {
                val treeCount = database.treeDao().getTreeCountByProject(project.id)

                if (treeCount > 0) {
                    val avgHeight = database.treeDao().getAverageHeight(project.id) ?: 0.0
                    val avgWidth = database.treeDao().getAverageWidth(project.id) ?: 0.0
                    val totalCarbon = database.treeDao().getTotalCarbon(project.id) ?: 0.0

                    summaries.add(
                        ProjectSummary(
                            project = project,
                            treeCount = treeCount,
                            avgHeight = avgHeight,
                            avgWidth = avgWidth,
                            totalCarbon = totalCarbon
                        )
                    )
                }
            }

            projectSummaryAdapter.submitList(summaries)

            if (summaries.isEmpty()) {
                binding.tvNoData.visibility = View.VISIBLE
                binding.rvProjects.visibility = View.GONE
            } else {
                binding.tvNoData.visibility = View.GONE
                binding.rvProjects.visibility = View.VISIBLE
            }
        }
    }

    private fun showProjectDetails(summary: ProjectSummary) {
        binding.layoutProjectDetails.visibility = View.VISIBLE

        binding.tvProjectName.text = summary.project.name
        binding.tvTreeCount.text = "Trees Tagged: ${summary.treeCount}"
        binding.tvAvgHeight.text = "Avg Height: ${String.format("%.2f", summary.avgHeight)} m"
        binding.tvAvgWidth.text = "Avg Width: ${String.format("%.2f", summary.avgWidth)} m"
        binding.tvTotalCarbon.text = "Total Carbon: ${String.format("%.2f", summary.totalCarbon)} kg CO₂/year"
    }

    private fun setupClickListeners() {
        binding.btnExportCsv.setOnClickListener {
            exportData("csv")
        }

        binding.btnExportExcel.setOnClickListener {
            Toast.makeText(this, "Excel export available in full version", Toast.LENGTH_SHORT).show()
        }

        binding.btnShare.setOnClickListener {
            shareExportedData()
        }
    }

    private fun exportData(format: String) {
        val projectId = selectedProjectId

        if (projectId == null) {
            Toast.makeText(this, "Please select a project first", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val trees = database.treeDao().getTreesByProject(projectId)
                val project = database.projectDao().getProjectById(projectId)

                if (trees.isEmpty()) {
                    Toast.makeText(
                        this@DataCurationActivity,
                        "No data to export",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val exportDir = File(getExternalFilesDir(null), "exports")
                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }

                val fileName = "${project?.name?.replace(" ", "_")}_${System.currentTimeMillis()}.csv"
                val file = File(exportDir, fileName)

                val success = CsvExporter.export(trees, file)

                if (success) {
                    Toast.makeText(
                        this@DataCurationActivity,
                        "Data exported: ${file.name}",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this@DataCurationActivity,
                        "Export failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@DataCurationActivity,
                    "Export error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun shareExportedData() {
        val projectId = selectedProjectId

        if (projectId == null) {
            Toast.makeText(this, "Please select a project first", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val trees = database.treeDao().getTreesByProject(projectId)
                val project = database.projectDao().getProjectById(projectId)

                val exportDir = File(getExternalFilesDir(null), "exports")
                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }

                val fileName = "${project?.name?.replace(" ", "_")}_${System.currentTimeMillis()}.csv"
                val file = File(exportDir, fileName)
                CsvExporter.export(trees, file)

                val uri = FileProvider.getUriForFile(
                    this@DataCurationActivity,
                    "${packageName}.fileprovider",
                    file
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "ArborTag Data - ${project?.name}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                startActivity(Intent.createChooser(shareIntent, "Share data via"))

            } catch (e: Exception) {
                Toast.makeText(
                    this@DataCurationActivity,
                    "Share error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}