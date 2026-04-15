package com.amshu.expensesense

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionDetailsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_details)

        findViewById<ImageButton>(R.id.btnDetailsBack).setOnClickListener { finish() }

        val txId = intent.getIntExtra("id", -1)
        val firebaseId = intent.getStringExtra("firebaseId")

        findViewById<ImageButton>(R.id.btnDetailsDelete).setOnClickListener {
            val returnIntent = Intent().apply {
                putExtra("action", "delete")
                putExtra("id", txId)
            }
            setResult(android.app.Activity.RESULT_OK, returnIntent)
            finish()
        }

        val editLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val returnIntent = Intent().apply {
                    putExtra("action", "edit")
                }
                setResult(android.app.Activity.RESULT_OK, returnIntent)
                finish()
            }
        }

        val title = intent.getStringExtra("title") ?: "Unknown"
        val amount = intent.getDoubleExtra("amount", 0.0)
        val category = intent.getStringExtra("category") ?: "Other"
        val timestamp = intent.getLongExtra("timestamp", 0L)
        val paymentMethod = intent.getStringExtra("paymentMethod") ?: "Cash"
        val referenceId = intent.getStringExtra("referenceId") ?: ""
        val note = intent.getStringExtra("note")

        findViewById<TextView>(R.id.tvDetailTitle).text = title
        findViewById<TextView>(R.id.tvDetailCategoryName).text = category

        val transactionType = intent.getStringExtra("transactionType") ?: Transaction.TYPE_EXPENSE
        val tvDetailAmount = findViewById<TextView>(R.id.tvDetailAmount)
        
        if (transactionType == Transaction.TYPE_INCOME) {
            tvDetailAmount.text = "+₹%.2f".format(amount)
            tvDetailAmount.setTextColor(android.graphics.Color.parseColor("#43A047")) // Green
        } else {
            tvDetailAmount.text = "-₹%.2f".format(amount)
            tvDetailAmount.setTextColor(android.graphics.Color.parseColor("#E53935")) // Red
        }

        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        findViewById<TextView>(R.id.tvDetailDateTime).text = sdf.format(Date(timestamp))

        val methodString = if (referenceId.isNotBlank() && referenceId != paymentMethod && paymentMethod != "Cash") {
            "$paymentMethod • $referenceId"
        } else {
            paymentMethod
        }
        findViewById<TextView>(R.id.tvDetailPayment).text = methodString

        val layoutNote = findViewById<LinearLayout>(R.id.layoutDetailNote)
        if (note.isNullOrBlank()) {
            layoutNote.visibility = View.GONE
        } else {
            layoutNote.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvDetailNote).text = note
        }

        val ivIcon = findViewById<ImageView>(R.id.ivDetailCategory)
        val iconResId = CategoryAdapter.getCategoryIcon(category)
        ivIcon.setImageResource(iconResId)

        findViewById<ImageButton>(R.id.btnDetailsEdit).setOnClickListener {
            val editIntent = Intent(this, AddExpenseActivity::class.java).apply {
                putExtra("isEditing", true)
                putExtra("id", txId)
                putExtra("firebaseId", firebaseId)
                putExtra("title", title)
                putExtra("amount", amount)
                putExtra("category", category)
                putExtra("timestamp", timestamp)
                putExtra("paymentMethod", paymentMethod)
                putExtra("referenceId", referenceId)
                putExtra("note", note)
                putExtra("accountName", intent.getStringExtra("accountName"))
            }
            editLauncher.launch(editIntent)
        }
    }
}
