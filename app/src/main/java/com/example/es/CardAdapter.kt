package com.example.es

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CardAdapter(
    private var cards: List<Card>,
    private var accountBalances: Map<String, Double>,
    private val onEdit: (Card) -> Unit,
    private val onDelete: (Card) -> Unit
) : RecyclerView.Adapter<CardAdapter.CardViewHolder>() {

    private var onItemClickListener: ((Card, View) -> Unit)? = null


    class CardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivCardBg: android.widget.ImageView = view.findViewById(R.id.ivCardBg)
        val tvCardName: TextView = view.findViewById(R.id.tvCardNameDisplay)
        val tvCardNumber: TextView = view.findViewById(R.id.tvCardNumberDisplay)
        val tvCardHolder: TextView = view.findViewById(R.id.tvCardHolderDisplay)
        val tvBalance: TextView = view.findViewById(R.id.tvBalanceDisplay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false)
        // Set camera distance for better 3D tilt effect
        val density = parent.resources.displayMetrics.density
        view.cameraDistance = 8000 * density
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val card = cards[position]
        
        holder.itemView.alpha = 1f
        holder.itemView.setOnClickListener {
            onItemClickListener?.invoke(card, it)
        }
        
        // Dynamic Background
        if (card.cardType == "Credit") {
            holder.ivCardBg.setImageResource(R.drawable.defaultcreditcard)
            holder.tvBalance.text = "Avl: ₹${String.format("%.2f", card.availableLimit ?: 0.0)}"
        } else {
            holder.ivCardBg.setImageResource(R.drawable.defaultdebitcard)
            val balance = accountBalances[card.accountName] ?: 0.0
            holder.tvBalance.text = "Bal: ₹${String.format("%.2f", balance)}"
        }

        holder.tvCardName.text = card.cardName
        holder.tvCardNumber.text = maskCardNumber(card.cardNumber)
        holder.tvCardHolder.text = card.cardHolderName.uppercase()
    }

    override fun getItemCount() = cards.size

    fun setOnItemClickListener(listener: (Card, View) -> Unit) {
        onItemClickListener = listener
    }

    fun getCards(): List<Card> = cards

    fun updateData(newCards: List<Card>, newBalances: Map<String, Double>? = null) {
        if (newBalances != null) {
            accountBalances = newBalances
        }
        
        val oldCards = this.cards
        this.cards = newCards

        // Use basic diffing or just notifyItemRemoved if the list size decreased by one
        if (oldCards.size == newCards.size + 1) {
            val removedIndex = oldCards.indexOfFirst { oldCard -> 
                newCards.none { it.id == oldCard.id } 
            }
            if (removedIndex != -1) {
                notifyItemRemoved(removedIndex)
                notifyItemRangeChanged(removedIndex, newCards.size - removedIndex)
                return
            }
        }

        notifyDataSetChanged()
    }

    private fun maskCardNumber(number: String): String {
        if (number.length < 4) return number
        val lastFour = number.takeLast(4)
        return "**** **** **** $lastFour"
    }

    fun getCardAt(position: Int): Card {
        return cards[position]
    }
}
