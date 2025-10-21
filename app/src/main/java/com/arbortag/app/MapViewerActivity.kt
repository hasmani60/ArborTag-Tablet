package com.arbortag.app

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.arbortag.app.databinding.ActivityMapViewerBinding
import com.arbortag.app.utils.AnalysisApiClient
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MapViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapViewerBinding
    private val apiClient = AnalysisApiClient()
    private var currentHtmlContent: String? = null
    private var mapType: String = ""
    private var csvFile: File? = null

    companion object {
        const val EXTRA_MAP_TYPE = "map_type"
        const val EXTRA_CSV_PATH = "csv_path"
        const val TYPE_HEATMAP = "heatmap"
        const val TYPE_DIVERSITY = "diversity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mapType = intent.getStringExtra(EXTRA_MAP_TYPE) ?: ""
        val csvPath = intent.getStringExtra(EXTRA_CSV_PATH) ?: ""
        csvFile = File(csvPath)

        setupToolbar()
        setupWebView()
        setupButtons()
        loadMap()
    }

    private fun setupToolbar() {
        val title = when (mapType) {
            TYPE_HEATMAP -> "Carbon Sequestration Map"
            TYPE_DIVERSITY -> "Species Diversity Map"
            else -> "Map View"
        }
        supportActionBar?.title = title
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupWebView() {
        binding.webView.settings.apply {
            javaScriptEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.progressBar.visibility = View.GONE
                binding.btnExportPng.visibility = View.VISIBLE
            }
        }
    }

    private fun setupButtons() {
        binding.btnExportPng.setOnClickListener {
            exportMapAsPng()
        }
    }

    private fun loadMap() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.text = "Loading interactive map..."

        lifecycleScope.launch {
            try {
                val htmlContent = when (mapType) {
                    TYPE_HEATMAP -> apiClient.getHeatmapHtml(csvFile!!)
                    TYPE_DIVERSITY -> apiClient.getDiversityHtml(csvFile!!)
                    else -> null
                }

                if (htmlContent != null) {
                    currentHtmlContent = htmlContent
                    binding.webView.loadDataWithBaseURL(
                        "file:///android_asset/",
                        htmlContent,
                        "text/html",
                        "UTF-8",
                        null
                    )
                    binding.tvStatus.visibility = View.GONE
                } else {
                    throw Exception("Failed to load map")
                }

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = "Error loading map: ${e.message}"
                Toast.makeText(
                    this@MapViewerActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun exportMapAsPng() {
        binding.progressBar.visibility = View.VISIBLE
        Toast.makeText(this, "Generating PNG...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                // Get PNG from backend
                val pngBytes = when (mapType) {
                    TYPE_HEATMAP -> apiClient.getHeatmapStaticAnalysis(csvFile!!)
                    TYPE_DIVERSITY -> apiClient.getDiversityStaticAnalysis(csvFile!!)
                    else -> null
                }

                if (pngBytes != null) {
                    // Save to Downloads
                    val fileName = "${mapType}_map_${System.currentTimeMillis()}.png"
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val file = File(downloadsDir, fileName)

                    FileOutputStream(file).use { output ->
                        output.write(pngBytes)
                    }

                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@MapViewerActivity,
                        "Map saved to Downloads/$fileName",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    throw Exception("Failed to generate PNG")
                }

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(
                    this@MapViewerActivity,
                    "Export error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}