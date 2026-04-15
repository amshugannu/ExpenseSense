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
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.snackbar.Snackbar
import android.graphics.Canvas
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
import androidx.fragment.app.activityViewModels
import java.util.stream.Collectors
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private lateinit var rvTransactions: RecyclerView
    private lateinit var tvTotal: TextView
    private lateinit var tvMonthTotal: TextView
    private lateinit var tvEmpty: TextView

    private var transactionList = mutableListOf<Transaction>()
    private lateinit var adapter: TransactionAdapter
    private lateinit var cardSwipeTooltip: CardView

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
    private lateinit var cardBudget: View

    private var accountList = mutableListOf<Account>()
    private lateinit var accountAdapter: AccountMiniAdapter
    private lateinit var tvHeaderCashBalance: TextView
    private val expenseViewModel: ExpenseViewModel by activityViewModels()
    
    // Privacy State
    private val revealedCardNumbers = mutableSetOf<String>()
    private val visibilityHandler = android.os.Handler(android.os.Looper.getMainLooper())


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
    private var isLastUpdateFromFirebase = false

    private val detailsLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val action = result.data?.getStringExtra("action")
            if (action == "delete") {
                val id = result.data?.getIntExtra("id", -1) ?: -1
                val deletedItem = transactionList.find { it.id == id }
                if (deletedItem != null) {
                    val position = adapter.records.indexOf(deletedItem)
                    if (position != -1) {
                        adapter.removeItem(position)
                        val snackbar = com.google.android.material.snackbar.Snackbar.make(rvTransactions, "${deletedItem.title} removed", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                        snackbar.setAction("UNDO") {
                            adapter.restoreItem(deletedItem, position)
                        }
                        snackbar.addCallback(object : com.google.android.material.snackbar.Snackbar.Callback() {
                            override fun onDismissed(transientBottomBar: com.google.android.material.snackbar.Snackbar?, event: Int) {
                                if (event != DISMISS_EVENT_ACTION) {
                                    deleteTransaction(deletedItem)
                                }
                            }
                        })
                        snackbar.show()
                    }
                }
            } else if (action == "edit") {
                loadFromRoom()
            }
        }
    }

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
        cardSwipeTooltip = view.findViewById(R.id.cardSwipeTooltip)
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
        cardBudget = view.findViewById(R.id.cardBudget)

        rvTransactions.layoutManager = LinearLayoutManager(requireContext())

        setupExpenseViewModel()
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

        view.findViewById<View>(R.id.tvSeeAllTransactions).setOnClickListener {
            startActivity(Intent(requireContext(), TransactionsActivity::class.java))
        }

        view.findViewById<View>(R.id.cardViewInsights).setOnClickListener {
            startActivity(Intent(requireContext(), AiInsightsActivity::class.java))
        }

        view.findViewById<View>(R.id.cardAddExpense).setOnClickListener {
            startActivity(Intent(requireContext(), AddExpenseActivity::class.java))
        }

        setupPersonalizedGreeting(view)
        
        // Trigger Smart Popups
        view.postDelayed({ checkAndShowPopups() }, 1000)
    }

    private fun setupPersonalizedGreeting(view: View) {
        val tvGreeting: TextView = view.findViewById(R.id.tvGreeting)
        val tvUsername: TextView = view.findViewById(R.id.tvUsername)

        val prefs = requireContext().getSharedPreferences("ExpenseSensePrefs", android.content.Context.MODE_PRIVATE)
        val fullName = prefs.getString("user_full_name", "User") ?: "User"
        
        // Split name to show only first name for a friendlier feel
        val firstName = fullName.split(" ").firstOrNull() ?: fullName
        tvUsername.text = firstName

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 0..11 -> "Good Morning,"
            in 12..15 -> "Good Afternoon,"
            in 16..20 -> "Good Evening,"
            else -> "Good Night,"
        }
        tvGreeting.text = greeting
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

    private fun setupExpenseViewModel() {
        expenseViewModel.totalBalance.observe(viewLifecycleOwner) { total ->
            tvTotal.text = "Total: ₹%.2f".format(total)
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

        cardViewModel.cards.observe(viewLifecycleOwner) { pair ->
            val cards = pair.first
            isLastUpdateFromFirebase = pair.second
            val sourceText = if (isLastUpdateFromFirebase) "FIREBASE" else "ROOM"
            
            android.util.Log.d("FLOW_DEBUG", "Cards observer triggered from $sourceText source")

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

       // --- Overlap Logic for Cards ---
    // Instead of a list, we use a FrameLayout and manually calculate offsets.
    // Each card is shifted based on its position 'i' in the stack.
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
                // Here is the CORE overlapping calculation:
                // targetX: moves to right, targetY: moves up for a staggered look
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

    private fun bindCardData(
        cardView: View,
        model: CardUIModel,
        accountBalances: Map<String, Double>
    ) {
        val source = if (isLastUpdateFromFirebase) "FIREBASE" else "ROOM"
        val balanceVal = if (model is CardUIModel.Credit) model.availableLimit else accountBalances[(model as? CardUIModel.Debit)?.linkedBankAccountId] ?: 0.0
        android.util.Log.d("FLOW_DEBUG", "UI updated from $source with card balance: $balanceVal")
        
        val ivCardBg = cardView.findViewById<ImageView>(R.id.ivCardBg)
        val tvCardNumber = cardView.findViewById<TextView>(R.id.tvCardNumberDisplay)
        val tvCardHolder = cardView.findViewById<TextView>(R.id.tvCardHolderDisplay)
        val tvBalance = cardView.findViewById<TextView>(R.id.tvBalanceDisplay)
        val ivEyeToggle = cardView.findViewById<ImageView>(R.id.ivEyeToggle)

        tvCardNumber.text = maskCardNumber(model.cardNumber)
        tvCardHolder.text = model.cardHolderName.uppercase()

        val privacyMode = BalancePrivacyManager.getPrivacyMode(requireContext())
        val isRevealed = revealedCardNumbers.contains(model.cardNumber)

        val formatBalance = when (model) {
            is CardUIModel.Credit -> "Avl: ₹${String.format("%.2f", model.availableLimit)}"
            is CardUIModel.Debit -> {
                val balance = accountBalances[model.linkedBankAccountId] ?: 0.0
                "Bal: ₹${String.format("%.2f", balance)}"
            }
        }

        if (privacyMode == BalancePrivacyManager.MODE_ALWAYS_VISIBLE || isRevealed) {
            tvBalance.text = formatBalance
        } else {
            tvBalance.text = BalancePrivacyManager.maskBalance(formatBalance)
        }

        ivEyeToggle.setOnClickListener {
            handleEyeClick(model.cardNumber, privacyMode)
        }

        when (model) {
            is CardUIModel.Credit -> {
                val resId = resources.getIdentifier(
                    model.drawableName ?: "",
                    "drawable",
                    requireContext().packageName
                )
                ivCardBg.setImageResource(if (resId != 0) resId else R.drawable.defaultcreditcard)
            }
            is CardUIModel.Debit -> {
                val resId = resources.getIdentifier(
                    model.drawableName ?: "",
                    "drawable",
                    requireContext().packageName
                )
                ivCardBg.setImageResource(if (resId != 0) resId else R.drawable.defaultdebitcard)
            }
        }
    }

    private fun handleEyeClick(cardNumber: String, mode: Int) {
        when (mode) {
            BalancePrivacyManager.MODE_SHOW_ON_CLICK -> {
                revealBalance(cardNumber, 5000)
            }
            BalancePrivacyManager.MODE_SHOW_ON_CLICK_PIN -> {
                showPinVerificationDialog {
                    revealBalance(cardNumber, 20000)
                }
            }
        }
    }

    private fun revealBalance(cardNumber: String, duration: Long) {
        revealedCardNumbers.add(cardNumber)
        renderCardStack(activeStack)
        
        visibilityHandler.postDelayed({
            revealedCardNumbers.remove(cardNumber)
            renderCardStack(activeStack)
        }, duration)
    }

    private fun showPinVerificationDialog(onSuccess: () -> Unit) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_pin_setup, null)
        val etPin = dialogView.findViewById<EditText>(R.id.etPin)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvPinTitle)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvPinSubtitle)
        val tvError = dialogView.findViewById<TextView>(R.id.tvPinError)

        tvTitle.text = "Enter Security PIN"
        tvSubtitle.text = "Please enter your 6-digit PIN to show the balance."
        etPin.hint = "Enter PIN"

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Verify", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val enteredPin = etPin.text.toString()
            val savedPin = BalancePrivacyManager.getPIN(requireContext())
            if (enteredPin == savedPin) {
                onSuccess()
                dialog.dismiss()
            } else {
                tvError.text = "Incorrect PIN"
                tvError.visibility = View.VISIBLE
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    private val dragHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var dragRunnable: Runnable? = null

    // --- Swipe Logic for Cards ---
    // Handles horizontal dismissal (swipe left) and vertical reordering (long press).
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
                        val maxUp = -(activeStack.size * 60 * density)
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

    // Logic to move a dismissed card to the background "leftStack"
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
                        view.setRenderEffect(
                            RenderEffect.createBlurEffect(
                                12f,
                                12f,
                                Shader.TileMode.CLAMP
                            )
                        )
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


    private fun setupBudgetViewModel() {
        val database = AppDatabase.getDatabase(requireContext())
        val repository = BudgetRepository(database.budgetDao(), database.transactionDao())
        val factory = BudgetViewModelFactory(repository)
        budgetViewModel = ViewModelProvider(this, factory).get(BudgetViewModel::class.java)
        budgetViewModel.budget.observe(viewLifecycleOwner) { budget ->
            updateBudgetUI(budget)
        }
        budgetViewModel.loadCurrentMonthBudget()

        cardBudget.setOnClickListener {
            val intent = Intent(requireContext(), BudgetDetailsActivity::class.java)
            startActivity(intent)
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
            if (layoutBudgetInput.visibility != View.VISIBLE) {
                layoutBudgetData.visibility = View.VISIBLE
                tvNoBudget.visibility = View.GONE
                btnSetBudget.visibility = View.GONE
                btnEditBudget.visibility = View.VISIBLE
            }
            val totalBudget = budget.totalBudget
            val remainingBudget = budget.remainingBudget
            val spent = totalBudget - remainingBudget
            
            tvBudgetSpent.text = "₹%.2f".format(spent)
            tvBudgetRemaining.text = "₹%.2f".format(remainingBudget)
            tvBudgetTotal.text = "Total Budget: ₹%.2f".format(totalBudget)
            
            val progress = if (totalBudget > 0) ((spent / totalBudget) * 100).toInt() else 0
            pbBudget.progress = progress.coerceIn(0, 100)
            
            // Step 5: Color Logic
            val colorTeal = Color.parseColor("#2ABFBF")
            val colorOrange = Color.parseColor("#FFA726")
            val colorRed = Color.parseColor("#EF5350")
            
            val indicatorColor = when {
                progress >= 100 -> colorRed
                progress >= 80 -> colorOrange
                else -> colorTeal
            }
            
            pbBudget.progressDrawable.setTint(indicatorColor)
            
            if (remainingBudget < 0) {
                tvBudgetRemaining.setTextColor(colorRed)
            } else {
                tvBudgetRemaining.setTextColor(colorTeal)
            }
        }
    }

    private fun checkAndShowPopups() {
        if (!isAdded) return
        val context = requireContext()
        val prefs = context.getSharedPreferences("ExpenseSensePrefs", android.content.Context.MODE_PRIVATE)
        val currentMonth = MonthUtils.getCurrentMonthKey()
        val prevMonth = MonthUtils.getPreviousMonthKey()

        // 1. Check Savings achievement from previous month
        val savingsDismissed = prefs.getBoolean("shownSavedPopup_$prevMonth", false)
        if (!savingsDismissed) {
            viewLifecycleOwner.lifecycleScope.launch {
                val db = AppDatabase.getDatabase(context)
                val budgetRepo = BudgetRepository(db.budgetDao(), db.transactionDao())
                val prevBudget = budgetRepo.getBudget(prevMonth)
                if (prevBudget != null && prevBudget.totalBudget > 0) {
                    val prevSpent = budgetRepo.getMonthlySpent(prevMonth)
                    if (prevSpent < prevBudget.totalBudget) {
                        val savings = prevBudget.totalBudget - prevSpent
                        showSavingsDialog(savings, prevMonth)
                    } else {
                        // Even if they didn't save, mark it as shown so we don't check again
                        prefs.edit().putBoolean("shownSavedPopup_$prevMonth", true).apply()
                    }
                }
            }
        }

        // 2. Check for missing budget for current month
        budgetViewModel.budget.observe(viewLifecycleOwner) { budget ->
            if (budget == null) {
                showBudgetSetupDialog()
            } else {
                // 3. Check for exceeded budget
                val exceededDismissed = prefs.getBoolean("shownExceededPopup_$currentMonth", false)
                if (!exceededDismissed) {
                    val spent = budget.totalBudget - budget.remainingBudget
                    if (spent >= budget.totalBudget && budget.totalBudget > 0) {
                        showExceededDialog(currentMonth)
                    }
                }
            }
        }
    }

    private fun showBudgetSetupDialog() {
        if (!isAdded) return
        val currentMonth = MonthUtils.getCurrentMonthKey()
        val prefs = requireContext().getSharedPreferences("ExpenseSensePrefs", android.content.Context.MODE_PRIVATE)
        
        // Don't show again in the same session if they dismissed it
        if (prefs.getString("last_setup_prompt", "") == currentMonth) return

        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_budget_setup, null)
        val etAmount = view.findViewById<EditText>(R.id.etBudgetAmount)
        val tvTitle = view.findViewById<TextView>(R.id.tvDialogTitle)
        tvTitle.text = "Set Budget for ${MonthUtils.formatMonthDisplay(currentMonth)}"

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setCancelable(false)
            .setPositiveButton("Set Budget") { _, _ ->
                val amount = etAmount.text.toString().toDoubleOrNull()
                if (amount != null && amount > 0) {
                    budgetViewModel.setMonthlyBudget(amount)
                    Toast.makeText(requireContext(), "Budget Set Successfully", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Later") { _, _ ->
                prefs.edit().putString("last_setup_prompt", currentMonth).apply()
            }
            .show()
    }

    private fun showExceededDialog(monthKey: String) {
        if (!isAdded) return
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Budget Limit Reached")
            .setMessage("You've reached your budget for ${MonthUtils.formatMonthDisplay(monthKey)}. Review your spending to stay on track.")
            .setPositiveButton("Review Spending") { _, _ ->
                startActivity(Intent(requireContext(), BudgetDetailsActivity::class.java))
            }
            .setNegativeButton("Got it") { dialog, _ ->
                val prefs = requireContext().getSharedPreferences("ExpenseSensePrefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putBoolean("shownExceededPopup_$monthKey", true).apply()
                dialog.dismiss()
            }
            .show()
    }

    private fun showSavingsDialog(savings: Double, monthKey: String) {
        if (!isAdded) return
        val monthName = MonthUtils.formatMonthDisplay(monthKey)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Congratulations 🎉")
            .setMessage("Amazing job! You saved ₹${String.format("%.2f", savings)} in $monthName. Keep up the great work!")
            .setPositiveButton("Awesome!") { dialog, _ ->
                val prefs = requireContext().getSharedPreferences("ExpenseSensePrefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putBoolean("shownSavedPopup_$monthKey", true).apply()
                dialog.dismiss()
            }
            .show()
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
        android.util.Log.d("FLOW_DEBUG", "Home screen loading (onResume)...")
        android.util.Log.d("FLOW_DEBUG", "Triggering lifecycle load from Room & Firebase sync")
        loadFromRoom()
        syncWithFirebase()
        budgetViewModel.loadCurrentMonthBudget()
        accountViewModel.loadAccounts()
        cardViewModel.loadCards()
    }

    private fun loadFromRoom() {
        android.util.Log.d("FLOW_DEBUG", "Triggering local Home data fetch from Room...")
        Thread {
            val db = AppDatabase.getDatabase(requireContext())
            val records = db.transactionDao().getAllTransactions()
            val sortedList = records.sortedByDescending { it.timestamp }
            
            val creditCards = db.creditCardDao().getAllCreditCards()
            val debitCards = db.debitCardDao().getAllDebitCards()
            val cardMap = mutableMapOf<String, String>()
            creditCards.forEach { card ->
                val shortName = card.cardName.split(" ").firstOrNull()?.uppercase() ?: card.cardName
                val last4 = card.last4Digits ?: ""
                cardMap[card.cardName] = if (last4.isNotBlank()) "$shortName $last4" else shortName
            }
            debitCards.forEach { card ->
                val shortName = card.cardName.split(" ").firstOrNull()?.uppercase() ?: card.cardName
                val last4 = card.last4Digits ?: ""
                cardMap[card.cardName] = if (last4.isNotBlank()) "$shortName $last4" else shortName
            }

            activity?.runOnUiThread {
                transactionList.clear()
                transactionList.addAll(sortedList)
                updateSummaryViews(sortedList)
                updateEmptyState()

                val prefs = requireContext().getSharedPreferences(
                    "ExpenseSensePrefs",
                    android.content.Context.MODE_PRIVATE
                )
                if (!prefs.getBoolean("swipe_hint_shown", false) && transactionList.isNotEmpty()) {
                    cardSwipeTooltip.visibility = View.VISIBLE
                }

                val limitedList = transactionList.take(5).toMutableList()
                adapter = TransactionAdapter(limitedList) { transaction ->
                    val intent =
                        Intent(requireContext(), TransactionDetailsActivity::class.java).apply {
                            putExtra("id", transaction.id)
                            putExtra("firebaseId", transaction.firebaseId)
                            putExtra("title", transaction.title)
                            putExtra("amount", transaction.amount)
                            putExtra("category", transaction.category)
                            putExtra("timestamp", transaction.timestamp)
                            putExtra("paymentMethod", transaction.paymentMethod)
                            putExtra("referenceId", transaction.referenceId)
                            putExtra("note", transaction.note)
                            putExtra("accountName", transaction.accountName)
                            putExtra("transactionType", transaction.transactionType)
                        }
                    detailsLauncher.launch(intent)
                }
                adapter.updateCardMap(cardMap)
                rvTransactions.adapter = adapter
                attachSwipeHelper()

                // Update Shared ViewModel
                val expenses = transactionList.map {
                    Expense(
                        it.title,
                        it.amount,
                        SimpleDateFormat(
                            "dd MMM yyyy",
                            Locale.getDefault()
                        ).format(Date(it.timestamp))
                    )
                }.toMutableList()
                expenseViewModel.updateExpenses(expenses)
            }
        }.start()
    }

    private fun syncWithFirebase() {
        android.util.Log.d("FLOW_DEBUG", "Triggering remote Home data fetch from Firebase...")
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
                        var parsedCount = 0
                        for (categorySnapshot in snapshot.children) {
                            for (expenseSnapshot in categorySnapshot.children) {
                                parsedCount++
                                val key = expenseSnapshot.key ?: continue
                                if (!localMap.containsKey(key)) {
                                    dao.insertTransaction(
                                        Transaction(
                                            title = key.substringBeforeLast("-"),
                                            amount = expenseSnapshot.child("amount")
                                                .getValue(Double::class.java) ?: 0.0,
                                            category = categorySnapshot.key ?: "Other",
                                            accountName = expenseSnapshot.child("account")
                                                .getValue(String::class.java) ?: "Cash",
                                            timestamp = expenseSnapshot.child("timestamp")
                                                .getValue(Long::class.java) ?: 0L,
                                            paymentMethod = expenseSnapshot.child("paymentMethod")
                                                .getValue(String::class.java) ?: "Cash",
                                            referenceId = expenseSnapshot.child("account")
                                                .getValue(String::class.java) ?: "Cash",
                                            firebaseId = key,
                                            note = expenseSnapshot.child("note")
                                                .getValue(String::class.java) ?: ""
                                        )
                                    )
                                    hasNew = true
                                }
                            }
                        }
                        if (hasNew && isAdded) loadFromRoom()
                    }.start()
                }

                override fun onCancelled(error: DatabaseError) {}
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
                                dao.insertBudget(
                                    Budget(
                                        monthYear,
                                        budgetSnapshot.child("totalBudget")
                                            .getValue(Double::class.java) ?: 0.0,
                                        budgetSnapshot.child("remainingBudget")
                                            .getValue(Double::class.java) ?: 0.0
                                    )
                                )
                            }
                        }
                        activity?.runOnUiThread { budgetViewModel.loadCurrentMonthBudget() }
                    }.start()
                }

                override fun onCancelled(error: DatabaseError) {}
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

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun deleteTransaction(record: Transaction) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val username = user.email?.substringBefore("@") ?: return
        val database = AppDatabase.getDatabase(requireContext())
        PaymentRepository(
            database,
            database.transactionDao(),
            database.accountDao(),
            database.debitCardDao(),
            database.creditCardDao(),
            database.budgetDao()
        )
            .deleteExpense(record, username) { success, _ ->
                if (success) activity?.runOnUiThread {
                    loadFromRoom()
                    budgetViewModel.loadCurrentMonthBudget()
                    accountViewModel.loadAccounts()
                    cardViewModel.loadCards()
                }
            }
    }

    private fun updateSummaryViews(records: List<Transaction>) {
        var total = 0.0
        for (expense in records) {
            total += expense.amount
        }
        val cal = Calendar.getInstance()
        val mTotal = records.filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            c.get(Calendar.MONTH) == cal.get(Calendar.MONTH) && c.get(Calendar.YEAR) == cal.get(
                Calendar.YEAR
            )
        }.sumOf { it.amount }

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

    private fun attachSwipeHelper() {
            val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean = false

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val prefs = requireContext().getSharedPreferences(
                        "ExpenseSensePrefs",
                        android.content.Context.MODE_PRIVATE
                    )
                    if (!prefs.getBoolean("swipe_hint_shown", false)) {
                        prefs.edit().putBoolean("swipe_hint_shown", true).apply()
                        cardSwipeTooltip.visibility = View.GONE
                    }

                    val position = viewHolder.adapterPosition
                    val deletedItem = adapter.records[position]

                    adapter.removeItem(position)

                    val snackbar = Snackbar.make(
                        rvTransactions,
                        "${deletedItem.title} removed",
                        Snackbar.LENGTH_LONG
                    )
                    snackbar.setAction("UNDO") {
                        adapter.restoreItem(deletedItem, position)
                    }
                    snackbar.addCallback(object : Snackbar.Callback() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            if (event != DISMISS_EVENT_ACTION) {
                                deleteTransaction(deletedItem)
                            }
                        }
                    })
                    snackbar.show()
                }

                override fun onChildDraw(
                    c: Canvas,
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    dX: Float,
                    dY: Float,
                    actionState: Int,
                    isCurrentlyActive: Boolean
                ) {
                    if (viewHolder is TransactionAdapter.TransactionViewHolder) {
                        // --- Compact List Structure Logic ---
                        // The "Compact" look is achieved by using item_transaction.xml which keeps 
                        // vertical padding small and uses a single row for metadata (Date • Payment).
                        ItemTouchHelper.Callback.getDefaultUIUtil().onDraw(
                            c, recyclerView, viewHolder.cardForeground, dX, dY,
                            actionState, isCurrentlyActive
                        )
                    }
                }

                override fun clearView(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder
                ) {
                    if (viewHolder is TransactionAdapter.TransactionViewHolder) {
                        ItemTouchHelper.Callback.getDefaultUIUtil()
                            .clearView(viewHolder.cardForeground)
                    }
                }

                override fun onSelectedChanged(
                    viewHolder: RecyclerView.ViewHolder?,
                    actionState: Int
                ) {
                    if (viewHolder is TransactionAdapter.TransactionViewHolder) {
                        ItemTouchHelper.Callback.getDefaultUIUtil()
                            .onSelected(viewHolder.cardForeground)
                    }
                }
            }
            ItemTouchHelper(swipeCallback).attachToRecyclerView(rvTransactions)
    }

    private fun maskCardNumber(number: String?): String {
        if (number.isNullOrEmpty()) return "**** ****"
        val clean = number.replace(" ", "")
        return if (clean.length >= 4) {
            "**** " + clean.takeLast(4)
        } else "**** ****"
    }
}
