package com.example.sasreportcamera // PASTIKAN NAMA PACKAGE BENAR

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertReport(report: ReportEntity)

    // Ambil semua data diurutkan dari yang terbaru (Fase 9 Galeri)
    @Query("SELECT * FROM report_table ORDER BY timestamp DESC")
    fun getAllReports(): List<ReportEntity>

    @Update
    fun updateReport(report: ReportEntity)

    @Delete
    fun deleteReport(report: ReportEntity)

    @Query("DELETE FROM report_table WHERE filePath = :path")
    fun deleteByPath(path: String)
}