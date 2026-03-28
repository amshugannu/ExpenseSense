package com.amshu.expensesense

data class ReconciliationTransaction(
    val date: String,
    val description: String,
    val amount: Double,
    val type: String // "debit" or "credit"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ReconciliationTransaction

        if (date != other.date) return false
        if (amount != other.amount) return false
        if (description.lowercase() != other.description.lowercase()) return false

        return true
    }

    override fun hashCode(): Int {
        var result = date.hashCode()
        result = 31 * result + description.lowercase().hashCode()
        result = 31 * result + amount.hashCode()
        return result
    }
}
