package com.amshu.expensesense

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import android.widget.AdapterView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.amshu.expensesense.databinding.FragmentAnalyticsBinding
import com.google.android.material.snackbar.Snackbar
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.components.XAxis
import java.text.SimpleDateFormat
import java.util.*

class AnalyticsFragment : Fragment() {

    private var _binding: FragmentAnalyticsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AnalyticsViewModel
    private var currentTimeRange = "Month"

    private lateinit var topSpendingAdapter: TopSpendingAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViewModel()
        setupUI()
        observeData()
        
        // Initial fetch for current month
        viewModel.fetchMonthlyData()
    }

    private fun setupViewModel() {
        val database = AppDatabase.getDatabase(requireContext())
        val repository = TransactionRepository(database.transactionDao())
        val application = requireActivity().application

        val factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return AnalyticsViewModel(application, repository) as T
            }
        }

        viewModel = ViewModelProvider(this, factory)[AnalyticsViewModel::class.java]
    }

    private fun setupMonthSpinner() {
        val months = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, months)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerGlobalMonth.adapter = adapter

        // Set current month as default
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
        binding.spinnerGlobalMonth.setSelection(currentMonth)

        binding.spinnerGlobalMonth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.selectedMonthCalendar.set(Calendar.MONTH, position)
                viewModel.fetchMonthlyData()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupUI() {
        setupMonthSpinner()

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, arrayOf("Expense", "Income"))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerType.adapter = adapter

        topSpendingAdapter = TopSpendingAdapter(emptyList())
        binding.rvTopSpending.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTopSpending.adapter = topSpendingAdapter

        binding.btnExportPdf.setOnClickListener {
            val transactions = viewModel.filteredTransactions.value ?: emptyList()
            viewModel.exportPdf(transactions, viewModel.selectedMonthCalendar)
        }

        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressed()
        }
    }

    private fun observeData() {
        viewModel.topSpending.observe(viewLifecycleOwner) {
            topSpendingAdapter.updateData(it)
        }

        viewModel.filteredTransactions.observe(viewLifecycleOwner) { transactions ->
            updateCharts(transactions)
        }

        viewModel.exportState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AnalyticsViewModel.ExportState.Loading -> {
                    binding.btnExportPdf.isEnabled = false
                    Snackbar.make(binding.root, "Generating PDF...", Snackbar.LENGTH_SHORT).show()
                }
                is AnalyticsViewModel.ExportState.Success -> {
                    binding.btnExportPdf.isEnabled = true
                    showExportSuccess(state.file)
                }
                is AnalyticsViewModel.ExportState.Error -> {
                    binding.btnExportPdf.isEnabled = true
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showExportSuccess(file: java.io.File) {
        Snackbar.make(binding.root, "PDF saved to Downloads", Snackbar.LENGTH_LONG)
            .setAction("Open") {
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.provider",
                    file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Open PDF"))
            }
            .show()
    }

    private fun updateCharts(transactions: List<Transaction>) {
        val expenses = transactions.filter { it.transactionType == Transaction.TYPE_EXPENSE }
        
        updateLineChart(expenses)
        updatePieChart(expenses)
        updateHeatmap(expenses)
        
        if (transactions.isEmpty()) {
            Toast.makeText(requireContext(), "No data for selected month", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateLineChart(transactions: List<Transaction>) {
        val cal = viewModel.selectedMonthCalendar
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentCal = Calendar.getInstance()
        
        val isCurrentMonth = cal.get(Calendar.YEAR) == currentCal.get(Calendar.YEAR) &&
                           cal.get(Calendar.MONTH) == currentCal.get(Calendar.MONTH)
        
        val maxDay = if (isCurrentMonth) currentCal.get(Calendar.DAY_OF_MONTH) else daysInMonth
        
        val dailySpending = mutableMapOf<Int, Double>()
        transactions.forEach {
            val tCal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            val day = tCal.get(Calendar.DAY_OF_MONTH)
            dailySpending[day] = (dailySpending[day] ?: 0.0) + it.amount
        }

        val entries = mutableListOf<Entry>()
        for (i in 1..maxDay) {
            entries.add(Entry(i.toFloat(), dailySpending[i]?.toFloat() ?: 0f))
        }

        val dataSet = LineDataSet(entries, "Daily Spending")
        dataSet.color = Color.parseColor("#5B4CF5")
        dataSet.setCircleColor(Color.parseColor("#5B4CF5"))
        dataSet.lineWidth = 2f
        dataSet.circleRadius = 3f
        dataSet.setDrawCircleHole(false)
        dataSet.valueTextSize = 0f
        dataSet.setDrawFilled(true)
        dataSet.fillColor = Color.parseColor("#5B4CF5")
        dataSet.fillAlpha = 50

        val labels = (0..maxDay + 1).map { "Day $it" }
        val markerView = CustomMarkerView(requireContext(), R.layout.layout_chart_marker, labels)

        binding.lineChart.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.textColor = Color.parseColor("#999999")
            axisLeft.setDrawGridLines(true)
            axisLeft.textColor = Color.parseColor("#999999")
            axisRight.isEnabled = false
            setScaleEnabled(false)
            setPinchZoom(false)
            isDoubleTapToZoomEnabled = false
            marker = markerView
            animateX(1000)
            invalidate()
        }
    }

    private fun updatePieChart(transactions: List<Transaction>) {
        val categoryData = transactions.groupBy { it.category }
            .map { PieEntry(it.value.sumOf { t -> t.amount }.toFloat(), it.key) }

        val dataSet = PieDataSet(categoryData, "")
        dataSet.colors = listOf(
            Color.parseColor("#5B4CF5"),
            Color.parseColor("#378ADD"),
            Color.parseColor("#1D9E75"),
            Color.parseColor("#F7931A"),
            Color.parseColor("#D85A30")
        )
        dataSet.valueTextColor = Color.WHITE
        dataSet.valueTextSize = 12f

        val pieMarker = CustomMarkerView(requireContext(), R.layout.layout_chart_marker)

        binding.pieChartCategory.apply {
            data = PieData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT)
            marker = pieMarker
            animateY(1000)
            invalidate()
        }
        
        val accountData = transactions.groupBy { it.accountName }
            .map { PieEntry(it.value.sumOf { t -> t.amount }.toFloat(), it.key) }
            
        val accountDataSet = PieDataSet(accountData, "")
        accountDataSet.colors = dataSet.colors
        binding.pieChartAccount.apply {
            data = PieData(accountDataSet)
            description.isEnabled = false
            legend.isEnabled = false
            isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT)
            marker = pieMarker
            animateY(1000)
            invalidate()
        }
    }

    private fun updateHeatmap(transactions: List<Transaction>) {
        binding.heatmapGrid.removeAllViews()
        
        val cal = viewModel.selectedMonthCalendar.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val startDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0 = Sun
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        
        val currentCal = Calendar.getInstance()
        val isCurrentMonth = cal.get(Calendar.YEAR) == currentCal.get(Calendar.YEAR) &&
                           cal.get(Calendar.MONTH) == currentCal.get(Calendar.MONTH)
        val today = currentCal.get(Calendar.DAY_OF_MONTH)

        val dailySpending = mutableMapOf<Int, Double>()
        transactions.forEach {
            val tCal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            val day = tCal.get(Calendar.DAY_OF_MONTH)
            dailySpending[day] = (dailySpending[day] ?: 0.0) + it.amount
        }

        // Add empty cells for padding
        for (i in 0 until startDayOfWeek) {
            val emptyView = View(requireContext())
            val params = android.widget.GridLayout.LayoutParams()
            params.width = 0
            params.height = resources.getDimensionPixelSize(R.dimen.heatmap_cell_size)
            params.columnSpec = android.widget.GridLayout.spec(i, 1f)
            emptyView.layoutParams = params
            binding.heatmapGrid.addView(emptyView)https://github.com/tilak-star308/ExpenseSense
        }

        for (day in 1..daysInMonth) {
            val cell = View(requireContext())
            val size = resources.getDimensionPixelSize(R.dimen.heatmap_cell_size)
            val params = android.widget.GridLayout.LayoutParams()
            params.width = 0
            params.height = size
            params.setMargins(4, 4, 4, 4)
            val col = (startDayOfWeek + day - 1) % 7
            params.columnSpec = android.widget.GridLayout.spec(col, 1f)
            cell.layoutParams = params

            val amount = dailySpending[day] ?: 0.0
            val isFuture = isCurrentMonth && day > today
            
            cell.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 8f
                setColor(when {
                    isFuture -> Color.parseColor("#F5F5F7")
                    amount <= 0 -> Color.parseColor("#F0F0F0")
                    amount < 500 -> Color.parseColor("#D1C4E9")
                    amount < 2000 -> Color.parseColor("#9575CD")
                    else -> Color.parseColor("#5B4CF5")
                })
            }
            cell.setOnClickListener {
                if (isFuture) return@setOnClickListener

                val popupView = layoutInflater.inflate(R.layout.layout_chart_marker, null)
                val tvTitle = popupView.findViewById<TextView>(R.id.tvMarkerTitle)
                val tvValue = popupView.findViewById<TextView>(R.id.tvMarkerValue)

                tvTitle.text = "Day $day"
                tvValue.text = if (amount > 0) "₹${String.format("%.2f", amount)}" else "No transactions"

                val popupWindow = android.widget.PopupWindow(
                    popupView,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    true
                )

                popupWindow.elevation = 8f
                // Make it transparent so layout corners work
                popupWindow.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))

                // Measure popup to center it over the cell
                popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                val xOffset = -(popupView.measuredWidth - cell.width) / 2
                val yOffset = -(popupView.measuredHeight + cell.height + 8) // Show just above the cell

                popupWindow.showAsDropDown(cell, xOffset, yOffset)
            }
            binding.heatmapGrid.addView(cell)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}