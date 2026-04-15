package com.amshu.expensesense

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider

class AiInsightsActivity : AppCompatActivity() {

    private lateinit var viewModel: AiInsightsViewModel
    private lateinit var layoutAIInsights: LinearLayout
    private lateinit var layoutLoadingAI: View
    private lateinit var tvInsightTitle: View
    private lateinit var tvTagBudgetRisk: TextView
    private lateinit var tvTagOnTrack: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_insights)

        setupViewModel()
        setupUI()
        observeData()

        viewModel.fetchAIInsights()
    }

    private fun setupViewModel() {
        val database = AppDatabase.getDatabase(this)
        val transactionRepository = TransactionRepository(database.transactionDao())
        val budgetRepository = BudgetRepository(database.budgetDao(), database.transactionDao())
        val factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return AiInsightsViewModel(application, transactionRepository, budgetRepository) as T
            }
        }
        viewModel = ViewModelProvider(this, factory)[AiInsightsViewModel::class.java]
    }

    private fun setupUI() {
        layoutAIInsights = findViewById(R.id.layoutAIInsights)
        layoutLoadingAI = findViewById(R.id.layoutLoadingAI)
        tvInsightTitle = findViewById(R.id.tvInsightTitle)
        tvTagBudgetRisk = findViewById(R.id.tvTagBudgetRisk)
        tvTagOnTrack = findViewById(R.id.tvTagOnTrack)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnDetailsEdit)?.visibility = View.GONE // Safety check
        findViewById<ImageButton>(R.id.btnRefreshAI).setOnClickListener {
            android.util.Log.d("AI_DEBUG", "Refresh button clicked")
            viewModel.fetchAIInsights()
        }
    }

    private fun observeData() {
        viewModel.aiInsights.observe(this) { state ->
            when (state) {
                is AiInsightsViewModel.AiInsightState.Loading -> {
                    layoutLoadingAI.visibility = View.VISIBLE
                    layoutAIInsights.removeAllViews()
                    tvTagBudgetRisk.visibility = View.GONE
                    tvTagOnTrack.visibility = View.GONE
                }
                is AiInsightsViewModel.AiInsightState.Success -> {
                    layoutLoadingAI.visibility = View.GONE
                    updateInsightUI(state.insights, state.isBudgetRisk)
                }
                is AiInsightsViewModel.AiInsightState.Error -> {
                    layoutLoadingAI.visibility = View.GONE
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateInsightUI(insights: List<String>, isBudgetRisk: Boolean) {
        layoutAIInsights.removeAllViews()
        
        // Update risk tags
        if (isBudgetRisk) {
            tvTagBudgetRisk.visibility = View.VISIBLE
            tvTagOnTrack.visibility = View.GONE
        } else {
            tvTagBudgetRisk.visibility = View.GONE
            tvTagOnTrack.visibility = View.VISIBLE
        }

        insights.forEach { line ->
            val tv = TextView(this)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            // Step 5: Properly parse and format sections
            if (line.contains("SECTION", ignoreCase = true)) {
                // Style as Section Header
                tv.text = line.uppercase()
                tv.setTextColor(Color.parseColor("#1A1A2E"))
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                tv.setTypeface(null, android.graphics.Typeface.BOLD)
                params.setMargins(0, 40, 0, 12)
            } else {
                // Style as Bullet Point / Detail
                val cleanLine = line.replace("**", "<b>").replace("**", "</b>")
                val bullet = if (line.startsWith("•") || line.startsWith("-") || line.startsWith("*")) "" else "• "
                val htmlText = bullet + cleanLine
                
                tv.text = android.text.Html.fromHtml(htmlText, android.text.Html.FROM_HTML_MODE_COMPACT)
                tv.setTextColor(Color.parseColor("#444444"))
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                params.setMargins(0, 8, 0, 8)
                tv.setLineSpacing(1.2f, 1.2f)
            }
            
            tv.layoutParams = params
            layoutAIInsights.addView(tv)
        }
    }
}
