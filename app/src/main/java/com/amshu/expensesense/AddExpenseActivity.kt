package com.amshu.expensesense

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddExpenseActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var etName: EditText
    private lateinit var etAmount: EditText
    private lateinit var etNote: EditText
    private lateinit var tvDate: TextView
    private lateinit var tvClear: TextView
    private lateinit var layoutDate: RelativeLayout
    private lateinit var tvTime: TextView
    private lateinit var layoutTime: RelativeLayout
    private lateinit var ivCategoryIcon: ImageView
    private lateinit var pbCategorySelect: ProgressBar
    private var selectedCategory: String = "other"
    private lateinit var chipGroupPaymentMethod: com.google.android.material.chip.ChipGroup
    private lateinit var layoutDynamicSelector: LinearLayout
    private lateinit var tvSelectorLabel: TextView
    private lateinit var spinnerDynamic: Spinner
    private lateinit var btnAddExpense: MaterialButton
    private lateinit var toggleType: MaterialButtonToggleGroup
    private var selectedTransactionType: String = Transaction.TYPE_EXPENSE

    private lateinit var paymentRepository: PaymentRepository
    private lateinit var cardRepository: CardRepository

    private val calendar = Calendar.getInstance()
    private lateinit var budgetRepository: BudgetRepository
    private lateinit var accountRepository: AccountRepository

    private val categorySelectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val category = result.data?.getStringExtra("selected_category")
            if (!category.isNullOrEmpty()) {
                selectedCategory = category
                updateCategoryImage(selectedCategory)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_expense)

        val database = AppDatabase.getDatabase(this)
        budgetRepository = BudgetRepository(database.budgetDao(), database.transactionDao())
        accountRepository = AccountRepository(database.accountDao())
        cardRepository = CardRepository(
            database.debitCardDao(),
            database.creditCardDao(),
            database.accountDao(),
            database.cardDao()
        )
        paymentRepository = PaymentRepository(
            database,
            database.transactionDao(),
            database.accountDao(),
            database.debitCardDao(),
            database.creditCardDao(),
            database.budgetDao()
        )

        btnBack       = findViewById(R.id.btnBack)
        etName        = findViewById(R.id.etName)
        etAmount      = findViewById(R.id.etAmount)
        etNote        = findViewById(R.id.etNote)
        tvDate        = findViewById(R.id.tvDate)
        tvClear       = findViewById(R.id.tvClear)
        layoutDate    = findViewById(R.id.layoutDate)
        tvTime        = findViewById(R.id.tvTime)
        layoutTime    = findViewById(R.id.layoutTime)
        ivCategoryIcon = findViewById(R.id.ivCategoryIcon)
        pbCategorySelect = findViewById(R.id.pbCategorySelect)
        
        // Payment Method Views
        chipGroupPaymentMethod = findViewById(R.id.chipGroupPaymentMethod)
        layoutDynamicSelector  = findViewById(R.id.layoutDynamicSelector)
        tvSelectorLabel        = findViewById(R.id.tvSelectorLabel)
        spinnerDynamic         = findViewById(R.id.spinnerDynamic)
        
        btnAddExpense = findViewById(R.id.btnAddExpense)
        toggleType     = findViewById(R.id.toggleType)

        // Setup Transaction Type Toggle
        toggleType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedTransactionType = if (checkedId == R.id.btnTypeExpense) {
                    Transaction.TYPE_EXPENSE
                } else {
                    Transaction.TYPE_INCOME
                }
                updateUIForType(selectedTransactionType)
            }
        }

        // Initialize category UI
        updateCategoryImage(selectedCategory)
        
        ivCategoryIcon.setOnClickListener {
            val intent = Intent(this, CategorySelectionActivity::class.java)
            categorySelectionLauncher.launch(intent)
        }

        // Setup Payment Method Selection
        chipGroupPaymentMethod.setOnCheckedChangeListener { group, checkedId ->
            handlePaymentMethodChange(checkedId)
        }
        
        // Initialize default state
        handlePaymentMethodChange(R.id.chipCash)

        // Pre-fill today's date and time
        updateDateLabel()
        updateTimeLabel()

        btnBack.setOnClickListener { finish() }

        tvClear.setOnClickListener {
            etAmount.setText("")
            etAmount.requestFocus()
        }

        layoutDate.setOnClickListener { showDatePicker() }
        tvDate.setOnClickListener    { showDatePicker() }

        layoutTime.setOnClickListener { showTimePicker() }
        tvTime.setOnClickListener    { showTimePicker() }

        btnAddExpense.setOnClickListener { submitExpense() }

        // Setup Keyword-based Auto-categorization
        etName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val expenseName = etName.text.toString().trim()
                if (expenseName.isNotEmpty()) {
                    pbCategorySelect.visibility = View.VISIBLE
                    ivCategoryIcon.visibility = View.INVISIBLE
                    
                    val detectedCategory = CategoryHelper.detectCategory(expenseName)
                    
                    // Small delay for visual feedback
                    etName.postDelayed({
                        selectedCategory = detectedCategory
                        updateCategoryImage(selectedCategory)
                        pbCategorySelect.visibility = View.GONE
                        ivCategoryIcon.visibility = View.VISIBLE
                    }, 400)
                } else {
                    selectedCategory = "other"
                    updateCategoryImage(selectedCategory)
                }
            }
        }

        // Handle Intent Extras from Bill Scanning and Editing
        intent.apply {
            val isEditing = getBooleanExtra("isEditing", false)
            if (isEditing) {
                btnAddExpense.text = "UPDATE TRANSACTION"
                val note = getStringExtra("note")
                val paymentMethod = getStringExtra("paymentMethod")
                
                if (!note.isNullOrEmpty()) etNote.setText(note)

                when (paymentMethod) {
                    "UPI" -> chipGroupPaymentMethod.check(R.id.chipUPI)
                    "Debit Card" -> chipGroupPaymentMethod.check(R.id.chipDebit)
                    "Credit Card" -> chipGroupPaymentMethod.check(R.id.chipCredit)
                    else -> chipGroupPaymentMethod.check(R.id.chipCash)
                }
            }

            val title = getStringExtra("title")
            val amount = getDoubleExtra("amount", -1.0)
            val category = getStringExtra("category")
            val timestamp = getLongExtra("timestamp", -1L)

            if (!title.isNullOrEmpty()) {
                etName.setText(title)
            }
            if (amount != -1.0 && amount != 0.0) {
                etAmount.setText(amount.toString())
            }
            if (!category.isNullOrEmpty()) {
                selectedCategory = category
                updateCategoryImage(selectedCategory)
            }
            if (timestamp != -1L && timestamp != 0L) {
                calendar.timeInMillis = timestamp
                updateDateLabel()
                updateTimeLabel()
            }
        }
    }

    private fun updateCategoryImage(category: String) {
        val iconResId = CategoryAdapter.getCategoryIcon(category)
        ivCategoryIcon.setImageResource(iconResId)
    }

    private fun updateUIForType(type: String) {
        findViewById<TextView>(R.id.tvTitle).text = if (type == Transaction.TYPE_INCOME) "Add Income" else "Add Expense"
        btnAddExpense.text = if (type == Transaction.TYPE_INCOME) "Add Income" else "Add Expense"
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

    private fun showTimePicker() {
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                updateTimeLabel()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        ).show()
    }

    private fun updateTimeLabel() {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        tvTime.text = sdf.format(calendar.time)
    }

    private fun handlePaymentMethodChange(chipId: Int) {
        when (chipId) {
            R.id.chipCash -> {
                layoutDynamicSelector.visibility = View.GONE
            }
            R.id.chipUPI -> {
                layoutDynamicSelector.visibility = View.VISIBLE
                tvSelectorLabel.text = "SELECT BANK"
                loadAccounts()
            }
            R.id.chipDebit -> {
                layoutDynamicSelector.visibility = View.VISIBLE
                tvSelectorLabel.text = "SELECT DEBIT CARD"
                loadCards("Debit")
            }
            R.id.chipCredit -> {
                layoutDynamicSelector.visibility = View.VISIBLE
                tvSelectorLabel.text = "SELECT CREDIT CARD"
                loadCards("Credit")
            }
        }
    }

    private fun loadAccounts() {
        accountRepository.getAllAccounts { accounts ->
            val filtered = accounts.filter { it.name != "Cash" }
            runOnUiThread {
                val names = filtered.map { it.name }
                val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerDynamic.adapter = adapter
            }
        }
    }

    private fun loadCards(type: String) {
        cardRepository.getAllCards { debits, credits, _ ->
            val names = if (type == "Debit") {
                debits.map { it.cardName }
            } else {
                credits.map { it.cardName }
            }
            runOnUiThread {
                val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerDynamic.adapter = adapter
            }
        }
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

        val paymentMethod = when (chipGroupPaymentMethod.checkedChipId) {
            R.id.chipCash -> "Cash"
            R.id.chipUPI -> "UPI"
            R.id.chipDebit -> "Debit Card"
            R.id.chipCredit -> "Credit Card"
            else -> "Cash"
        }

        val referenceId = spinnerDynamic.selectedItem?.toString()
        if (paymentMethod != "Cash" && referenceId == null) {
            Toast.makeText(this, "Please select a ${tvSelectorLabel.text}", Toast.LENGTH_SHORT).show()
            return
        }

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || user.email == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val username  = user.email!!.substringBefore("@")
        val category  = selectedCategory
        val timestamp = calendar.timeInMillis
        val sanctionedName = name.replace(Regex("[.#$\\[\\]/]"), "-")
        val uniqueTitleKey = "$sanctionedName-$timestamp"

        val note      = etNote.text.toString().trim()

        val transaction = Transaction(
            title      = name,
            amount     = amount,
            category   = category,
            accountName = referenceId ?: "Cash",
            timestamp  = timestamp,
            paymentMethod = paymentMethod,
            referenceId = referenceId,
            firebaseId = uniqueTitleKey,
            note       = note,
            transactionType = selectedTransactionType
        )

        btnAddExpense.isEnabled = false
        val isEditing = intent.getBooleanExtra("isEditing", false)
        
        if (isEditing) {
            val oldTransaction = Transaction(
                id = intent.getIntExtra("id", 0),
                title = intent.getStringExtra("title") ?: "",
                amount = intent.getDoubleExtra("amount", 0.0),
                category = intent.getStringExtra("category") ?: "",
                timestamp = intent.getLongExtra("timestamp", 0L),
                paymentMethod = intent.getStringExtra("paymentMethod") ?: "Cash",
                referenceId = intent.getStringExtra("referenceId"),
                firebaseId = intent.getStringExtra("firebaseId"),
                note = intent.getStringExtra("note") ?: "",
                accountName = intent.getStringExtra("accountName") ?: "Cash"
            )
            val updatedTransaction = transaction.copy(
                id = oldTransaction.id,
                firebaseId = oldTransaction.firebaseId // retain old firebase ID
            )
            
            paymentRepository.updateExpense(oldTransaction, updatedTransaction, paymentMethod, referenceId, username) { success, error ->
                runOnUiThread {
                    btnAddExpense.isEnabled = true
                    if (success) {
                        Toast.makeText(this@AddExpenseActivity, "Expense Updated", Toast.LENGTH_SHORT).show()
                        setResult(android.app.Activity.RESULT_OK)
                        finish()
                    } else {
                        Toast.makeText(this@AddExpenseActivity, "Error: $error", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            paymentRepository.saveExpense(transaction, paymentMethod, referenceId, username) { success, error ->
                runOnUiThread {
                    btnAddExpense.isEnabled = true
                    if (success) {
                        Toast.makeText(this@AddExpenseActivity, "Expense Saved", Toast.LENGTH_SHORT).show()
                        setResult(android.app.Activity.RESULT_OK)
                        finish()
                    } else {
                        Toast.makeText(this@AddExpenseActivity, "Error: $error", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}

