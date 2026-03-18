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

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
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
                Log.e("ScanCardActivity", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
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

        // 1. Extract Card Number (Match 16 digits, with or without spaces)
        val numberRegex = Regex("(\\d{4}\\s?\\d{4}\\s?\\d{4}\\s?\\d{4})|(\\d{16})")
        val numberMatch = numberRegex.find(text)
        if (numberMatch != null) {
            cardNumber = numberMatch.value.replace("\\s".toRegex(), "")
        }

        // 2. Extract Bank Name & Card Type
        val banks = listOf("ICICI", "HDFC", "SBI", "AXIS", "KOTAK", "PNB", "YES BANK", "HSBC", "CITI", "BOB", "CANARA")
        for (line in lines) {
            val upperLine = line.uppercase()
            
            // Look for Bank name
            for (bank in banks) {
                if (upperLine.contains(bank)) {
                    bankName = bank
                    break
                }
            }

            if (upperLine.contains("CREDIT")) cardType = "Credit Card"
            if (upperLine.contains("DEBIT")) cardType = "Debit Card"
        }

        // 3. Extract Name (Heuristic: Longer uppercase line that isn't a bank or type)
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.length > 5 && trimmed == trimmed.uppercase() && 
                !trimmed.contains("BANK") && !trimmed.contains("VISA") && 
                !trimmed.contains("MASTER") && !trimmed.contains("VALID") &&
                !trimmed.contains("DEBIT") && !trimmed.contains("CREDIT")) {
                cardHolder = trimmed
                break
            }
        }

        // If we found at least a number, consider it a success for now
        if (cardNumber != null) {
            val intent = Intent()
            intent.putExtra("cardHolder", cardHolder ?: "CARD HOLDER")
            intent.putExtra("cardNumber", cardNumber)
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
