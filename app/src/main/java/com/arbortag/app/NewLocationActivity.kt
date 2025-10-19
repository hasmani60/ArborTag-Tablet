package com.arbortag.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.arbortag.app.adapters.ProjectAdapter
import com.arbortag.app.data.ArborTagDatabase
import com.arbortag.app.data.Project
import com.arbortag.app.databinding.ActivityNewLocationBinding
import kotlinx.coroutines.launch

class NewLocationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewLocationBinding
    private lateinit var database: ArborTagDatabase
    private lateinit var projectAdapter: ProjectAdapter
    private val CAMERA_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewLocationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = ArborTagDatabase.getInstance(this)

        setupRecyclerView()
        loadExistingProjects()

        binding.btnNewLocation.setOnClickListener {
            showNewLocationDialog()
        }
    }

    private fun setupRecyclerView() {
        projectAdapter = ProjectAdapter { project ->
            checkPermissionsAndStartTagging(project.id)
        }

        binding.rvProjects.apply {
            layoutManager = LinearLayoutManager(this@NewLocationActivity)
            adapter = projectAdapter
        }
    }

    private fun loadExistingProjects() {
        lifecycleScope.launch {
            val projects = database.projectDao().getAllProjects()
            projectAdapter.submitList(projects)

            if (projects.isEmpty()) {
                binding.tvNoProjects.visibility = View.VISIBLE
                binding.rvProjects.visibility = View.GONE
            } else {
                binding.tvNoProjects.visibility = View.GONE
                binding.rvProjects.visibility = View.VISIBLE
            }
        }
    }

    private fun showNewLocationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_new_location, null)
        val etLocationName = dialogView.findViewById<EditText>(R.id.etLocationName)

        AlertDialog.Builder(this)
            .setTitle("Create New Location")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val locationName = etLocationName.text.toString().trim()
                if (locationName.isNotEmpty()) {
                    createNewProject(locationName)
                } else {
                    Toast.makeText(this, "Please enter a location name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createNewProject(name: String) {
        lifecycleScope.launch {
            val project = Project(
                name = name,
                createdDate = System.currentTimeMillis()
            )
            val projectId = database.projectDao().insert(project)

            Toast.makeText(
                this@NewLocationActivity,
                "Location created successfully",
                Toast.LENGTH_SHORT
            ).show()

            loadExistingProjects()
            checkPermissionsAndStartTagging(projectId)
        }
    }

    private fun checkPermissionsAndStartTagging(projectId: Long) {
        val cameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        )
        val locationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        when {
            cameraPermission == PackageManager.PERMISSION_GRANTED &&
                    locationPermission == PackageManager.PERMISSION_GRANTED -> {
                startTreeTagging(projectId)
            }
            else -> {
                // Store projectId for use after permission granted
                getSharedPreferences("arbortag_prefs", MODE_PRIVATE)
                    .edit()
                    .putLong("pending_project_id", projectId)
                    .apply()

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    CAMERA_PERMISSION_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                val projectId = getSharedPreferences("arbortag_prefs", MODE_PRIVATE)
                    .getLong("pending_project_id", -1)

                if (projectId != -1L) {
                    startTreeTagging(projectId)
                }
            } else {
                Toast.makeText(
                    this,
                    "Camera and Location permissions are required to tag trees",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun startTreeTagging(projectId: Long) {
        // For now, just show a toast. We'll implement TreeTaggingActivity in Phase 9
        Toast.makeText(
            this,
            "Starting tree tagging for project $projectId\n(Camera feature coming in Phase 9)",
            Toast.LENGTH_LONG
        ).show()

        // TODO: Phase 9 - Uncomment this when TreeTaggingActivity is ready
        val intent = Intent(this, TreeTaggingActivity::class.java)
         intent.putExtra("project_id", projectId)
         startActivity(intent)
    }
}