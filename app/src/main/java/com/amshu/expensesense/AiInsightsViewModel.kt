package com.amshu.expensesense

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class AiInsightsViewModel(
    application: Application,
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository
) : AndroidViewModel(application) {

    sealed class AiInsightState {
        object Loading : AiInsightState()
        data class Success(val insights: List<String>, val isBudgetRisk: Boolean = false) : AiInsightState()
        data class Error(val message: String) : AiInsightState()
    }

    private val _aiInsights = MutableLiveData<AiInsightState>()
    val aiInsights: LiveData<AiInsightState> = _aiInsights

    private val client = OkHttpClient()

    fun fetchAIInsights() {
        Log.d("AI_DEBUG", "fetchInsights() called")
        if (_aiInsights.value is AiInsightState.Loading) return
        _aiInsights.value = AiInsightState.Loading

        viewModelScope.launch {
            try {
                val apiKey = com.amshu.expensesense.BuildConfig.GEMINI_API_KEY_2
                Log.d("AI_DEBUG", "API KEY: $apiKey")
                if (apiKey.isBlank()) {
                    showFallbackInsights("API key missing")
                    return@launch
                }

                val cal = Calendar.getInstance()
                val currentMonthKey = MonthUtils.getCurrentMonthKey()
                val daysPassed = cal.get(Calendar.DAY_OF_MONTH)
                val totalDaysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

                if (daysPassed < 5) {
                    _aiInsights.postValue(AiInsightState.Success(listOf("Not enough data for accurate prediction. Please check back after 5 days of spending.")))
                    return@launch
                }

                // STEP 2: Move Room calls to IO thread (managed by suspend functions + Repository)
                val currentMonthTxs = transactionRepository.getTransactionsInRange(
                    getStartOfMonth(cal),
                    getEndOfMonth(cal)
                ).filter { it.transactionType == Transaction.TYPE_EXPENSE }

                if (currentMonthTxs.isEmpty()) {
                    _aiInsights.postValue(AiInsightState.Success(listOf("No transactions recorded for this month yet.")))
                    return@launch
                }

                // STEP 4: Combine Room + Firebase (handled via repository suspend calls)
                val budgetObj = budgetRepository.getBudget(currentMonthKey)
                val monthlyBudget = budgetObj?.totalBudget ?: 0.0
                val catBudgets = budgetObj?.categoryBudgets ?: emptyMap()

                val categoryData = currentMonthTxs.groupBy { it.category }
                Log.d("AI_DEBUG", "Total Spent: ${currentMonthTxs.sumOf { it.amount }}")
                Log.d("AI_DEBUG", "Budget: $monthlyBudget")
                Log.d("AI_DEBUG", "Category Map: ${categoryData.mapValues { it.value.size }}")
                
                val categoriesJson = JSONArray()
                var totalPredicted = 0.0

                val essentialCategories = listOf("Food", "Travel", "Fuel", "Medical", "Groceries", "Bills", "Gas")

                categoryData.forEach { (name, txs) ->
                    val totalSpentSoFar = txs.sumOf { it.amount }
                    val avgDaily = totalSpentSoFar / daysPassed
                    val predictedMonthly = avgDaily * totalDaysInMonth
                    totalPredicted += predictedMonthly

                    val transactionCount = txs.size
                    val frequencyPerDay = transactionCount.toDouble() / daysPassed
                    val isEssential = essentialCategories.contains(name)
                    
                    val risk = if (frequencyPerDay > 1.5 && !isEssential) "high" else "normal"

                    val catJson = JSONObject().apply {
                        put("name", name)
                        put("spent_so_far", totalSpentSoFar)
                        put("avg_daily", avgDaily)
                        put("predicted_monthly", predictedMonthly)
                        put("category_budget", catBudgets[name] ?: 0.0)
                        put("frequency_per_day", frequencyPerDay)
                        put("is_essential", isEssential)
                        put("risk", risk)
                    }
                    categoriesJson.put(catJson)
                }

                val rootJson = JSONObject().apply {
                    put("categories", categoriesJson)
                    put("total_predicted", totalPredicted)
                    put("overall_budget", monthlyBudget)
                    put("avg_daily_all", totalPredicted / totalDaysInMonth)
                    put("days_passed", daysPassed)
                    put("total_days", totalDaysInMonth)
                }

                val isBudgetRisk = totalPredicted > monthlyBudget && monthlyBudget > 0
                val prompt = generateDeepGeminiPrompt(rootJson.toString())
                Log.d("AI_DEBUG", "JSON SENT TO GEMINI: ${rootJson.toString()}")
                
                // STEP 5: Call Gemini after data is ready
                callGeminiApi(prompt, apiKey, isBudgetRisk)

            } catch (e: Exception) {
                // STEP 7: Handle Exceptions
                Log.e("AI_DEBUG", "ERROR: ${e.message}")
                showFallbackInsights(e.message ?: "Unknown error")
            }
        }
    }

    private fun getStartOfMonth(cal: Calendar): Long {
        val start = cal.clone() as Calendar
        start.set(Calendar.DAY_OF_MONTH, 1)
        start.set(Calendar.HOUR_OF_DAY, 0)
        start.set(Calendar.MINUTE, 0)
        start.set(Calendar.SECOND, 0)
        start.set(Calendar.MILLISECOND, 0)
        return start.timeInMillis
    }

    private fun getEndOfMonth(cal: Calendar): Long {
        val end = cal.clone() as Calendar
        end.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        end.set(Calendar.HOUR_OF_DAY, 23)
        end.set(Calendar.MINUTE, 59)
        end.set(Calendar.SECOND, 59)
        end.set(Calendar.MILLISECOND, 999)
        return end.timeInMillis
    }

    private fun generateDeepGeminiPrompt(jsonData: String): String {
        return """
            You are a financial AI inside an expense tracking app.

            Generate deep insights in EXACTLY 3 sections:

            SECTION 1: Prediction Insights
            • Show avg daily spend (₹)
            • Show predicted monthly spend (₹)
            • Compare with category budget

            SECTION 2: Behavioral Insights
            • Detect high-frequency categories
            • Identify non-essential spending
            • Explain reason

            SECTION 3: Budget Risk Insights
            • Show total predicted spend (₹)
            • Compare with overall budget
            • Show risk (over/under)

            STRICT RULES:
            • Use bullet points
            • MUST include numbers (₹ values)
            • DO NOT give generic advice
            • DO NOT repeat same sentences
            • Each section must have 2–3 points

            User Data:
            $jsonData
        """.trimIndent()
    }

    private fun callGeminiApi(prompt: String, apiKey: String, isBudgetRisk: Boolean) {
        Log.d("AI_DEBUG", "Calling Gemini API...")
        val url = "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent?key=$apiKey"
        
        val requestBodyJson = JSONObject()
        val contentsArray = JSONArray()
        val contentObj = JSONObject()
        val partsArray = JSONArray()
        val partObj = JSONObject()
        partObj.put("text", prompt)
        partsArray.put(partObj)
        contentObj.put("parts", partsArray)
        contentObj.put("role", "user")
        contentsArray.put(contentObj)
        requestBodyJson.put("contents", contentsArray)

        val requestBody = requestBodyJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("AI_DEBUG", "API FAILED: ${e.message}")
                viewModelScope.launch { showFallbackInsights(e.message ?: "Network error") }
            }

            override fun onResponse(call: Call, response: Response) {
                val rawResponse = response.body?.string()
                Log.d("AI_DEBUG", "RAW RESPONSE: $rawResponse")
                
                viewModelScope.launch {
                    if (response.isSuccessful && rawResponse != null) {
                        try {
                            val responseJson = JSONObject(rawResponse)
                            val candidates = responseJson.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val text = candidates.getJSONObject(0)
                                    .getJSONObject("content")
                                    .getJSONArray("parts")
                                    .getJSONObject(0)
                                    .getString("text")
                                
                                Log.d("AI_DEBUG", "PARSED TEXT: $text")

                                if (text.isNotEmpty()) {
                                    Log.d("AI_DEBUG", "Displaying AI Insights")
                                } else {
                                    Log.d("AI_DEBUG", "Displaying Fallback Insights")
                                }

                                val insightLines = text.split("\n")
                                    .filter { it.isNotBlank() }
                                    .map { it.trim().removePrefix("* ").removePrefix("- ").removePrefix("• ").trim() }
                                
                                _aiInsights.postValue(AiInsightState.Success(insightLines, isBudgetRisk))
                            } else {
                                Log.e("AI_DEBUG", "Empty candidates in response")
                                showFallbackInsights("Empty AI response")
                            }
                        } catch (e: Exception) {
                            Log.e("AI_DEBUG", "Parsing error: ${e.message}")
                            showFallbackInsights("Parsing error: ${e.message}")
                        }
                    } else {
                        Log.e("AI_DEBUG", "API error: ${response.code}")
                        showFallbackInsights("API Error: ${response.code}")
                    }
                }
            }
        })
    }

    private fun showFallbackInsights(reason: String) {
        Log.d("AI_DEBUG", "FALLBACK TRIGGERED: $reason")
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            val currentMonthTxs = transactionRepository.getTransactionsInRange(
                getStartOfMonth(cal),
                getEndOfMonth(cal)
            ).filter { it.transactionType == Transaction.TYPE_EXPENSE }
            
            val total = currentMonthTxs.sumOf { it.amount }
            val highest = currentMonthTxs.groupBy { it.category }.maxByOrNull { it.value.sumOf { t -> t.amount } }?.key ?: "N/A"

            val fallbacks = mutableListOf<String>()
            fallbacks.add("Your spending trend suggests a possible budget overrun.")
            fallbacks.add("Monitor high-frequency expenses in top categories.")
            fallbacks.add("Consider reducing non-essential spending to stay within your limits.")
            if (highest != "N/A") {
                fallbacks.add("Currently, $highest is your most active category at ₹${String.format("%.2f", total)}.")
            }
            
            _aiInsights.postValue(AiInsightState.Success(fallbacks, false))
        }
    }
}
