package com.amshu.expensesense

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class HomeFragment : Fragment() {

    private lateinit var rvTransactions: RecyclerView
    private lateinit var tvTotal: TextView
    private lateinit var tvMonthTotal: TextView
    private lateinit var tvEmpty: TextView

    private var transactionList = mutableListOf<Transaction>()
    private lateinit var adapter: TransactionAdapter

    // Budget UI
    private lateinit var budgetViewModel: BudgetViewModel
    private lateinit var accountViewModel: AccountViewModel
    private lateinit var rvAccountsMini: RecyclerView
    private lateinit var layoutBudgetData: LinearLayout
    private lateinit var layoutBudgetInput: LinearLayout
    private lateinit var tvNoBudget: TextView
    private lateinit var btnSetBudget: TextView
    private lateinit var btnEditBudget: TextView
    private lateinit var btnSaveBudget: Button
    private lateinit var etBudgetInput: EditText
    private lateinit var pbBudget: ProgressBar
    private lateinit var tvBudgetSpent: TextView
    private lateinit var tvBudgetRemaining: TextView
    private lateinit var tvBudgetTotal: TextView

    private var accountList = mutableListOf<Account>()
    private lateinit var accountAdapter: AccountMiniAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvTransactions  = view.findViewById(R.id.rvTransactions)
        rvAccountsMini  = view.findViewById(R.id.rvAccountsMini)
        tvTotal         = view.findViewById(R.id.tvTotal)
        tvMonthTotal    = view.findViewById(R.id.tvMonthTotal)
        tvEmpty         = view.findViewById(R.id.tvEmpty)

        // Account Mini Grid
        rvAccountsMini.layoutManager = GridLayoutManager(requireContext(), 2)
        accountAdapter = AccountMiniAdapter(accountList)
        rvAccountsMini.adapter = accountAdapter

        // Budget Views
        layoutBudgetData  = view.findViewById(R.id.layoutBudgetData)
        layoutBudgetInput = view.findViewById(R.id.layoutBudgetInput)
        tvNoBudget        = view.findViewById(R.id.tvNoBudget)
        btnSetBudget      = view.findViewById(R.id.btnSetBudget)
        btnEditBudget     = view.findViewById(R.id.btnEditBudget)
        btnSaveBudget     = view.findViewById(R.id.btnSaveBudget)
        etBudgetInput     = view.findViewById(R.id.etBudgetInput)
        pbBudget          = view.findViewById(R.id.pbBudget)
        tvBudgetSpent     = view.findViewById(R.id.tvBudgetSpent)
        tvBudgetRemaining = view.findViewById(R.id.tvBudgetRemaining)
        tvBudgetTotal     = view.findViewById(R.id.tvBudgetTotal)

        rvTransactions.layoutManager = LinearLayoutManager(requireContext())

        setupBudgetViewModel()
        setupAccountViewModel()

        btnSetBudget.setOnClickListener { 
            showInputMode(true)
        }
        
        btnEditBudget.setOnClickListener {
            // Pre-fill if current budget exists
            val currentAmount = budgetViewModel.budget.value?.totalBudget?.toString() ?: ""
            etBudgetInput.setText(currentAmount)
            showInputMode(true)
        }
        
        btnSaveBudget.setOnClickListener {
            val amount = etBudgetInput.text.toString().toDoubleOrNull()
            if (amount != null && amount > 0) {
                budgetViewModel.setMonthlyBudget(amount)
                showInputMode(false)
                Toast.makeText(requireContext(), "Budget Updated", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Invalid amount", Toast.LENGTH_SHORT).show()
            }
        }

        // Expose the launcher so MainActivity can trigger it via the FAB
        (activity as? MainActivity)?.setAddExpenseLauncher {
            context?.let {
                startActivity(Intent(it, AddExpenseActivity::class.java))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? MainActivity)?.setAddExpenseLauncher(null)
    }

    private fun setupAccountViewModel() {
        val database = AppDatabase.getDatabase(requireContext())
        val repository = AccountRepository(database.accountDao())
        val factory = AccountViewModelFactory(repository)
        accountViewModel = ViewModelProvider(this, factory).get(AccountViewModel::class.java)

        accountViewModel.accounts.observe(viewLifecycleOwner) { accounts ->
            accountList.clear()
            accountList.addAll(accounts)
            accountAdapter.notifyDataSetChanged()
        }
    }

    private fun setupBudgetViewModel() {
        val database = AppDatabase.getDatabase(requireContext())
        val repository = BudgetRepository(database.budgetDao(), database.transactionDao())
        val factory = BudgetViewModelFactory(repository)
        budgetViewModel = ViewModelProvider(this, factory).get(BudgetViewModel::class.java)

        budgetViewModel.budget.observe(viewLifecycleOwner) { budget ->
            updateBudgetUI(budget)
        }
    }

    private fun updateBudgetUI(budget: Budget?) {
        if (budget == null) {
            layoutBudgetData.visibility = View.GONE
            layoutBudgetInput.visibility = View.GONE
            tvNoBudget.visibility = View.VISIBLE
            btnSetBudget.visibility = View.VISIBLE
            btnEditBudget.visibility = View.GONE
        } else {
            // Only update display if not currently in input mode
            if (layoutBudgetInput.visibility != View.VISIBLE) {
                layoutBudgetData.visibility = View.VISIBLE
                tvNoBudget.visibility = View.GONE
                btnSetBudget.visibility = View.GONE
                btnEditBudget.visibility = View.VISIBLE
            }

            val spent = budget.totalBudget - budget.remainingBudget
            tvBudgetSpent.text = "₹%.2f".format(spent)
            tvBudgetRemaining.text = "₹%.2f".format(budget.remainingBudget)
            tvBudgetTotal.text = "Total Budget: ₹%.2f".format(budget.totalBudget)

            // Progress
            val progress = ((spent / budget.totalBudget) * 100).toInt()
            pbBudget.progress = progress.coerceIn(0, 100)

            // Color coding
            if (budget.remainingBudget < 0) {
                tvBudgetRemaining.setTextColor(Color.RED)
                pbBudget.progressDrawable.setTint(Color.RED)
            } else {
                tvBudgetRemaining.setTextColor(Color.parseColor("#2ABFBF"))
                pbBudget.progressDrawable.setTint(Color.parseColor("#2ABFBF"))
            }
        }
    }

    private fun showInputMode(isInput: Boolean) {
        if (isInput) {
            layoutBudgetData.visibility = View.GONE
            tvNoBudget.visibility = View.GONE
            btnSetBudget.visibility = View.GONE
            btnEditBudget.visibility = View.GONE
            layoutBudgetInput.visibility = View.VISIBLE
            etBudgetInput.requestFocus()
        } else {
            layoutBudgetInput.visibility = View.GONE
            // Let observer restore the correct state
            updateBudgetUI(budgetViewModel.budget.value)
        }
    }

    // Removed showSetBudgetDialog as it's no longer used

    override fun onResume() {
        super.onResume()
        loadFromRoom()      // offline-first: instant display
        syncWithFirebase()  // background sync, non-blocking
        budgetViewModel.loadCurrentMonthBudget()
        accountViewModel.loadAccounts()
    }

    // ── Room load ─────────────────────────────────────────────────────────────

    private fun loadFromRoom() {
        Thread {
            val records = AppDatabase.getDatabase(requireContext())
                .transactionDao()
                .getAllTransactions()

            activity?.runOnUiThread {
                transactionList.clear()
                transactionList.addAll(records)
                updateSummaryViews(records)
                updateEmptyState()

                if (!::adapter.isInitialized) {
                    adapter = TransactionAdapter(transactionList) { deleteTransaction(it) }
                    rvTransactions.adapter = adapter
                } else {
                    adapter.notifyDataSetChanged()
                }
            }
        }.start()
    }

    // ── Firebase sync ─────────────────────────────────────────────────────────

    private fun syncWithFirebase() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || user.email == null) return
        
        val username = user.email!!.substringBefore("@")
        
        FirebaseDatabase.getInstance()
            .getReference("users/$username/expenses")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded || context == null) return
                    val safeContext = requireContext()
                    
                    Thread {
                        val dao = AppDatabase.getDatabase(safeContext).transactionDao()
                        val localMap = dao.getAllTransactions().associateBy { it.firebaseId }
                        var hasNew = false

                        // snapshot is `expenses`. Children are Categories (Food, Entertainment, etc)
                        for (categorySnapshot in snapshot.children) {
                            val categoryName = categorySnapshot.key ?: "Other"
                            
                            // children of category are the actual expense nodes (Title-Timestamp keys)
                            for (expenseSnapshot in categorySnapshot.children) {
                                val uniqueTitleKey = expenseSnapshot.key
                                if (uniqueTitleKey != null && !localMap.containsKey(uniqueTitleKey)) {
                                    
                                    // Extract the clean title (everything before the last '-')
                                    val cleanTitle = if (uniqueTitleKey.contains("-")) {
                                        uniqueTitleKey.substringBeforeLast("-")
                                    } else {
                                        uniqueTitleKey
                                    }
                                    
                                    val timestamp = expenseSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                                    val amount = expenseSnapshot.child("amount").getValue(Double::class.java) ?: 0.0
                                    val accountName = expenseSnapshot.child("account").getValue(String::class.java) ?: "Cash"
                                    val paymentMethod = expenseSnapshot.child("paymentMethod").getValue(String::class.java) ?: "Cash"
                                    
                                    val record = Transaction(
                                        title      = cleanTitle,
                                        amount     = amount,
                                        category   = categoryName,
                                        accountName = accountName,
                                        timestamp  = timestamp,
                                        paymentMethod = paymentMethod,
                                        referenceId = accountName,
                                        firebaseId = uniqueTitleKey // Store unique key for deletion
                                    )
                                    dao.insertTransaction(record)
                                    hasNew = true
                                }
                            }
                        }
                        if (hasNew && isAdded) {
                            loadFromRoom()
                            activity?.runOnUiThread { budgetViewModel.loadCurrentMonthBudget() }
                        }
                    }.start()
                }

                override fun onCancelled(error: DatabaseError) { /* silent fail */ }
            })

        // Also sync budgets
        FirebaseDatabase.getInstance()
            .getReference("users/$username/budgets")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded || context == null) return
                    val safeContext = requireContext()
                    
                    Thread {
                        val dao = AppDatabase.getDatabase(safeContext).budgetDao()
                        for (budgetSnapshot in snapshot.children) {
                            val monthYear = budgetSnapshot.key ?: continue
                            val total = budgetSnapshot.child("totalBudget").getValue(Double::class.java) ?: 0.0
                            val remaining = budgetSnapshot.child("remainingBudget").getValue(Double::class.java) ?: 0.0
                            
                            dao.insertBudget(Budget(monthYear, total, remaining))
                        }
                        activity?.runOnUiThread { budgetViewModel.loadCurrentMonthBudget() }
                    }.start()
                }

                override fun onCancelled(error: DatabaseError) { }
            })

        // Also sync accounts
        FirebaseDatabase.getInstance()
            .getReference("users/$username/accounts")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded || context == null) return
                    val safeContext = requireContext()
                    
                    Thread {
                        val dao = AppDatabase.getDatabase(safeContext).accountDao()
                        for (accountSnapshot in snapshot.children) {
                            val account = accountSnapshot.getValue(Account::class.java)
                            if (account != null) {
                                dao.insertAccount(account)
                            }
                        }
                        activity?.runOnUiThread { accountViewModel.loadAccounts() }
                    }.start()
                }

                override fun onCancelled(error: DatabaseError) { }
            })
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    private fun deleteTransaction(record: Transaction) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || user.email == null) return
        
        val username = user.email!!.substringBefore("@")

        val database = AppDatabase.getDatabase(requireContext())
        val paymentRepository = PaymentRepository(
            database,
            database.transactionDao(),
            database.accountDao(),
            database.debitCardDao(),
            database.creditCardDao(),
            database.budgetDao()
        )
        paymentRepository.deleteExpense(record, username) { success, _ ->
            if (!success) return@deleteExpense
            activity?.runOnUiThread {
                loadFromRoom()
                budgetViewModel.loadCurrentMonthBudget()
                accountViewModel.loadAccounts()
            }
        }
    }

    // ── Summary helpers ───────────────────────────────────────────────────────

    private fun updateSummaryViews(records: List<Transaction>) {
        val total = records.sumOf { it.amount }

        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear  = cal.get(Calendar.YEAR)
        val monthTotal = records.filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            c.get(Calendar.MONTH) == currentMonth && c.get(Calendar.YEAR) == currentYear
        }.sumOf { it.amount }

        tvTotal.text      = "Total: ₹%.2f".format(total)
        tvMonthTotal.text = "This Month: ₹%.2f".format(monthTotal)
    }

    private fun updateEmptyState() {
        if (transactionList.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            rvTransactions.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            rvTransactions.visibility = View.VISIBLE
        }
    }
}
