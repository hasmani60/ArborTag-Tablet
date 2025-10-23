package com.arbortag.app

import android.annotation.SuppressLint
import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import com.arbortag.app.data.ArborTagDatabase
import com.arbortag.app.data.Species
import com.arbortag.app.data.Tree
import com.arbortag.app.databinding.ActivitySpeciesSelectionBinding
import com.arbortag.app.utils.PermissionHelper
import kotlinx.coroutines.launch

class SpeciesSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySpeciesSelectionBinding
    private lateinit var database: ArborTagDatabase
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private var locationCallback: LocationCallback? = null

    private var projectId: Long = -1
    private var imagePath: String? = null
    private var height: Double = 0.0
    private var width: Double = 0.0
    private var canopy: Double? = null
    private var selectedSpecies: Species? = null
    private var currentLocation: Location? = null

    private val GPS_TIMEOUT_MS = 30000L // 30 seconds
    private var gpsTimeoutHandler: Handler? = null
    private var isGpsAcquisitionActive = false

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

        setupLocationRequest()
        loadSpecies()
        setupClickListeners()

        // Start GPS acquisition
        startGPSAcquisition()
    }

    private fun setupLocationRequest() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateIntervalMillis(500)
            .setMaxUpdates(5)
            .build()
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
    private fun startGPSAcquisition() {
        // Check permissions first
        if (!PermissionHelper.hasAllPermissions(this)) {
            binding.tvGpsStatus.text = "❌ Location permission not granted"
            binding.tvGpsStatus.setTextColor(getColor(R.color.error))
            binding.btnRefreshGps.visibility = View.VISIBLE
            return
        }

        // Check if GPS is enabled
        if (!PermissionHelper.isGPSEnabled(this)) {
            binding.tvGpsStatus.text = "❌ GPS is disabled"
            binding.tvGpsStatus.setTextColor(getColor(R.color.error))
            binding.btnRefreshGps.visibility = View.VISIBLE
            binding.btnEnableGps.visibility = View.VISIBLE
            return
        }

        isGpsAcquisitionActive = true
        binding.tvGpsStatus.text = "⌛ Acquiring GPS location..."
        binding.tvGpsStatus.setTextColor(getColor(R.color.warning))
        binding.progressBarGps.visibility = View.VISIBLE
        binding.btnRefreshGps.visibility = View.GONE
        binding.btnEnableGps.visibility = View.GONE

        // Try to get last known location first (quick)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null && !isGpsAcquisitionActive) {
                handleLocationSuccess(location, isLastKnown = true)
            }
        }

        // Set up timeout
        gpsTimeoutHandler = Handler(Looper.getMainLooper())
        gpsTimeoutHandler?.postDelayed({
            if (isGpsAcquisitionActive) {
                handleGPSTimeout()
            }
        }, GPS_TIMEOUT_MS)

        // Request fresh location updates
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    if (isGpsAcquisitionActive) {
                        handleLocationSuccess(location, isLastKnown = false)
                    }
                }
            }
        }

        locationCallback?.let {
            fusedLocationClient.requestLocationUpdates(locationRequest, it, Looper.getMainLooper())
        }
    }

    private fun handleLocationSuccess(location: Location, isLastKnown: Boolean) {
        isGpsAcquisitionActive = false
        currentLocation = location

        // Cancel timeout
        gpsTimeoutHandler?.removeCallbacksAndMessages(null)

        // Stop location updates
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }

        binding.progressBarGps.visibility = View.GONE
        binding.tvGpsStatus.text = if (isLastKnown) {
            "✓ GPS acquired (last known)"
        } else {
            "✓ GPS acquired"
        }
        binding.tvGpsStatus.setTextColor(getColor(R.color.success))

        val accuracy = location.accuracy
        val accuracyText = "± ${String.format("%.0f", accuracy)} m"

        binding.tvGpsCoordinates.text = String.format(
            "Lat: %.6f, Long: %.6f\nAccuracy: %s",
            location.latitude,
            location.longitude,
            accuracyText
        )

        binding.tvGpsCoordinates.setTextColor(getColor(R.color.primary_text))

        // Show accuracy warning if poor
        if (accuracy > 20) {
            binding.tvGpsAccuracyWarning.visibility = View.VISIBLE
            binding.tvGpsAccuracyWarning.text = "⚠️ GPS accuracy is low. Consider moving to open area."
        } else {
            binding.tvGpsAccuracyWarning.visibility = View.GONE
        }

        Toast.makeText(
            this,
            "GPS location acquired successfully!",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun handleGPSTimeout() {
        isGpsAcquisitionActive = false

        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }

        binding.progressBarGps.visibility = View.GONE
        binding.tvGpsStatus.text = "❌ GPS timeout"
        binding.tvGpsStatus.setTextColor(getColor(R.color.error))
        binding.btnRefreshGps.visibility = View.VISIBLE

        // Try to use last known location if available
        currentLocation?.let {
            Toast.makeText(
                this,
                "Using last known GPS location",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        Toast.makeText(
            this,
            "GPS acquisition timed out. Please try again.",
            Toast.LENGTH_LONG
        ).show()
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

        binding.btnRefreshGps.setOnClickListener {
            startGPSAcquisition()
        }

        binding.btnEnableGps.setOnClickListener {
            PermissionHelper.checkAndEnableGPS(this)
        }
    }

    private fun showReviewDialog() {
        if (selectedSpecies == null) {
            Toast.makeText(this, "Please select a species", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentLocation == null) {
            AlertDialog.Builder(this)
                .setTitle("No GPS Location")
                .setMessage("GPS location is not available. Do you want to continue without GPS coordinates?")
                .setPositiveButton("Retry GPS") { _, _ ->
                    startGPSAcquisition()
                }
                .setNegativeButton("Cancel", null)
                .show()
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
            GPS Accuracy: ± ${String.format("%.0f", currentLocation!!.accuracy)} m
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
            .setTitle("Tree Tagged Successfully ✓")
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

    override fun onDestroy() {
        super.onDestroy()
        gpsTimeoutHandler?.removeCallbacksAndMessages(null)
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
    }
}