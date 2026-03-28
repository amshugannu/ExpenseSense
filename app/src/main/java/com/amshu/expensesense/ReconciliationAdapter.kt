package com.amshu.expensesense

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ReconciliationAdapter(
    private var transactions: List<ReconciliationTransaction>
) : RecyclerView.Adapter<ReconciliationAdapter.TransactionViewHolder>() {

    class TransactionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDesc: TextView = view.findViewById(R.id.tvTransactionDesc)
        val tvDate: TextView = view.findViewById(R.id.tvTransactionDate)
        val tvAmount: TextView = view.findViewById(R.id.tvTransactionAmount)
        val tvType: TextView = view.findViewById(R.id.tvTransactionType)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reconciliation_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val tx = transactions[position]
        holder.tvDesc.text = tx.description
        holder.tvDate.text = tx.date
        
        holder.tvType.text = tx.type.uppercase()

        if (tx.type == "credit") {
            holder.tvAmount.text = "+$${String.format("%.2f", tx.amount)}"
            holder.tvAmount.setTextColor(Color.parseColor("#43A047")) // Green
        } else {
            holder.tvAmount.text = "-$${String.format("%.2f", tx.amount)}"
            holder.tvAmount.setTextColor(Color.parseColor("#E53935")) // Red
        }
    }

    override fun getItemCount(): Int = transactions.size

    fun updateData(newTransactions: List<ReconciliationTransaction>) {
        transactions = newTransactions
        notifyDataSetChanged()
    }
}
