package com.amshu.expensesense

import android.content.Intent
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth

class TransactionsActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var rvTransactions: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: TransactionAdapter
    private var transactionList = mutableListOf<Transaction>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transactions)

        btnBack = findViewById(R.id.btnBack)
        rvTransactions = findViewById(R.id.rvTransactions)
        tvEmpty = findViewById(R.id.tvEmpty)

        btnBack.setOnClickListener { finish() }

        rvTransactions.layoutManager = LinearLayoutManager(this)

        val detailsLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val action = result.data?.getStringExtra("action")
                if (action == "delete") {
                    val id = result.data?.getIntExtra("id", -1) ?: -1
                    val deletedItem = transactionList.find { it.id == id }
                    if (deletedItem != null) {
                        val position = adapter.records.indexOf(deletedItem)
                        if (position != -1) {
                            adapter.removeItem(position)
                            val snackbar = Snackbar.make(rvTransactions, "${deletedItem.title} removed", Snackbar.LENGTH_LONG)
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
                    }
                } else if (action == "edit") {
                    loadFromRoom()
                }
            }
        }

        adapter = TransactionAdapter(transactionList) { transaction ->
            val intent = Intent(this, TransactionDetailsActivity::class.java).apply {
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
            }
            detailsLauncher.launch(intent)
        }
        rvTransactions.adapter = adapter

        attachSwipeHelper()
    }

    override fun onResume() {
        super.onResume()
        loadFromRoom()
    }

    private fun loadFromRoom() {
        Thread {
            val db = AppDatabase.getDatabase(this)
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
            
            runOnUiThread {
                transactionList.clear()
                transactionList.addAll(sortedList)
                adapter.updateCardMap(cardMap)
                
                if (transactionList.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                    rvTransactions.visibility = View.GONE
                } else {
                    tvEmpty.visibility = View.GONE
                    rvTransactions.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    private fun deleteTransaction(record: Transaction) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val username = user.email?.substringBefore("@") ?: return
        val database = AppDatabase.getDatabase(this)
        PaymentRepository(
            database, 
            database.transactionDao(), 
            database.accountDao(), 
            database.debitCardDao(), 
            database.creditCardDao(), 
            database.budgetDao()
        ).deleteExpense(record, username) { success, _ ->
            if (success) {
                loadFromRoom()
            }
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
                val position = viewHolder.adapterPosition
                val deletedItem = adapter.records[position]

                adapter.removeItem(position)

                val snackbar = Snackbar.make(rvTransactions, "${deletedItem.title} removed", Snackbar.LENGTH_LONG)
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
                    ItemTouchHelper.Callback.getDefaultUIUtil().onDraw(
                        c, recyclerView, viewHolder.cardForeground, dX, dY,
                        actionState, isCurrentlyActive
                    )
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                if (viewHolder is TransactionAdapter.TransactionViewHolder) {
                    ItemTouchHelper.Callback.getDefaultUIUtil().clearView(viewHolder.cardForeground)
                }
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                if (viewHolder is TransactionAdapter.TransactionViewHolder) {
                    ItemTouchHelper.Callback.getDefaultUIUtil().onSelected(viewHolder.cardForeground)
                }
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(rvTransactions)
    }
}
