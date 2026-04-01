package com.amshu.expensesense

sealed interface CardUIModel {
    val id: String
    val cardHolderName: String
    val cardNumber: String
    val cardName: String
    val bankName: String
    val last4Digits: String
    val createdAt: Long

    data class Debit(val card: DebitCard) : CardUIModel {
        override val id = card.cardName
        override val cardHolderName = card.cardHolderName ?: ""
        override val cardNumber = card.cardNumber ?: ""
        override val cardName = card.cardName ?: ""
        override val bankName = card.bankName ?: ""
        override val last4Digits = card.last4Digits ?: ""
        override val createdAt = card.createdAt
        val linkedBankAccountId = card.linkedBankAccountId ?: ""
        val drawableName = card.drawableName
    }

    data class Credit(val card: CreditCard) : CardUIModel {
        override val id = card.cardName
        override val cardHolderName = card.cardHolderName ?: ""
        override val cardNumber = card.cardNumber ?: ""
        override val cardName = card.cardName ?: ""
        override val bankName = card.bankName ?: ""
        override val last4Digits = card.last4Digits ?: ""
        override val createdAt = card.createdAt
        val totalLimit = card.totalLimit ?: 0.0
        val availableLimit = card.availableLimit ?: 0.0
        val drawableName = card.drawableName
    }
}
