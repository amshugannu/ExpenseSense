package com.amshu.expensesense

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

// Reconciliation Feature Entry Point
class StatementReconciliationActivity : AppCompatActivity() {

    private lateinit var layoutInitial: View
    private lateinit var overlayProcessing: View
    private lateinit var layoutResult: View
    private lateinit var tvProcessingStatus: TextView
    private lateinit var rvTransactions: RecyclerView
    private lateinit var btnUploadStatement: MaterialButton

    private lateinit var adapter: ReconciliationAdapter
    private val viewModel: ReconciliationViewModel by viewModels()

    // Developer Debug Toggle
    private val isDebugMode = true

    private lateinit var debugSectionRaw: View
    private lateinit var debugSectionA: View
    private lateinit var debugSectionB: View
    private lateinit var debugSectionC: View
    private lateinit var debugSectionD: View
    private lateinit var debugDivider: View
    private lateinit var tvDebugRawSource: TextView
    private lateinit var tvDebugRawText: TextView
    private lateinit var tvDebugSectionText: TextView
    private lateinit var tvDebugBlocks: TextView
    private lateinit var tvDebugChunks: TextView
    private lateinit var tvDebugResponses: TextView

    private val pickPdfLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                Log.d("Recon", "PDF selected: $uri")
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Ignore if persistence fails, we still have temporary access
                }
                processPdf(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statement_reconciliation)

        // Initialize PDFBox
        PDFBoxResourceLoader.init(applicationContext)

        // Bind UI Components
        layoutInitial = findViewById(R.id.layoutInitial)
        overlayProcessing = findViewById(R.id.overlayProcessing)
        layoutResult = findViewById(R.id.layoutResult)
        tvProcessingStatus = findViewById(R.id.tvProcessingStatus)
        rvTransactions = findViewById(R.id.rvTransactions)
        btnUploadStatement = findViewById(R.id.btnUploadStatement)

        // Bind Debug Components
        debugSectionRaw = findViewById(R.id.debugSectionRaw)
        debugSectionA = findViewById(R.id.debugSectionA)
        debugSectionB = findViewById(R.id.debugSectionB)
        debugSectionC = findViewById(R.id.debugSectionC)
        debugSectionD = findViewById(R.id.debugSectionD)
        debugDivider = findViewById(R.id.debugDivider)
        tvDebugRawSource = findViewById(R.id.tvDebugRawSource)
        tvDebugRawText = findViewById(R.id.tvDebugRawText)
        tvDebugSectionText = findViewById(R.id.tvDebugSectionText)
        tvDebugBlocks = findViewById(R.id.tvDebugBlocks)
        tvDebugChunks = findViewById(R.id.tvDebugChunks)
        tvDebugResponses = findViewById(R.id.tvDebugResponses)

        // Toggle Debug View Visibility
        if (isDebugMode) {
            debugSectionRaw.visibility = View.VISIBLE
            debugSectionA.visibility = View.VISIBLE
            debugSectionB.visibility = View.VISIBLE
            debugSectionC.visibility = View.VISIBLE
            debugSectionD.visibility = View.VISIBLE
            debugDivider.visibility = View.VISIBLE
        }

        // Setup RecyclerView
        adapter = ReconciliationAdapter(emptyList())
        rvTransactions.layoutManager = LinearLayoutManager(this)
        rvTransactions.adapter = adapter

        // Setup ViewModel Observer
        viewModel.parsingState.observe(this) { state ->
            when (state) {
                is ParsingState.Idle -> hideProcessing()
                is ParsingState.Loading -> showProcessing("Analyzing transactions via AI...")
                is ParsingState.Success -> {
                    hideProcessing()
                    showResultView(state.transactions)
                }
                is ParsingState.Error -> {
                    hideProcessing()
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        // Setup Debug Observers
        if (isDebugMode) {
            viewModel.debugRawText.observe(this) { text ->
                tvDebugRawText.text = text
            }
            viewModel.debugRawSource.observe(this) { source ->
                tvDebugRawSource.text = source
            }
            viewModel.debugSectionText.observe(this) { text ->
                tvDebugSectionText.text = text
            }
            viewModel.debugBlocksText.observe(this) { text ->
                tvDebugBlocks.text = text
            }
            viewModel.debugChunksText.observe(this) { chunks ->
                tvDebugChunks.text = chunks
            }
            viewModel.debugResponsesText.observe(this) { responses ->
                tvDebugResponses.text = responses
            }
        }

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            // Safely return to main screen
            finish()
        }

        btnUploadStatement.setOnClickListener {
            pickPdfLauncher.launch(arrayOf("application/pdf"))
        }
    }

    private fun processPdf(uri: Uri, password: String? = null) {
        showProcessing("Opening document...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val document: PDDocument = try {
                        if (password != null) {
                            PDDocument.load(inputStream, password)
                        } else {
                            PDDocument.load(inputStream)
                        }
                    } catch (e: InvalidPasswordException) {
                        Log.d("Recon", "Password required for PDF")
                        withContext(Dispatchers.Main) {
                            hideProcessing()
                            if (password != null) {
                                Toast.makeText(
                                    this@StatementReconciliationActivity,
                                    "Incorrect password. Try again.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                Log.d("Recon", "Password incorrect")
                            }
                            showPasswordDialog(uri)
                        }
                        return@launch
                    }

                    if (password != null) {
                        Log.d("Recon", "Password correct")
                    }

                    Log.d("Recon", "Text extraction started")
                    withContext(Dispatchers.Main) {
                        tvProcessingStatus.text = "Extracting text..."
                    }

                    val stripper = PDFTextStripper()
                    val extractedText = stripper.getText(document) ?: ""

                    // Condition: If extracted text is EMPTY or too small (< threshold)
                    if (extractedText.trim().length < 50) {
                        Log.d("Recon", "OCR fallback triggered. Base text length: ${extractedText.trim().length}")
                        
                        // Save unencrypted copy for PdfRenderer OCR
                        val tempFile = File(cacheDir, "temp_reconciliation_statement.pdf")
                        document.setAllSecurityToBeRemoved(true)
                        document.save(tempFile)
                        document.close()

                        val ocrText = runOcrFallback(tempFile)
                        tempFile.delete() // Ensure cleanup

                        viewModel.updateRawDebug(ocrText, "OCR")
                        viewModel.parseTransactions(ocrText)
                    } else {
                        Log.d("Recon", "Extraction completed via PDFBox")
                        document.close()
                        viewModel.updateRawDebug(extractedText, "PDF Text Extraction")
                        viewModel.parseTransactions(extractedText)
                    }
                }
            } catch (e: Exception) {
                Log.e("Recon", "Error processing PDF", e)
                withContext(Dispatchers.Main) {
                    hideProcessing()
                    Toast.makeText(
                        this@StatementReconciliationActivity,
                        "Unable to read this PDF: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private suspend fun runOcrFallback(file: File): String {
        return withContext(Dispatchers.IO) {
            val resultBuilder = java.lang.StringBuilder()
            try {
                val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val pdfRenderer = PdfRenderer(fileDescriptor)
                val pageCount = pdfRenderer.pageCount

                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                if (pageCount == 0) {
                    pdfRenderer.close()
                    fileDescriptor.close()
                    return@withContext "No readable text found"
                }

                // Loop through all pages sequentially to limit memory
                for (i in 0 until pageCount) {
                    withContext(Dispatchers.Main) {
                        tvProcessingStatus.text = "Running OCR... (Page ${i + 1} of $pageCount)"
                    }

                    val page = pdfRenderer.openPage(i)
                    
                    // Scale limit: 2x density is a safe bet for OCR readability without OOM
                    val width = (page.width * 2f).toInt()
                    val height = (page.height * 2f).toInt()
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    
                    // Erase to white background
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val image = InputImage.fromBitmap(bitmap, 0)

                    // Await OCR asynchronously before proceeding to next page
                    val pageText = suspendCoroutine<String> { cont ->
                        recognizer.process(image)
                            .addOnSuccessListener { visionText ->
                                cont.resume(visionText.text)
                            }
                            .addOnFailureListener {
                                cont.resume("")
                            }
                    }

                    resultBuilder.append(pageText).append("\n\n")
                    
                    // Release memory immediately
                    bitmap.recycle()
                }

                pdfRenderer.close()
                fileDescriptor.close()

                Log.d("Recon", "Extraction completed via OCR Fallback")
                
                val finalOcr = resultBuilder.toString().trim()
                if (finalOcr.isEmpty()) "No readable text found" else finalOcr

            } catch (e: Exception) {
                Log.e("Recon", "OCR Fallback failed", e)
                "OCR processing failed: ${e.message}"
            }
        }
    }

    private fun showPasswordDialog(uri: Uri) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Password Required")
        builder.setMessage("This PDF is protected. Enter password to continue.")

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        input.layoutParams = lp
        builder.setView(input)

        builder.setPositiveButton("Unlock") { dialog, _ ->
            val pwd = input.text.toString()
            processPdf(uri, pwd) // Kept in memory only
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }
        
        // Prevent dialog cancel by clicking outside to enforce explicit UI flow
        builder.setCancelable(false)
        builder.show()
    }

    private fun showResultView(transactions: List<ReconciliationTransaction>) {
        layoutInitial.visibility = View.GONE
        layoutResult.visibility = View.VISIBLE
        adapter.updateData(transactions)
    }

    private fun showProcessing(message: String) {
        overlayProcessing.visibility = View.VISIBLE
        tvProcessingStatus.text = message
    }

    private fun hideProcessing() {
        overlayProcessing.visibility = View.GONE
    }
}
