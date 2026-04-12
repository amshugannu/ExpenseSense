package com.amshu.expensesense

import android.app.Application
import androidx.lifecycle.*
import com.amshu.expensesense.utils.ExpenseSensePdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

class AnalyticsViewModel(
    application: Application,
    private val repository: TransactionRepository
) : AndroidViewModel(application) {

    private val _exportState = MutableLiveData<ExportState>()
    val exportState: LiveData<ExportState> = _exportState

    fun exportPdf(transactions: List<Transaction>, calendar: Calendar) {

        _exportState.value = ExportState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val exporter = ExpenseSensePdfExporter(getApplication())

                android.util.Log.e("DEBUG_VM", "Transactions ready for export: ${transactions.size}")
                transactions.forEach {
                    android.util.Log.e("DEBUG_VM", "TX -> ${it.title}, ${Date(it.timestamp)}")
                }

                val file = exporter.exportToPdf(transactions, calendar)
                

                _exportState.postValue(ExportState.Success(file))

            } catch (e: Exception) {
                _exportState.postValue(
                    ExportState.Error(e.message ?: "Export failed")
                )
            }
        }
    }

    sealed class ExportState {
        object Loading : ExportState()
        data class Success(val file: File) : ExportState()
        data class Error(val message: String) : ExportState()
    }

    private val _topSpending = MutableLiveData<List<Transaction>>()
    val topSpending: LiveData<List<Transaction>> = _topSpending

    private val _filteredTransactions = MutableLiveData<List<Transaction>>()
    val filteredTransactions: LiveData<List<Transaction>> = _filteredTransactions

    val selectedMonthCalendar: Calendar = Calendar.getInstance()

    fun fetchMonthlyData() {
        viewModelScope.launch {
            val start = selectedMonthCalendar.clone() as Calendar
            start.set(Calendar.DAY_OF_MONTH, 1)
            start.set(Calendar.HOUR_OF_DAY, 0)
            start.set(Calendar.MINUTE, 0)
            start.set(Calendar.SECOND, 0)
            start.set(Calendar.MILLISECOND, 0)

            val end = selectedMonthCalendar.clone() as Calendar
            end.set(Calendar.DAY_OF_MONTH, selectedMonthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH))
            end.set(Calendar.HOUR_OF_DAY, 23)
            end.set(Calendar.MINUTE, 59)
            end.set(Calendar.SECOND, 59)
            end.set(Calendar.MILLISECOND, 999)

            // Top Spending (always 5 for the UI list)
            val top = repository.getTopSpending(start.timeInMillis, end.timeInMillis, 5)
            _topSpending.postValue(top)

            // Full list for charts and PDF
            val transactions = repository.getTransactionsInRange(start.timeInMillis, end.timeInMillis)
            android.util.Log.d("VM_DEBUG", "Monthly fetch: ${transactions.size} items for ${selectedMonthCalendar.get(Calendar.MONTH)}")
            _filteredTransactions.postValue(transactions)
        }
    }

    fun fetchData(timeRange: String, calendar: Calendar) {
        android.util.Log.d("VM_DEBUG", "TimeRange: $timeRange")
        viewModelScope.launch {
            val (start, end) = getTimeRange(timeRange, calendar)
            android.util.Log.d("VM_DEBUG", "Start: $start End: $end")

            val top = repository.getTopSpending(start, end, 5)
            _topSpending.postValue(top)

            val transactions = repository.getTransactionsInRange(start, end)
            android.util.Log.e("DEBUG_VM", "Transactions fetched: ${transactions.size}")

            transactions.forEach {
                android.util.Log.e("DEBUG_VM", "TX -> ${it.title}, ${Date(it.timestamp)}")
            }

            _filteredTransactions.postValue(transactions)
        }
    }

    private fun getTimeRange(range: String, cal: Calendar): Pair<Long, Long> {

        val start = cal.clone() as Calendar
        val end = cal.clone() as Calendar

        when (range.lowercase()) {
            "day" -> {
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)

                end.set(Calendar.HOUR_OF_DAY, 23)
                end.set(Calendar.MINUTE, 59)
                end.set(Calendar.SECOND, 59)
                end.set(Calendar.MILLISECOND, 999)
            }

            "week" -> {
                start.set(Calendar.DAY_OF_WEEK, start.firstDayOfWeek)
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)

                end.timeInMillis = start.timeInMillis
                end.add(Calendar.DAY_OF_WEEK, 6)
                end.set(Calendar.HOUR_OF_DAY, 23)
                end.set(Calendar.MINUTE, 59)
                end.set(Calendar.SECOND, 59)
                end.set(Calendar.MILLISECOND, 999)
            }

            "month" -> {
                start.set(Calendar.DAY_OF_MONTH, 1)
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)

                end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH))
                end.set(Calendar.HOUR_OF_DAY, 23)
                end.set(Calendar.MINUTE, 59)
                end.set(Calendar.SECOND, 59)
                end.set(Calendar.MILLISECOND, 999)
            }

            "year" -> {
                start.set(Calendar.MONTH, 0)
                start.set(Calendar.DAY_OF_MONTH, 1)
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)

                end.set(Calendar.MONTH, 11)
                end.set(Calendar.DAY_OF_MONTH, 31)
                end.set(Calendar.HOUR_OF_DAY, 23)
                end.set(Calendar.MINUTE, 59)
                end.set(Calendar.SECOND, 59)
                end.set(Calendar.MILLISECOND, 999)
            }
        }

        return Pair(start.timeInMillis, end.timeInMillis)
    }
}