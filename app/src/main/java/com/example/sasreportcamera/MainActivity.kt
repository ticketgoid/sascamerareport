package com.example.sasreportcamera

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.exifinterface.media.ExifInterface
import com.example.sasreportcamera.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    var currentLocationText: String = "Mencari lokasi..."
    private var flashModeIndex = 0

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = requiredPermissions.all { permissions[it] == true }
        if (allGranted) {
            startCamera()
            startLocationTracking()
        } else {
            Toast.makeText(this, "Izin mutlak diperlukan.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (allPermissionsGranted()) {
            startCamera()
            startLocationTracking()
        } else {
            requestPermissionsLauncher.launch(requiredPermissions)
        }

        binding.btnShutter.setOnClickListener { takePhoto() }
        binding.btnFlash.setOnClickListener { toggleFlash() }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationTracking() {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) getAddressFromLocation(location.latitude, location.longitude)
                else currentLocationText = "Lokasi tidak ditemukan"
            }
            .addOnFailureListener {
                currentLocationText = "Gagal mengambil lokasi"
            }
    }

    private fun getAddressFromLocation(lat: Double, lon: Double) {
        cameraExecutor.execute {
            try {
                val geocoder = Geocoder(this@MainActivity, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)

                currentLocationText = if (!addresses.isNullOrEmpty()) {
                    addresses[0].getAddressLine(0) ?: "$lat, $lon"
                } else {
                    "$lat, $lon"
                }
            } catch (_: Exception) {
                currentLocationText = "$lat, $lon"
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e("CameraX", "Gagal inisialisasi", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(cacheDir, "TMP_STAMP_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(baseContext, "Gagal mengambil foto.", Toast.LENGTH_SHORT).show()
                }
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    runOnUiThread { showFormDialog(photoFile) }
                }
            }
        )
    }

    private fun showFormDialog(photoFile: File) {
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(R.layout.dialog_form)
        dialog.setCancelable(false)

        val etNamaWarung = dialog.findViewById<EditText>(R.id.etNamaWarung)
        val etNoWa = dialog.findViewById<EditText>(R.id.etNoWa)
        val etNamaPemilik = dialog.findViewById<EditText>(R.id.etNamaPemilik)
        val btnSimpan = dialog.findViewById<Button>(R.id.btnSimpan)

        btnSimpan?.setOnClickListener {
            val namaWarung = etNamaWarung?.text.toString().trim()
            val noWa = etNoWa?.text.toString().trim()
            val namaPemilik = etNamaPemilik?.text.toString().trim()

            if (namaWarung.isEmpty() || noWa.isEmpty()) {
                Toast.makeText(this, "Nama Warung dan No WA Wajib Diisi!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialog.dismiss()
            Toast.makeText(this, "Memproses watermarks...", Toast.LENGTH_SHORT).show()

            processAndStampImage(photoFile, namaWarung, noWa, namaPemilik)
        }

        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                showDiscardAlert(photoFile, dialog)
                return@setOnKeyListener true
            }
            false
        }
        dialog.show()
    }

    private fun showDiscardAlert(photoFile: File, formDialog: BottomSheetDialog) {
        AlertDialog.Builder(this)
            .setTitle("Buang Foto?")
            .setMessage("Data belum disimpan. Foto ini akan dihapus jika Anda kembali.")
            .setPositiveButton("Buang") { _, _ ->
                if (photoFile.exists()) photoFile.delete()
                formDialog.dismiss()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun processAndStampImage(originalFile: File, warung: String, wa: String, pemilik: String) {
        cameraExecutor.execute {
            try {
                val exif = ExifInterface(originalFile.absolutePath)
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                }

                val options = BitmapFactory.Options()
                options.inMutable = true
                var bitmap = BitmapFactory.decodeFile(originalFile.absolutePath, options)

                if (orientation != ExifInterface.ORIENTATION_NORMAL) {
                    val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    bitmap.recycle()
                    bitmap = rotatedBitmap
                }

                val canvas = Canvas(bitmap)
                val scale = bitmap.width / 1080f

                val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 32f * scale
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    setShadowLayer(4f * scale, 2f * scale, 2f * scale, Color.BLACK)
                }

                val paintBg = Paint().apply {
                    color = "#4D000000".toColorInt()
                }

                val timeStamp = SimpleDateFormat("HH:mm, dd/MM/yyyy", Locale.getDefault()).format(Date())
                val waText = if (pemilik.isNotEmpty()) "$wa ($pemilik)" else wa

                val lines = mutableListOf<String>()
                lines.add("1. $warung")
                lines.add("2. $waText")
                lines.add("3. $timeStamp")

                val alamatPrefix = "4. "
                val maxChar = 45
                val alamatChunks = currentLocationText.chunked(maxChar)
                lines.add("$alamatPrefix${alamatChunks.firstOrNull() ?: ""}")
                if (alamatChunks.size > 1) {
                    for (i in 1 until alamatChunks.size) {
                        lines.add("   ${alamatChunks[i]}")
                    }
                }

                val padding = 32f * scale
                val lineHeight = paintText.descent() - paintText.ascent()
                val totalHeight = lines.size * lineHeight
                val startY = bitmap.height - padding - totalHeight
                var currentY = startY

                var maxWidth = 0f
                for (line in lines) {
                    val w = paintText.measureText(line)
                    if (w > maxWidth) maxWidth = w
                }

                canvas.drawRect(
                    padding - (10f * scale),
                    startY - lineHeight + (8f * scale),
                    padding + maxWidth + (10f * scale),
                    bitmap.height - padding + (10f * scale),
                    paintBg
                )

                for (line in lines) {
                    canvas.drawText(line, padding, currentY, paintText)
                    currentY += lineHeight
                }

                val finalFile = File(cacheDir, "FINAL_STAMP_${System.currentTimeMillis()}.jpg")
                val outStream = FileOutputStream(finalFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outStream)
                outStream.flush()
                outStream.close()

                bitmap.recycle()

                val permanentDir = File(getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "SasReport")
                if (!permanentDir.exists()) permanentDir.mkdirs()

                val permanentFile = File(permanentDir, "REPORT_${System.currentTimeMillis()}.jpg")
                finalFile.copyTo(permanentFile, overwrite = true)

                originalFile.delete()
                finalFile.delete()

                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Tersimpan: ${permanentFile.name}", Toast.LENGTH_SHORT).show()

                    val thumbOptions = BitmapFactory.Options().apply { inSampleSize = 8 }
                    val thumbBitmap = BitmapFactory.decodeFile(permanentFile.absolutePath, thumbOptions)
                    binding.btnGallery.setImageBitmap(thumbBitmap)
                }

            } catch (e: Exception) {
                Log.e("Watermark", "Gagal memproses gambar", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Gagal memproses gambar (OOM / Error).", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun toggleFlash() {
        flashModeIndex = (flashModeIndex + 1) % 4
        when (flashModeIndex) {
            0 -> { imageCapture?.flashMode = ImageCapture.FLASH_MODE_AUTO; camera?.cameraControl?.enableTorch(false) }
            1 -> { imageCapture?.flashMode = ImageCapture.FLASH_MODE_ON; camera?.cameraControl?.enableTorch(false) }
            2 -> { imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF; camera?.cameraControl?.enableTorch(true) }
            3 -> { imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF; camera?.cameraControl?.enableTorch(false) }
        }
    }

    private fun allPermissionsGranted() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}