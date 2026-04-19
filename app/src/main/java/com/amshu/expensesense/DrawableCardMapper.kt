package com.amshu.expensesense

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

    private val bobSpecialDebit = listOf(
        CardOption("Baroda Sapphire HIM DC", "barodasapphirehimdc"),
        CardOption("Baroda Sapphire HER DC", "barodasapphireherdc"),
        CardOption("Baroda Platinum DC", "barodaplatinumdc"),
        CardOption("Baroda Vyapaar DC", "barodavyapaardc"),
        CardOption("Baroda Visa Classic DC", "barodavisaclassicdc"),
        CardOption("Baroda Classic DC", "barodaclassicdc")
    )

    private val bobSpecialCredit = listOf(
        CardOption("BOB Card Premier CC", "bobcardpremiercc"),
        CardOption("BOB Card Vikram CC", "bobcardvikramcc"),
        CardOption("BOB Card Select CC", "bobcardselectcc"),
        CardOption("HPCL Energie BOB Card CC", "hpclenergiebobcardcc"),
        CardOption("BOB Card Easy CC", "bobcardeasycc")
    )

    private val sbiSpecialDebit = listOf(
        CardOption("SBI Platinum RuPay Select DC", "sbiplatinumrepupayselectdc"),
        CardOption("SBI Virtual Global DC", "sbivirtualgobaldc"),
        CardOption("SBI Global DC", "sbigobaldc"),
        CardOption("SBI RuPay PMJDY DC", "sbirupaypmjdydc"),
        CardOption("SBI Mumbai Metro Smart DC", "sbimumbaimetrosmartdc")
    )

    private val sbiSpecialCredit = listOf(
        CardOption("SBI Pulse CC", "sbiplusecc"),
        CardOption("SBI Elite CC", "sbielitecc"),
        CardOption("SBI Prime CC", "sbiprimecc"),
        CardOption("BPCL SBI CC", "bpclsbicc"),
        CardOption("SBI SimplySave CC", "sbisimplysavecc")
    )

    private val kotakSpecialDebit = listOf(
        CardOption("Kotak Everyday DC", "kotakeverydaydc"),
        CardOption("Kotak 811 DC", "kotak811dc"),
        CardOption("Kotak My Junior DC", "kotakmyjuniordc"),
        CardOption("Kotak RuPay DC", "kotakrupaydc"),
        CardOption("Kotak PayShopMore DC", "kotakpayshopmoredc")
    )

    private val kotakSpecialCredit = listOf(
        CardOption("Kotak League CC", "kotakleaguecc"),
        CardOption("Kotak IndianOil CC", "kotakindianoilcc"),
        CardOption("Kotak PVR INOX CC", "kotakpvrinoxcc"),
        CardOption("Kotak Myntra CC", "kotakmyntracc"),
        CardOption("Kotak Mojo Platinum CC", "kotakmojoplatinumcc")
    )

    private val unionSpecialDebit = listOf(
        CardOption("Union RuPay DC", "unionrupaydc"),
        CardOption("Union Visa Signature DC", "unionvisasignaturedc"),
        CardOption("Union PMJDY DC", "unionpmjdydc"),
        CardOption("Union Platinum DC", "unionplatinundc"),
        CardOption("Union Classic DC", "unionclassicdc")
    )

    private val unionSpecialCredit = listOf(
        CardOption("Union Visa Signature CC", "unionvisasignaturecc"),
        CardOption("Union Visa Platinum CC", "unionvisaplatinumcc"),
        CardOption("Union Divaa CC", "uniondivaacc"),
        CardOption("Union RuPay Select CC", "unionrupayselectcc"),
        CardOption("Union RuPay Platinum CC", "unionrupayplatinumcc")
    )

    private val hdfcSpecialDebit = listOf(
        CardOption("HDFC Millennia DC", "hdfcmillenniadc"),
        CardOption("HDFC Platinum DC", "hdfcplatinumdc"),
        CardOption("HDFC Moneyback DC", "hdfcmoneybackdc"),
        CardOption("HDFC Women Advantage DC", "hdfcwomenadvantagedc"),
        CardOption("HDFC EasyShop Platinum DC", "hdfceasyshopplatinumdc")
    )

    private val hdfcSpecialCredit = listOf(
        CardOption("HDFC Regalia Gold CC", "hdfcregaliagoldcc"),
        CardOption("HDFC Shoppers Stop CC", "hdfcshoppersstopcc"),
        CardOption("HDFC Swiggy CC", "hdfcswiggycc"),
        CardOption("HDFC Freedom CC", "hdfcfreedomcc"),
        CardOption("HDFC IndianOil CC", "hdfcindianoilcc")
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
            // Generate numbered cards 1-5 (except for banks with specific named cards)
            val specificBanks = listOf("icici", "sbi", "bob", "kotak", "union", "hdfc")
            if (bankKey !in specificBanks) {
                for (i in 1..5) {
                    val drawable = "${bankKey}${typeKey}card$i"
                    val label = "${bank} ${if (cardType == "Debit") "Debit" else "Credit"} Card $i"
                    result.add(CardOption(label, drawable))
                }
            } else {
                // Add specific cards for selected banks
                when (bankKey) {
                    "icici" -> {
                        if (cardType == "Debit") result.addAll(iciciSpecialDebit)
                        else result.addAll(iciciSpecialCredit)
                    }
                    "sbi" -> {
                        if (cardType == "Debit") result.addAll(sbiSpecialDebit)
                        else result.addAll(sbiSpecialCredit)
                    }
                    "bob" -> {
                        if (cardType == "Debit") result.addAll(bobSpecialDebit)
                        else result.addAll(bobSpecialCredit)
                    }
                    "kotak" -> {
                        if (cardType == "Debit") result.addAll(kotakSpecialDebit)
                        else result.addAll(kotakSpecialCredit)
                    }
                    "union" -> {
                        if (cardType == "Debit") result.addAll(unionSpecialDebit)
                        else result.addAll(unionSpecialCredit)
                    }
                    "hdfc" -> {
                        if (cardType == "Debit") result.addAll(hdfcSpecialDebit)
                        else result.addAll(hdfcSpecialCredit)
                    }
                }
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
