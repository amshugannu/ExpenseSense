package com.example.es

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.data.Entry
import kotlinx.coroutines.launch
import java.util.Calendar

class AnalyticsViewModel(private val repository: TransactionRepository) : ViewModel() {

    private val _topSpending = MutableLiveData<List<Transaction>>()
    val topSpending: LiveData<List<Transaction>> = _topSpending

    private val _filteredTransactions = MutableLiveData<List<Transaction>>()
    val filteredTransactions: LiveData<List<Transaction>> = _filteredTransactions

    private val _heatmapData = MutableLiveData<List<Float>>()
    val heatmapData: LiveData<List<Float>> = _heatmapData

    private val _chartData = MutableLiveData<Pair<List<Entry>, List<String>>>()
    val chartData: LiveData<Pair<List<Entry>, List<String>>> = _chartData

    fun fetchData(timeRange: String, selectedCalendar: Calendar) {
        viewModelScope.launch {
            val (startTime, endTime) = getTimeRange(timeRange, selectedCalendar)
            
            val top = repository.getTopSpending(startTime, endTime, 5)
            _topSpending.postValue(top)

            val transactions = repository.getTransactionsInRange(startTime, endTime)
            _filteredTransactions.postValue(transactions)

            // Process Chart Data
            val aggregated = aggregateForChart(transactions, timeRange, selectedCalendar)
            _chartData.postValue(aggregated)

            // Generate heatmap data for the current month
            val monthStart = selectedCalendar.clone() as Calendar
            monthStart.set(Calendar.DAY_OF_MONTH, 1)
            monthStart.set(Calendar.HOUR_OF_DAY, 0)
            monthStart.set(Calendar.MINUTE, 0)
            monthStart.set(Calendar.SECOND, 0)
            
            val monthEnd = selectedCalendar.clone() as Calendar
            monthEnd.set(Calendar.DAY_OF_MONTH, monthEnd.getActualMaximum(Calendar.DAY_OF_MONTH))
            monthEnd.set(Calendar.HOUR_OF_DAY, 23)
            
            val monthTransactions = repository.getTransactionsInRange(monthStart.timeInMillis, monthEnd.timeInMillis)
            _heatmapData.postValue(calculateHeatmapIntensity(monthTransactions, monthStart))
        }
    }

