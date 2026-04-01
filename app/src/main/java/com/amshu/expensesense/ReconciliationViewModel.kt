package com.amshu.expensesense

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed class ParsingState {
    object Idle : ParsingState()
    object Loading : ParsingState()
    data class Success(val transactions: List<ReconciliationTransaction>) : ParsingState()
    data class Error(val message: String) : ParsingState()
}

class ReconciliationViewModel : ViewModel() {

    private val _parsingState = MutableLiveData<ParsingState>(ParsingState.Idle)
    val parsingState: LiveData<ParsingState> = _parsingState

    // Developer Debug Streams
    val debugRawText = MutableLiveData<String>()
    val debugRawSource = MutableLiveData<String>()
    val debugSectionText = MutableLiveData<String>()
    val debugBlocksText = MutableLiveData<String>()
    val debugChunksText = MutableLiveData<String>()
    val debugResponsesText = MutableLiveData<String>()

    data class TransactionBlock(val lines: MutableList<String> = mutableListOf()) {
        fun getCleanedText(): String {
            return lines.joinToString(" ")
                .replace(Regex("\\s+"), " ")
                .replace("*", "")
                .trim()
        }
    }

    fun updateRawDebug(text: String, source: String) {
        if (BuildConfig.DEBUG) { Log.d("ReconViewModel", "RAW TEXT LENGTH: ${text.length}") }
        if (BuildConfig.DEBUG) { Log.d("ReconViewModel", "RAW TEXT PREVIEW: ${text.take(200)}") }
        debugRawText.postValue(text)
        debugRawSource.postValue("Source: $source")
    }

