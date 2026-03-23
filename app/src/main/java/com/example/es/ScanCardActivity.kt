package com.example.es

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScanCardActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraPreview: PreviewView
    private lateinit var layoutScanning: LinearLayout
    private var isProcessing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_card)

        cameraPreview = findViewById(R.id.cameraPreview)
        layoutScanning = findViewById(R.id.layoutScanning)
        findViewById<ImageButton>(R.id.btnCancelScan).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnGallery).setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }


        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCamera()
            } else {
                handlePermissionDenied()
            }
        }

    private fun handlePermissionDenied() {
        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            // Permanent denial
            showSettingsDialog()
        } else {
            // Normal denial
            Toast.makeText(this, "Camera permission is required to scan cards", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun showSettingsDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("Camera permission is permanently denied. Please enable it in settings to use this feature.")
            .setPositiveButton("Settings") { _, _ ->
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(cameraPreview.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (exc: Exception) {
                // Binding failed
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            processGalleryImage(uri)
        }
    }

    private fun processGalleryImage(uri: android.net.Uri) {
        layoutScanning.visibility = View.VISIBLE
        val image: InputImage
        try {
            image = InputImage.fromFilePath(this, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val result = parseCardDetails(visionText.text)
                    if (result != null) {
                        setResult(Activity.RESULT_OK, result)
                        finish()
                    } else {
                        layoutScanning.visibility = View.GONE
                        Toast.makeText(this, "Could not detect card details", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    layoutScanning.visibility = View.GONE
                    Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            layoutScanning.visibility = View.GONE
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(imageProxy: ImageProxy) {
        if (isProcessing) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            isProcessing = true
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val result = parseCardDetails(visionText.text)
                    if (result != null) {
                        runOnUiThread {
                            setResult(Activity.RESULT_OK, result)
                            finish()
                        }
                    } else {
                        isProcessing = false
                    }
                }
                .addOnFailureListener {
                    isProcessing = false
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun parseCardDetails(text: String): Intent? {
        val lines = text.split("\n")
        var cardNumber: String? = null
        var cardHolder: String? = null
        var bankName: String? = null
        var cardType: String = "Debit Card" // Default

        // 1. Extract Card Number
        // Pattern: XXXX XXXX XXXX XXXX or XXXXXXXXXXXXXXXX
        val numberRegex = Regex("\\b(\\d{4}\\s?\\d{4}\\s?\\d{4}\\s?\\d{4})\\b")
        val fallbackRegex = Regex("\\b\\d{16}\\b")
        
        val numberMatch = numberRegex.find(text) ?: fallbackRegex.find(text)
        if (numberMatch != null) {
            cardNumber = numberMatch.value.replace("\\s".toRegex(), "")
        }

        // 2. Extract Bank Name & Card Type
        val banks = listOf("HDFC", "ICICI", "SBI", "AXIS", "KOTAK", "PNB", "YES BANK", "HSBC", "CITI", "BOB", "CANARA")
        val upperText = text.uppercase()

        for (bank in banks) {
            if (upperText.contains(bank)) {
                bankName = bank
                break
            }
        }

        if (upperText.contains("CREDIT")) cardType = "Credit"
        else if (upperText.contains("DEBIT")) cardType = "Debit"
        else if (upperText.contains("VISA") || upperText.contains("MASTERCARD") || upperText.contains("RUPAY")) {
            // Default to Debit if it's a known network but Credit not specified
            cardType = "Debit"
        }

        // 3. Extract Name (Heuristic: Uppercase words near bottom, avoiding common labels)
        val filteredLines = lines.map { it.trim() }.filter { it.length > 5 && it == it.uppercase() }
        val negativeKeywords = listOf("BANK", "VISA", "MASTERCARD", "RUPAY", "VALID", "FROM", "THRU", "DATE", "EXPIRES")
        
        for (line in filteredLines.reversed()) { // Check from bottom
            if (negativeKeywords.none { line.contains(it) } && !line.any { it.isDigit() }) {
                cardHolder = line
                break
            }
        }

        if (cardNumber != null) {
            val intent = Intent()
            intent.putExtra("cardNumber", cardNumber.takeLast(4)) // Last 4 for display/logic
            intent.putExtra("cardHolder", cardHolder ?: "CARD HOLDER")
            intent.putExtra("cardName", "${bankName ?: ""} Card".trim())
            intent.putExtra("cardType", cardType)
            intent.putExtra("bankName", bankName ?: "")
            return intent
        }

        return null
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }


    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
