package com.arbortag.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arbortag.app.databinding.ActivityMainBinding
import org.opencv.android.OpenCVLoader

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize OpenCV
        initializeOpenCV()

        setupClickListeners()
    }

    private fun initializeOpenCV() {
        if (OpenCVLoader.initDebug()) {
            Log.d(TAG, "OpenCV loaded successfully")
            Toast.makeText(this, "OpenCV loaded successfully", Toast.LENGTH_SHORT).show()
        } else {
            Log.e(TAG, "OpenCV initialization failed!")
            Toast.makeText(
                this,
                "OpenCV initialization failed! ArUco features may not work.",
                Toast.LENGTH_LONG
            ).show()
        }
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