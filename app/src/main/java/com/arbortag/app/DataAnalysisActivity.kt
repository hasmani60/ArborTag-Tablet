package com.arbortag.app

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.arbortag.app.data.ArborTagDatabase
import com.arbortag.app.databinding.ActivityDataAnalysisBinding
import com.arbortag.app.utils.AnalysisApiClient
import kotlinx.coroutines.launch
import java.io.File

class DataAnalysisActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDataAnalysisBinding
    private lateinit var database: ArborTagDatabase
    private var selectedProjectId: Long? = null
    private val apiClient = AnalysisApiClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDataAnalysisBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = ArborTagDatabase.getInstance(this)

        loadProjects()
        setupClickListeners()
    }

    private fun loadProjects() {
        lifecycleScope.launch {
            val projects = database.projectDao().getAllProjects()

            if (projects.isEmpty()) {
                binding.tvNoProjects.visibility = View.VISIBLE
                binding.layoutAnalysisOptions.visibility = View.GONE
                return@launch
            }

            // Create project names list
            val projectNames = projects.map { it.name }
            val adapter = ArrayAdapter(
                this@DataAnalysisActivity,
                android.R.layout.simple_spinner_item,
                projectNames
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerProject.adapter = adapter

            binding.spinnerProject.setOnItemSelectedListener(
                object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: android.widget.AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        selectedProjectId = projects[position].id
                        loadProjectStats()
                    }

                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                        selectedProjectId = null
                    }
                }
            )
        }
    }

    private fun loadProjectStats() {
        val projectId = selectedProjectId ?: return

        lifecycleScope.launch {
            try {
                val treeCount = database.treeDao().getTreeCountByProject(projectId)
                val avgHeight = database.treeDao().getAverageHeight(projectId) ?: 0.0
                val avgWidth = database.treeDao().getAverageWidth(projectId) ?: 0.0
                val totalCarbon = database.treeDao().getTotalCarbon(projectId) ?: 0.0

                binding.tvStatsContent.text = """
                    Trees Tagged: $treeCount
                    Average Height: ${String.format("%.2f", avgHeight)} m
                    Average Width: ${String.format("%.2f", avgWidth)} m
                    Total Carbon: ${String.format("%.2f", totalCarbon)} kg CO₂/year
                """.trimIndent()

                binding.layoutStats.visibility = View.VISIBLE
            } catch (e: Exception) {
                Toast.makeText(
                    this@DataAnalysisActivity,
                    "Error loading stats: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnDistribution.setOnClickListener {
            generateAnalysis("distribution")
        }

        binding.btnHeight.setOnClickListener {
            generateAnalysis("height")
        }

        binding.btnWidth.setOnClickListener {
            generateAnalysis("width")
        }

        binding.btnSummary.setOnClickListener {
            generateSummary()
        }
    }

    private fun generateAnalysis(type: String) {
        val projectId = selectedProjectId
        if (projectId == null) {
            Toast.makeText(this, "Please select a project", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.ivAnalysisResult.visibility = View.GONE
        binding.tvAnalysisResult.visibility = View.GONE

        lifecycleScope.launch {
            try {
                // Export CSV for analysis
                val trees = database.treeDao().getTreesByProject(projectId)
                if (trees.isEmpty()) {
                    Toast.makeText(
                        this@DataAnalysisActivity,
                        "No data to analyze",
                        Toast.LENGTH_SHORT
                    ).show()
                    binding.progressBar.visibility = View.GONE
                    return@launch
                }

                val csvFile = createTempCsvFile(trees)

                // Call backend API
                val imageBytes = when (type) {
                    "distribution" -> apiClient.getDistributionAnalysis(csvFile)
                    "height" -> apiClient.getHeightAnalysis(csvFile)
                    "width" -> apiClient.getWidthAnalysis(csvFile)
                    else -> null
                }

                if (imageBytes != null) {
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    binding.ivAnalysisResult.setImageBitmap(bitmap)
                    binding.ivAnalysisResult.visibility = View.VISIBLE
                    binding.tvAnalysisResult.visibility = View.GONE
                } else {
                    throw Exception("Failed to generate analysis")
                }

                binding.progressBar.visibility = View.GONE

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(
                    this@DataAnalysisActivity,
                    "Analysis error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun generateSummary() {
        val projectId = selectedProjectId
        if (projectId == null) {
            Toast.makeText(this, "Please select a project", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.ivAnalysisResult.visibility = View.GONE
        binding.tvAnalysisResult.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val trees = database.treeDao().getTreesByProject(projectId)
                if (trees.isEmpty()) {
                    Toast.makeText(
                        this@DataAnalysisActivity,
                        "No data to analyze",
                        Toast.LENGTH_SHORT
                    ).show()
                    binding.progressBar.visibility = View.GONE
                    return@launch
                }

                val csvFile = createTempCsvFile(trees)
                val summary = apiClient.getSummaryAnalysis(csvFile)

                binding.tvAnalysisResult.text = summary
                binding.tvAnalysisResult.visibility = View.VISIBLE
                binding.ivAnalysisResult.visibility = View.GONE
                binding.progressBar.visibility = View.GONE

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(
                    this@DataAnalysisActivity,
                    "Summary error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private suspend fun createTempCsvFile(trees: List<com.arbortag.app.data.Tree>): File {
        val tempFile = File(cacheDir, "temp_analysis_${System.currentTimeMillis()}.csv")
        com.arbortag.app.utils.CsvExporter.export(trees, tempFile)
        return tempFile
    }
}