    private fun aggregateForChart(
        transactions: List<Transaction>,
        timeRange: String,
        selectedCalendar: Calendar
    ): Pair<List<Entry>, List<String>> {
        val entries = mutableListOf<Entry>()
        val labels = mutableListOf<String>()
        val calendar = Calendar.getInstance()
        val now = Calendar.getInstance()
        val isCurrentPeriod = isSamePeriod(timeRange, selectedCalendar, now)

        when (timeRange.lowercase()) {
            "day" -> {
                val hourlyTotals = FloatArray(24)
                transactions.forEach {
                    calendar.timeInMillis = it.timestamp
                    val hour = calendar.get(Calendar.HOUR_OF_DAY)
                    hourlyTotals[hour] += it.amount.toFloat()
                }
                val limit = if (isCurrentPeriod) now.get(Calendar.HOUR_OF_DAY) else 23
                for (i in 0..limit) {
                    entries.add(Entry(i.toFloat(), hourlyTotals[i]))
                }
                for (i in 0..23) {
                    labels.add(if (i % 2 == 0) "${if (i == 0) 12 else if (i > 12) i - 12 else i} ${if (i < 12) "AM" else "PM"}" else "")
                }
            }
            "week" -> {
                val dailyTotals = FloatArray(7)
                transactions.forEach {
                    calendar.timeInMillis = it.timestamp
                    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sun, 6=Sat
                    dailyTotals[dayOfWeek] += it.amount.toFloat()
                }
                val limit = if (isCurrentPeriod) now.get(Calendar.DAY_OF_WEEK) - 1 else 6
                for (i in 0..limit) {
                    entries.add(Entry(i.toFloat(), dailyTotals[i]))
                }
                labels.addAll(listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"))
            }
            "month" -> {
                val daysInMonth = selectedCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                val dailyTotals = FloatArray(daysInMonth)
                transactions.forEach {
                    calendar.timeInMillis = it.timestamp
                    val day = calendar.get(Calendar.DAY_OF_MONTH) - 1
                    dailyTotals[day] += it.amount.toFloat()
                }
                val limit = if (isCurrentPeriod) now.get(Calendar.DAY_OF_MONTH) - 1 else daysInMonth - 1
                for (i in 0..limit) {
                    entries.add(Entry(i.toFloat(), dailyTotals[i]))
                }
                for (i in 1..daysInMonth) {
                    labels.add(if (i % 2 != 0) i.toString() else "")
                }
            }
            "year" -> {
                val monthlyTotals = FloatArray(12)
                transactions.forEach {
                    calendar.timeInMillis = it.timestamp
                    val month = calendar.get(Calendar.MONTH)
                    monthlyTotals[month] += it.amount.toFloat()
                }
                val limit = if (isCurrentPeriod) now.get(Calendar.MONTH) else 11
                for (i in 0..limit) {
                    entries.add(Entry(i.toFloat(), monthlyTotals[i]))
                }
                labels.addAll(listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"))
            }
        }
        return Pair(entries, labels)
    }

    private fun isSamePeriod(range: String, cal1: Calendar, cal2: Calendar): Boolean {
        return when (range.lowercase()) {
            "day" -> cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
            "week" -> cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.WEEK_OF_YEAR) == cal2.get(Calendar.WEEK_OF_YEAR)
            "month" -> cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH)
            "year" -> cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
            else -> false
        }
    }

    private fun getTimeRange(timeRange: String, cal: Calendar): Pair<Long, Long> {
        val start = cal.clone() as Calendar
        val end = cal.clone() as Calendar

        when (timeRange.lowercase()) {
            "day" -> {
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                end.set(Calendar.HOUR_OF_DAY, 23)
                end.set(Calendar.MINUTE, 59)
                end.set(Calendar.SECOND, 59)
            }
            "week" -> {
                start.set(Calendar.DAY_OF_WEEK, start.firstDayOfWeek)
                start.set(Calendar.HOUR_OF_DAY, 0)
                end.set(Calendar.DAY_OF_WEEK, start.firstDayOfWeek)
                end.add(Calendar.DAY_OF_WEEK, 6)
                end.set(Calendar.HOUR_OF_DAY, 23)
            }
            "month" -> {
                start.set(Calendar.DAY_OF_MONTH, 1)
                start.set(Calendar.HOUR_OF_DAY, 0)
                end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH))
                end.set(Calendar.HOUR_OF_DAY, 23)
            }
            "year" -> {
                start.set(Calendar.MONTH, 0)
                start.set(Calendar.DAY_OF_MONTH, 1)
                start.set(Calendar.HOUR_OF_DAY, 0)
                end.set(Calendar.MONTH, 11)
                end.set(Calendar.DAY_OF_MONTH, 31)
                end.set(Calendar.HOUR_OF_DAY, 23)
            }
        }
        return Pair(start.timeInMillis, end.timeInMillis)
    }

    private fun calculateHeatmapIntensity(transactions: List<Transaction>, monthStart: Calendar): List<Float> {
        val daysInMonth = monthStart.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dailyTotals = FloatArray(daysInMonth)
        var maxTotal = 0.1f

        transactions.forEach {
            val transCal = Calendar.getInstance()
            transCal.timeInMillis = it.timestamp
            val day = transCal.get(Calendar.DAY_OF_MONTH) - 1
            if (day in 0 until daysInMonth) {
                dailyTotals[day] += it.amount.toFloat()
                if (dailyTotals[day] > maxTotal) maxTotal = dailyTotals[day]
            }
        }

        return dailyTotals.map { it / maxTotal }
    }
}
