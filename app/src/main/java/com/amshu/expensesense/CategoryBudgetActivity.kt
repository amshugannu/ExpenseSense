package com.amshu.expensesense

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CategoryBudgetActivity : AppCompatActivity() {

    private lateinit var etTotalBudget: EditText
    private lateinit var btnUpdateTotalBudget: Button
    private lateinit var spinnerCategories: Spinner
    private lateinit var etCategoryBudget: EditText
    private lateinit var btnSaveCategoryBudget: Button

    private lateinit var budgetRepository: BudgetRepository
    private lateinit var transactionDao: TransactionDao
    private var currentMonthYear: String = ""
    private var totalMonthlyBudget: Double = 0.0
    private var existingCategoryBudgets: Map<String, Double> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_category_budget)

        val db = AppDatabase.getDatabase(this)
        budgetRepository = BudgetRepository(db.budgetDao(), db.transactionDao())
        transactionDao = db.transactionDao()
        currentMonthYear = MonthUtils.getCurrentMonthKey()

        setupUI()
        loadData()
    }

    private fun setupUI() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        
        etTotalBudget = findViewById(R.id.etTotalBudget)
        btnUpdateTotalBudget = findViewById(R.id.btnUpdateTotalBudget)
        spinnerCategories = findViewById(R.id.spinnerCategories)
        etCategoryBudget = findViewById(R.id.etCategoryBudget)
        btnSaveCategoryBudget = findViewById(R.id.btnSaveCategoryBudget)

        btnSaveCategoryBudget.setOnClickListener {
            saveCategoryBudget()
        }

        btnUpdateTotalBudget.setOnClickListener {
            updateTotalBudget()
        }
    }

    private fun updateTotalBudget() {
        val amountStr = etTotalBudget.text.toString()
        val amount = amountStr.toDoubleOrNull()

        if (amount == null || amount <= 0) {
            Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            budgetRepository.setBudget(amount, currentMonthYear)
            
            // Sync to Firebase (global monthlyBudget)
            val username = FirebaseAuth.getInstance().currentUser?.email?.substringBefore("@")
            if (username != null) {
                com.google.firebase.database.FirebaseDatabase.getInstance()
                    .getReference("users/$username/monthlyBudget")
                    .setValue(amount)
            }

            totalMonthlyBudget = amount
            Toast.makeText(this@CategoryBudgetActivity, "Total Monthly Budget Updated", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            // 1. Get total budget
            val budget = budgetRepository.getBudget(currentMonthYear)
            totalMonthlyBudget = budget?.totalBudget ?: 0.0
            existingCategoryBudgets = budget?.categoryBudgets ?: emptyMap()

            // 2. Get all categories from resources
            val allCategories = resources.getStringArray(R.array.transaction_categories).toList()

            if (totalMonthlyBudget > 0) {
                etTotalBudget.setText(totalMonthlyBudget.toString())
            }
            
            val adapter = ArrayAdapter(this@CategoryBudgetActivity, android.R.layout.simple_spinner_item, allCategories)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerCategories.adapter = adapter
        }
    }

    private fun saveCategoryBudget() {
        val selectedCategory = spinnerCategories.selectedItem?.toString() ?: return
        val amountStr = etCategoryBudget.text.toString()
        val amount = amountStr.toDoubleOrNull()

        if (amount == null || amount <= 0) {
            Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
            return
        }

        if (totalMonthlyBudget <= 0) {
            Toast.makeText(this, "Please set a total monthly budget first in Profile", Toast.LENGTH_LONG).show()
            return
        }

        if (amount > totalMonthlyBudget) {
            Toast.makeText(this, "Category budget cannot exceed total budget (₹$totalMonthlyBudget)", Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            budgetRepository.updateCategoryBudget(currentMonthYear, selectedCategory, amount)
            
            Toast.makeText(this@CategoryBudgetActivity, "Budget for $selectedCategory saved", Toast.LENGTH_SHORT).show()
            etCategoryBudget.text.clear()
            
            // Refresh data
            val budget = budgetRepository.getBudget(currentMonthYear)
            existingCategoryBudgets = budget?.categoryBudgets ?: emptyMap()
        }
    }
}
