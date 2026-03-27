package com.amshu.expensesense

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

// Reconciliation Feature Entry Point
class StatementReconciliationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statement_reconciliation)

        // Find and set up the back button
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            // Safely return to previous screen
            finish()
        }
    }
}
