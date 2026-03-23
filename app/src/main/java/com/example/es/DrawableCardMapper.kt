package com.example.es

data class CardOption(
    val displayName: String,
    val drawableName: String,
    val isCustom: Boolean = false
)

object DrawableCardMapper {

    // Maps display bank name → drawable prefix key
    private val bankKeyMap = mapOf(
        "HDFC Bank"     to "hdfc",
        "ICICI Bank"    to "icici",
        "SBI"           to "sbi",
        "Axis Bank"     to "axis",
        "Kotak Bank"    to "kotak",
        "IDFC Bank"     to "idfc",
        "Bank of Baroda" to "bob",
        "Canara Bank"   to "canara",
        "PNB"           to "pnb",
        "Union Bank"    to "union"
    )

    // Special named Axis cards (not following the numeric pattern)
    private val axisSpecialDebit = listOf(
        CardOption("Axis Burgundy DC",         "axisburgundydc"),
        CardOption("Axis Business Platinum DC", "axisbussinessplatinumdc"),
        CardOption("Axis Delight DC",          "axisdelightdc"),
        CardOption("Axis Priority DC",         "axisprioritydc"),
        CardOption("Axis Reward Plus DC",      "axisrewardplusdc")
    )

    private val axisSpecialCredit = listOf(
        CardOption("Axis ACE CC",          "axisacecc"),
        CardOption("Axis My Zone CC",      "axismyzonecc"),
        CardOption("Axis Neo CC",          "axisneocc"),
        CardOption("Axis Select CC",       "axisselectcc"),
        CardOption("Flipkart Axis CC",     "flipkartaxiscc")
    )

    private val iciciSpecialDebit = listOf(
        CardOption("ICICI Titanium DC", "icicititaniumdc"),
        CardOption("ICICI Wealth Management DC", "iciciwealthmangementdc"),
        CardOption("ICICI Rubyx DC", "icicirubyxdc"),
        CardOption("ICICI Sapphiro DC", "icicisapphirodc"),
        CardOption("ICICI Coral DC", "icicicoraldc")
    )

    private val iciciSpecialCredit = listOf(
        CardOption("Amazon Pay ICICI CC", "amazonpayicicicc"),
        CardOption("ICICI Rubyx CC", "icicirubyxcc"),
        CardOption("ICICI Sapphiro CC", "icicisapphirocc"),
        CardOption("ICICI Coral CC", "icicicoralcc"),
        CardOption("ICICI Platinum Chip CC", "iciciplatinumchipcc")
    )

    /**
     * Returns the list of [CardOption] for a given [bank] display name and
     * [cardType] ("Debit" or "Credit"). Always appends "Other Card" at the end.
     */
    fun getCards(bank: String, cardType: String): List<CardOption> {
        val bankKey = bankKeyMap[bank]
        val typeKey = if (cardType == "Debit") "debit" else "credit"
        val result = mutableListOf<CardOption>()

        if (bankKey != null) {
            // Generate numbered cards 1-5 (except for ICICI Bank which has specific names now)
            if (bankKey != "icici") {
                for (i in 1..5) {
                    val drawable = "${bankKey}${typeKey}card$i"
                    val label = "${bank} ${if (cardType == "Debit") "Debit" else "Credit"} Card $i"
                    result.add(CardOption(label, drawable))
                }
            } else {
                // Add specific ICICI cards
                if (cardType == "Debit") result.addAll(iciciSpecialDebit)
                else result.addAll(iciciSpecialCredit)
            }

            // Add special Axis cards
            if (bankKey == "axis") {
                if (cardType == "Debit") result.addAll(axisSpecialDebit)
                else result.addAll(axisSpecialCredit)
            }
        }

        // Always append "Other Card"
        result.add(CardOption("Other Card", "", isCustom = true))
        return result
    }

    /** Returns the list of bank display names for the spinner. */
    fun getBankList(): List<String> =
        listOf("Select Bank") + bankKeyMap.keys.toList() + listOf("Other")
}
