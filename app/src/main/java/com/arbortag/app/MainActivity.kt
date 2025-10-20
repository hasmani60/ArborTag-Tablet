package com.arbortag.app

import android.content.Intent
import android.os.Bundle
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
            startActivity(Intent(this, DataCurationActivity::class.java))
        }

        binding.cardDataAnalysis.setOnClickListener {
            startActivity(Intent(this, DataAnalysisActivity::class.java))
        }
    }
}