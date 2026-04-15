package com.amshu.expensesense

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

    private val revealedCardNumbers = mutableSetOf<String>()
    private val visibilityHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private var onItemClickListener: ((CardUIModel, View) -> Unit)? = null

    class CardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivCardBg: android.widget.ImageView = view.findViewById(R.id.ivCardBg)
        val tvCardNumber: TextView = view.findViewById(R.id.tvCardNumberDisplay)
        val tvCardHolder: TextView = view.findViewById(R.id.tvCardHolderDisplay)
        val tvBalance: TextView = view.findViewById(R.id.tvBalanceDisplay)
        val ivEyeToggle: android.widget.ImageView = view.findViewById(R.id.ivEyeToggle)
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
        
        val privacyMode = BalancePrivacyManager.getPrivacyMode(holder.itemView.context)
        val isRevealed = revealedCardNumbers.contains(model.cardNumber)

        val formatBalance = when (model) {
            is CardUIModel.Credit -> "Avl: ₹${String.format("%.2f", model.availableLimit)}"
            is CardUIModel.Debit -> {
                val balance = accountBalances[model.linkedBankAccountId] ?: 0.0
                "Bal: ₹${String.format("%.2f", balance)}"
            }
        }

        if (privacyMode == BalancePrivacyManager.MODE_ALWAYS_VISIBLE || isRevealed) {
            holder.tvBalance.text = formatBalance
        } else {
            holder.tvBalance.text = BalancePrivacyManager.maskBalance(formatBalance)
        }

        holder.ivEyeToggle.setOnClickListener {
            handleEyeClick(holder, model.cardNumber, privacyMode)
        }

        holder.tvCardNumber.text = maskCardNumber(model.cardNumber)
        holder.tvCardHolder.text = model.cardHolderName.uppercase()

        when (model) {
            is CardUIModel.Credit -> {
                // Try to resolve stored drawable, fall back to default credit card
                val drawName = model.drawableName
                val resId = if (!drawName.isNullOrEmpty()) {
                    holder.itemView.context.resources.getIdentifier(drawName, "drawable", holder.itemView.context.packageName)
                } else 0
                holder.ivCardBg.setImageResource(if (resId != 0) resId else R.drawable.defaultcreditcard)
            }
            is CardUIModel.Debit -> {
                // Try to resolve stored drawable, fall back to default debit card
                val drawName = model.drawableName
                val resId = if (!drawName.isNullOrEmpty()) {
                    holder.itemView.context.resources.getIdentifier(drawName, "drawable", holder.itemView.context.packageName)
                } else 0
                holder.ivCardBg.setImageResource(if (resId != 0) resId else R.drawable.defaultdebitcard)
            }
        }
    }

    private fun maskCardNumber(number: String?): String {
        if (number.isNullOrEmpty()) return "**** ****"
        val clean = number.replace(" ", "")
        return if (clean.length >= 4) {
             "**** " + clean.takeLast(4)
        } else "**** ****"
    }

    private fun handleEyeClick(holder: CardViewHolder, cardNumber: String, mode: Int) {
        val context = holder.itemView.context
        when (mode) {
            BalancePrivacyManager.MODE_SHOW_ON_CLICK -> {
                revealBalance(cardNumber, 5000)
            }
            BalancePrivacyManager.MODE_SHOW_ON_CLICK_PIN -> {
                showPinVerificationDialog(context) {
                    revealBalance(cardNumber, 20000)
                }
            }
        }
    }

    private fun revealBalance(cardNumber: String, duration: Long) {
        revealedCardNumbers.add(cardNumber)
        notifyDataSetChanged() // Inefficient but simple for a small card list
        
        visibilityHandler.postDelayed({
            revealedCardNumbers.remove(cardNumber)
            notifyDataSetChanged()
        }, duration)
    }

    private fun showPinVerificationDialog(context: android.content.Context, onSuccess: () -> Unit) {
        val dialogView = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_pin_setup, null)
        val etPin = dialogView.findViewById<android.widget.EditText>(R.id.etPin)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvPinTitle)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvPinSubtitle)
        val tvError = dialogView.findViewById<TextView>(R.id.tvPinError)

        tvTitle.text = "Enter Security PIN"
        tvSubtitle.text = "Please enter your 6-digit PIN to show the balance."
        etPin.hint = "Enter PIN"

        val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .setView(dialogView)
            .setPositiveButton("Verify", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val enteredPin = etPin.text.toString()
            val savedPin = BalancePrivacyManager.getPIN(context)
            if (enteredPin == savedPin) {
                onSuccess()
                dialog.dismiss()
            } else {
                tvError.text = "Incorrect PIN"
                tvError.visibility = android.view.View.VISIBLE
                it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            }
        }
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

    fun getCardAt(position: Int): CardUIModel {
        return cards[position]
    }
}
