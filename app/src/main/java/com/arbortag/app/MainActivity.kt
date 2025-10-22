package com.arbortag.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.arbortag.app.databinding.ActivityMainBinding
import org.opencv.android.OpenCVLoader
import org.opencv.aruco.Aruco
import org.opencv.core.Core

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize OpenCV with comprehensive testing
        initializeAndTestOpenCV()

        setupClickListeners()
    }

    private fun initializeAndTestOpenCV() {
        Log.d(TAG, "========================================")
        Log.d(TAG, "Starting OpenCV Initialization")
        Log.d(TAG, "========================================")

        // Step 1: Load OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "✗ OpenCV initialization FAILED!")
            showOpenCVError(
                "OpenCV Failed to Load",
                "The OpenCV library could not be initialized. ArUco marker detection will not work.\n\n" +
                        "Possible causes:\n" +
                        "• OpenCV library not properly included\n" +
                        "• Native libraries missing\n" +
                        "• Check app/build.gradle.kts dependencies"
            )
            return
        }

        Log.d(TAG, "✓ OpenCV loaded successfully")

        // Step 2: Get OpenCV version
        try {
            val opencvVersion = Core.getVersionString()
            Log.d(TAG, "✓ OpenCV version: $opencvVersion")
        } catch (e: Exception) {
            Log.e(TAG, "✗ Could not get OpenCV version: ${e.message}")
        }

        // Step 3: Test ArUco module availability
        var arucoWorks = false
        try {
            val dict = Aruco.getPredefinedDictionary(Aruco.DICT_6X6_250)
            Log.d(TAG, "✓ ArUco dictionary created successfully")
            arucoWorks = true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "✗ ArUco NATIVE LIBRARY ERROR: ${e.message}")
            Log.e(TAG, "This means OpenCV ArUco native libraries are not loaded!")
            showOpenCVError(
                "ArUco Native Library Missing",
                "OpenCV loaded, but ArUco native libraries are missing.\n\n" +
                        "Solution:\n" +
                        "1. Change OpenCV dependency in build.gradle.kts\n" +
                        "2. Use: implementation(\"com.quickbirdstudios:opencv-contrib:4.5.3\")\n" +
                        "3. Or download official OpenCV Android SDK"
            )
        } catch (e: Exception) {
            Log.e(TAG, "✗ ArUco module error: ${e.message}", e)
            showOpenCVError(
                "ArUco Module Error",
                "Error testing ArUco module: ${e.message}\n\n" +
                        "ArUco marker detection may not work properly."
            )
        }

        // Step 4: Show result to user
        if (arucoWorks) {
            Log.d(TAG, "========================================")
            Log.d(TAG, "✓ ALL SYSTEMS READY")
            Log.d(TAG, "✓ OpenCV: OK")
            Log.d(TAG, "✓ ArUco: OK")
            Log.d(TAG, "========================================")

            Toast.makeText(
                this,
                "✓ OpenCV & ArUco Ready!\nHeight detection available",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Log.d(TAG, "========================================")
            Log.d(TAG, "⚠ PARTIAL INITIALIZATION")
            Log.d(TAG, "✓ OpenCV: OK")
            Log.d(TAG, "✗ ArUco: FAILED")
            Log.d(TAG, "========================================")
        }
    }

    private fun showOpenCVError(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> }
            .setNeutralButton("View Logs") { _, _ ->
                // Optionally: open log viewer or settings
                Toast.makeText(this, "Check Logcat for details", Toast.LENGTH_SHORT).show()
            }
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun setupClickListeners() {
        binding.cardNewLocation.setOnClickListener {
            startActivity(Intent(this, NewLocationActivity::class.java))
        }

        binding.cardDataCuration.setOnClickListener {
            startActivity(Intent(this, DataCurationActivity::class.java))
        }

        binding.cardDataAnalysis.setOnClickListener {
            startActivity(Intent(this, DataAnalysisActivity::class.java))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}