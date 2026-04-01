package com.amshu.expensesense

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.HapticFeedbackConstants
import java.util.Collections
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
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.transition.TransitionManager
import android.view.animation.DecelerateInterpolator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import android.util.TypedValue
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat

class HomeFragment : Fragment() {

    private lateinit var rvTransactions: RecyclerView
    private lateinit var tvTotal: TextView
    private lateinit var tvMonthTotal: TextView
    private lateinit var tvEmpty: TextView

    private var transactionList = mutableListOf<Transaction>()
    private lateinit var adapter: TransactionAdapter

    // Card Stack
    private lateinit var cardStackContainer: FrameLayout
    private lateinit var cardViewModel: CardViewModel

    // Budget UI
    private lateinit var budgetViewModel: BudgetViewModel
    private lateinit var accountViewModel: AccountViewModel

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
    private lateinit var tvHeaderCashBalance: TextView


    // Swipe stacks
    private var activeStack = mutableListOf<CardUIModel>()
    private var hiddenStack = mutableListOf<CardUIModel>()
    private val leftStack = mutableListOf<View>()
    private val leftStackModels = mutableListOf<CardUIModel>()
    private lateinit var leftStackContainer: FrameLayout
    
    // Reordering State
    private var isReordering = false
    private var draggedViewTag: String? = null
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragInitialTranslationY = 0f

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvTransactions = view.findViewById(R.id.rvTransactions)
        tvTotal = view.findViewById(R.id.tvTotal)
        tvMonthTotal = view.findViewById(R.id.tvMonthTotal)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        cardStackContainer = view.findViewById(R.id.cardStackContainer)
        leftStackContainer = view.findViewById(R.id.leftStackContainer)

        tvHeaderCashBalance = view.findViewById(R.id.tvHeaderCashBalance)

        // Budget Views
        layoutBudgetData = view.findViewById(R.id.layoutBudgetData)
        layoutBudgetInput = view.findViewById(R.id.layoutBudgetInput)
        tvNoBudget = view.findViewById(R.id.tvNoBudget)
        btnSetBudget = view.findViewById(R.id.btnSetBudget)
        btnEditBudget = view.findViewById(R.id.btnEditBudget)
        btnSaveBudget = view.findViewById(R.id.btnSaveBudget)
        etBudgetInput = view.findViewById(R.id.etBudgetInput)
        pbBudget = view.findViewById(R.id.pbBudget)
        tvBudgetSpent = view.findViewById(R.id.tvBudgetSpent)
        tvBudgetRemaining = view.findViewById(R.id.tvBudgetRemaining)
        tvBudgetTotal = view.findViewById(R.id.tvBudgetTotal)

        rvTransactions.layoutManager = LinearLayoutManager(requireContext())

        setupBudgetViewModel()
        setupAccountViewModel()
        setupCardViewModel()
        setupBackgroundSwipe()

        btnSetBudget.setOnClickListener { showInputMode(true) }
        btnEditBudget.setOnClickListener {
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

        (activity as? MainActivity)?.setAddExpenseLauncher {
            context?.let { startActivity(Intent(it, AddExpenseActivity::class.java)) }
        }

        // Quick Actions Click Listeners
        view.findViewById<View>(R.id.cardScanReceipt).setOnClickListener {
            (activity as? MainActivity)?.triggerBillScan()
        }

        view.findViewById<View>(R.id.cardUploadStatement).setOnClickListener {
            startActivity(Intent(requireContext(), StatementReconciliationActivity::class.java))
        }
    }

    private fun setupBackgroundSwipe() {
        var startX = 0f
        cardStackContainer.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = startX - event.rawX
                    if (deltaX < -150) restoreLastCard()
                    true
                }
                else -> false
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
            
            // Update top-right Cash display
            val cashAccount = accounts.find { it.name.equals("Cash", ignoreCase = true) }
            tvHeaderCashBalance.text = "₹${String.format("%.0f", cashAccount?.balance ?: 0.0)}"

