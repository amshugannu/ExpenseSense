package com.example.es

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddExpenseActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var etName: EditText
    private lateinit var etAmount: EditText
    private lateinit var tvDate: TextView
    private lateinit var tvClear: TextView
    private lateinit var layoutDate: RelativeLayout
    private lateinit var spinnerCategory: Spinner
    private lateinit var spinnerAccount: Spinner
    private lateinit var btnAddExpense: MaterialButton

    private val calendar = Calendar.getInstance()
    private lateinit var budgetRepository: BudgetRepository
    private lateinit var accountRepository: AccountRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_expense)

        val database = AppDatabase.getDatabase(this)
        budgetRepository = BudgetRepository(database.budgetDao(), database.transactionDao())
        accountRepository = AccountRepository(database.accountDao())

        btnBack       = findViewById(R.id.btnBack)
        etName        = findViewById(R.id.etName)
        etAmount      = findViewById(R.id.etAmount)
        tvDate        = findViewById(R.id.tvDate)
        tvClear       = findViewById(R.id.tvClear)
        layoutDate    = findViewById(R.id.layoutDate)
        spinnerCategory = findViewById(R.id.spinnerCategory)
        spinnerAccount  = findViewById(R.id.spinnerAccount)
        btnAddExpense = findViewById(R.id.btnAddExpense)

        // Populate category spinner
        val categories = resources.getStringArray(R.array.transaction_categories)
        spinnerCategory.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            categories
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // Populate account spinner
        accountRepository.getAllAccounts { accounts ->
            runOnUiThread {
                val accountNames = accounts.map { it.name }
                val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, accountNames)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerAccount.adapter = adapter
            }
        }

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

        // Setup Keyword-based Auto-categorization
        etName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val expenseName = etName.text.toString().trim()
                if (expenseName.isNotEmpty()) {
                    val detectedCategory = CategoryHelper.detectCategory(expenseName)
                    val index = categories.indexOfFirst { it.equals(detectedCategory, ignoreCase = true) }
                    if (index >= 0) {
                        spinnerCategory.setSelection(index)
                    }
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
        val category  = spinnerCategory.selectedItem?.toString() ?: "other"
        val account   = spinnerAccount.selectedItem?.toString() ?: "Cash"
        val timestamp = calendar.timeInMillis

        // Sanitize name for Firebase key (illegal: . $ [ ] # /)
        val sanitizedName = name.replace(Regex("[.#$\\[\\]/]"), "-")
        val uniqueTitleKey = "$sanitizedName-$timestamp"

        val databaseRef = FirebaseDatabase.getInstance()
            .getReference("users/$username/expenses/$category/$uniqueTitleKey")
        
        val firebaseExpenseData = mapOf(
            "timestamp" to timestamp,
            "amount" to amount,
            "account" to account
        )

        // The local Room DB object (keeps all fields for UI rendering)
        val transaction = Transaction(
            title      = name,    // The clean title for the UI
            amount     = amount,
            category   = category,
            accountName = account,
            timestamp  = timestamp,
            firebaseId = uniqueTitleKey // We store the unique key so we can delete it later
        )

        // Save to Firebase with listeners to catch errors
        databaseRef.setValue(firebaseExpenseData)
            .addOnSuccessListener {
                // Deduct from budget and account
                val monthYear = SimpleDateFormat("MM-yyyy", Locale.getDefault()).format(Date(timestamp))
                budgetRepository.deductFromBudget(monthYear, amount)
                accountRepository.updateBalance(account, -amount)

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
                
                // Still deduct from budget and account locally
                val monthYear = SimpleDateFormat("MM-yyyy", Locale.getDefault()).format(Date(timestamp))
                budgetRepository.deductFromBudget(monthYear, amount)
                accountRepository.updateBalance(account, -amount)

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
