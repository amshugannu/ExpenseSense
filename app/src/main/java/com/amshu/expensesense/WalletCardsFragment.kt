package com.amshu.expensesense

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import android.graphics.Canvas
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WalletCardsFragment : Fragment() {

    private lateinit var rvCards: RecyclerView
    private lateinit var fabAddCard: FloatingActionButton
    private lateinit var fabScanCard: FloatingActionButton
    private lateinit var cardAdapter: CardAdapter
    private lateinit var cardRepository: CardRepository
    private lateinit var accountRepository: AccountRepository

    private lateinit var stackedLayoutManager: StackedLayoutManager

    private val scanResultLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                val intent = Intent(requireContext(), AddCardActivity::class.java)
                intent.putExtra("isScan", true)
                intent.putExtra("cardHolder", data.getStringExtra("cardHolder"))
                intent.putExtra("cardNumber", data.getStringExtra("cardNumber"))
                intent.putExtra("cardName", data.getStringExtra("cardName"))
                intent.putExtra("cardType", data.getStringExtra("cardType"))
                intent.putExtra("bankName", data.getStringExtra("bankName"))
                startActivity(intent)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchScanner()
        } else {
            handlePermissionDenied()
        }
    }

    private fun handlePermissionDenied() {
        if (!shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)) {
            // Permanent denial
            showSettingsDialog()
        } else {
            // Normal denial
            android.widget.Toast.makeText(
                requireContext(),
                "Camera permission is required to scan cards",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showSettingsDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Permission Required")
            .setMessage("Camera permission is permanently denied. Please enable it in settings to use this feature.")
            .setPositiveButton("Settings") { _, _ ->
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", requireContext().packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
    }

    private fun launchScanner() {
        val intent = Intent(requireContext(), ScanCardActivity::class.java)
        scanResultLauncher.launch(intent)
    }

    private lateinit var layoutEmptyState: View
    private lateinit var btnEmptyAddCard: com.google.android.material.button.MaterialButton
    private lateinit var cardViewModel: CardViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_wallet_cards, container, false)

        rvCards = view.findViewById(R.id.rvCards)
        fabAddCard = view.findViewById(R.id.fabAddCard)
        fabScanCard = view.findViewById(R.id.fabScanCard)
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState)
        btnEmptyAddCard = view.findViewById(R.id.btnEmptyAddCard)

        val database = AppDatabase.getDatabase(requireContext())
        cardRepository = CardRepository(
            database.debitCardDao(),
            database.creditCardDao(),
            database.accountDao(),
            database.cardDao()
        )
        accountRepository = AccountRepository(database.accountDao())

        // Initialize ViewModel
        val factory = CardViewModelFactory(cardRepository)
        cardViewModel = androidx.lifecycle.ViewModelProvider(this, factory).get(CardViewModel::class.java)

        // Observe Cards from ViewModel
        cardViewModel.cards.observe(viewLifecycleOwner) { list ->
            
            lifecycleScope.launch {
                val accounts = withContext(Dispatchers.IO) {
                    database.accountDao().getAllAccounts()
                }
                val balances = accounts.associate { it.name to it.balance }
                
                withContext(Dispatchers.Main) {
                    if (list.isEmpty()) {
                        rvCards.visibility = View.GONE
                        layoutEmptyState.visibility = View.VISIBLE
                    } else {
                        rvCards.visibility = View.VISIBLE
                        layoutEmptyState.visibility = View.GONE
                        cardAdapter.updateData(list, balances)
                    }
                }
            }
        }

        stackedLayoutManager = StackedLayoutManager(collapsedOffset = 180)
        rvCards.layoutManager = stackedLayoutManager

        cardAdapter = CardAdapter(
            emptyList(),
            emptyMap(),
            onEdit = { model ->
                val intent = Intent(requireContext(), AddCardActivity::class.java)
                intent.putExtra(AddCardActivity.EXTRA_CARD_NAME, model.id)
                intent.putExtra("extra_card_type", if (model is CardUIModel.Credit) "Credit" else "Debit")
                startActivity(intent)
            },
            onDelete = { model ->
                showDeleteConfirmation(model)
            }
        )

        rvCards.adapter = cardAdapter

        // FIX: Prevent snap-back animation when dropping cards
        (rvCards.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)
            ?.supportsChangeAnimations = false

        fabAddCard.setOnClickListener {
            startActivity(Intent(requireContext(), AddCardActivity::class.java))
        }

        btnEmptyAddCard.setOnClickListener {
            startActivity(Intent(requireContext(), AddCardActivity::class.java))
        }

        fabScanCard.setOnClickListener {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.CAMERA
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                launchScanner()
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
        }

        setupGesturesAndAnimations()

        // Trigger Cleanup of root level cards node per strict rule
        cardRepository.cleanupRootCards()

        return view
    }

    override fun onResume() {
        super.onResume()
        loadCards()
    }

    private fun loadCards() {
        val user = FirebaseAuth.getInstance().currentUser
        val username = user?.email?.substringBefore("@")
        
        if (username.isNullOrEmpty()) {
            return
        }

        cardViewModel.loadCards()
    }

    private fun setupGesturesAndAnimations() {

        val gestureDetector = android.view.GestureDetector(
            requireContext(),
            object : android.view.GestureDetector.SimpleOnGestureListener() {

                override fun onFling(
                    e1: android.view.MotionEvent?,
                    e2: android.view.MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {

                    if (e1 == null) return false

                    val diffY = e2.y - e1.y

                    if (Math.abs(diffY) > 100) {
                        if (diffY < 0) collapsedToExpanded()
                        else expandedToCollapsed()
                        return true
                    }

                    return false
                }
            })

        rvCards.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }

        val itemTouchHelper = ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                0
            ) {

                private var currentDragPos = -1

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean = true

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

                override fun onSelectedChanged(
                    viewHolder: RecyclerView.ViewHolder?,
                    actionState: Int
                ) {

                    super.onSelectedChanged(viewHolder, actionState)

                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {

                        val vh = viewHolder ?: return

                        currentDragPos = vh.adapterPosition
                        stackedLayoutManager.setDragState(currentDragPos)

                        vh.itemView.elevation = 100f
                    }
                }

                override fun clearView(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder
                ) {

                    val finalTarget = stackedLayoutManager.currentTargetPosition

                    if (currentDragPos != -1 && finalTarget != -1 && currentDragPos != finalTarget) {

                        val cards = cardAdapter.getCards().toMutableList()
                        val movedCard = cards.removeAt(currentDragPos)
                        cards.add(finalTarget, movedCard)

                        cardAdapter.moveItem(currentDragPos, finalTarget, cards.toList())

                        // Force a re-layout in the next frame to ensure stacking is perfect
                        rvCards.post { rvCards.requestLayout() }

                        saveCardOrder()
                    }

                    viewHolder.itemView.animate()
                        .rotationX(0f)
                        .setDuration(150)
                        .start()

                    currentDragPos = -1

                    stackedLayoutManager.onDragFinished()

                    // IMPORTANT: call super LAST
                    super.clearView(recyclerView, viewHolder)
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

                    super.onChildDraw(
                        c,
                        recyclerView,
                        viewHolder,
                        dX,
                        dY,
                        actionState,
                        isCurrentlyActive
                    )

                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && isCurrentlyActive) {

                        val dragTopY = viewHolder.itemView.top + dY
                        val dragCenterY = dragTopY + (viewHolder.itemView.height / 2)

                        stackedLayoutManager.setDragCoordinates(
                            dragTopY,
                            dragCenterY
                        )
                    }
                }
            })

        itemTouchHelper.attachToRecyclerView(rvCards)

        cardAdapter.setOnItemClickListener { card, view ->
            openCardFullscreen(card, view)
        }
    }

    private fun collapsedToExpanded() {
        if (!stackedLayoutManager.isExpanded()) {
            stackedLayoutManager.setExpanded(true)
        }
    }

    private fun expandedToCollapsed() {
        if (stackedLayoutManager.isExpanded()) {
            stackedLayoutManager.setExpanded(false)
        }
    }

    private fun openCardFullscreen(model: CardUIModel, cardView: View) {
        val tvBalance: android.widget.TextView = cardView.findViewById(R.id.tvBalanceDisplay)
        val balanceStr = tvBalance.text.toString()

        (activity as? MainActivity)?.showCardFocus(
            cardView,
            model,
            balanceStr,
            onEdit = {
                val intent = Intent(requireContext(), AddCardActivity::class.java)
                intent.putExtra(AddCardActivity.EXTRA_CARD_NAME, model.id)
                intent.putExtra("extra_card_type", if (model is CardUIModel.Credit) "Credit" else "Debit")
                startActivity(intent)
            },
            onDelete = {
                // Immediate UI removal
                val currentCards = cardAdapter.getCards()
                val pos = currentCards.indexOfFirst { it.id == model.id }
                if (pos != -1) {
                    val newList = currentCards.toMutableList()
                    newList.removeAt(pos)
                    cardAdapter.updateData(newList)
                }

                // Background database deletion
                lifecycleScope.launch(Dispatchers.IO) {
                    val callback: (Boolean) -> Unit = { success ->
                        if (!success) launch(Dispatchers.Main) { loadCards() }
                    }
                    when (model) {
                        is CardUIModel.Debit -> cardRepository.deleteDebitCard(model.card, callback)
                        is CardUIModel.Credit -> cardRepository.deleteCreditCard(model.card, callback)
                    }
                }
            }
        )
    }

    private fun saveCardOrder() {
        val models = cardAdapter.getCards()
        val db = AppDatabase.getDatabase(requireContext())

        lifecycleScope.launch(Dispatchers.IO) {
            models.forEachIndexed { index, model ->
                when (model) {
                    is CardUIModel.Debit -> {
                        val updated = model.card.copy(orderIndex = index)
                        db.debitCardDao().updateDebitCard(updated)
                    }
                    is CardUIModel.Credit -> {
                        val updated = model.card.copy(orderIndex = index)
                        db.creditCardDao().updateCreditCard(updated)
                    }
                }
            }
        }
    }

    private fun showDeleteConfirmation(model: CardUIModel) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Card")
            .setMessage("Are you sure you want to delete this card?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val callback: (Boolean) -> Unit = { success ->
                        if (success) {
                            launch(Dispatchers.Main) {

                                val currentCards = cardAdapter.getCards()
                                val pos = currentCards.indexOfFirst { it.id == model.id }
                                if (pos != -1) {
                                    val newList = currentCards.toMutableList()
                                    newList.removeAt(pos)
                                    cardAdapter.updateData(newList)
                                }
                            }
                        }
                    }
                    when (model) {
                        is CardUIModel.Debit -> cardRepository.deleteDebitCard(model.card, callback)
                        is CardUIModel.Credit -> cardRepository.deleteCreditCard(model.card, callback)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}