package com.example.sasreportcamera // PASTIKAN NAMA PACKAGE BENAR

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "report_table")
data class ReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filePath: String,        // Lokasi absolut file JPG di HP
    val warungName: String,      // Untuk pencarian/sorting nanti
    val timestamp: Long,         // Waktu jepret (dalam milliseconds untuk sorting akurat)
    var note: String = "",       // Catatan opsional dari user
    var isMarked: Boolean = false, // Apakah ditandai centang?
    var markColor: String = ""   // Warna centang (Hex, misal: "#FF0000")
)