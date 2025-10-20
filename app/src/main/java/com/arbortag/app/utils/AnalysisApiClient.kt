package com.arbortag.app.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class AnalysisApiClient {

    // TODO: Replace with your actual backend URL
    // For local testing: "http://10.0.2.2:5000" (Android emulator)
    // For device testing: "http://YOUR_COMPUTER_IP:5000"
    // For production: "https://your-backend.onrender.com" or similar
    private val baseUrl = "https://arbortag-tablet.onrender.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun getDistributionAnalysis(csvFile: File): ByteArray? {
        return makeAnalysisRequest("$baseUrl/analyze/distribution", csvFile)
    }

    suspend fun getHeightAnalysis(csvFile: File): ByteArray? {
        return makeAnalysisRequest("$baseUrl/analyze/height", csvFile)
    }

    suspend fun getWidthAnalysis(csvFile: File): ByteArray? {
        return makeAnalysisRequest("$baseUrl/analyze/width", csvFile)
    }

    suspend fun getHeatmapStaticAnalysis(csvFile: File): ByteArray? {
        return makeAnalysisRequest("$baseUrl/analyze/heatmap_static", csvFile)
    }

    suspend fun getDiversityStaticAnalysis(csvFile: File): ByteArray? {
        return makeAnalysisRequest("$baseUrl/analyze/diversity_static", csvFile)
    }

    suspend fun getSummaryAnalysis(csvFile: File): String {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        csvFile.name,
                        csvFile.asRequestBody("text/csv".toMediaTypeOrNull())
                    )
                    .build()

                val request = Request.Builder()
                    .url("$baseUrl/analyze/summary")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val jsonString = response.body?.string() ?: "{}"
                    formatSummaryJson(jsonString)
                } else {
                    throw Exception("API Error: ${response.code}")
                }
            } catch (e: Exception) {
                throw Exception("Network error: ${e.message}")
            }
        }
    }

    suspend fun getStats(csvFile: File): String {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        csvFile.name,
                        csvFile.asRequestBody("text/csv".toMediaTypeOrNull())
                    )
                    .build()

                val request = Request.Builder()
                    .url("$baseUrl/analyze/stats")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val jsonString = response.body?.string() ?: "{}"
                    formatStatsJson(jsonString)
                } else {
                    throw Exception("API Error: ${response.code}")
                }
            } catch (e: Exception) {
                throw Exception("Network error: ${e.message}")
            }
        }
    }

    suspend fun checkHealth(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/health")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }
    }

    private suspend fun makeAnalysisRequest(url: String, csvFile: File): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        csvFile.name,
                        csvFile.asRequestBody("text/csv".toMediaTypeOrNull())
                    )
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    response.body?.bytes()
                } else {
                    throw Exception("API Error: ${response.code}")
                }
            } catch (e: Exception) {
                throw Exception("Network error: ${e.message}")
            }
        }
    }

    private fun formatSummaryJson(jsonString: String): String {
        return try {
            val json = JSONObject(jsonString)
            val projectStats = json.getJSONObject("project_stats")
            val topSpecies = json.getJSONObject("top_species")
            val heightRange = json.getJSONObject("height_range")

            buildString {
                appendLine("📊 PROJECT SUMMARY")
                appendLine("═══════════════════════════════")
                appendLine()
                appendLine("📈 Overall Statistics:")
                appendLine("  • Total Trees: ${projectStats.getInt("total_trees")}")
                appendLine("  • Unique Species: ${projectStats.getInt("unique_species")}")
                appendLine("  • Avg Height: ${projectStats.getDouble("avg_height_m")} m")
                appendLine("  • Avg Width: ${projectStats.getDouble("avg_width_m")} m")
                appendLine("  • Total Carbon: ${projectStats.getDouble("total_carbon_kg_year")} kg CO₂/year")
                appendLine()
                appendLine("🌳 Top 5 Species:")
                val keys = topSpecies.keys()
                var count = 1
                while (keys.hasNext() && count <= 5) {
                    val species = keys.next()
                    appendLine("  $count. $species: ${topSpecies.getInt(species)} trees")
                    count++
                }
                appendLine()
                appendLine("📏 Height Distribution:")
                appendLine("  • Minimum: ${heightRange.getDouble("min")} m")
                appendLine("  • Median: ${heightRange.getDouble("median")} m")
                appendLine("  • Maximum: ${heightRange.getDouble("max")} m")
            }
        } catch (e: Exception) {
            "Error parsing summary: ${e.message}"
        }
    }

    private fun formatStatsJson(jsonString: String): String {
        return try {
            val json = JSONObject(jsonString)
            buildString {
                appendLine("Statistics:")
                appendLine("Total Trees: ${json.getInt("total_trees")}")
                appendLine("Total Species: ${json.getInt("total_species")}")
                appendLine("Average Height: ${String.format("%.2f", json.getDouble("avg_height"))} m")
                appendLine("Average Width: ${String.format("%.2f", json.getDouble("avg_width"))} m")
                appendLine("Total Carbon: ${String.format("%.2f", json.getDouble("total_carbon"))} kg CO₂/year")
                appendLine("Most Common: ${json.getString("most_common_species")}")
            }
        } catch (e: Exception) {
            "Error parsing stats: ${e.message}"
        }
    }
}