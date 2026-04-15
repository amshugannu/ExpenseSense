package com.amshu.expensesense

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class PrivacySettingsActivity : AppCompatActivity() {

    private lateinit var rgPrivacyMode: RadioGroup
    private lateinit var btnChangePin: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_settings)

        setupUI()
        loadSettings()
    }

    private fun setupUI() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        rgPrivacyMode = findViewById(R.id.rgPrivacyMode)
        btnChangePin = findViewById(R.id.btnChangePin)

        rgPrivacyMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rbAlwaysVisible -> BalancePrivacyManager.MODE_ALWAYS_VISIBLE
                R.id.rbVisibleOnClick -> BalancePrivacyManager.MODE_SHOW_ON_CLICK
                R.id.rbVisibleOnClickPin -> BalancePrivacyManager.MODE_SHOW_ON_CLICK_PIN
                else -> BalancePrivacyManager.MODE_ALWAYS_VISIBLE
            }

            BalancePrivacyManager.setPrivacyMode(this, mode)
            updatePinButtonVisibility(mode)

            if (mode == BalancePrivacyManager.MODE_SHOW_ON_CLICK_PIN && !BalancePrivacyManager.isPinSet(this)) {
                showPinSetupDialog()
            }
        }

        btnChangePin.setOnClickListener {
            showPinSetupDialog()
        }
    }

    private fun loadSettings() {
        val mode = BalancePrivacyManager.getPrivacyMode(this)
        when (mode) {
            BalancePrivacyManager.MODE_ALWAYS_VISIBLE -> rgPrivacyMode.check(R.id.rbAlwaysVisible)
            BalancePrivacyManager.MODE_SHOW_ON_CLICK -> rgPrivacyMode.check(R.id.rbVisibleOnClick)
            BalancePrivacyManager.MODE_SHOW_ON_CLICK_PIN -> rgPrivacyMode.check(R.id.rbVisibleOnClickPin)
        }
        updatePinButtonVisibility(mode)
    }

    private fun updatePinButtonVisibility(mode: Int) {
        btnChangePin.visibility = if (mode == BalancePrivacyManager.MODE_SHOW_ON_CLICK_PIN) View.VISIBLE else View.GONE
    }

    private fun showPinSetupDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_pin_setup, null)
        val etPin = dialogView.findViewById<EditText>(R.id.etPin)
        val tvError = dialogView.findViewById<TextView>(R.id.tvPinError)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel") { d, _ ->
                if (!BalancePrivacyManager.isPinSet(this)) {
                    // Revert to Mode 0 if PIN is mandatory but not set
                    rgPrivacyMode.check(R.id.rbAlwaysVisible)
                }
                d.dismiss()
            }
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val pin = etPin.text.toString()
            if (pin.length == 6) {
                BalancePrivacyManager.setPIN(this, pin)
                Toast.makeText(this, "PIN saved successfully", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                tvError.visibility = View.VISIBLE
            }
        }
    }
}
