package com.amshu.expensesense

import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class AddCardActivity : AppCompatActivity() {

    // ── Views ──────────────────────────────────────────────────────────────
    private lateinit var spinnerBank: Spinner
    private lateinit var layoutCustomBank: View
    private lateinit var etCustomBankName: EditText

    private lateinit var cardStepType: CardView
    private lateinit var spinnerCardType: Spinner

    // Step 3a: predefined card picker (for known banks)
    private lateinit var cardStepPicker: CardView
    private lateinit var rvCardOptions: RecyclerView
    private lateinit var layoutCustomCard: View
    private lateinit var etCustomCardName: EditText

    // Step 3b: other-bank free-text card name
    private lateinit var cardStepOtherBankCard: CardView
    private lateinit var etOtherBankCardName: EditText

    // Step 4: holder details + type-specific fields
    private lateinit var cardStepDetails: CardView
    private lateinit var etCardHolderName: EditText
    private lateinit var etCardNumber: EditText
    private lateinit var layoutCreditFields: View
    private lateinit var etCreditLimit: EditText
    private lateinit var etAvailableLimit: EditText
    private lateinit var layoutDebitFields: View
    private lateinit var cbAddBankAccount: CheckBox
    private lateinit var layoutNewAccount: View
    private lateinit var etAccountBalance: EditText
    private lateinit var spinnerAccountType: Spinner

    private lateinit var btnSaveCard: MaterialButton
    private lateinit var btnBack: View

    // ── Repositories ────────────────────────────────────────────────────────
    private lateinit var cardRepository: CardRepository
    private lateinit var accountRepository: AccountRepository

    // ── State ───────────────────────────────────────────────────────────────
    private var selectedBank: String = ""
    private var isOtherBank: Boolean = false
    private var selectedCardType: String = "" // "Debit" or "Credit"
    private var selectedOption: CardOption? = null
    private var editingCardName: String? = null

    private lateinit var cardOptionAdapter: CardOptionAdapter

    companion object {
        const val EXTRA_CARD_NAME = "extra_card_name"
    }

    // ────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_card)

        // Repositories
        val database = AppDatabase.getDatabase(this)
        cardRepository = CardRepository(
            database.debitCardDao(),
            database.creditCardDao(),
            database.accountDao(),
            database.cardDao()
        )
        accountRepository = AccountRepository(database.accountDao())

        bindViews()
        setupBankSpinner()
        setupCardTypeSpinner()
        setupCardOptionsRecycler()
        setupCheckbox()

        btnBack.setOnClickListener { finish() }
        btnSaveCard.setOnClickListener { saveCard() }

        // Edit mode
        editingCardName = intent.getStringExtra(EXTRA_CARD_NAME)
        val cardType = intent.getStringExtra("extra_card_type")
        if (!editingCardName.isNullOrEmpty()) {
            loadCardData(editingCardName!!, cardType)
            btnSaveCard.text = "Update Card"
            findViewById<TextView>(R.id.headerTitle)?.text = "Edit Card"
        } else {
            prefillFromScan()
        }

    }

    // ── View Binding ─────────────────────────────────────────────────────────

    private fun bindViews() {
        btnBack            = findViewById(R.id.btnBack)
        spinnerBank        = findViewById(R.id.spinnerBank)
        layoutCustomBank   = findViewById(R.id.layoutCustomBank)
        etCustomBankName   = findViewById(R.id.etCustomBankName)

        cardStepType       = findViewById(R.id.cardStepType)
        spinnerCardType    = findViewById(R.id.spinnerCardType)

        cardStepPicker     = findViewById(R.id.cardStepPicker)
        rvCardOptions      = findViewById(R.id.rvCardOptions)
        layoutCustomCard   = findViewById(R.id.layoutCustomCard)
        etCustomCardName   = findViewById(R.id.etCustomCardName)

        cardStepOtherBankCard  = findViewById(R.id.cardStepOtherBankCard)
        etOtherBankCardName    = findViewById(R.id.etOtherBankCardName)

        cardStepDetails    = findViewById(R.id.cardStepDetails)
        etCardHolderName   = findViewById(R.id.etCardHolderName)
        etCardNumber       = findViewById(R.id.etCardNumber)
        layoutCreditFields = findViewById(R.id.layoutCreditFields)
        etCreditLimit      = findViewById(R.id.etCreditLimit)
        etAvailableLimit   = findViewById(R.id.etAvailableLimit)
        layoutDebitFields  = findViewById(R.id.layoutDebitFields)
        cbAddBankAccount   = findViewById(R.id.cbAddBankAccount)
        layoutNewAccount   = findViewById(R.id.layoutNewAccount)
        etAccountBalance   = findViewById(R.id.etAccountBalance)
        spinnerAccountType = findViewById(R.id.spinnerAccountType)
        btnSaveCard        = findViewById(R.id.btnSaveCard)
    }

    // ── Bank Spinner ──────────────────────────────────────────────────────────

    private fun setupBankSpinner() {
        val banks = DrawableCardMapper.getBankList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, banks)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerBank.adapter = adapter

        spinnerBank.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val bank = parent?.getItemAtPosition(pos).toString()
                if (bank == "Select Bank") {
                    selectedBank = ""
                    isOtherBank = false
                    layoutCustomBank.visibility = View.GONE
                    resetFromBankChange()
                    return
                }
                if (bank == "Other") {
                    isOtherBank = true
                    selectedBank = ""
                    layoutCustomBank.visibility = View.VISIBLE
                } else {
                    isOtherBank = false
                    selectedBank = bank
                    layoutCustomBank.visibility = View.GONE
                }
                resetFromBankChange()
                revealStep(cardStepType)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // ── Card Type Spinner ─────────────────────────────────────────────────────

    private fun setupCardTypeSpinner() {
        spinnerCardType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val type = parent?.getItemAtPosition(pos).toString()
                if (type == "Select Type") {
                    selectedCardType = ""
                    resetFromTypeChange()
                    return
                }
                selectedCardType = if (type == "Debit Card") "Debit" else "Credit"
                resetFromTypeChange()

                if (isOtherBank) {
                    // Other bank → show free-text card section, skip picker
                    revealStep(cardStepOtherBankCard)
                    revealStep(cardStepDetails)
                    showTypeSpecificFields()
                } else {
                    // Known bank → load filtered card options
                    val currentBank = if (isOtherBank) etCustomBankName.text.toString().trim() else selectedBank
                    val options = DrawableCardMapper.getCards(currentBank, selectedCardType)
                    cardOptionAdapter.updateOptions(options)
                    revealStep(cardStepPicker)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // ── Card Options RecyclerView ─────────────────────────────────────────────

    private fun setupCardOptionsRecycler() {
        cardOptionAdapter = CardOptionAdapter(this, emptyList()) { option ->
            selectedOption = option
            if (option.isCustom) {
                layoutCustomCard.visibility = View.VISIBLE
            } else {
                layoutCustomCard.visibility = View.GONE
            }
            // Always reveal step 4 once user picks a card
            revealStep(cardStepDetails)
            showTypeSpecificFields()
        }

        rvCardOptions.apply {
            layoutManager = LinearLayoutManager(this@AddCardActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = cardOptionAdapter
            setHasFixedSize(false)
        }
    }

    // ── Checkbox ──────────────────────────────────────────────────────────────

    private fun setupCheckbox() {
        cbAddBankAccount.setOnCheckedChangeListener { _, isChecked ->
            layoutNewAccount.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
    }

    // ── UI Helpers ────────────────────────────────────────────────────────────

    private fun revealStep(cardView: CardView) {
        if (cardView.visibility == View.VISIBLE) return
        cardView.visibility = View.VISIBLE
        val anim = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        anim.duration = 280
        cardView.startAnimation(anim)
    }

    private fun hideStep(cardView: CardView) {
        cardView.visibility = View.GONE
    }

    private fun showTypeSpecificFields() {
        if (selectedCardType == "Credit") {
            layoutCreditFields.visibility = View.VISIBLE
            layoutDebitFields.visibility = View.GONE
        } else {
            layoutCreditFields.visibility = View.GONE
            layoutDebitFields.visibility = View.VISIBLE
        }
    }

    private fun resetFromBankChange() {
        selectedCardType = ""
        selectedOption = null
        spinnerCardType.setSelection(0)
        cardOptionAdapter.clearSelection()
        layoutCustomCard.visibility = View.GONE
        cardStepPicker.visibility = View.GONE
        cardStepOtherBankCard.visibility = View.GONE
        cardStepDetails.visibility = View.GONE
        layoutCreditFields.visibility = View.GONE
        layoutDebitFields.visibility = View.GONE
    }

    private fun resetFromTypeChange() {
        selectedOption = null
        cardOptionAdapter.clearSelection()
        layoutCustomCard.visibility = View.GONE
        cardStepPicker.visibility = View.GONE
        cardStepOtherBankCard.visibility = View.GONE
        cardStepDetails.visibility = View.GONE
        layoutCreditFields.visibility = View.GONE
        layoutDebitFields.visibility = View.GONE
    }

    // ── Validation & Save ─────────────────────────────────────────────────────

    private fun saveCard() {
        // Resolve bank name
        val bank = if (isOtherBank) etCustomBankName.text.toString().trim() else selectedBank

        if (bank.isEmpty()) {
            Toast.makeText(this, "Please select or enter a bank", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedCardType.isEmpty()) {
            Toast.makeText(this, "Please select a card type", Toast.LENGTH_SHORT).show()
            return
        }

        // Resolve card name and drawable
        val cardName: String
        val drawableName: String

        if (isOtherBank) {
            val otherName = etOtherBankCardName.text.toString().trim()
            if (otherName.isEmpty()) {
                Toast.makeText(this, "Please enter a card name", Toast.LENGTH_SHORT).show()
                return
            }
            cardName = otherName
            drawableName = ""
        } else {
            val opt = selectedOption
            if (opt == null) {
                Toast.makeText(this, "Please select a card", Toast.LENGTH_SHORT).show()
                return
            }
            if (opt.isCustom) {
                val customName = etCustomCardName.text.toString().trim()
                if (customName.isEmpty()) {
                    Toast.makeText(this, "Please enter a card name", Toast.LENGTH_SHORT).show()
                    return
                }
                cardName = customName
                drawableName = ""
            } else {
                cardName = opt.displayName
                drawableName = opt.drawableName
            }
        }

        val holder = etCardHolderName.text.toString().trim()
        val number = etCardNumber.text.toString().trim()

        if (holder.isEmpty() || number.isEmpty()) {
            Toast.makeText(this, "Please fill card holder name and card number", Toast.LENGTH_SHORT).show()
            return
        }

        val last4 = if (number.length >= 4) number.takeLast(4) else number

        // Sanitize card name for use as Firebase key
        val safeCardName = cardName.replace(Regex("[.#\$\\[\\]/]"), "_")

        if (selectedCardType == "Credit") {
            val limit     = etCreditLimit.text.toString().toDoubleOrNull() ?: 0.0
            val available = etAvailableLimit.text.toString().toDoubleOrNull() ?: 0.0
            val card = CreditCard(
                cardHolderName = holder,
                cardNumber     = number,
                cardName       = safeCardName,
                bankName       = bank,
                last4Digits    = last4,
                totalLimit     = limit,
                availableLimit = available,
                drawableName   = drawableName
            )
            cardRepository.saveCreditCard(card) { success ->
                runOnUiThread {
                    if (success) {
                        Toast.makeText(this, if (editingCardName.isNullOrEmpty()) "Credit Card Saved!" else "Credit Card Updated!", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this, "Error saving card. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            val saveDebitAction = {
                val card = DebitCard(
                    cardHolderName     = holder,
                    cardNumber         = number,
                    cardName           = safeCardName,
                    bankName           = bank,
                    last4Digits        = last4,
                    linkedBankAccountId = bank,
                    drawableName       = drawableName
                )
                cardRepository.saveDebitCard(card) { success ->
                    runOnUiThread {
                        if (success) {
                            Toast.makeText(this, if (editingCardName.isNullOrEmpty()) "Debit Card Saved!" else "Debit Card Updated!", Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            Toast.makeText(this, "Error saving card. Please try again.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            if (cbAddBankAccount.isChecked) {
                val balance = etAccountBalance.text.toString().toDoubleOrNull() ?: 0.0
                val accType = spinnerAccountType.selectedItem.toString()
                val account = Account(name = bank, type = accType, balance = balance)
                accountRepository.saveAccount(account) { saveDebitAction() }
            } else {
                saveDebitAction()
            }
        }
    }

    // ── Edit Mode Pre-fill ────────────────────────────────────────────────────

    private fun loadCardData(name: String, type: String?) {
        if (type == "Credit") {
            cardRepository.getCreditCardByName(name) { card ->
                if (card != null) {
                    runOnUiThread { prefillCredit(card) }
                }
            }
        } else {
            cardRepository.getDebitCardByName(name) { card ->
                if (card != null) {
                    runOnUiThread { prefillDebit(card) }
                }
            }
        }
    }

    private fun prefillCredit(card: CreditCard) {
        val bankName = card.bankName ?: ""
        val banks    = DrawableCardMapper.getBankList()
        val bankIdx  = banks.indexOf(bankName).takeIf { it >= 0 } ?: (banks.size - 1)

        selectedBank     = bankName
        selectedCardType = "Credit"
        isOtherBank      = (bankIdx == banks.size - 1 && bankName != "Other")

        spinnerBank.setSelection(bankIdx)

        // Reveal type step and set Credit
        revealStep(cardStepType)
        spinnerCardType.setSelection(2) // "Credit Card"

        // Reveal picker and pre-select match
        val options = DrawableCardMapper.getCards(bankName, "Credit")
        cardOptionAdapter.updateOptions(options)
        revealStep(cardStepPicker)

        // Try to find pre-existing drawable
        val matchIdx = options.indexOfFirst { it.drawableName == (card.drawableName ?: "") }
        if (matchIdx >= 0) {
            selectedOption = options[matchIdx]
            cardOptionAdapter.notifyItemChanged(matchIdx)
        } else {
            // Show Other Card input with existing name
            val otherIdx = options.indexOfFirst { it.isCustom }
            if (otherIdx >= 0) {
                selectedOption = options[otherIdx]
                layoutCustomCard.visibility = View.VISIBLE
                etCustomCardName.setText(card.cardName ?: "")
            }
        }

        revealStep(cardStepDetails)
        etCardHolderName.setText(card.cardHolderName ?: "")
        etCardNumber.setText(card.cardNumber ?: "")
        layoutCreditFields.visibility = View.VISIBLE
        layoutDebitFields.visibility = View.GONE
        etCreditLimit.setText(card.totalLimit?.toString() ?: "0.0")
        etAvailableLimit.setText(card.availableLimit?.toString() ?: "0.0")
    }

    private fun prefillDebit(card: DebitCard) {
        val bankName = card.bankName ?: ""
        val banks    = DrawableCardMapper.getBankList()
        val bankIdx  = banks.indexOf(bankName).takeIf { it >= 0 } ?: (banks.size - 1)

        selectedBank     = bankName
        selectedCardType = "Debit"
        isOtherBank      = (bankIdx == banks.size - 1 && bankName != "Other")

        spinnerBank.setSelection(bankIdx)
        revealStep(cardStepType)
        spinnerCardType.setSelection(1) // "Debit Card"

        val options = DrawableCardMapper.getCards(bankName, "Debit")
        cardOptionAdapter.updateOptions(options)
        revealStep(cardStepPicker)

        val matchIdx = options.indexOfFirst { it.drawableName == (card.drawableName ?: "") }
        if (matchIdx >= 0) {
            selectedOption = options[matchIdx]
            cardOptionAdapter.notifyItemChanged(matchIdx)
        } else {
            val otherIdx = options.indexOfFirst { it.isCustom }
            if (otherIdx >= 0) {
                selectedOption = options[otherIdx]
                layoutCustomCard.visibility = View.VISIBLE
                etCustomCardName.setText(card.cardName ?: "")
            }
        }

        revealStep(cardStepDetails)
        etCardHolderName.setText(card.cardHolderName ?: "")
        etCardNumber.setText(card.cardNumber ?: "")
        layoutCreditFields.visibility = View.GONE
        layoutDebitFields.visibility = View.VISIBLE
        cbAddBankAccount.visibility = View.GONE
        layoutNewAccount.visibility = View.GONE
    }

    private fun prefillFromScan() {
        if (!intent.getBooleanExtra("isScan", false)) return

        val bankName = intent.getStringExtra("bankName") ?: ""
        val cardType = intent.getStringExtra("cardType") ?: "Debit"
        val cardHolder = intent.getStringExtra("cardHolder") ?: ""
        val cardNumber = intent.getStringExtra("cardNumber") ?: ""

        // 1. Set Bank
        val banks = DrawableCardMapper.getBankList()
        // Try to find a bank that contains the scanned name (e.g. "HDFC" matches "HDFC Bank")
        val bankIdx = if (bankName.isNotEmpty()) {
            banks.indexOfFirst { it.contains(bankName, ignoreCase = true) }
        } else -1

        if (bankIdx >= 0) {
            spinnerBank.setSelection(bankIdx)
            selectedBank = banks[bankIdx]
            isOtherBank = false
        } else if (bankName.isNotEmpty()) {
            // bank detected but not in our list
            spinnerBank.setSelection(banks.indexOf("Other"))
            isOtherBank = true
            selectedBank = ""
            layoutCustomBank.visibility = View.VISIBLE
            etCustomBankName.setText(bankName)
        }

        // 2. Set Card Type
        if (cardType == "Credit") {
            spinnerCardType.setSelection(2) // "Credit Card"
            selectedCardType = "Credit"
        } else {
            spinnerCardType.setSelection(1) // "Debit Card"
            selectedCardType = "Debit"
        }

        // 3. Set Details
        etCardHolderName.setText(cardHolder)
        etCardNumber.setText(cardNumber)

        // 4. Reveal steps
        revealStep(cardStepType)
        
        if (!isOtherBank && selectedBank.isNotEmpty()) {
            val options = DrawableCardMapper.getCards(selectedBank, selectedCardType)
            cardOptionAdapter.updateOptions(options)
            revealStep(cardStepPicker)
        } else if (isOtherBank) {
            revealStep(cardStepOtherBankCard)
        }
        
        revealStep(cardStepDetails)
        showTypeSpecificFields()
    }
}

