package com.arbortag.app.utils

import com.arbortag.app.data.Tree
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object CsvExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun export(trees: List<Tree>, outputFile: File): Boolean {
        return try {
            FileWriter(outputFile).use { writer ->
                // Write header
                writer.append("id,project_id,scientific_name,common_name,height,width,canopy,")
                writer.append("lat,long,carbon_seq,capture_date\n")

                // Write data rows
                trees.forEach { tree ->
                    writer.append("${tree.id},")
                    writer.append("${tree.projectId},")
                    writer.append("\"${tree.scientificName}\",")
                    writer.append("\"${tree.commonName}\",")
                    writer.append("${tree.height},")
                    writer.append("${tree.width},")
                    writer.append("${tree.canopy ?: ""},")
                    writer.append("${tree.latitude},")
                    writer.append("${tree.longitude},")
                    writer.append("${tree.carbonSequestration},")
                    writer.append("\"${dateFormat.format(Date(tree.captureDate))}\"\n")
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}