    fun parseTransactions(rawText: String) {
        _parsingState.postValue(ParsingState.Loading)
        if (BuildConfig.DEBUG) { Log.d("ReconViewModel", "Local rule-based pipeline started") }

        viewModelScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                if (BuildConfig.DEBUG) { Log.d("ReconViewModel", "Parsing started") }
                val allLines = rawText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                
                val transactionLines = detectTransactionSection(allLines)
                debugSectionText.postValue(transactionLines.joinToString("\n"))

                val blocks = constructTransactionBlocks(transactionLines)
                debugBlocksText.postValue(blocks.joinToString("\n---\n") { it.getCleanedText() })
                if (BuildConfig.DEBUG) { Log.d("ReconViewModel", "Total blocks: ${blocks.size}") }

                val parsedList = mutableListOf<ReconciliationTransaction>()
                var unknownCount = 0

                for (block in blocks) {
                    if (block.lines.isEmpty()) continue

                    val extractedDate = extractDateValue(block.lines)
                    val extractedAmountObj = extractAmountAndLine(block.lines)
                    
                    if (extractedDate.isNullOrEmpty() || extractedAmountObj == null) {
                        continue
                    }

                    val description = extractDescription(block.lines, extractedDate, extractedAmountObj.rawText)
                    val type = classifyType(description, extractedAmountObj.rawText)
                    
                    if (type == "unknown") unknownCount++

                    parsedList.add(ReconciliationTransaction(
                        date = extractedDate,
                        description = description,
                        amount = Math.abs(extractedAmountObj.value),
                        type = type
                    ))
                }

                val finalResults = parsedList.distinct().sortedByDescending { it.date }
                
                if (BuildConfig.DEBUG) { Log.d("ReconViewModel", "Parsed transactions: ${finalResults.size}") }
                if (BuildConfig.DEBUG) { Log.d("ReconViewModel", "Unknown type count: $unknownCount") }
                if (BuildConfig.DEBUG) { Log.d("ReconViewModel", "Parsing completed in ${System.currentTimeMillis() - startTime}ms") }

                _parsingState.postValue(ParsingState.Success(finalResults))

            } catch (e: Exception) {
                if (BuildConfig.DEBUG) { Log.e("ReconViewModel", "Pipeline failure", e) }
                postError("Extraction failed: ${e.message}")
            }
        }
    }

    data class ExtractedAmount(val value: Double, val rawText: String, val lineMatch: String)

    private fun extractAmountAndLine(lines: List<String>): ExtractedAmount? {
        // Enforce user regex (using decimal part mandatory to avoid picking dates)
        val amountRegex = Regex("""-?\d{1,3}(?:,\d{3})*(?:\.\d{2})""")
        
        val allNumbers = mutableListOf<ExtractedAmount>()
        
        for (line in lines) {
            val matches = amountRegex.findAll(line)
            for (match in matches) {
                val rawStr = match.value
                val cleanStr = rawStr.replace(",", "")
                val value = cleanStr.toDoubleOrNull()
                if (value != null) {
                    allNumbers.add(ExtractedAmount(value, rawStr, line))
                }
            }
        }
        
        if (allNumbers.isEmpty()) return null
        if (allNumbers.size == 1) return allNumbers[0]
        
        // Heuristic fallback matching user rules
        if (allNumbers.size == 2) {
            return allNumbers[0] // first = transaction amount
        } else {
            return allNumbers[1] // middle often = amount
        }
    }

    private fun extractDateValue(lines: List<String>): String? {
        val dateRegex = Regex("""(\d{2}-\d{2}-\d{4})|(\d{2}/\d{2}/\d{4})|(\d{4}-\d{2}-\d{2})""")
        for (line in lines) {
            val match = dateRegex.find(line)
            if (match != null) {
                val rawDate = match.value
                val cleanDate = rawDate.replace("/", "-")
                if (cleanDate.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
                    return cleanDate
                } else {
                    val parts = cleanDate.split("-")
                    // DD-MM-YYYY to YYYY-MM-DD
                    return "${parts[2]}-${parts[1]}-${parts[0]}"
                }
            }
        }
        return null
    }

    private fun extractDescription(lines: List<String>, dateStr: String, amountStr: String): String {
        val cleanLines = mutableListOf<String>()
        val dateSplit = dateStr.split("-")
        val altDate1 = "${dateSplit[2]}/${dateSplit[1]}/${dateSplit[0]}"
        val altDate2 = "${dateSplit[2]}-${dateSplit[1]}-${dateSplit[0]}"
        
        for (line in lines) {
            var cleanLine = line
                .replace(dateStr, "")
                .replace(altDate1, "")
                .replace(altDate2, "")
                .replace(amountStr, "")
            cleanLines.add(cleanLine)
        }
        val combined = cleanLines.joinToString(" ")
        // Remove extra spaces and symbols, lowercase
        val noSymbols = combined.replace(Regex("""[^a-zA-Z0-9\s]"""), " ")
        return noSymbols.replace(Regex("""\s+"""), " ").trim().lowercase()
    }

    private val creditKeywords = listOf(
        "credit", "cr", "deposit", "dep", "received", "recv",
        "inward", "salary", "refund", "cashback",
        "interest", "int", "dividend",
        "reversal", "rev", "return", "incoming", "credited"
    )

    private val debitKeywords = listOf(
        "debit", "dr", "withdrawal", "wdl", "withdraw",
        "purchase", "spent", "charge", "charges", "fee",
        "atm wdl", "pos", "emi", "loan",
        "subscription", "insurance", "tax", "gst",
        "rent", "debited"
    )

    private fun classifyType(description: String, rawAmount: String): String {
        if (rawAmount.contains("-")) return "debit"
        
        var creditScore = 0
        var debitScore = 0
        
        creditKeywords.forEach {
            if (description.contains(it)) creditScore++
        }
        
        debitKeywords.forEach {
            if (description.contains(it)) debitScore++
        }
        
        if (creditScore > debitScore) return "credit"
        if (debitScore > creditScore) return "debit"
        return "unknown"
    }

    private val datePatterns = listOf(
        Regex("""^\d{2}/\d{2}/\d{4}"""),
        Regex("""^\d{2}-\d{2}-\d{4}""")
    )

    private fun isTransactionStart(line: String): Boolean {
        // Keeping logic matching existing behavior to avoid regressing section detection
        return datePatterns.any { it.matches(line.trim().take(10)) || it.containsMatchIn(line.trim().take(25)) } 
            || Regex("""(\d{2}-\d{2}-\d{4})|(\d{2}/\d{2}/\d{4})|(\d{4}-\d{2}-\d{2})""").containsMatchIn(line.trim().take(25))
    }

    private val detectionDateRegex = Regex(
        """(\d{2}-\d{2}-\d{4})|(\d{2}/\d{2}/\d{4})|(\d{4}-\d{2}-\d{2})|(\d{1,2}\s[A-Za-z]{3}\s\d{4})"""
    )
    private val detectionAmountRegex = Regex("""\d{1,3}(,\d{3})*(\.\d{2})?""")

    private val sectionHeaderKeywords = listOf(
        "date", "transaction", "description", "details",
        "particulars", "debit", "credit", "balance", "amount"
    )

    private fun isSectionHeader(line: String): Boolean {
        val lower = line.lowercase()
        return sectionHeaderKeywords.count { lower.contains(it) } >= 3
    }

    private fun isTransactionLike(line: String): Boolean {
        val lower = line.lowercase()
        val hasDate = detectionDateRegex.containsMatchIn(lower)
        val hasAmount = detectionAmountRegex.containsMatchIn(lower)

        val isDateRange = lower.contains("-") && lower.count { it == '-' } >= 2 && !hasAmount
        val isNoise = listOf(
            "account", "customer", "ifsc", "branch", "gst",
            "page", "statement", "address"
        ).any { lower.contains(it) }

        return hasDate && hasAmount && !isDateRange && !isNoise
    }

    private fun detectTransactionSection(lines: List<String>): List<String> {
        val WINDOW_SIZE = 6
        var startIndex = -1

        for (i in 0 until lines.size - WINDOW_SIZE) {
            val window = lines.subList(i, i + WINDOW_SIZE)
            val headerDetected = window.any { isSectionHeader(it) }
            val transactionCount = window.count { isTransactionLike(it) }

            if (headerDetected && transactionCount >= 2) {
                startIndex = i
                break
            }
        }

        if (startIndex == -1) {
            startIndex = lines.indexOfFirst { isTransactionLike(it) }
        }

        if (startIndex == -1) {
            startIndex = 0
        }

        var refinedStart = startIndex

        for (i in startIndex until lines.size) {
            if (isTransactionLike(lines[i])) {
                refinedStart = i
                break
            }
        }

        if (BuildConfig.DEBUG) { Log.d("START_DETECTION", "Initial startIndex: $startIndex") }
        if (BuildConfig.DEBUG) { Log.d("START_DETECTION", "Refined startIndex: $refinedStart") }
        if (BuildConfig.DEBUG) { Log.d("START_DETECTION", "First 5 lines from start:") }
        
        lines.subList(refinedStart, kotlin.math.min(refinedStart + 5, lines.size)).forEach {
            if (BuildConfig.DEBUG) { Log.d("START_DETECTION", it) }
        }

        return lines.subList(refinedStart, lines.size)
    }

    private fun constructTransactionBlocks(lines: List<String>): List<TransactionBlock> {
        val blocks = mutableListOf<TransactionBlock>()
        var startCount = 0
        var currentBlock: TransactionBlock? = null

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (isTransactionStart(line)) {
                startCount++
                currentBlock?.let { blocks.add(it) }

                currentBlock = TransactionBlock()
                currentBlock.lines.add(line)
            } else {
                currentBlock?.lines?.add(line)
            }
        }

        currentBlock?.let { blocks.add(it) }

        if (BuildConfig.DEBUG) { Log.d("ReconViewModel", "Detected starts: \$startCount") }
        return blocks
    }

    private fun postError(msg: String) {
        _parsingState.postValue(ParsingState.Error(msg))
    }
}
