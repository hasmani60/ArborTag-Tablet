package com.arbortag.app

import android.annotation.SuppressLint
import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.arbortag.app.data.ArborTagDatabase
import com.arbortag.app.data.Species
import com.arbortag.app.data.Tree
import com.arbortag.app.databinding.ActivitySpeciesSelectionBinding
import kotlinx.coroutines.launch

class SpeciesSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySpeciesSelectionBinding
    private lateinit var database: ArborTagDatabase
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var projectId: Long = -1
    private var imagePath: String? = null
    private var height: Double = 0.0
    private var width: Double = 0.0
    private var canopy: Double? = null
    private var selectedSpecies: Species? = null
    private var currentLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpeciesSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = ArborTagDatabase.getInstance(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Get data from intent
        projectId = intent.getLongExtra("project_id", -1)
        imagePath = intent.getStringExtra("image_path")
        height = intent.getDoubleExtra("height", 0.0)
        width = intent.getDoubleExtra("width", 0.0)
        canopy = intent.getDoubleExtra("canopy", -1.0).takeIf { it != -1.0 }

        loadSpecies()
        getCurrentLocation()
        setupClickListeners()
    }

    private fun loadSpecies() {
        lifecycleScope.launch {
            var speciesList = database.speciesDao().getAllSpecies()

            if (speciesList.isEmpty()) {
                initializeSpeciesDatabase()
                speciesList = database.speciesDao().getAllSpecies()
            }

            val displayNames = speciesList.map { "${it.commonName} (${it.scientificName})" }
            val adapter = ArrayAdapter(
                this@SpeciesSelectionActivity,
                android.R.layout.simple_spinner_item,
                displayNames
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

            binding.spinnerSpecies.adapter = adapter
            binding.spinnerSpecies.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedSpecies = speciesList[position]
                    updateCarbonCalculation()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    selectedSpecies = null
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        binding.tvGpsStatus.text = "Getting GPS location..."

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                currentLocation = location
                binding.tvGpsCoordinates.text =
                    "Lat: ${String.format("%.6f", location.latitude)}, " +
                            "Long: ${String.format("%.6f", location.longitude)}"
                binding.tvGpsStatus.text = "GPS acquired ✓"
                binding.tvGpsStatus.setTextColor(getColor(R.color.success))
            } else {
                binding.tvGpsStatus.text = "GPS location unavailable"
                binding.tvGpsStatus.setTextColor(getColor(R.color.error))
                Toast.makeText(this, "Please ensure GPS is enabled", Toast.LENGTH_LONG).show()
            }
        }.addOnFailureListener {
            binding.tvGpsStatus.text = "GPS error"
            binding.tvGpsStatus.setTextColor(getColor(R.color.error))
        }
    }

    private fun updateCarbonCalculation() {
        val species = selectedSpecies ?: return

        // Carbon = SpeciesFactor * (Height * Width)
        val carbonSeq = species.carbonFactor * (height * width)

        binding.tvCarbonValue.text = String.format("%.2f kg CO₂/year", carbonSeq)
    }

    private fun setupClickListeners() {
        binding.btnSave.setOnClickListener {
            showReviewDialog()
        }

        binding.btnDiscard.setOnClickListener {
            finish()
        }
    }

    private fun showReviewDialog() {
        if (selectedSpecies == null) {
            Toast.makeText(this, "Please select a species", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentLocation == null) {
            Toast.makeText(this, "GPS location not available", Toast.LENGTH_SHORT).show()
            return
        }

        val species = selectedSpecies!!
        val carbonSeq = species.carbonFactor * (height * width)

        val reviewMessage = """
            Common Name: ${species.commonName}
            Scientific Name: ${species.scientificName}
            Height: ${String.format("%.2f", height)} m
            Width: ${String.format("%.2f", width)} m
            ${canopy?.let { "Canopy: ${String.format("%.2f", it)} m\n" } ?: ""}
            Coordinates: ${String.format("%.6f", currentLocation!!.latitude)}, ${String.format("%.6f", currentLocation!!.longitude)}
            Carbon Sequestration: ${String.format("%.2f", carbonSeq)} kg CO₂/year
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Review Tree Data")
            .setMessage(reviewMessage)
            .setPositiveButton("Save") { _, _ ->
                saveTreeData()
            }
            .setNegativeButton("Edit", null)
            .show()
    }

    private fun saveTreeData() {
        val species = selectedSpecies ?: return
        val location = currentLocation ?: return

        val carbonSeq = species.carbonFactor * (height * width)

        val tree = Tree(
            projectId = projectId,
            scientificName = species.scientificName,
            commonName = species.commonName,
            height = height,
            width = width,
            canopy = canopy,
            latitude = location.latitude,
            longitude = location.longitude,
            carbonSequestration = carbonSeq,
            imagePath = imagePath
        )

        lifecycleScope.launch {
            database.treeDao().insert(tree)

            Toast.makeText(
                this@SpeciesSelectionActivity,
                "Tree data saved successfully",
                Toast.LENGTH_SHORT
            ).show()

            showContinueDialog()
        }
    }

    private fun showContinueDialog() {
        AlertDialog.Builder(this)
            .setTitle("Tree Tagged Successfully")
            .setMessage("Do you want to continue tagging more trees?")
            .setPositiveButton("Continue") { _, _ ->
                val intent = Intent(this, TreeTaggingActivity::class.java)
                intent.putExtra("project_id", projectId)
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Stop") { _, _ ->
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(intent)
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private suspend fun initializeSpeciesDatabase() {
        val defaultSpecies = listOf(
            Species("Ficus religiosa", "Peepal", 0.62),
            Species("Azadirachta indica", "Neem", 0.58),
            Species("Mangifera indica", "Mango", 0.54),
            Species("Terminalia arjuna", "Arjuna", 0.65),
            Species("Dalbergia sissoo", "Shisham", 0.70),
            Species("Tectona grandis", "Teak", 0.68),
            Species("Eucalyptus globulus", "Eucalyptus", 0.52),
            Species("Acacia nilotica", "Babul", 0.60),
            Species("Pongamia pinnata", "Pongam", 0.56),
            Species("Albizia lebbeck", "Siris", 0.59)
        )

        database.speciesDao().insertAll(defaultSpecies)
    }
}