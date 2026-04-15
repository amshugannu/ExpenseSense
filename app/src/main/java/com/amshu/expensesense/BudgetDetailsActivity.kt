package com.amshu.expensesense

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class BudgetDetailsActivity : AppCompatActivity() {

    private lateinit var tvDetailMonth: TextView
    private lateinit var tvDetailBudgetStatus: TextView
    private lateinit var tvDetailSpentAmount: TextView
    private lateinit var tvDetailTotalLimit: TextView
    private lateinit var tvDetailRemaining: TextView
    private lateinit var tvDetailUsagePercent: TextView
    private lateinit var pbDetailCircular: ProgressBar
    private lateinit var rvCategoryBreakdown: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_budget_details)

        setupUI()
        loadBudgetData()
    }

    private fun setupUI() {
        tvDetailMonth = findViewById(R.id.tvDetailMonth)
        tvDetailBudgetStatus = findViewById(R.id.tvDetailBudgetStatus)
        tvDetailSpentAmount = findViewById(R.id.tvDetailSpentAmount)
        tvDetailTotalLimit = findViewById(R.id.tvDetailTotalLimit)
        tvDetailRemaining = findViewById(R.id.tvDetailRemaining)
        tvDetailUsagePercent = findViewById(R.id.tvDetailUsagePercent)
        pbDetailCircular = findViewById(R.id.pbDetailCircular)
        rvCategoryBreakdown = findViewById(R.id.rvCategoryBreakdown)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        rvCategoryBreakdown.layoutManager = LinearLayoutManager(this)
        
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        tvDetailMonth.text = sdf.format(Date())
    }

    private fun loadBudgetData() {
        val currentMonthYear = MonthUtils.getCurrentMonthKey()
        val db = AppDatabase.getDatabase(this)
        val budgetDao = db.budgetDao()
        val transactionDao = db.transactionDao()

        Thread {
            val budget = budgetDao.getBudget(currentMonthYear)
            val transactions = transactionDao.getAllTransactions()
            
            // Filter transactions for current month
            val currentMonthTxs = transactions.filter {
                val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                val m = cal.get(Calendar.MONTH) + 1
                val y = cal.get(Calendar.YEAR)
                val formatted = "%d-%02d".format(y, m) // YYYY-MM
                formatted == currentMonthYear && it.transactionType == Transaction.TYPE_EXPENSE
            }

            val totalSpent = currentMonthTxs.sumOf { it.amount }
            val catTotalsRaw = currentMonthTxs.groupBy { it.category }
                .mapValues { it.value.sumOf { t -> t.amount } }

            val categoryBudgets = budget?.categoryBudgets ?: emptyMap()

            // Sort: Budgeted categories first, then by spent amount
            val catTotals = catTotalsRaw.toList().sortedWith(compareByDescending<Pair<String, Double>> {
                categoryBudgets.containsKey(it.first)
            }.thenByDescending { it.second })

            runOnUiThread {
                updateUI(budget, totalSpent, catTotals)
            }
        }.start()
    }

    private fun updateUI(budget: Budget?, totalSpent: Double, catTotals: List<Pair<String, Double>>) {
        val totalBudget = budget?.totalBudget ?: 0.0
        
        tvDetailSpentAmount.text = "₹${String.format("%,.0f", totalSpent)}"
        tvDetailTotalLimit.text = "of ₹${String.format("%,.0f", totalBudget)}"
        
        val remaining = totalBudget - totalSpent
        tvDetailRemaining.text = "₹${String.format("%,.0f", remaining)}"
        if (remaining < 0) {
            tvDetailRemaining.setTextColor(Color.parseColor("#EF5350")) // Red
            tvDetailBudgetStatus.text = "Over Budget"
            tvDetailBudgetStatus.setTextColor(Color.parseColor("#EF5350"))
        } else {
            tvDetailRemaining.setTextColor(Color.parseColor("#2ABFBF")) // Teal
            tvDetailBudgetStatus.text = if (totalBudget > 0) "On Track" else "Set a Budget"
            tvDetailBudgetStatus.setTextColor(Color.parseColor("#2ABFBF"))
        }

        val usagePercent = if (totalBudget > 0) (totalSpent / totalBudget * 100).toInt() else 0
        tvDetailUsagePercent.text = "$usagePercent%"
        pbDetailCircular.progress = usagePercent.coerceAtMost(100)

        // Color logic for circular progress
        val color = when {
            usagePercent >= 100 -> Color.parseColor("#EF5350") // Red
            usagePercent >= 80 -> Color.parseColor("#FFA726") // Orange
            else -> Color.parseColor("#2ABFBF") // Teal
        }
        
        // FIX: Only tint the progress layer, not the background circle
        val progressDrawable = pbDetailCircular.progressDrawable as? android.graphics.drawable.LayerDrawable
        val progressItem = progressDrawable?.findDrawableByLayerId(android.R.id.progress)
        progressItem?.setTint(color)

        rvCategoryBreakdown.adapter = CategoryBreakdownAdapter(catTotals, totalSpent, budget?.categoryBudgets ?: emptyMap())
    }

    inner class CategoryBreakdownAdapter(
        private val items: List<Pair<String, Double>>,
        private val totalSpent: Double,
        private val categoryBudgets: Map<String, Double>
    ) : RecyclerView.Adapter<CategoryBreakdownAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvCatName)
            val tvAmount: TextView = view.findViewById(R.id.tvCatAmount)
            val pbProgress: com.google.android.material.progressindicator.LinearProgressIndicator = view.findViewById(R.id.pbCatProgress)
            val tvPercent: TextView = view.findViewById(R.id.tvCatPercent)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_budget_category, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val catName = item.first
            val spent = item.second
            val catBudget = categoryBudgets[catName]

            holder.tvName.text = catName

            if (catBudget != null && catBudget > 0) {
                // CASE 1: Category has a custom budget
                holder.tvAmount.text = "₹${String.format("%,.0f", spent)} / ₹${String.format("%,.0f", catBudget)}"
                holder.pbProgress.visibility = View.VISIBLE
                holder.tvPercent.visibility = View.GONE
                
                val progress = (spent / catBudget * 100).toInt()
                holder.pbProgress.progress = progress.coerceIn(0, 100)
                
                if (spent > catBudget) {
                    holder.pbProgress.setIndicatorColor(Color.parseColor("#EF5350")) // Red
                    holder.tvAmount.setTextColor(Color.parseColor("#EF5350"))
                } else {
                    holder.pbProgress.setIndicatorColor(Color.parseColor("#2ABFBF")) // Teal
                    holder.tvAmount.setTextColor(Color.parseColor("#1A1A2E"))
                }
            } else {
                // CASE 2: Category WITHOUT budget
                holder.tvAmount.text = "₹${String.format("%,.0f", spent)}"
                holder.pbProgress.visibility = View.GONE
                holder.tvPercent.visibility = View.VISIBLE
                
                val percent = if (totalSpent > 0) (spent / totalSpent * 100).toInt() else 0
                holder.tvPercent.text = "$percent% of total expenses"
                holder.tvAmount.setTextColor(Color.parseColor("#1A1A2E"))
            }
        }

        override fun getItemCount() = items.size
    }
}
