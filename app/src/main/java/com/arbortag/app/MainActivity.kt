package com.arbortag.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arbortag.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.cardNewLocation.setOnClickListener {
            startActivity(Intent(this, NewLocationActivity::class.java))
        }

        binding.cardDataCuration.setOnClickListener {
            Toast.makeText(this, "Data Curation - Coming in Phase 10", Toast.LENGTH_SHORT).show()
        }

        binding.cardDataAnalysis.setOnClickListener {
            Toast.makeText(this, "Data Analysis - Coming in Phase 11", Toast.LENGTH_SHORT).show()
        }

        binding.cardDataCuration.setOnClickListener {
            startActivity(Intent(this, DataCurationActivity::class.java))
        }
    }
}