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

    suspend fun getCarbonMapAnalysis(csvFile: File): ByteArray? {
        return makeAnalysisRequest("$baseUrl/analyze/carbon_map", csvFile)
    }

    suspend fun getCanopyAnalysis(csvFile: File): ByteArray? {
        return makeAnalysisRequest("$baseUrl/analyze/canopy", csvFile)
    }

    suspend fun getDiversityAnalysis(csvFile: File): String {
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
                    .url("$baseUrl/analyze/diversity")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val jsonString = response.body?.string() ?: "{}"
                    formatDiversityJson(jsonString)
                } else {
                    throw Exception("API Error: ${response.code}")
                }
            } catch (e: Exception) {
                throw Exception("Network error: ${e.message}")
            }
        }
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

    private fun formatDiversityJson(jsonString: String): String {
        return try {
            val json = JSONObject(jsonString)

            buildString {
                appendLine("🌿 SPECIES DIVERSITY ANALYSIS")
                appendLine("═══════════════════════════════════════")
                appendLine()
                appendLine("📊 Basic Metrics:")
                appendLine("  • Total Species: ${json.getInt("total_species")}")
                appendLine("  • Total Trees: ${json.getInt("total_trees")}")
                appendLine()
                appendLine("📈 Diversity Indices:")
                appendLine("  • Shannon Index: ${json.getDouble("shannon_diversity_index")}")
                appendLine("    (Higher = More diverse)")
                appendLine("  • Simpson Index: ${json.getDouble("simpson_diversity_index")}")
                appendLine("    (0-1 scale, higher = more diverse)")
                appendLine()
                appendLine("🏆 Species Abundance:")
                appendLine("  Most Abundant:")
                appendLine("    ${json.getString("most_abundant")}")
                appendLine("    Count: ${json.getInt("most_abundant_count")} trees")
                appendLine()
                appendLine("  Least Abundant:")
                appendLine("    ${json.getString("least_abundant")}")
                appendLine("    Count: ${json.getInt("least_abundant_count")} trees")
                appendLine()
                appendLine("═══════════════════════════════════════")
                appendLine()
                appendLine("💡 Interpretation:")
                if (json.getDouble("shannon_diversity_index") > 2.5) {
                    appendLine("  High species diversity detected!")
                    appendLine("  This indicates a healthy, balanced ecosystem.")
                } else if (json.getDouble("shannon_diversity_index") > 1.5) {
                    appendLine("  Moderate species diversity.")
                    appendLine("  Consider adding more variety to enhance")
                    appendLine("  ecosystem resilience.")
                } else {
                    appendLine("  Low species diversity detected.")
                    appendLine("  Increasing species variety would improve")
                    appendLine("  ecological balance.")
                }
            }
        } catch (e: Exception) {
            "Error parsing diversity data: ${e.message}"
        }
    }

    private fun formatSummaryJson(jsonString: String): String {
        return try {
            val json = JSONObject(jsonString)
            val projectStats = json.getJSONObject("project_stats")
            val topSpecies = json.getJSONObject("top_species")
            val heightRange = json.getJSONObject("height_range")

            buildString {
                appendLine("📊 COMPREHENSIVE PROJECT SUMMARY")
                appendLine("═══════════════════════════════════════")
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
                    val percentage = (topSpecies.getInt(species).toDouble() /
                            projectStats.getInt("total_trees") * 100)
                    appendLine("  $count. $species")
                    appendLine("     ${topSpecies.getInt(species)} trees (${String.format("%.1f", percentage)}%)")
                    count++
                }
                appendLine()
                appendLine("📏 Height Distribution:")
                appendLine("  • Minimum: ${heightRange.getDouble("min")} m")
                appendLine("  • Median: ${heightRange.getDouble("median")} m")
                appendLine("  • Maximum: ${heightRange.getDouble("max")} m")
                appendLine()
                appendLine("═══════════════════════════════════════")
                appendLine()
                appendLine("🌍 Environmental Impact:")
                val carbonPerYear = projectStats.getDouble("total_carbon_kg_year")
                val carbonPerDay = carbonPerYear / 365
                appendLine("  Daily Carbon Offset: ${String.format("%.2f", carbonPerDay)} kg CO₂")
                appendLine("  Equivalent to:")
                appendLine("    • ${String.format("%.1f", carbonPerDay * 4.5)} km driven")
                appendLine("    • ${String.format("%.0f", carbonPerYear / 411)} trees needed")
                appendLine("      to offset average person's footprint")
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