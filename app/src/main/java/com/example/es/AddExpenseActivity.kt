package com.example.es

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddExpenseActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var etName: EditText
    private lateinit var etAmount: EditText
    private lateinit var tvDate: TextView
    private lateinit var tvClear: TextView
    private lateinit var layoutDate: RelativeLayout
    private lateinit var spinnerCategory: Spinner
    private lateinit var btnAddExpense: MaterialButton

    private val calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_expense)

        btnBack       = findViewById(R.id.btnBack)
        etName        = findViewById(R.id.etName)
        etAmount      = findViewById(R.id.etAmount)
        tvDate        = findViewById(R.id.tvDate)
        tvClear       = findViewById(R.id.tvClear)
        layoutDate    = findViewById(R.id.layoutDate)
        spinnerCategory = findViewById(R.id.spinnerCategory)
        btnAddExpense = findViewById(R.id.btnAddExpense)

        // Populate category spinner
        val categories = resources.getStringArray(R.array.transaction_categories)
        spinnerCategory.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            categories
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // Pre-fill today's date
        updateDateLabel()

        btnBack.setOnClickListener { finish() }

        tvClear.setOnClickListener {
            etAmount.setText("")
            etAmount.requestFocus()
        }

        layoutDate.setOnClickListener { showDatePicker() }
        tvDate.setOnClickListener    { showDatePicker() }

        btnAddExpense.setOnClickListener { submitExpense() }

        // Setup AI Auto-categorization
        etName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val expenseDesc = etName.text.toString().trim()
                if (expenseDesc.isNotEmpty()) {
                    autoCategorizeExpense(expenseDesc, categories)
                }
            }
        }
    }

    private fun autoCategorizeExpense(description: String, categories: Array<String>) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey == "PLACE_YOUR_API_KEY_HERE" || apiKey.isBlank()) {
            return // Don't try to call if key isn't set yet
        }

        Toast.makeText(this, "✨ AI organizing...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val generativeModel = GenerativeModel(
                    modelName = "gemini-1.5-flash",
                    apiKey = apiKey
                )

                val prompt = """
                    You are exactly a categorization engine. You must output NOTHING except a single category name from the strict list provided.
                    Do not write any introductory text, no explanations, no punctuation, and no JSON. Just the category word exactly as it appears in the list.

                    List of allowed categories:
                    Games, Movies, Sports, Dinner, Groceries, Drinks, Household supplies, Maintainance, Rent, Medical, Taxes, Insurances, Gifts, Travel, Fuel, Internet, Gas, other

                    Input description to categorize: "$description"
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                val rawResponse = response.text ?: ""
                
                // Clean the response from any accidental punctuation or whitespace
                val aiCategory = rawResponse.replace(Regex("[^a-zA-Z ]"), "").trim()

                withContext(Dispatchers.Main) {
                    // For debugging, show exactly what Gemini returned
                    // Toast.makeText(this@AddExpenseActivity, "Gemini said: [$aiCategory]", Toast.LENGTH_SHORT).show()
                    
                    val index = categories.indexOfFirst { it.equals(aiCategory, ignoreCase = true) }
                    if (index >= 0) {
                        spinnerCategory.setSelection(index)
                        Toast.makeText(this@AddExpenseActivity, "✨ Auto-selected: $aiCategory", Toast.LENGTH_SHORT).show()
                    } else {
                        // Fallback logic as requested
                        val fallbackIndex = categories.indexOf("Household supplies")
                        if (fallbackIndex >= 0) spinnerCategory.setSelection(fallbackIndex)
                        Toast.makeText(this@AddExpenseActivity, "Gemini returned unknown category: '$aiCategory'", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddExpenseActivity, "AI Error: ${e.message}", Toast.LENGTH_LONG).show()
                    val index = categories.indexOf("Household supplies")
                    if (index >= 0) spinnerCategory.setSelection(index)
                }
            }
        }
    }

    private fun showDatePicker() {
        DatePickerDialog(
            this,
            { _, year, month, day ->
                calendar.set(year, month, day)
                updateDateLabel()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateDateLabel() {
        val sdf = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
        tvDate.text = sdf.format(calendar.time)
    }

    private fun submitExpense() {
        val name   = etName.text.toString().trim()
        val amtStr = etAmount.text.toString().trim()

        if (name.isEmpty()) {
            etName.error = "Enter expense name"
            etName.requestFocus()
            return
        }
        if (amtStr.isEmpty()) {
            etAmount.error = "Enter amount"
            etAmount.requestFocus()
            return
        }
        val amount = amtStr.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            etAmount.error = "Enter a valid amount"
            etAmount.requestFocus()
            return
        }

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || user.email == null) {
            Toast.makeText(this, "Not logged in or missing email", Toast.LENGTH_SHORT).show()
            return
        }

        val username  = user.email!!.substringBefore("@")
        val category  = spinnerCategory.selectedItem.toString()
        val timestamp = calendar.timeInMillis   // use selected date millis

        // To prevent exact titles (like "Pizza") from overwriting each other in Firebase, 
        // we append the timestamp to the node key.
        val uniqueTitleKey = "$name-$timestamp"

        val databaseRef = FirebaseDatabase.getInstance()
            .getReference("users/$username/expenses/$category/$uniqueTitleKey")
        
        // This is exactly what is saved under the Title node in Firebase
        val firebaseExpenseData = mapOf(
            "timestamp" to timestamp,
            "amount" to amount
        )

        // The local Room DB object (keeps all fields for UI rendering)
        val transaction = Transaction(
            title      = name,    // The clean title for the UI
            amount     = amount,
            category   = category,
            timestamp  = timestamp,
            firebaseId = uniqueTitleKey // We store the unique key so we can delete it later
        )

        // Save to Firebase with listeners to catch errors
        databaseRef.setValue(firebaseExpenseData)
            .addOnSuccessListener {
                // Save to Room on background thread, then close activity
                Thread {
                    AppDatabase.getDatabase(this@AddExpenseActivity)
                        .transactionDao()
                        .insertTransaction(transaction)
                    runOnUiThread {
                        Toast.makeText(this@AddExpenseActivity, "Expense Saved", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }.start()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Firebase Error: ${exception.message}", Toast.LENGTH_LONG).show()
                // Still save to Room so data isn't lost
                Thread {
                    AppDatabase.getDatabase(this@AddExpenseActivity)
                        .transactionDao()
                        .insertTransaction(transaction)
                    runOnUiThread {
                        finish()
                    }
                }.start()
            }
    }
}
