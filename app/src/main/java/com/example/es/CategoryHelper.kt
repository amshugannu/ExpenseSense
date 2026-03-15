package com.example.es

object CategoryHelper {

    private val keywordsMap = mapOf(
        "Travel" to listOf("uber", "ola", "rapido", "taxi", "flight", "train", "bus", "metro", "airline", "travel", "hotel", "stay", "airbnb", "booking"),
        "Fuel" to listOf("petrol", "diesel", "fuel", "gas station", "cng", "shell", "hp", "bpcl", "iocl"),
        "Dinner" to listOf("dinner", "lunch", "breakfast", "restaurant", "cafe", "hotel", "food", "swiggy", "zomato", "kfc", "mcdonald", "burger", "pizza", "starbucks"),
        "Groceries" to listOf("grocery", "groceries", "milk", "vegetables", "fruit", "supermarket", "mart", "blinkit", "zepto", "bigbasket", "walmart", "egg", "meat"),
        "Movies" to listOf("movie", "cinema", "theatre", "imax", "pvr", "inox", "bookmyshow", "netflix", "prime video", "hotstar", "disney"),
        "Internet" to listOf("wifi", "broadband", "recharge", "jio", "airtel", "vi", "bsnl", "data", "internet", "modem", "router"),
        "Medical" to listOf("doctor", "hospital", "medicine", "pharmacy", "clinic", "health", "dental", "surgery", "medical", "test", "checkup"),
        "Gas" to listOf("gas", "cylinder", "lpg", "pipeline", "utility"),
        "Gifts" to listOf("gift", "present", "birthday", "anniversary", "wedding", "flower", "chocolate", "surprise"),
        "Bills" to listOf("bill", "electricity", "water", "rent", "maintenance", "recharge", "payment", "due"),
        "Shopping" to listOf("amazon", "flipkart", "myntra", "ajio", "mall", "clothing", "fashion", "shoes", "electronics", "buying", "bought", "purchase"),
        "Subscriptions" to listOf("subscription", "monthly", "yearly", "renewal", "membership", "sub", "spotify", "youtube premium", "gym")
    )

    fun detectCategory(title: String): String {
        val normalizedTitle = title.lowercase()
        for ((category, keywords) in keywordsMap) {
            if (keywords.any { normalizedTitle.contains(it) }) {
                return category
            }
        }
        return "other"
    }
}
