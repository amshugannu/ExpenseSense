package com.example.es

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CardAdapter(
    private var cards: List<CardUIModel>,
    private var accountBalances: Map<String, Double>,
    private val onEdit: (CardUIModel) -> Unit,
    private val onDelete: (CardUIModel) -> Unit
) : RecyclerView.Adapter<CardAdapter.CardViewHolder>() {

    private var onItemClickListener: ((CardUIModel, View) -> Unit)? = null

    class CardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivCardBg: android.widget.ImageView = view.findViewById(R.id.ivCardBg)
        val tvCardNumber: TextView = view.findViewById(R.id.tvCardNumberDisplay)
        val tvCardHolder: TextView = view.findViewById(R.id.tvCardHolderDisplay)
        val tvBalance: TextView = view.findViewById(R.id.tvBalanceDisplay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false)
        val density = parent.resources.displayMetrics.density
        view.cameraDistance = 8000 * density
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val model = cards[position]
        
        holder.itemView.alpha = 1f
        holder.itemView.setOnClickListener {
            onItemClickListener?.invoke(model, it)
        }
        
        when (model) {
            is CardUIModel.Credit -> {
                // Try to resolve stored drawable, fall back to default credit card
                val drawName = model.drawableName
                val resId = if (!drawName.isNullOrEmpty()) {
                    holder.itemView.context.resources.getIdentifier(drawName, "drawable", holder.itemView.context.packageName)
                } else 0
                holder.ivCardBg.setImageResource(if (resId != 0) resId else R.drawable.defaultcreditcard)
                holder.tvBalance.text = "Avl: ₹${String.format("%.2f", model.availableLimit)}"
            }
            is CardUIModel.Debit -> {
                // Try to resolve stored drawable, fall back to default debit card
                val drawName = model.drawableName
                val resId = if (!drawName.isNullOrEmpty()) {
                    holder.itemView.context.resources.getIdentifier(drawName, "drawable", holder.itemView.context.packageName)
                } else 0
                holder.ivCardBg.setImageResource(if (resId != 0) resId else R.drawable.defaultdebitcard)
                val balance = accountBalances[model.linkedBankAccountId] ?: 0.0
                holder.tvBalance.text = "Bal: ₹${String.format("%.2f", balance)}"
            }
        }

        holder.tvCardNumber.text = maskCardNumber(model.cardNumber)
        holder.tvCardHolder.text = model.cardHolderName.uppercase()
    }

    override fun getItemCount() = cards.size

    fun setOnItemClickListener(listener: (CardUIModel, View) -> Unit) {
        onItemClickListener = listener
    }

    fun getCards(): List<CardUIModel> = cards

    fun updateData(newCards: List<CardUIModel>, newBalances: Map<String, Double>? = null) {
        val oldCards = this.cards
        this.cards = newCards
        
        if (newBalances != null) {
            accountBalances = newBalances
        }

        // Handle Removal (1 item)
        if (oldCards.size == newCards.size + 1) {
            val removedIndex = oldCards.indexOfFirst { oldCard -> 
                newCards.none { it.id == oldCard.id && it.javaClass == oldCard.javaClass } 
            }
            if (removedIndex != -1) {
                notifyItemRemoved(removedIndex)
                notifyItemRangeChanged(removedIndex, newCards.size - removedIndex)
                return
            }
        }

        // Handle Insertion (1 item)
        if (oldCards.size == newCards.size - 1) {
            val insertedIndex = newCards.indexOfFirst { newCard ->
                oldCards.none { it.id == newCard.id && it.javaClass == newCard.javaClass }
            }
            if (insertedIndex != -1) {
                notifyItemInserted(insertedIndex)
                notifyItemRangeChanged(insertedIndex, newCards.size - insertedIndex)
                return
            }
        }

        // Handle small localized changes (e.g., edits)
        if (oldCards.size == newCards.size) {
            var diffCount = 0
            for (i in oldCards.indices) {
                if (oldCards[i] != newCards[i]) diffCount++
            }
            if (diffCount > 0 && diffCount <= 5) {
                for (i in oldCards.indices) {
                    if (oldCards[i] != newCards[i]) notifyItemChanged(i)
                }
                return
            }
        }

        notifyDataSetChanged()
    }

    fun moveItem(from: Int, to: Int, newList: List<CardUIModel>) {
        this.cards = newList
        notifyItemMoved(from, to)
        // Adjust indices for other items affected by the move
        val start = Math.min(from, to)
        val count = Math.abs(from - to) + 1
        notifyItemRangeChanged(start, count)
    }

    private fun maskCardNumber(number: String): String {
        if (number.length < 4) return number
        val lastFour = number.takeLast(4)
        return "**** $lastFour"
    }

    fun getCardAt(position: Int): CardUIModel {
        return cards[position]
    }
}
