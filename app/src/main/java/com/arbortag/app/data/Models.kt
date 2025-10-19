package com.arbortag.app.data

import androidx.room.*

// Project Entity
@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdDate: Long,
    val lastModified: Long = System.currentTimeMillis()
)

// Tree Entity
@Entity(
    tableName = "trees",
    foreignKeys = [ForeignKey(
        entity = Project::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Tree(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long,
    val scientificName: String,
    val commonName: String,
    val height: Double,
    val width: Double,
    val canopy: Double? = null,
    val latitude: Double,
    val longitude: Double,
    val carbonSequestration: Double,
    val imagePath: String? = null,
    val captureDate: Long = System.currentTimeMillis()
)

// Species Entity
@Entity(tableName = "species")
data class Species(
    @PrimaryKey
    val scientificName: String,
    val commonName: String,
    val carbonFactor: Double
)

// Project Summary for UI
data class ProjectSummary(
    val project: Project,
    val treeCount: Int,
    val avgHeight: Double,
    val avgWidth: Double,
    val totalCarbon: Double
)