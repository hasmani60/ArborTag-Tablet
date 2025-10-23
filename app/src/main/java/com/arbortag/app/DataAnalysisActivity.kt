package com.arbortag.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Environment
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
import java.io.FileOutputStream

class DataAnalysisActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDataAnalysisBinding
    private lateinit var database: ArborTagDatabase
    private lateinit var exportManager: ExportManager
    private var selectedProjectId: Long? = null
    private val apiClient = AnalysisApiClient()

    // Store chart data for export
    private val chartData = mutableMapOf<String, ByteArray>()
    private var currentChartName: String? = null
    private var currentChartBytes: ByteArray? = null

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
        menuInflater.inflate(R.menu.menu_analysis, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_export_all -> {
                exportAllAnalyses()
                true
            }
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
                        chartData.clear()
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

        // Export buttons
        binding.btnExportCurrentPng.setOnClickListener {
            exportCurrentChart(ExportFormat.PNG)
        }

        binding.btnExportCurrentPdf.setOnClickListener {
            exportCurrentChart(ExportFormat.PDF)
        }

        binding.btnExportAllCharts.setOnClickListener {
            exportAllChartsDialog()
        }
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
        binding.layoutExportButtons.visibility = View.GONE

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
                    currentChartName = title
                    currentChartBytes = imageBytes

                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    binding.ivAnalysisResult.setImageBitmap(bitmap)
                    binding.ivAnalysisResult.visibility = View.VISIBLE
                    binding.tvAnalysisResult.visibility = View.GONE
                    binding.layoutExportButtons.visibility = View.VISIBLE
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
        binding.layoutExportButtons.visibility = View.GONE

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

    /**
     * Export current visible chart
     */
    private fun exportCurrentChart(format: ExportFormat) {
        val projectId = selectedProjectId
        val chartBytes = currentChartBytes
        val chartName = currentChartName

        if (projectId == null || chartBytes == null || chartName == null) {
            Toast.makeText(this, "Please generate a chart first", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val project = database.projectDao().getProjectById(projectId)
                val projectName = project?.name ?: "Project"

                binding.progressBar.visibility = View.VISIBLE

                when (format) {
                    ExportFormat.PNG -> {
                        val file = exportChartAsPNG(chartBytes, chartName, projectName)
                        if (file != null) {
                            showExportSuccessDialog(file, "PNG")
                        }
                    }
                    ExportFormat.PDF -> {
                        val file = exportChartAsPDF(chartBytes, chartName, projectName)
                        if (file != null) {
                            showExportSuccessDialog(file, "PDF")
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

    /**
     * Export chart as PNG
     */
    private fun exportChartAsPNG(
        imageBytes: ByteArray,
        chartName: String,
        projectName: String
    ): File? {
        return try {
            val fileName = "${projectName}_${chartName}_${System.currentTimeMillis()}.png"
            val exportDir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "ArborTag_Exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val file = File(exportDir, fileName)

            FileOutputStream(file).use { output ->
                output.write(imageBytes)
            }

            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Export chart as PDF
     */
    private fun exportChartAsPDF(
        imageBytes: ByteArray,
        chartName: String,
        projectName: String
    ): File? {
        return try {
            val fileName = "${projectName}_${chartName}_${System.currentTimeMillis()}.pdf"
            val exportDir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "ArborTag_Exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val file = File(exportDir, fileName)

            // Create PDF document
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(842, 595, 1).create() // A4 landscape
            val page = pdfDocument.startPage(pageInfo)

            // Decode bitmap
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

            // Scale bitmap to fit page
            val canvas = page.canvas
            val scaleFactor = Math.min(
                pageInfo.pageWidth.toFloat() / bitmap.width,
                pageInfo.pageHeight.toFloat() / bitmap.height
            )

            val scaledWidth = bitmap.width * scaleFactor
            val scaledHeight = bitmap.height * scaleFactor

            val left = (pageInfo.pageWidth - scaledWidth) / 2
            val top = (pageInfo.pageHeight - scaledHeight) / 2

            val destRect = android.graphics.RectF(
                left,
                top,
                left + scaledWidth,
                top + scaledHeight
            )

            canvas.drawBitmap(bitmap, null, destRect, null)

            pdfDocument.finishPage(page)

            // Save to file
            FileOutputStream(file).use { output ->
                pdfDocument.writeTo(output)
            }

            pdfDocument.close()

            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Dialog to export all generated charts
     */
    private fun exportAllChartsDialog() {
        if (chartData.isEmpty()) {
            Toast.makeText(this, "Please generate at least one chart first", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Export All Charts")
            .setMessage("Export all generated charts as:\n\n• Individual PNG files\n• Combined PDF report")
            .setPositiveButton("Export Both") { _, _ ->
                exportAllCharts(both = true)
            }
            .setNeutralButton("PNG Only") { _, _ ->
                exportAllCharts(pngOnly = true)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Export all generated charts
     */
    private fun exportAllCharts(both: Boolean = false, pngOnly: Boolean = false) {
        val projectId = selectedProjectId ?: return

        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                val project = database.projectDao().getProjectById(projectId)!!
                val projectName = project.name

                val exportedFiles = mutableListOf<File>()

                // Export PNGs
                chartData.forEach { (type, bytes) ->
                    val chartName = when (type) {
                        "distribution" -> "Species_Distribution"
                        "height" -> "Height_Analysis"
                        "width" -> "Width_Analysis"
                        else -> type
                    }

                    exportChartAsPNG(bytes, chartName, projectName)?.let {
                        exportedFiles.add(it)
                    }
                }

                // Export PDF if requested
                if (both) {
                    val trees = database.treeDao().getTreesByProject(projectId)
                    exportManager.generateProjectPDF(project, trees, chartData)?.let {
                        exportedFiles.add(it)
                    }
                }

                binding.progressBar.visibility = View.GONE

                if (exportedFiles.isNotEmpty()) {
                    showBatchExportSuccessDialog(exportedFiles)
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

    private fun exportAllAnalyses() {
        exportAllChartsDialog()
    }

    private fun showExportSuccessDialog(file: File, format: String) {
        AlertDialog.Builder(this)
            .setTitle("✓ Export Successful")
            .setMessage("$format file saved:\n${file.name}\n\nLocation:\n${file.parent}")
            .setPositiveButton("Open") { _, _ ->
                openFile(file)
            }
            .setNeutralButton("Share") { _, _ ->
                shareFile(file)
            }
            .setNegativeButton("OK", null)
            .show()
    }

    private fun showBatchExportSuccessDialog(files: List<File>) {
        val message = "Successfully exported ${files.size} files:\n\n" +
                files.joinToString("\n") { "• ${it.name}" } +
                "\n\nLocation:\n${files.first().parent}"

        AlertDialog.Builder(this)
            .setTitle("✓ Batch Export Successful")
            .setMessage(message)
            .setPositiveButton("Open Folder") { _, _ ->
                openExportFolder()
            }
            .setNegativeButton("OK", null)
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
                putExtra(Intent.EXTRA_SUBJECT, "ArborTag Analysis - ${file.nameWithoutExtension}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, "Share via"))
        } catch (e: Exception) {
            Toast.makeText(this, "Error sharing file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openExportFolder() {
        try {
            val exportDir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "ArborTag_Exports")
            val uri = FileProvider.getUriForFile(
                this,
                "com.arbortag.app.fileprovider",
                exportDir
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(intent)
        } catch (e: Exception) {
            val exportDir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "ArborTag_Exports")
            Toast.makeText(
                this,
                "Files saved to:\n${exportDir.absolutePath}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            else -> "*/*"
        }
    }

    private suspend fun createTempCsvFile(trees: List<com.arbortag.app.data.Tree>): File {
        val tempFile = File(cacheDir, "temp_analysis_${System.currentTimeMillis()}.csv")
        com.arbortag.app.utils.CsvExporter.export(trees, tempFile)
        return tempFile
    }
}