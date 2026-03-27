package com.amshu.expensesense

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.widget.Button
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Color
import android.util.Log


class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var fabScanBill: FloatingActionButton
    
    private val apiKey = BuildConfig.GEMINI_API_KEY
    
    // Focus Overlay Views
    private lateinit var focusOverlay: android.view.ViewGroup
    private lateinit var focusDimView: View
    private lateinit var focusedCardContainer: android.view.ViewGroup
    
    // Loading Overlay Views
    private lateinit var llLoadingOverlay: View

    private lateinit var overlayActionsContainer: android.view.ViewGroup
    private lateinit var btnOverlayEdit: Button
    private lateinit var btnOverlayDelete: Button

    // State for restoring card
    private var originalCardView: View? = null
    private var originalCardPosition = IntArray(2)
    private var clonedCardView: View? = null

    // Callback set by HomeFragment so FAB routes through it
    private var addExpenseAction: (() -> Unit)? = null

    fun setAddExpenseLauncher(action: (() -> Unit)?) {
        addExpenseAction = action
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNavigationView = findViewById(R.id.bottomNavigationView)
        fabAdd = findViewById(R.id.fabAdd)

        focusOverlay = findViewById(R.id.focusOverlay)
        focusDimView = findViewById(R.id.focusDimView)
        focusedCardContainer = findViewById(R.id.focusedCardContainer)
        
        overlayActionsContainer = findViewById(R.id.overlayActionsContainer)
        btnOverlayEdit = findViewById(R.id.btnOverlayEdit)
        btnOverlayDelete = findViewById(R.id.btnOverlayDelete)
        fabScanBill = findViewById(R.id.fabScanBill)
        llLoadingOverlay = findViewById(R.id.llLoadingOverlay)

        focusDimView.setOnClickListener { hideCardFocus() }


        // Load default fragment and apply home nav state
        if (savedInstanceState == null) {
            updateStatusBar(false)
            applyHomeNav()
            loadFragment(HomeFragment())
        }

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    updateStatusBar(false)
                    applyHomeNav()
                    loadFragment(HomeFragment())
                    true
                }
                R.id.nav_analytics -> {
                    updateStatusBar(false)
                    applyNormalNav()
                    loadFragment(AnalyticsFragment())
                    true
                }
                R.id.nav_wallet -> {
                    updateStatusBar(true)
                    applyNormalNav()
                    loadFragment(WalletFragment())
                    true
                }
                R.id.nav_profile -> {
                    updateStatusBar(true)
                    applyNormalNav()
                    loadFragment(ProfileFragment())
                    true
                }
                R.id.nav_placeholder -> {
                    false
                }
                else -> false
            }
        }

        // FAB delegates to HomeFragment when available, otherwise opens AddExpenseActivity directly
        fabAdd.setOnClickListener {
            addExpenseAction?.invoke()
                ?: startActivity(Intent(this, AddExpenseActivity::class.java))
        }

        fabScanBill.setOnClickListener { 
            // Click guard: only allow scan if on Home screen
            val isHome = bottomNavigationView.selectedItemId == R.id.nav_home
            if (isHome) {
                showBillScanOptions() 
            }
        }

        initBillScanning()

        // Handle Back Press for Card Focus Overlay
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (focusOverlay.visibility == View.VISIBLE) {
                    hideCardFocus()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    private lateinit var scannerLauncher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>
    private lateinit var galleryLauncher: androidx.activity.result.ActivityResultLauncher<String>

    private fun initBillScanning() {
        scannerLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                scanResult?.pages?.get(0)?.imageUri?.let { uri: Uri ->
                    showLoading()
                    processImageUri(uri)
                } ?: onScanFailure()
            }
        }

        galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { 
                showLoading()
                processImageUri(it) 
            }
        }
    }

    private fun showBillScanOptions() {
        val options = arrayOf("Scan Bill", "Choose from Gallery")
        android.app.AlertDialog.Builder(this)
            .setTitle("Add Expense automatically")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startScanner()
                    1 -> galleryLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun startScanner() {
        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(SCANNER_MODE_FULL)
            .setResultFormats(RESULT_FORMAT_JPEG)
            .build()

        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(this)
            .addOnSuccessListener { intentSender: android.content.IntentSender ->
                scannerLauncher.launch(
                    IntentSenderRequest.Builder(intentSender).build()
                )
            }
            .addOnFailureListener { onScanFailure() }
    }

    private fun processImageUri(uri: Uri) {
        Log.d("AI_DEBUG", "Image path: $uri")
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val image = InputImage.fromFilePath(this, uri)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val rawText = visionText.text
                    if (rawText.isNotEmpty()) {
                        parseWithGemini(rawText)
                    } else {
                        Log.e("AI_DEBUG", "API FAILED: OCR failed: No text detected")
                        onScanFailure("OCR failed: No text detected")
                    }
                }
                .addOnFailureListener { e -> 
                    Log.e("AI_DEBUG", "Crash: " + e.message, e)
                    Log.e("AI_DEBUG", "API FAILED: OCR failed: ${e.message}", e)
                    onScanFailure("OCR failed: ${e.message}") 
                }
        } catch (e: Exception) {
            Log.e("AI_DEBUG", "Crash: " + e.message, e)
            Log.e("AI_DEBUG", "API FAILED: Process error: ${e.message}", e)
            onScanFailure("Process error: ${e.message}")
        }
    }

    private fun parseWithGemini(rawText: String) {
        try {
            Log.d("AI_DEBUG", "API KEY: " + BuildConfig.GEMINI_API_KEY)
            Log.d("AI_DEBUG", "API KEY LENGTH: " + (if (BuildConfig.GEMINI_API_KEY != null) BuildConfig.GEMINI_API_KEY.length else "null"))
            
            Log.d("AI_DEBUG", "Internet available: " + NetworkUtils.isInternetAvailable(this))
            if (!NetworkUtils.isInternetAvailable(this)) {
                hideLoading()
                onScanFailure("No internet connection. Please check your network and try again.")
                return
            }

            Log.d("AI_DEBUG", "OCR text: $rawText")

            val client = OkHttpClient()
            val mediaType = "application/json; charset=utf-8".toMediaType()
            
            val prompt = "Extract data from this receipt text and return ONLY a JSON object with keys: vendor (String), total_amount (Number), date (YYYY-MM-DD), and category (Groceries, Dinner, Drinks, Travel, Fuel, Shopping, Bills, Subscriptions, Other).\n\nReceipt Text: $rawText"
            
            val apiKey = BuildConfig.GEMINI_API_KEY
            val jsonBody = JSONObject().apply {
                put("contents", org.json.JSONArray().put(JSONObject().apply {
                    put("parts", org.json.JSONArray().put(JSONObject().apply {
                        put("text", prompt)
                    }))
                }))
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                .post(jsonBody.toString().toRequestBody(mediaType))
                .build()
        
            val startTime = System.currentTimeMillis()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Log.d("AI_DEBUG", "Starting API request")
                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string()
                    
                    Log.d("AI_DEBUG", "API Time: " + (System.currentTimeMillis() - startTime) + "ms")
                    
                    if (response.isSuccessful && responseBody != null) {
                        Log.d("AI_DEBUG", "API Success Response: $responseBody")
                        
                        val json = JSONObject(responseBody)
                        val candidates = json.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val textResponse = candidates
                                .optJSONObject(0)
                                ?.optJSONObject("content")
                                ?.optJSONArray("parts")
                                ?.optJSONObject(0)
                                ?.optString("text")
                            
                            if (textResponse != null) {
                                // Clean markdown backticks just in case
                                val cleanedText = textResponse.trim()
                                    .removeSurrounding("```json", "```")
                                    .removeSurrounding("```")
                                    .trim()
                                    
                                val resultData = JSONObject(cleanedText)
                                withContext(Dispatchers.Main) {
                                    hideLoading()
                                    launchAddExpense(resultData)
                                }
                            } else {
                                Log.e("AI_DEBUG", "API FAILED: textResponse is null")
                                withContext(Dispatchers.Main) { 
                                    hideLoading()
                                    onScanFailure("AI failed: Unexpected response format") 
                                }
                            }
                        } else {
                            Log.e("AI_DEBUG", "API FAILED: candidates is null or empty")
                            withContext(Dispatchers.Main) { 
                                hideLoading()
                                onScanFailure("AI failed: No response from Gemini") 
                            }
                        }
                    } else {
                        Log.e("AI_DEBUG", "API FAILED: Response code: " + response.code)
                        Log.e("AI_DEBUG", "API FAILED: Error body: " + responseBody)
                        val errorMsg = response.message
                        withContext(Dispatchers.Main) { 
                            hideLoading()
                            onScanFailure("API Request Failed: $errorMsg") 
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AI_DEBUG", "Crash: " + e.message, e)
                    Log.e("AI_DEBUG", "API FAILED: " + e.message, e)
                    Log.d("AI_DEBUG", "API Time: " + (System.currentTimeMillis() - startTime) + "ms")
                    withContext(Dispatchers.Main) { 
                        hideLoading()
                        onScanFailure("AI Logic Error: ${e.message}") 
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AI_DEBUG", "Crash: " + e.message, e)
            Log.e("AI_DEBUG", "API FAILED: " + e.message, e)
        }
    }

    private fun launchAddExpense(data: JSONObject) {
        val intent = Intent(this, AddExpenseActivity::class.java).apply {
            putExtra("title", data.optString("vendor", ""))
            putExtra("amount", data.optDouble("total_amount", 0.0))
            putExtra("category", data.optString("category", "Other"))
            
            val dateStr = data.optString("date", "")
            if (dateStr.isNotEmpty()) {
                try {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    val date = sdf.parse(dateStr)
                    putExtra("timestamp", date?.time ?: System.currentTimeMillis())
                } catch (e: Exception) {
                    putExtra("timestamp", System.currentTimeMillis())
                }
            }
        }
        startActivity(intent)
    }

    private fun onScanFailure(message: String = "AI extraction failed. Please enter manually.") {
        Log.e("AI_DEBUG", "onScanFailure: $message")
        hideLoading()
        Toast.makeText(this, "Scan failed. Try again.", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, AddExpenseActivity::class.java)
        startActivity(intent)
    }



    private fun applyHomeNav() {
        if (bottomNavigationView.menu.size() != 5) {
            val currentId = bottomNavigationView.selectedItemId
            bottomNavigationView.menu.clear()
            bottomNavigationView.inflateMenu(R.menu.bottom_nav_menu_home)
            bottomNavigationView.selectedItemId = R.id.nav_home
            bottomNavigationView.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_home -> {
                        updateStatusBar(false)
                        applyHomeNav(); loadFragment(HomeFragment()); true
                    }
                    R.id.nav_analytics -> {
                        updateStatusBar(false)
                        applyNormalNav(); loadFragment(AnalyticsFragment()); true
                    }
                    R.id.nav_wallet -> {
                        updateStatusBar(true)
                        applyNormalNav(); loadFragment(WalletFragment()); true
                    }
                    R.id.nav_profile -> {
                        updateStatusBar(true)
                        applyNormalNav(); loadFragment(ProfileFragment()); true
                    }
                    R.id.nav_placeholder -> false
                    else -> false
                }
            }
        }
        showFab()
    }

    private fun applyNormalNav() {
        if (bottomNavigationView.menu.size() != 4) {
            bottomNavigationView.menu.clear()
            bottomNavigationView.inflateMenu(R.menu.bottom_nav_menu)
            bottomNavigationView.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_home -> {
                        updateStatusBar(false)
                        applyHomeNav(); loadFragment(HomeFragment()); true
                    }
                    R.id.nav_analytics -> {
                        updateStatusBar(false)
                        applyNormalNav(); loadFragment(AnalyticsFragment()); true
                    }
                    R.id.nav_wallet -> {
                        updateStatusBar(true)
                        applyNormalNav(); loadFragment(WalletFragment()); true
                    }
                    R.id.nav_profile -> {
                        updateStatusBar(true)
                        applyNormalNav(); loadFragment(ProfileFragment()); true
                    }
                    else -> false
                }
            }
        }
        hideFab()
    }

    private fun updateStatusBar(isHeaderMatch: Boolean) {
        val window = window
        val decorView = window.decorView
        val wic = androidx.core.view.WindowInsetsControllerCompat(window, decorView)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        
        if (isHeaderMatch) {
            window.statusBarColor = Color.parseColor("#2ABFBF")
            wic.isAppearanceLightStatusBars = false
        } else {
            window.statusBarColor = Color.WHITE
            wic.isAppearanceLightStatusBars = true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        addExpenseAction = null
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.fragment_enter, R.anim.fragment_exit)
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun showFab() {
        listOf(fabAdd, fabScanBill).forEach { fab ->
            fab.scaleX = 0f
            fab.scaleY = 0f
            fab.visibility = View.VISIBLE
            fab.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(320)
                .setInterpolator(OvershootInterpolator(1.6f))
                .start()
        }
    }

    private fun hideFab() {
        listOf(fabAdd, fabScanBill).forEach { fab ->
            fab.animate()
                .scaleX(0f)
                .scaleY(0f)
                .setDuration(230)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction { fab.visibility = View.GONE }
                .start()
        }
    }

    fun showCardFocus(cardView: View, model: CardUIModel?, balanceDisplay: String, onEdit: () -> Unit, onDelete: () -> Unit) {
        if (clonedCardView != null) return
        if (focusOverlay.visibility == View.VISIBLE) return

        originalCardView = cardView
        cardView.getLocationOnScreen(originalCardPosition)

        focusOverlay.visibility = View.VISIBLE
        focusDimView.animate().alpha(1f).setDuration(300).start()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blur = RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP)
            findViewById<View>(R.id.fragmentContainer).setRenderEffect(blur)
            findViewById<View>(R.id.bottomNavigationView).setRenderEffect(blur)
        }

        val inflater = android.view.LayoutInflater.from(this)
        val clone = inflater.inflate(R.layout.item_card, focusedCardContainer, false)
        clonedCardView = clone

        // Bind data... (same binding logic)
        clone.findViewById<android.widget.TextView>(R.id.tvBalanceDisplay).text = balanceDisplay
        // (Other binding omitted for brevity but should be kept in full write)
        
        // I'll keep the full binding from the view_file to be safe
        val ivCardBg: android.widget.ImageView = clone.findViewById(R.id.ivCardBg)
        val tvCardNumber: android.widget.TextView = clone.findViewById(R.id.tvCardNumberDisplay)
        val tvCardHolder: android.widget.TextView = clone.findViewById(R.id.tvCardHolderDisplay)
        val tvBalance: android.widget.TextView = clone.findViewById(R.id.tvBalanceDisplay)

        if (model != null) {
            when (model) {
                is CardUIModel.Credit -> {
                    val drawName = model.drawableName
                    val resId = if (!drawName.isNullOrEmpty()) resources.getIdentifier(drawName, "drawable", packageName) else 0
                    ivCardBg.setImageResource(if (resId != 0) resId else R.drawable.defaultcreditcard)
                }
                is CardUIModel.Debit -> {
                    val drawName = model.drawableName
                    val resId = if (!drawName.isNullOrEmpty()) resources.getIdentifier(drawName, "drawable", packageName) else 0
                    ivCardBg.setImageResource(if (resId != 0) resId else R.drawable.defaultdebitcard)
                }
            }
            tvBalance.text = balanceDisplay
            tvCardNumber.text = maskCardNumber(model.cardNumber)
            tvCardHolder.text = model.cardHolderName.uppercase()
        }

        val lp = android.widget.FrameLayout.LayoutParams(cardView.width, cardView.height)
        focusedCardContainer.addView(clone, lp)
        clone.translationX = originalCardPosition[0].toFloat()
        clone.translationY = originalCardPosition[1].toFloat()
        cardView.setHasTransientState(true)
        cardView.animate().alpha(0f).setDuration(150).start()

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val centerX = (screenWidth - cardView.width) / 2f
        val centerY = (screenHeight - cardView.height) / 2f - 100f
        
        clone.animate()
            .translationX(centerX)
            .translationY(centerY)
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(450)
            .setInterpolator(OvershootInterpolator(1.2f))
            .withStartAction { clone.elevation = 150f }
            .withEndAction { showFocusActions(onEdit, onDelete) }
            .start()
    }

    private fun showFocusActions(onEdit: () -> Unit, onDelete: () -> Unit) {
        overlayActionsContainer.visibility = View.VISIBLE
        overlayActionsContainer.alpha = 0f
        overlayActionsContainer.translationY = 50f
        overlayActionsContainer.animate().alpha(1f).translationY(0f).setDuration(400).start()
        btnOverlayEdit.setOnClickListener { onEdit(); hideCardFocus() }
        btnOverlayDelete.setOnClickListener { deleteFocusedCard(onDelete) }
    }

    private fun deleteFocusedCard(onDelete: () -> Unit) {
        val clone = clonedCardView ?: return
        clone.animate().translationXBy(20f).setDuration(50).withEndAction {
            clone.animate().translationXBy(-40f).setDuration(50).withEndAction {
                clone.animate().translationXBy(20f).setDuration(50).withEndAction {
                    clone.animate().scaleX(0.5f).scaleY(0.5f).alpha(0f).translationYBy(300f).setDuration(400).withEndAction {
                        onDelete(); resetOverlayState(true)
                    }.start()
                    overlayActionsContainer.animate().alpha(0f).translationY(50f).setDuration(200).start()
                }.start()
            }.start()
        }.start()
    }

    private fun resetOverlayState(isDeletion: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            findViewById<View>(R.id.fragmentContainer).setRenderEffect(null)
            findViewById<View>(R.id.bottomNavigationView).setRenderEffect(null)
        }
        focusDimView.animate().alpha(0f).setDuration(300).withEndAction {
            focusOverlay.visibility = View.GONE
            overlayActionsContainer.visibility = View.GONE
        }.start()
        clonedCardView?.let { focusedCardContainer.removeView(it) }
        clonedCardView = null
        if (!isDeletion) originalCardView?.alpha = 1f
        originalCardView?.setHasTransientState(false)
        originalCardView = null
    }

    private fun maskCardNumber(number: String): String {
        if (number.length < 4) return number
        return "**** ${number.takeLast(4)}"
    }

    fun hideCardFocus() {
        val clone = clonedCardView ?: return
        val originalView = originalCardView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            findViewById<View>(R.id.fragmentContainer).setRenderEffect(null)
            findViewById<View>(R.id.bottomNavigationView).setRenderEffect(null)
        }
        focusDimView.animate().alpha(0f).setDuration(300).withEndAction {
            focusOverlay.visibility = View.GONE
            overlayActionsContainer.visibility = View.GONE
        }.start()
        overlayActionsContainer.animate().alpha(0f).translationY(50f).setDuration(200).start()
        originalView?.animate()?.alpha(1f)?.setDuration(350)?.start()
        clone.animate()
            .translationX(originalCardPosition[0].toFloat())
            .translationY(originalCardPosition[1].toFloat())
            .scaleX(1f).scaleY(1f).alpha(0f)
            .setDuration(350).setInterpolator(AccelerateInterpolator())
            .withEndAction {
                originalView?.visibility = View.VISIBLE
                originalView?.setHasTransientState(false)
                focusedCardContainer.removeView(clone)
                clonedCardView = null
                originalCardView = null
            }.start()
    }

    private fun showLoading() {
        llLoadingOverlay.visibility = View.VISIBLE
        llLoadingOverlay.alpha = 0f
        llLoadingOverlay.animate().alpha(1f).setDuration(300).start()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurEffect = RenderEffect.createBlurEffect(15f, 15f, Shader.TileMode.CLAMP)
            findViewById<View>(R.id.fragmentContainer).setRenderEffect(blurEffect)
            findViewById<View>(R.id.bottomNavigationView).setRenderEffect(blurEffect)
            findViewById<View>(R.id.fabAdd).setRenderEffect(blurEffect)
            findViewById<View>(R.id.fabScanBill).setRenderEffect(blurEffect)
        }
    }

    private fun hideLoading() {
        llLoadingOverlay.visibility = View.GONE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            findViewById<View>(R.id.fragmentContainer).setRenderEffect(null)
            findViewById<View>(R.id.bottomNavigationView).setRenderEffect(null)
            findViewById<View>(R.id.fabAdd).setRenderEffect(null)
            findViewById<View>(R.id.fabScanBill).setRenderEffect(null)
        }
    }
}