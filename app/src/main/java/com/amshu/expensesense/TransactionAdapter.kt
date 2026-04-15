package com.amshu.expensesense

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionAdapter(
    val records: MutableList<Transaction>,
    private var cardDisplayMap: Map<String, String> = emptyMap(),
    private val onItemClick: (Transaction) -> Unit
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    // --- Compact List Structure Logic ---
    // Achieving a sleek, data-rich but space-efficient UI. Each item is inflated
    // from item_transaction.xml which uses minimal padding and horizontal alignment.

    fun updateCardMap(newMap: Map<String, String>) {
        cardDisplayMap = newMap
        notifyDataSetChanged()
    }

    inner class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivCategory:     ImageView        = itemView.findViewById(R.id.ivTransactionCategory)
        val tvTitle:        TextView         = itemView.findViewById(R.id.tvTransactionTitle)
        val tvDateTime:     TextView         = itemView.findViewById(R.id.tvTransactionDateTime)
        val tvAmount:       TextView         = itemView.findViewById(R.id.tvTransactionAmount)
        val tvPayment:      TextView         = itemView.findViewById(R.id.tvTransactionPayment)
        val cardForeground: View             = itemView.findViewById(R.id.cardForeground)

        init {
            cardForeground.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onItemClick(records[adapterPosition])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val record = records[position]
        
        holder.tvTitle.text = record.title
        val amountText = if (record.transactionType == Transaction.TYPE_INCOME) {
            holder.tvAmount.setTextColor(android.graphics.Color.parseColor("#43A047")) // Green
            "+₹%.2f".format(record.amount)
        } else {
            holder.tvAmount.setTextColor(android.graphics.Color.parseColor("#E53935")) // Red
            "-₹%.2f".format(record.amount)
        }
        holder.tvAmount.text = amountText

        val formattedPayment = if ((record.paymentMethod == "Credit Card" || record.paymentMethod == "Debit Card") 
                                    && record.referenceId != null 
                                    && cardDisplayMap.containsKey(record.referenceId)) {
            cardDisplayMap[record.referenceId]
        } else if (!record.referenceId.isNullOrBlank() && record.referenceId != record.paymentMethod && record.paymentMethod != "Cash") {
            "${record.paymentMethod} • ${record.referenceId}"
        } else {
            record.paymentMethod
        }
        holder.tvPayment.text = formattedPayment

        val sdfDate = SimpleDateFormat("MMM dd", Locale.getDefault())
        val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dt = Date(record.timestamp)
        holder.tvDateTime.text = "${sdfDate.format(dt)} • ${sdfTime.format(dt)}"

        val iconResId = CategoryAdapter.getCategoryIcon(record.category)
        holder.ivCategory.setImageResource(iconResId)
    }

    override fun getItemCount(): Int = records.size

    fun removeItem(position: Int) {
        records.removeAt(position)
        notifyItemRemoved(position)
    }

    fun restoreItem(item: Transaction, position: Int) {
        records.add(position, item)
        notifyItemInserted(position)
    }
}
