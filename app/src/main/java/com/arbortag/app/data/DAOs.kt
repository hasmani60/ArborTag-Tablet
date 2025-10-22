package com.arbortag.app.data

import androidx.room.*

@Dao
interface ProjectDao {
    @Insert
    suspend fun insert(project: Project): Long

    @Update
    suspend fun update(project: Project)

    @Delete
    suspend fun delete(project: Project)

    @Query("SELECT * FROM projects ORDER BY lastModified DESC")
    suspend fun getAllProjects(): List<Project>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: Long): Project?
}

@Dao
interface TreeDao {
    @Insert
    suspend fun insert(tree: Tree): Long

    @Update
    suspend fun update(tree: Tree)

    @Delete
    suspend fun delete(tree: Tree)

    @Query("SELECT * FROM trees WHERE projectId = :projectId")
    suspend fun getTreesByProject(projectId: Long): List<Tree>

    @Query("SELECT COUNT(*) FROM trees WHERE projectId = :projectId")
    suspend fun getTreeCountByProject(projectId: Long): Int

    @Query("SELECT AVG(height) FROM trees WHERE projectId = :projectId")
    suspend fun getAverageHeight(projectId: Long): Double?

    @Query("SELECT AVG(width) FROM trees WHERE projectId = :projectId")
    suspend fun getAverageWidth(projectId: Long): Double?

    @Query("SELECT SUM(carbonSequestration) FROM trees WHERE projectId = :projectId")
    suspend fun getTotalCarbon(projectId: Long): Double?
}

@Dao
interface SpeciesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(species: List<Species>)

    @Query("SELECT * FROM species ORDER BY commonName ASC")
    suspend fun getAllSpecies(): List<Species>

    @Query("SELECT * FROM species WHERE scientificName = :scientificName")
    suspend fun getSpeciesByScientificName(scientificName: String): Species?
}

@Dao
interface ArUcoMarkerDao {
    @Insert
    suspend fun insert(marker: ArUcoMarker): Long

    @Update
    suspend fun update(marker: ArUcoMarker)

    @Delete
    suspend fun delete(marker: ArUcoMarker)

    @Query("SELECT * FROM aruco_markers WHERE projectId = :projectId ORDER BY detectionDate DESC")
    suspend fun getMarkersByProject(projectId: Long): List<ArUcoMarker>

    @Query("SELECT * FROM aruco_markers WHERE id = :markerId")
    suspend fun getMarkerById(markerId: Long): ArUcoMarker?

    @Query("SELECT * FROM aruco_markers WHERE projectId = :projectId ORDER BY detectionDate DESC LIMIT 1")
    suspend fun getLatestMarkerByProject(projectId: Long): ArUcoMarker?

    @Query("DELETE FROM aruco_markers WHERE projectId = :projectId")
    suspend fun deleteMarkersByProject(projectId: Long)
}