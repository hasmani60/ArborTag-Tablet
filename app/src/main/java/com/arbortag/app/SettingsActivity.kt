package com.arbortag.app

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arbortag.app.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    companion object {
        private const val PREFS_NAME = "arbortag_settings"
        private const val KEY_MARKER_SIZE = "marker_size"
        private const val DEFAULT_MARKER_SIZE = 0.10 // meters (10cm x 10cm marker)

        /**
         * Get marker size (side length) in meters
         */
        fun getMarkerSize(context: Context): Double {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getFloat(KEY_MARKER_SIZE, DEFAULT_MARKER_SIZE.toFloat()).toDouble()
        }

        /**
         * Save marker size (side length) in meters
         */
        fun saveMarkerSize(context: Context, size: Double) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putFloat(KEY_MARKER_SIZE, size.toFloat()).apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        loadCurrentSettings()
        setupClickListeners()
    }

    private fun loadCurrentSettings() {
        val currentSize = getMarkerSize(this)
        binding.etMarkerPerimeter.setText(currentSize.toString())

        // Update info text
        updateInfoText(currentSize)
    }

    private fun setupClickListeners() {
        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
        }

        binding.btnResetToDefault.setOnClickListener {
            binding.etMarkerPerimeter.setText(DEFAULT_MARKER_SIZE.toString())
            updateInfoText(DEFAULT_MARKER_SIZE)
        }

        // Preset buttons for common marker sizes
        binding.btnPreset10cm.setOnClickListener {
            val size = 0.10 // 10cm side length
            binding.etMarkerPerimeter.setText(size.toString())
            updateInfoText(size)
        }

        binding.btnPreset15cm.setOnClickListener {
            val size = 0.15 // 15cm side length
            binding.etMarkerPerimeter.setText(size.toString())
            updateInfoText(size)
        }

        binding.btnPreset18cm.setOnClickListener {
            val size = 0.18 // 18cm side length
            binding.etMarkerPerimeter.setText(size.toString())
            updateInfoText(size)
        }

        binding.btnPreset20cm.setOnClickListener {
            val size = 0.20 // 20cm side length
            binding.etMarkerPerimeter.setText(size.toString())
            updateInfoText(size)
        }
    }

    private fun saveSettings() {
        val sizeText = binding.etMarkerPerimeter.text.toString()

        if (sizeText.isEmpty()) {
            Toast.makeText(this, "Please enter a marker size", Toast.LENGTH_SHORT).show()
            return
        }

        val size = sizeText.toDoubleOrNull()
        if (size == null || size <= 0) {
            Toast.makeText(this, "Please enter a valid positive number", Toast.LENGTH_SHORT).show()
            return
        }

        saveMarkerSize(this, size)
        Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun updateInfoText(size: Double) {
        val sizeCm = size * 100
        val perimeter = size * 4
        binding.tvMarkerInfo.text = String.format(
            "Marker: %.1f cm × %.1f cm square\nPerimeter: %.2f m",
            sizeCm, sizeCm, perimeter
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}