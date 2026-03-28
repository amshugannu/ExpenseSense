package com.amshu.expensesense

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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

    private val chunksLog = StringBuilder()
    private val responsesLog = StringBuilder()

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(100, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    data class TransactionBlock(val lines: MutableList<String> = mutableListOf()) {
        fun getCleanedText(): String {
            return lines.joinToString(" ")
                .replace(Regex("\\s+"), " ")
                .replace("*", "")
                .trim()
        }
    }

    fun updateRawDebug(text: String, source: String) {
        Log.d("ReconViewModel", "RAW TEXT LENGTH: ${text.length}")
        Log.d("ReconViewModel", "RAW TEXT PREVIEW: ${text.take(200)}")
        debugRawText.postValue(text)
        debugRawSource.postValue("Source: $source")
    }

    fun parseTransactions(rawText: String) {
        _parsingState.postValue(ParsingState.Loading)
        Log.d("ReconViewModel", "Upgraded pipeline started")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // STAGE 1: TRANSACTION SECTION DETECTION
                val allLines = rawText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                Log.d("Recon", "Total lines: ${allLines.size}")

                val transactionLines = detectTransactionSection(allLines)
                Log.d("Recon", "Transaction lines: ${transactionLines.size}")
                debugSectionText.postValue(transactionLines.joinToString("\n"))

                // Fetch API Key (Needed for both standard and fallback)
                val apiKey = BuildConfig.GEMINI_API_KEY_2
                if (apiKey.isNullOrEmpty()) {
                    postError("Gemini API key is missing.")
                    return@launch
                }

                // STAGE 2 & 3: BLOCK CONSTRUCTION & CLEANING
                val transactionSectionSize = transactionLines.size
                Log.d("Recon", "Transaction section size: $transactionSectionSize")

                val blocks = constructTransactionBlocks(transactionLines)
                
                // FILTER: Only keep blocks with an amount line
                val amountRegex = Regex("""\d+\.\d{2}""")
                val validBlocks = blocks.filter { block -> 
                    block.lines.any { it.contains(amountRegex) } 
                }

                Log.d("Recon", "Blocks BEFORE validation: ${blocks.size}")
                Log.d("Recon", "Blocks AFTER validation: ${validBlocks.size}")

                if (validBlocks.isEmpty()) {
                    Log.d("Recon", "No valid blocks -> fallback triggered")
                    val fallbackText = transactionLines.joinToString("\n")
                    debugBlocksText.postValue(fallbackText + "\n (FALLBACK: RAW SECTION)")
                    
                    val results = processChunkWithGemini(fallbackText, apiKey, 1)
                    val finalResults = finalizeTransactions(results)
                    
                    if (finalResults.isNotEmpty()) {
                        Log.d("ReconViewModel", "Fallback Success: ${finalResults.size} transactions")
                        _parsingState.postValue(ParsingState.Success(finalResults))
                    } else {
                        postError("No transactions detected in this statement")
                    }
                    return@launch
                }

                val cleanedBlocks = validBlocks.map { it.getCleanedText() }
                debugBlocksText.postValue(cleanedBlocks.joinToString("\n---\n"))

                // STAGE 4: HEADER HANDLING
                val header = detectHeader(allLines, transactionLines)

                // STAGE 5: CHUNKING
                val chunks = createChunks(cleanedBlocks, header)
                debugChunksText.postValue(chunks.joinToString("\n\n=== CHUNK ===\n\n"))

                // STAGE 6 & 7: AI PARSING & RESPONSE HANDLING
                chunksLog.clear()
                responsesLog.clear()
                val allTransactions = mutableListOf<ReconciliationTransaction>()

                for ((index, chunk) in chunks.withIndex()) {
                    Log.d("ReconViewModel", "Processing chunk ${index + 1}/${chunks.size}")
                    val results = processChunkWithGemini(chunk, apiKey, index + 1)
                    allTransactions.addAll(results)
                }

                // AI FALLBACK: If total results are zero, use local regex extraction from validBlocks
                if (allTransactions.isEmpty()) {
                    Log.d("Recon", "AI parsing yielded 0 results -> using local regex fallback")
                    for (block in validBlocks) {
                        allTransactions.add(ReconciliationTransaction(
                            date = extractDate(block),
                            description = block.getCleanedText(),
                            amount = extractAmount(block),
                            type = "unknown"
                        ))
                    }
                }

                // STAGE 8: FINAL CLEANUP
                val finalResults = finalizeTransactions(allTransactions)

                if (finalResults.isNotEmpty()) {
                    Log.d("ReconViewModel", "Success: ${finalResults.size} transactions")
                    _parsingState.postValue(ParsingState.Success(finalResults))
                } else {
                    // One final effort: if everything failed, it likely means no transactions.
                    // Instead of a hard error, show whatever we have or inform.
                    _parsingState.postValue(ParsingState.Success(emptyList()))
                }

            } catch (e: Exception) {
                Log.e("ReconViewModel", "Pipeline failure", e)
                postError("Extraction failed: ${e.message}")
            }
        }
    }

    private val datePatterns = listOf(
        Regex("""^\d{2}/\d{2}/\d{4}"""),
        Regex("""^\d{2}-\d{2}-\d{4}""")
    )

    private fun isTransactionStart(line: String): Boolean {
        return datePatterns.any { it.matches(line.trim().take(10)) || it.containsMatchIn(line.trim().take(25)) }
    }

    private fun hasAmountNearby(lines: List<String>, index: Int): Boolean {
        val amountRegex = Regex("""\d+\.\d{2}""")
        val endPeek = minOf(index + 5, lines.size)
        // Check current line and next 5 lines
        return lines.subList(index, endPeek).any { amountRegex.containsMatchIn(it) }
    }

    private val headerKeywords = listOf(
        "date", "transaction", "particulars", "description",
        "details", "debit", "credit", "balance",
        "amount", "withdrawal", "deposit"
    )

    private fun isHeaderLine(line: String): Boolean {
        val lowerLine = line.lowercase()
        return headerKeywords.count { lowerLine.contains(it) } >= 3
    }

    private val footerKeywords = listOf(
        "closing balance", "opening balance", "summary",
        "statement summary", "bank charges",
        "registered office", "gst",
        "customer care", "contact", "support"
    )

    private fun isFooterLine(line: String): Boolean {
        val lowerLine = line.lowercase()
        return footerKeywords.any { lowerLine.contains(it) }
    }

    private fun detectTransactionSection(lines: List<String>): List<String> {
        var headerFound = false
        var startIndex = 0

        // 1. Header Detection (Start Logic)
        for (i in lines.indices) {
            val current = lines[i].lowercase()
            val next = lines.getOrNull(i + 1)?.lowercase() ?: ""
            val combined = "$current $next"

            val keywordMatches = headerKeywords.count { combined.contains(it) }

            if (!headerFound && keywordMatches >= 3) {
                headerFound = true
                startIndex = i + 1 // Transactions start AFTER header
                break
            }
        }

        if (!headerFound) return lines // Fallback if no header found

        // 2. Soft End Detection (Footer Logic)
        var footerCount = 0
        var endIndex = lines.size

        for (i in startIndex until lines.size) {
            val line = lines[i].lowercase()

            if (isFooterLine(line)) {
                footerCount++
            } else {
                footerCount = 0
            }

            if (footerCount >= 3) {
                endIndex = i - 2 // Go back to where footer started
                break
            }
        }

        // 3. Extraction & Cleaning
        val transactionLines = lines.subList(startIndex, endIndex)
        val noiseKeywords = listOf("page", "account number", "ifsc", "branch", "customer id")

        return transactionLines.filter { line ->
            val lowerLine = line.lowercase()
            
            // Skip repeated headers
            if (isHeaderLine(lowerLine)) return@filter false
            
            // Skip noise lines
            if (noiseKeywords.any { lowerLine.contains(it) }) return@filter false
            
            true
        }
    }

    private fun constructTransactionBlocks(lines: List<String>): List<TransactionBlock> {
        val blocks = mutableListOf<TransactionBlock>()
        var startCount = 0
        var currentBlock: TransactionBlock? = null

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (isTransactionStart(line)) {
                startCount++
                // close previous block
                currentBlock?.let { blocks.add(it) }

                currentBlock = TransactionBlock()
                currentBlock.lines.add(line)
            } else {
                currentBlock?.lines?.add(line)
            }
        }

        // add last block
        currentBlock?.let { blocks.add(it) }

        Log.d("Recon", "Detected starts: $startCount")
        return blocks
    }

    private fun detectHeader(allLines: List<String>, transactionLines: List<String>): String {
        val defaultHeader = "Date | Description | Debit | Credit | Balance"
        
        if (transactionLines.isEmpty()) return defaultHeader
        
        val firstTxLine = transactionLines[0]
        val txStartIndexInAll = allLines.indexOf(firstTxLine)
        
        if (txStartIndexInAll > 0) {
            val potentialHeader = allLines[txStartIndexInAll - 1]
            // Heuristic: 3+ tokens, no amounts (dots/digits alone), reasonable length
            val tokens = potentialHeader.split(Regex("\\s+")).filter { it.length > 2 }
            if (tokens.size >= 3 && potentialHeader.length in 20..120 && !potentialHeader.contains(Regex("\\d+\\.\\d{2}"))) {
                return potentialHeader
            }
        }
        
        return defaultHeader
    }

    private fun createChunks(blocks: List<String>, header: String): List<String> {
        // Max 10 transactions per chunk, reduce if tokens large (lines are long)
        val chunkSize = if (blocks.any { it.length > 200 }) 7 else 10
        return blocks.chunked(chunkSize).map { chunk ->
            header + "\n" + chunk.joinToString("\n")
        }
    }

    private fun processChunkWithGemini(chunkText: String, apiKey: String, chunkIndex: Int): List<ReconciliationTransaction> {
        val parsedList = mutableListOf<ReconciliationTransaction>()
        try {
            val prompt = """
                Extract financial transactions from the following bank statement data.
                Rules:
                - Return ONLY valid JSON array
                - Map fields: date (YYYY-MM-DD), description, amount (num), type (debit/credit)
                JSON:
                $chunkText
            """.trimIndent()

            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", prompt)
                    }))
                }))
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
                .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBodyString = response.body?.string()
            
            responsesLog.append("--- CHUNK $chunkIndex ---\n")

            if (response.isSuccessful && responseBodyString != null) {
                val jsonResponse = JSONObject(responseBodyString)
                val aiText = jsonResponse.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text") ?: ""

                val cleanedResponse = aiText.trim()
                    .replace("`json", "")
                    .replace("`", "")
                    .trim()

                Log.d("Recon", "CLEANED AI RESPONSE: $cleanedResponse")
                responsesLog.append("RAW: ").append(maskSensitiveData(aiText)).append("\n")
                responsesLog.append("CLEANED: ").append(maskSensitiveData(cleanedResponse)).append("\n\n")
                debugResponsesText.postValue(responsesLog.toString())

                try {
                    val jsonArray = JSONArray(cleanedResponse)
                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.optJSONObject(i) ?: continue
                        val rawDate = item.optString("date", "")
                        val rawDesc = item.optString("description", "")
                        val rawAmount = item.optDouble("amount", 0.0)
                        val rawType = item.optString("type", "unknown").lowercase()

                        if (rawDate.isNotEmpty() && rawAmount > 0.0) {
                            parsedList.add(ReconciliationTransaction(
                                normalizeDate(rawDate),
                                normalizeDescription(rawDesc),
                                rawAmount,
                                if (rawType.contains("credit")) "credit" else "debit"
                            ))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Recon", "JSON parse error", e)
                }
            } else {
                responsesLog.append("API ERROR: ${response.code}\n\n")
                debugResponsesText.postValue(responsesLog.toString())
            }
        } catch (e: Exception) {
            Log.e("ReconViewModel", "Gemini chunk failure", e)
        }
        return parsedList
    }

    private fun extractDate(block: TransactionBlock): String {
        val dateRegex = Regex("""\d{2}[-/]\d{2}[-/]\d{4}""")
        val firstLine = block.lines.firstOrNull() ?: ""
        val match = dateRegex.find(firstLine)
        return match?.value ?: ""
    }

    private fun extractAmount(block: TransactionBlock): Double {
        val amountRegex = Regex("""\d+,\d+\.\d{2}|\d+\.\d{2}""")
        for (line in block.lines.reversed()) { // Try from end (usually balance/amount)
            val match = amountRegex.find(line.replace(",", ""))
            if (match != null) return match.value.toDoubleOrNull() ?: 0.0
        }
        return 0.0
    }

    private fun finalizeTransactions(txs: List<ReconciliationTransaction>): List<ReconciliationTransaction> {
        return txs.distinct()
            .filter { it.amount > 0 && it.description.isNotEmpty() }
            .sortedByDescending { it.date }
    }

    private fun normalizeDate(raw: String): String {
        return raw.replace(Regex("[^0-9-]"), "")
    }

    private fun normalizeDescription(raw: String): String {
        return raw.trim().replace(Regex("\\s+"), " ")
    }

    private fun maskSensitiveData(input: String): String {
        val regex = Regex("""\b\d{5,}\b""")
        return input.replace(regex, "***")
    }

    private fun postError(msg: String) {
        _parsingState.postValue(ParsingState.Error(msg))
    }
}
