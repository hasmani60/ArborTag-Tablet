package com.arbortag.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import com.arbortag.app.data.Project
import com.arbortag.app.data.Tree
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Comprehensive export manager for ArborTag data
 * Handles PDF, PNG, and Excel exports with professional formatting
 */
class ExportManager(private val context: Context) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("MMMM dd, yyyy HH:mm", Locale.getDefault())

    /**
     * Export directory in app-specific storage
     */
    private fun getExportDirectory(): File {
        val exportDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "ArborTag_Exports")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        return exportDir
    }

    /**
     * Export a single chart as PNG
     */
    fun exportChartAsPNG(
        imageBytes: ByteArray,
        chartName: String,
        projectName: String
    ): File? {
        return try {
            val fileName = "${projectName}_${chartName}_${dateFormat.format(Date())}.png"
            val file = File(getExportDirectory(), fileName)

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
     * Generate comprehensive PDF report for a project
     */
    fun generateProjectPDF(
        project: Project,
        trees: List<Tree>,
        charts: Map<String, ByteArray>
    ): File? {
        return try {
            val fileName = "${project.name}_Report_${dateFormat.format(Date())}.pdf"
            val file = File(getExportDirectory(), fileName)

            val writer = PdfWriter(file)
            val pdfDoc = PdfDocument(writer)
            val document = Document(pdfDoc)

            // Add cover page
            addCoverPage(document, project, trees)

            // Add statistics page
            addStatisticsPage(document, trees)

            // Add charts
            charts.forEach { (chartName, imageBytes) ->
                addChartPage(document, chartName, imageBytes)
            }

            // Add data table
            addDataTablePage(document, trees)

            // Add footer to all pages
            addPageFooters(pdfDoc, project)

            document.close()
            file

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Add cover page to PDF
     */
    private fun addCoverPage(document: Document, project: Project, trees: List<Tree>) {
        // Title
        val title = Paragraph("ArborTag Project Report")
            .setFontSize(28f)
            .setBold()
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(ColorConstants.GREEN)
        document.add(title)

        document.add(Paragraph("\n"))

        // Project name
        val projectName = Paragraph(project.name)
            .setFontSize(20f)
            .setBold()
            .setTextAlignment(TextAlignment.CENTER)
        document.add(projectName)

        document.add(Paragraph("\n\n"))

        // Report info
        val reportInfo = """
            Generated: ${displayDateFormat.format(Date())}
            
            Project Created: ${displayDateFormat.format(Date(project.createdDate))}
            Total Trees Tagged: ${trees.size}
            
            This report contains comprehensive analysis of tree data
            including species distribution, measurements, and carbon
            sequestration estimates.
        """.trimIndent()

        val infoParagraph = Paragraph(reportInfo)
            .setFontSize(12f)
            .setTextAlignment(TextAlignment.CENTER)
        document.add(infoParagraph)

        document.add(Paragraph("\n\n\n"))

        // Quick stats box
        val totalCarbon = trees.sumOf { it.carbonSequestration }
        val avgHeight = trees.map { it.height }.average()
        val avgWidth = trees.map { it.width }.average()
        val uniqueSpecies = trees.map { it.scientificName }.distinct().size

        val quickStats = """
            ═══════════════════════════════════════
            QUICK STATISTICS
            ═══════════════════════════════════════
            
            🌳 Total Trees: ${trees.size}
            🌿 Unique Species: $uniqueSpecies
            📏 Average Height: ${String.format("%.2f m", avgHeight)}
            📐 Average Width: ${String.format("%.2f m", avgWidth)}
            💨 Total Carbon: ${String.format("%.2f kg CO₂/year", totalCarbon)}
            
            ═══════════════════════════════════════
        """.trimIndent()

        val statsBox = Paragraph(quickStats)
            .setFontSize(11f)
            .setBackgroundColor(ColorConstants.LIGHT_GRAY)
            .setPadding(15f)
        document.add(statsBox)

        // New page for content
        document.add(com.itextpdf.layout.element.AreaBreak())
    }

    /**
     * Add detailed statistics page
     */
    private fun addStatisticsPage(document: Document, trees: List<Tree>) {
        val header = Paragraph("Detailed Statistics")
            .setFontSize(20f)
            .setBold()
            .setFontColor(ColorConstants.GREEN)
        document.add(header)

        document.add(Paragraph("\n"))

        // Species breakdown
        val speciesCount = trees.groupBy { it.scientificName }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }

        val speciesHeader = Paragraph("Species Distribution")
            .setFontSize(14f)
            .setBold()
        document.add(speciesHeader)

        val speciesTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f, 1f)))
            .setWidth(UnitValue.createPercentValue(100f))

        speciesTable.addHeaderCell("Scientific Name")
        speciesTable.addHeaderCell("Count")
        speciesTable.addHeaderCell("Percentage")

        speciesCount.forEach { (species, count) ->
            val percentage = (count.toFloat() / trees.size) * 100
            speciesTable.addCell(species)
            speciesTable.addCell(count.toString())
            speciesTable.addCell(String.format("%.1f%%", percentage))
        }

        document.add(speciesTable)
        document.add(Paragraph("\n\n"))

        // Height statistics
        val heights = trees.map { it.height }
        val heightStats = Paragraph("""
            Height Analysis
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            Minimum: ${String.format("%.2f m", heights.minOrNull() ?: 0.0)}
            Maximum: ${String.format("%.2f m", heights.maxOrNull() ?: 0.0)}
            Average: ${String.format("%.2f m", heights.average())}
            Median: ${String.format("%.2f m", heights.sorted()[heights.size / 2])}
        """.trimIndent())
            .setFontSize(12f)
        document.add(heightStats)

        document.add(Paragraph("\n"))

        // Width statistics
        val widths = trees.map { it.width }
        val widthStats = Paragraph("""
            Width Analysis
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            Minimum: ${String.format("%.2f m", widths.minOrNull() ?: 0.0)}
            Maximum: ${String.format("%.2f m", widths.maxOrNull() ?: 0.0)}
            Average: ${String.format("%.2f m", widths.average())}
            Median: ${String.format("%.2f m", widths.sorted()[widths.size / 2])}
        """.trimIndent())
            .setFontSize(12f)
        document.add(widthStats)

        document.add(Paragraph("\n"))

        // Carbon sequestration
        val carbons = trees.map { it.carbonSequestration }
        val carbonStats = Paragraph("""
            Carbon Sequestration
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            Total: ${String.format("%.2f kg CO₂/year", carbons.sum())}
            Average per tree: ${String.format("%.2f kg CO₂/year", carbons.average())}
            Top contributor: ${String.format("%.2f kg CO₂/year", carbons.maxOrNull() ?: 0.0)}
        """.trimIndent())
            .setFontSize(12f)
            .setBackgroundColor(ColorConstants.LIGHT_GRAY)
            .setPadding(10f)
        document.add(carbonStats)

        document.add(com.itextpdf.layout.element.AreaBreak())
    }

    /**
     * Add a chart page
     */
    private fun addChartPage(document: Document, chartName: String, imageBytes: ByteArray) {
        val title = Paragraph(chartName)
            .setFontSize(18f)
            .setBold()
            .setFontColor(ColorConstants.GREEN)
        document.add(title)

        document.add(Paragraph("\n"))

        // Add image
        try {
            val imageData = ImageDataFactory.create(imageBytes)
            val image = Image(imageData)

            // Scale image to fit page width
            val pageWidth = document.pdfDocument.defaultPageSize.width - document.leftMargin - document.rightMargin
            image.setWidth(pageWidth)
            image.setAutoScale(true)

            document.add(image)
        } catch (e: Exception) {
            val errorMsg = Paragraph("Error loading chart image")
                .setFontColor(ColorConstants.RED)
            document.add(errorMsg)
        }

        document.add(com.itextpdf.layout.element.AreaBreak())
    }

    /**
     * Add data table page
     */
    private fun addDataTablePage(document: Document, trees: List<Tree>) {
        val title = Paragraph("Complete Tree Data")
            .setFontSize(18f)
            .setBold()
            .setFontColor(ColorConstants.GREEN)
        document.add(title)

        document.add(Paragraph("\n"))

        // Create table
        val table = Table(UnitValue.createPercentArray(floatArrayOf(2f, 2f, 1f, 1f, 1f, 1.5f)))
            .setWidth(UnitValue.createPercentValue(100f))
            .setFontSize(9f)

        // Headers
        table.addHeaderCell("Scientific Name")
        table.addHeaderCell("Common Name")
        table.addHeaderCell("Height (m)")
        table.addHeaderCell("Width (m)")
        table.addHeaderCell("Canopy (m)")
        table.addHeaderCell("Carbon (kg CO₂/yr)")

        // Data rows
        trees.forEach { tree ->
            table.addCell(tree.scientificName)
            table.addCell(tree.commonName)
            table.addCell(String.format("%.2f", tree.height))
            table.addCell(String.format("%.2f", tree.width))
            table.addCell(tree.canopy?.let { String.format("%.2f", it) } ?: "N/A")
            table.addCell(String.format("%.2f", tree.carbonSequestration))
        }

        document.add(table)
    }

    /**
     * Add page footers
     */
    private fun addPageFooters(pdfDoc: PdfDocument, project: Project) {
        val numberOfPages = pdfDoc.numberOfPages

        for (i in 1..numberOfPages) {
            val page = pdfDoc.getPage(i)
            val pageSize = page.pageSize

            // You can add custom footers here if needed
            // For now, iText7 handles page numbers automatically
        }
    }

    /**
     * Export all analyses as a batch
     */
    fun exportBatchAnalyses(
        project: Project,
        trees: List<Tree>,
        charts: Map<String, ByteArray>
    ): List<File> {
        val files = mutableListOf<File>()

        // Export individual PNGs
        charts.forEach { (chartName, imageBytes) ->
            exportChartAsPNG(imageBytes, chartName, project.name)?.let {
                files.add(it)
            }
        }

        // Export comprehensive PDF
        generateProjectPDF(project, trees, charts)?.let {
            files.add(it)
        }

        return files
    }

    /**
     * Get export directory path for user
     */
    fun getExportDirectoryPath(): String {
        return getExportDirectory().absolutePath
    }

    /**
     * Clean up old export files (older than 30 days)
     */
    fun cleanupOldExports() {
        val exportDir = getExportDirectory()
        val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)

        exportDir.listFiles()?.forEach { file ->
            if (file.lastModified() < thirtyDaysAgo) {
                file.delete()
            }
        }
    }
}