            if (activeStack.isNotEmpty()) renderCardStack(activeStack)
        }
    }

    private fun setupCardViewModel() {
        val database = AppDatabase.getDatabase(requireContext())
        val repository = CardRepository(
            database.debitCardDao(),
            database.creditCardDao(),
            database.accountDao(),
            database.cardDao()
        )
        val factory = CardViewModelFactory(repository)
        cardViewModel = ViewModelProvider(this, factory).get(CardViewModel::class.java)

        cardViewModel.cards.observe(viewLifecycleOwner) { cards ->
            if (activeStack.isEmpty() && hiddenStack.isEmpty()) {
                activeStack.addAll(cards.take(3))
                hiddenStack.addAll(cards.drop(3))
            } else {
                val freshMap = cards.associateBy { it.cardNumber }
                activeStack.forEachIndexed { idx, oldModel ->
                    freshMap[oldModel.cardNumber]?.let { activeStack[idx] = it }
                }
                hiddenStack.forEachIndexed { idx, oldModel ->
                    freshMap[oldModel.cardNumber]?.let { hiddenStack[idx] = it }
                }
            }
            renderCardStack(activeStack)
        }
        cardViewModel.loadCards()
    }

    private fun renderCardStack(cards: List<CardUIModel>) {
        if (cards.isEmpty()) {
            cardStackContainer.removeAllViews()
            return
        }
        cardStackContainer.visibility = View.VISIBLE
        TransitionManager.beginDelayedTransition(cardStackContainer)

        val density = resources.displayMetrics.density
        val accountBalances = accountList.associate { it.name to it.balance }

        val newTags = cards.map { it.cardNumber }
        for (i in cardStackContainer.childCount - 1 downTo 0) {
            val child = cardStackContainer.getChildAt(i)
            if (child.tag == "vanishing") continue
            if (!newTags.contains(child.tag)) cardStackContainer.removeView(child)
        }

        for (i in (cards.size - 1) downTo 0) {
            val model = cards[i]
            var cardView = cardStackContainer.findViewWithTag<CardView>(model.cardNumber)
            
            if (cardView == null) {
                cardView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.card_item, cardStackContainer, false) as CardView
                cardView.tag = model.cardNumber
                cardStackContainer.addView(cardView)
            }

            bindCardData(cardView, model, accountBalances)

            if (cardView.tag == draggedViewTag) {
                cardView.bringToFront()
                cardView.animate()
                    .scaleX(1.05f).scaleY(1.05f)
                    .setDuration(150)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
                ViewCompat.setElevation(cardView, 100 * density)
            } else {
                val targetX = i * 24 * density
                val targetY = -i * 14 * density
                val scale = if (i == 0) 1.0f else 0.95f - ((i - 1) * 0.02f)
                val elevation = (cards.size - i).toFloat() * 2 * density
                cardView.cardElevation = elevation
                cardView.bringToFront()
                cardView.animate()
                    .translationX(targetX)
                    .translationY(targetY)
                    .scaleX(scale).scaleY(scale)
                    .rotation(0f)
                    .setDuration(250)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }

            attachSwipeListener(cardView)

            cardView.setOnLongClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                isReordering = true
                draggedViewTag = it.tag as String
                renderCardStack(activeStack)
                true
            }
        }
    }

    private fun bindCardData(cardView: View, model: CardUIModel, accountBalances: Map<String, Double>) {
        val ivCardBg = cardView.findViewById<ImageView>(R.id.ivCardBg)
        val tvCardNumber = cardView.findViewById<TextView>(R.id.tvCardNumberDisplay)
        val tvCardHolder = cardView.findViewById<TextView>(R.id.tvCardHolderDisplay)
        val tvBalance = cardView.findViewById<TextView>(R.id.tvBalanceDisplay)

        tvCardNumber.text = maskCardNumber(model.cardNumber)
        tvCardHolder.text = model.cardHolderName.uppercase()

        when (model) {
            is CardUIModel.Credit -> {
                val resId = resources.getIdentifier(model.drawableName ?: "", "drawable", requireContext().packageName)
                ivCardBg.setImageResource(if (resId != 0) resId else R.drawable.defaultcreditcard)
                tvBalance.text = "Avl: ₹${String.format("%.2f", model.availableLimit)}"
            }
            is CardUIModel.Debit -> {
                val resId = resources.getIdentifier(model.drawableName ?: "", "drawable", requireContext().packageName)
                ivCardBg.setImageResource(if (resId != 0) resId else R.drawable.defaultdebitcard)
                val balance = accountBalances[model.linkedBankAccountId] ?: 0.0
                tvBalance.text = "Bal: ₹${String.format("%.2f", balance)}"
            }
        }
    }

    private val dragHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var dragRunnable: Runnable? = null

    private fun attachSwipeListener(cardView: CardView) {
        cardView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = event.rawX
                    dragStartY = event.rawY
                    dragInitialTranslationY = v.translationY

                    // Touch press feedback
                    v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(100).start()

                    // Manual Long Press Detection
                    dragRunnable?.let { dragHandler.removeCallbacks(it) }
                    dragRunnable = Runnable {
                        if (!isReordering) {
                            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            isReordering = true
                            draggedViewTag = v.tag as String
                            v.parent.requestDisallowInterceptTouchEvent(true) // 🔒 Lock parent scroll
                            renderCardStack(activeStack)
                        }
                    }
                    dragHandler.postDelayed(dragRunnable!!, 500)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - dragStartX
                    val deltaY = event.rawY - dragStartY

                    if (isReordering && v.tag == draggedViewTag) {
                        v.parent.requestDisallowInterceptTouchEvent(true) // 🔒 Keep parent locked
                        
                        val density = resources.displayMetrics.density
                        v.translationY = dragInitialTranslationY + deltaY
                        
                        // Limit dragging to within reasonable range
                        val maxUp = - (activeStack.size * 60 * density)
                        val maxDown = 150 * density
                        v.translationY = v.translationY.coerceIn(maxUp, maxDown)
                        
                        val currentIdx = activeStack.indexOfFirst { it.cardNumber == v.tag }
                        if (currentIdx != -1) {
                            val targetX = currentIdx * 24 * density
                            v.translationX = targetX + (deltaX * 0.2f)
                        }
                        checkAndPerformSwap(v)
                        return@setOnTouchListener true
                    }

                    // Cancel long press timer if finger moved too much before trigger
                    if (Math.abs(deltaX) > 15 || Math.abs(deltaY) > 15) {
                        dragRunnable?.let { dragHandler.removeCallbacks(it) }
                    }

                    if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 20) {
                        v.parent.requestDisallowInterceptTouchEvent(true)
                        // Follow finger horizontally
                        v.translationX = deltaX * 0.6f
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragRunnable?.let { dragHandler.removeCallbacks(it) }

                    // Release press feedback
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()

                    if (isReordering && v.tag == draggedViewTag) {
                        isReordering = false
                        draggedViewTag = null
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        renderCardStack(activeStack)
                        return@setOnTouchListener true
                    }

                    val deltaXAbsolute = dragStartX - event.rawX
                    if (deltaXAbsolute > 150) animateCardDismissal(v, true)
                    else if (deltaXAbsolute < -150) restoreLastCard()
                    else {
                        // Snap back
                        v.animate().translationX(0f).setDuration(220)
                            .setInterpolator(DecelerateInterpolator()).start()
                    }
                    v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun checkAndPerformSwap(draggedView: View) {
        val currentIdx = activeStack.indexOfFirst { it.cardNumber == draggedView.tag }
        if (currentIdx == -1) return
        val density = resources.displayMetrics.density
        val currentY = draggedView.translationY
        
        if (currentIdx > 0) {
            val neighborY = -(currentIdx - 1) * 14 * density
            val threshold = (neighborY + (-(currentIdx) * 14 * density)) / 2
            if (currentY > threshold) {
                Collections.swap(activeStack, currentIdx, currentIdx - 1)
                draggedView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                renderCardStack(activeStack)
            }
            return
        }
        if (currentIdx < activeStack.size - 1) {
            val neighborY = -(currentIdx + 1) * 14 * density
            val threshold = (neighborY + (-(currentIdx) * 14 * density)) / 2
            if (currentY < threshold) {
                Collections.swap(activeStack, currentIdx, currentIdx + 1)
                draggedView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                renderCardStack(activeStack)
            }
        }
    }

    private fun animateCardDismissal(view: View, isLeft: Boolean) {
        if (!isLeft) return
        val density = resources.displayMetrics.density
        val cardWidth = view.width.toFloat()
        val targetX = (cardWidth * 0.03f) - view.left - cardWidth

        // Animate remaining stack cards forward smoothly
        activeStack.forEachIndexed { idx, model ->
            if (idx == 0) return@forEachIndexed
            val child = cardStackContainer.findViewWithTag<View>(model.cardNumber)
            val newIdx = idx - 1
            val newScale = if (newIdx == 0) 1.0f else 0.95f - ((newIdx - 1) * 0.02f)
            child?.animate()
                ?.translationX(newIdx * 24 * density)
                ?.translationY(-newIdx * 14 * density)
                ?.scaleX(newScale)?.scaleY(newScale)
                ?.setDuration(300)?.setInterpolator(DecelerateInterpolator())?.start()
        }

        view.animate()
            .translationX(targetX)
            .translationY(30f)
            .scaleX(0.92f).scaleY(0.92f)
            .alpha(1f)
            .setDuration(320)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (activeStack.isNotEmpty()) {
                    val dismissedModel = activeStack.removeAt(0)
                    leftStackModels.add(dismissedModel)
                    if (hiddenStack.isNotEmpty()) activeStack.add(hiddenStack.removeAt(0))
                    cardStackContainer.removeView(view)
                    view.translationX = targetX
                    view.translationY = 30f
                    view.alpha = 1f
                    view.scaleX = 0.92f
                    view.scaleY = 0.92f
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        view.setRenderEffect(RenderEffect.createBlurEffect(12f, 12f, Shader.TileMode.CLAMP))
                    }
                    ViewCompat.setElevation(view, 0f)
                    leftStackContainer.addView(view)
                    leftStack.add(view)
                    view.bringToFront()
                    renderCardStack(activeStack)
                }
            }.start()
    }

    private fun restoreLastCard() {
        if (leftStack.isEmpty() || leftStackModels.isEmpty()) return
        val restoredView = leftStack.removeAt(leftStack.size - 1)
        val restoredModel = leftStackModels.removeAt(leftStackModels.size - 1)
        if (activeStack.size >= 3) {
            val modelToOverflow = activeStack[activeStack.size - 1]
            val overflowView = cardStackContainer.findViewWithTag<View>(modelToOverflow.cardNumber)
            overflowView?.let {
                it.tag = "vanishing"
                it.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(300)
                    .withEndAction { cardStackContainer.removeView(it) }.start()
            }
            hiddenStack.add(0, activeStack.removeAt(activeStack.size - 1))
        }
        activeStack.add(0, restoredModel)
        leftStackContainer.removeView(restoredView)
        cardStackContainer.addView(restoredView)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) restoredView.setRenderEffect(null)
        ViewCompat.setElevation(restoredView, 10f * resources.displayMetrics.density)
        // Start slightly off-screen to left, then settle into top
        restoredView.translationX = -restoredView.width.toFloat().coerceAtLeast(300f)
        restoredView.animate()
            .translationX(0f)
            .translationY(0f)
            .scaleX(1f).scaleY(1f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { renderCardStack(activeStack) }
            .start()
        renderCardStack(activeStack)
    }

    private fun maskCardNumber(number: String): String {
        return if (number.length < 4) number else "**** ${number.takeLast(4)}"
    }

    private fun setupBudgetViewModel() {
        val database = AppDatabase.getDatabase(requireContext())
        val repository = BudgetRepository(database.budgetDao(), database.transactionDao())
        val factory = BudgetViewModelFactory(repository)
        budgetViewModel = ViewModelProvider(this, factory).get(BudgetViewModel::class.java)
        budgetViewModel.budget.observe(viewLifecycleOwner) { updateBudgetUI(it) }
        budgetViewModel.loadCurrentMonthBudget()
    }

    private fun updateBudgetUI(budget: Budget?) {
        if (budget == null) {
            layoutBudgetData.visibility = View.GONE
            layoutBudgetInput.visibility = View.GONE
            tvNoBudget.visibility = View.VISIBLE
            btnSetBudget.visibility = View.VISIBLE
            btnEditBudget.visibility = View.GONE
        } else {
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
            pbBudget.progress = ((spent / budget.totalBudget) * 100).toInt().coerceIn(0, 100)
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
            updateBudgetUI(budgetViewModel.budget.value)
        }
    }

    override fun onResume() {
        super.onResume()
        loadFromRoom()
        syncWithFirebase()
        budgetViewModel.loadCurrentMonthBudget()
        accountViewModel.loadAccounts()
        cardViewModel.loadCards()
    }

    private fun loadFromRoom() {
        Thread {
            val records = AppDatabase.getDatabase(requireContext()).transactionDao().getAllTransactions()
            val sortedList = records.sortedByDescending { it.timestamp }
            activity?.runOnUiThread {
                transactionList.clear()
                transactionList.addAll(sortedList)
                updateSummaryViews(sortedList)
                updateEmptyState()
                
                val limitedList = transactionList.take(5)
                adapter = TransactionAdapter(limitedList) { deleteTransaction(it) }
                rvTransactions.adapter = adapter
            }
        }.start()
    }

    private fun syncWithFirebase() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val username = user.email?.substringBefore("@") ?: return
        FirebaseDatabase.getInstance().getReference("users/$username/expenses")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded || context == null) return
                    val safeContext = requireContext()
                    Thread {
                        val dao = AppDatabase.getDatabase(safeContext).transactionDao()
                        val localMap = dao.getAllTransactions().associateBy { it.firebaseId }
                        var hasNew = false
                        for (categorySnapshot in snapshot.children) {
                            for (expenseSnapshot in categorySnapshot.children) {
                                val key = expenseSnapshot.key ?: continue
                                if (!localMap.containsKey(key)) {
                                    dao.insertTransaction(Transaction(
                                        title = key.substringBeforeLast("-"),
                                        amount = expenseSnapshot.child("amount").getValue(Double::class.java) ?: 0.0,
                                        category = categorySnapshot.key ?: "Other",
                                        accountName = expenseSnapshot.child("account").getValue(String::class.java) ?: "Cash",
                                        timestamp = expenseSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L,
                                        paymentMethod = expenseSnapshot.child("paymentMethod").getValue(String::class.java) ?: "Cash",
                                        referenceId = expenseSnapshot.child("account").getValue(String::class.java) ?: "Cash",
                                        firebaseId = key
                                    ))
                                    hasNew = true
                                }
                            }
                        }
                        if (hasNew && isAdded) loadFromRoom()
                    }.start()
                }
                override fun onCancelled(error: DatabaseError) { }
            })

        FirebaseDatabase.getInstance().getReference("users/$username/budgets")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded || context == null) return
                    val safeContext = requireContext()
                    Thread {
                        val dao = AppDatabase.getDatabase(safeContext).budgetDao()
                        val localBudgets = dao.getAllBudgets().associateBy { it.monthYear }
                        for (budgetSnapshot in snapshot.children) {
                            val monthYear = budgetSnapshot.key ?: continue
                            if (!localBudgets.containsKey(monthYear)) {
                                dao.insertBudget(Budget(monthYear, budgetSnapshot.child("totalBudget").getValue(Double::class.java) ?: 0.0, budgetSnapshot.child("remainingBudget").getValue(Double::class.java) ?: 0.0))
                            }
                        }
                        activity?.runOnUiThread { budgetViewModel.loadCurrentMonthBudget() }
                    }.start()
                }
                override fun onCancelled(error: DatabaseError) { }
            })

        FirebaseDatabase.getInstance().getReference("users/$username/accounts")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded || context == null) return
                    val safeContext = requireContext()
                    Thread {
                        val dao = AppDatabase.getDatabase(safeContext).accountDao()
                        val localAccounts = dao.getAllAccounts().associateBy { it.name }
                        for (accountSnapshot in snapshot.children) {
                            val account = accountSnapshot.getValue(Account::class.java)
                            if (account != null && !localAccounts.containsKey(account.name)) {
                                dao.insertAccount(account)
                            }
                        }
                        activity?.runOnUiThread { accountViewModel.loadAccounts() }
                    }.start()
                }
                override fun onCancelled(error: DatabaseError) { }
            })
    }

    private fun deleteTransaction(record: Transaction) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val username = user.email?.substringBefore("@") ?: return
        val database = AppDatabase.getDatabase(requireContext())
        PaymentRepository(database, database.transactionDao(), database.accountDao(), database.debitCardDao(), database.creditCardDao(), database.budgetDao())
            .deleteExpense(record, username) { success, _ ->
                if (success) activity?.runOnUiThread {
                    loadFromRoom()
                    budgetViewModel.loadCurrentMonthBudget()
                    accountViewModel.loadAccounts()
                }
            }
    }

    private fun updateSummaryViews(records: List<Transaction>) {
        val total = records.sumOf { it.amount }
        val cal = Calendar.getInstance()
        val mTotal = records.filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            c.get(Calendar.MONTH) == cal.get(Calendar.MONTH) && c.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
        }.sumOf { it.amount }
        tvTotal.text = "Total: ₹%.2f".format(total)
        tvMonthTotal.text = "This Month: ₹%.2f".format(mTotal)
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
