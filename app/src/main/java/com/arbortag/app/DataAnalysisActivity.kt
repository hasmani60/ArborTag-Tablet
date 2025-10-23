package com.arbortag.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.arbortag.app.data.ArborTagDatabase
import com.arbortag.app.databinding.ActivityDataAnalysisBinding
import com.arbortag.app.utils.AnalysisApiClient
import com.arbortag.app.utils.ExportManager
import kotlinx.coroutines.launch
import java.io.File

class DataAnalysisActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDataAnalysisBinding
    private lateinit var database: ArborTagDatabase
    private lateinit var exportManager: ExportManager
    private var selectedProjectId: Long? = null
    private val apiClient = AnalysisApiClient()

    // Store chart data for export
    private val chartData = mutableMapOf<String, ByteArray>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDataAnalysisBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = ArborTagDatabase.getInstance(this)
        exportManager = ExportManager(this)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Data Analysis"

        loadProjects()
        setupClickListeners()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // menuInflater.inflate(R.menu.menu_analysis, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            // R.id.action_export_all -> {
            //     exportAllAnalyses()
            //     true
            // }
            // R.id.action_clear_cache -> {
            //     clearAnalysisCache()
            //     true
            // }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadProjects() {
        lifecycleScope.launch {
            val projects = database.projectDao().getAllProjects()

            if (projects.isEmpty()) {
                binding.tvNoProjects.visibility = View.VISIBLE
                binding.layoutAnalysisOptions.visibility = View.GONE
                return@launch
            }

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
                        chartData.clear() // Clear previous chart data
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
                    🌳 Trees Tagged: $treeCount
                    📏 Average Height: ${String.format("%.2f", avgHeight)} m
                    📐 Average Width: ${String.format("%.2f", avgWidth)} m
                    💨 Total Carbon: ${String.format("%.2f", totalCarbon)} kg CO₂/year
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
            generateAnalysis("distribution", "Species Distribution")
        }

        binding.btnHeight.setOnClickListener {
            generateAnalysis("height", "Height Analysis")
        }

        binding.btnWidth.setOnClickListener {
            generateAnalysis("width", "Width Analysis")
        }

        binding.btnHeatmap.setOnClickListener {
            openInteractiveMap(MapViewerActivity.TYPE_HEATMAP)
        }

        binding.btnDiversity.setOnClickListener {
            openInteractiveMap(MapViewerActivity.TYPE_DIVERSITY)
        }

        binding.btnSummary.setOnClickListener {
            generateSummary()
        }

        // Export buttons - comment out if these buttons don't exist in your layout
        // Uncomment when you add these buttons to activity_data_analysis.xml
        /*
        binding.btnExportDistributionPng.setOnClickListener {
            exportChart("distribution", "Species_Distribution", ExportFormat.PNG)
        }

        binding.btnExportDistributionPdf.setOnClickListener {
            exportChart("distribution", "Species_Distribution", ExportFormat.PDF)
        }

        binding.btnExportHeightPng.setOnClickListener {
            exportChart("height", "Height_Analysis", ExportFormat.PNG)
        }

        binding.btnExportHeightPdf.setOnClickListener {
            exportChart("height", "Height_Analysis", ExportFormat.PDF)
        }

        binding.btnExportWidthPng.setOnClickListener {
            exportChart("width", "Width_Analysis", ExportFormat.PNG)
        }

        binding.btnExportWidthPdf.setOnClickListener {
            exportChart("width", "Width_Analysis", ExportFormat.PDF)
        }
        */
    }

    private fun openInteractiveMap(mapType: String) {
        val projectId = selectedProjectId
        if (projectId == null) {
            Toast.makeText(this, "Please select a project", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val trees = database.treeDao().getTreesByProject(projectId)
                if (trees.isEmpty()) {
                    Toast.makeText(
                        this@DataAnalysisActivity,
                        "No data to analyze",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val csvFile = createTempCsvFile(trees)

                val intent = Intent(this@DataAnalysisActivity, MapViewerActivity::class.java)
                intent.putExtra(MapViewerActivity.EXTRA_MAP_TYPE, mapType)
                intent.putExtra(MapViewerActivity.EXTRA_CSV_PATH, csvFile.absolutePath)
                startActivity(intent)

            } catch (e: Exception) {
                Toast.makeText(
                    this@DataAnalysisActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun generateAnalysis(type: String, title: String) {
        val projectId = selectedProjectId
        if (projectId == null) {
            Toast.makeText(this, "Please select a project", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.ivAnalysisResult.visibility = View.GONE
        binding.tvAnalysisResult.visibility = View.GONE
        // binding.layoutExportButtons.visibility = View.GONE

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

                val imageBytes = when (type) {
                    "distribution" -> apiClient.getDistributionAnalysis(csvFile)
                    "height" -> apiClient.getHeightAnalysis(csvFile)
                    "width" -> apiClient.getWidthAnalysis(csvFile)
                    else -> null
                }

                if (imageBytes != null) {
                    // Store for export
                    chartData[type] = imageBytes

                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    binding.ivAnalysisResult.setImageBitmap(bitmap)
                    binding.ivAnalysisResult.visibility = View.VISIBLE
                    binding.tvAnalysisResult.visibility = View.GONE
                    // binding.layoutExportButtons.visibility = View.VISIBLE
                    // binding.tvCurrentChartTitle.text = title
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
        // binding.layoutExportButtons.visibility = View.GONE

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

    private enum class ExportFormat {
        PNG, PDF
    }

    private fun exportChart(chartType: String, chartName: String, format: ExportFormat) {
        val projectId = selectedProjectId
        if (projectId == null) {
            Toast.makeText(this, "Please select a project first", Toast.LENGTH_SHORT).show()
            return
        }

        val imageBytes = chartData[chartType]
        if (imageBytes == null) {
            Toast.makeText(this, "Please generate the chart first", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val project = database.projectDao().getProjectById(projectId)
                val projectName = project?.name ?: "Project"

                binding.progressBar.visibility = View.VISIBLE

                when (format) {
                    ExportFormat.PNG -> {
                        val file = exportManager.exportChartAsPNG(
                            imageBytes,
                            chartName,
                            projectName
                        )

                        if (file != null) {
                            showExportSuccessDialog(file, "PNG")
                        } else {
                            throw Exception("Failed to export PNG")
                        }
                    }

                    ExportFormat.PDF -> {
                        val trees = database.treeDao().getTreesByProject(projectId)
                        val charts = mapOf(chartName to imageBytes)

                        val file = exportManager.generateProjectPDF(
                            project!!,
                            trees,
                            charts
                        )

                        if (file != null) {
                            showExportSuccessDialog(file, "PDF")
                        } else {
                            throw Exception("Failed to export PDF")
                        }
                    }
                }

                binding.progressBar.visibility = View.GONE

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(
                    this@DataAnalysisActivity,
                    "Export error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun exportAllAnalyses() {
        val projectId = selectedProjectId
        if (projectId == null) {
            Toast.makeText(this, "Please select a project first", Toast.LENGTH_SHORT).show()
            return
        }

        if (chartData.isEmpty()) {
            Toast.makeText(this, "Please generate at least one analysis first", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Export All Analyses")
            .setMessage("This will export all generated charts as PNG files and create a comprehensive PDF report.")
            .setPositiveButton("Export") { _, _ ->
                performBatchExport()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performBatchExport() {
        val projectId = selectedProjectId ?: return

        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                val project = database.projectDao().getProjectById(projectId)!!
                val trees = database.treeDao().getTreesByProject(projectId)

                val files = exportManager.exportBatchAnalyses(project, trees, chartData)

                binding.progressBar.visibility = View.GONE

                if (files.isNotEmpty()) {
                    showBatchExportSuccessDialog(files)
                } else {
                    throw Exception("No files were exported")
                }

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(
                    this@DataAnalysisActivity,
                    "Batch export error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showExportSuccessDialog(file: File, format: String) {
        AlertDialog.Builder(this)
            .setTitle("Export Successful")
            .setMessage("$format file saved to:\n${file.absolutePath}")
            .setPositiveButton("Open") { _, _ ->
                openFile(file)
            }
            .setNegativeButton("Share") { _, _ ->
                shareFile(file)
            }
            .setNeutralButton("OK", null)
            .show()
    }

    private fun showBatchExportSuccessDialog(files: List<File>) {
        val message = "Exported ${files.size} files to:\n${exportManager.getExportDirectoryPath()}"

        AlertDialog.Builder(this)
            .setTitle("Batch Export Successful")
            .setMessage(message)
            .setPositiveButton("Open Folder") { _, _ ->
                openExportFolder()
            }
            .setNeutralButton("OK", null)
            .show()
    }

    private fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "com.arbortag.app.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "com.arbortag.app.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = getMimeType(file)
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "ArborTag Analysis")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, "Share via"))
        } catch (e: Exception) {
            Toast.makeText(this, "Error sharing file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openExportFolder() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(exportManager.getExportDirectoryPath()), "resource/folder")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Please use a file manager to view: ${exportManager.getExportDirectoryPath()}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            else -> "*/*"
        }
    }

    private fun clearAnalysisCache() {
        chartData.clear()
        binding.ivAnalysisResult.visibility = View.GONE
        binding.tvAnalysisResult.visibility = View.GONE
        // binding.layoutExportButtons.visibility = View.GONE
        Toast.makeText(this, "Analysis cache cleared", Toast.LENGTH_SHORT).show()
    }

    private suspend fun createTempCsvFile(trees: List<com.arbortag.app.data.Tree>): File {
        val tempFile = File(cacheDir, "temp_analysis_${System.currentTimeMillis()}.csv")
        com.arbortag.app.utils.CsvExporter.export(trees, tempFile)
        return tempFile
    }
}