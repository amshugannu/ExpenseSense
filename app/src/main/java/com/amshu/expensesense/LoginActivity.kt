package com.amshu.expensesense

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.textfield.TextInputEditText
import android.widget.ImageButton
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var mGoogleSignInClient: GoogleSignInClient

    companion object {
        private const val RC_SIGN_IN = 9001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)

        // Check if user is already logged in and verified
        val currentUser = auth.currentUser
        if (currentUser != null) {
            if (currentUser.isEmailVerified || currentUser.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }) {
                performInitialSyncAndNavigate()
            }
        }

        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnGoogle = findViewById<ImageButton>(R.id.btnGoogle)
        val tvForgetPassword = findViewById<TextView>(R.id.tvForgetPassword)
        val tvGoToSignUp = findViewById<TextView>(R.id.tvGoToSignUp)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        if (user != null && user.isEmailVerified) {
                            performInitialSyncAndNavigate()
                        } else {
                            Toast.makeText(this, "Please verify email", Toast.LENGTH_SHORT).show()
                            auth.signOut()
                        }
                    } else {
                        Toast.makeText(this, "Login failed", Toast.LENGTH_SHORT).show()
                    }
                }
        }
        btnGoogle.setOnClickListener {
            signInWithGoogle()
        }

        tvForgetPassword.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email to reset password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Password reset email sent!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Failed to send reset email: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        tvGoToSignUp.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }

    }
    private fun signInWithGoogle() {
        val signInIntent = mGoogleSignInClient.signInIntent
        startActivityForResult(signInIntent,
            com.amshu.expensesense.LoginActivity.Companion.RC_SIGN_IN
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Result returned from launching the Intent from GoogleSignInClient.signInIntent
        if (requestCode == com.amshu.expensesense.LoginActivity.Companion.RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                // Google Sign In was successful, authenticate with Firebase
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken != null) {
                    firebaseAuthWithGoogle(idToken)
                } else {
                    Toast.makeText(this, "Google sign in failed: No ID Token", Toast.LENGTH_LONG).show()
                }
            } catch (e: ApiException) {
                // Google Sign In failed
                Toast.makeText(this, "Google sign in failed: ${e.statusCode}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        if (task.result?.additionalUserInfo?.isNewUser == true) {
                            // Delete the auto-created Firebase user and sign out
                            user.delete().addOnCompleteListener {
                                auth.signOut()
                                mGoogleSignInClient.signOut()
                                Toast.makeText(this@LoginActivity, "Email not registered. Please sign up first.", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            performInitialSyncAndNavigate()
                        }
                    }
                } else {
                    Toast.makeText(this, "Firebase authentication failed", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun performInitialSyncAndNavigate() {
        val user = auth.currentUser ?: return
        val username = user.email?.substringBefore("@") ?: return

        Thread {
            val db = AppDatabase.getDatabase(this@LoginActivity)
            val isLocalDbEmpty = db.accountDao().getAllAccounts().isEmpty()

            if (!NetworkUtils.isInternetAvailable(this@LoginActivity)) {
                if (isLocalDbEmpty) {
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "No internet and no local data to load.", Toast.LENGTH_LONG).show()
                    }
                    return@Thread // Safely block app load
                } else {
                    runOnUiThread { goToDashboardActivity() }
                    return@Thread
                }
            }

            // Phase 1: Clear Room DB
            db.clearAllTables()

            val database = com.google.firebase.database.FirebaseDatabase.getInstance()
            val latch = java.util.concurrent.CountDownLatch(3)

            // 1. Fetch Accounts
            database.getReference("users/$username/accounts").get()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result != null && task.result!!.exists()) {
                        try {
                            val accounts = mutableListOf<Account>()
                            for (snapshot in task.result!!.children) {
                                val accName = snapshot.child("name").getValue(String::class.java) ?: snapshot.key ?: ""
                                val type = snapshot.child("type").getValue(String::class.java)
                                val balanceRaw = snapshot.child("balance").value
                                val balance = when (balanceRaw) {
                                    is Long -> balanceRaw.toDouble()
                                    is Double -> balanceRaw
                                    else -> 0.0
                                }
                                accounts.add(Account(name = accName, type = type, balance = balance))
                            }
                            db.accountDao().insertAll(accounts)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else {
                        db.accountDao().insertAccount(Account(name = "Cash", type = "Cash", balance = 0.0))
                    }
                    latch.countDown()
                }

            // 2. Fetch Budgets
            database.getReference("users/$username/budgets").get()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result != null && task.result!!.exists()) {
                        try {
                            for (snapshot in task.result!!.children) {
                                val monthYear = snapshot.child("monthYear").getValue(String::class.java) ?: snapshot.key ?: ""
                                val tbRaw = snapshot.child("totalBudget").value
                                val rbRaw = snapshot.child("remainingBudget").value
                                val tb = when(tbRaw) { is Long -> tbRaw.toDouble(); is Double -> tbRaw; else -> 0.0 }
                                val rb = when(rbRaw) { is Long -> rbRaw.toDouble(); is Double -> rbRaw; else -> 0.0 }
                                db.budgetDao().insertBudget(Budget(monthYear = monthYear, totalBudget = tb, remainingBudget = rb))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    latch.countDown()
                }

            // 3. Fetch Expenses
            database.getReference("users/$username/expenses").get()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result != null && task.result!!.exists()) {
                        try {
                            val transactions = mutableListOf<Transaction>()
                            for (catGroup in task.result!!.children) {
                                val category = catGroup.key ?: "Expense"
                                for (expenseSnap in catGroup.children) {
                                    val fId = expenseSnap.key
                                    val timestampRaw = expenseSnap.child("timestamp").value
                                    val timestamp = when (timestampRaw) {
                                        is Long -> timestampRaw
                                        is Int -> timestampRaw.toLong()
                                        else -> System.currentTimeMillis()
                                    }
                                    val amountRaw = expenseSnap.child("amount").value
                                    val amount = when (amountRaw) {
                                        is Long -> amountRaw.toDouble()
                                        is Double -> amountRaw
                                        else -> 0.0
                                    }
                                    val paymentMethod = expenseSnap.child("paymentMethod").getValue(String::class.java) ?: "Cash"
                                    val account = expenseSnap.child("account").getValue(String::class.java) ?: "Cash"

                                    transactions.add(
                                        Transaction(
                                            id = 0,
                                            title = category,
                                            amount = amount,
                                            category = category,
                                            accountName = account,
                                            timestamp = timestamp,
                                            paymentMethod = paymentMethod,
                                            referenceId = account,
                                            firebaseId = fId
                                        )
                                    )
                                }
                            }
                            db.transactionDao().insertAll(transactions)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    latch.countDown()
                }

            try {
                latch.await()
                runOnUiThread { goToDashboardActivity() }
            } catch (e: InterruptedException) {
                e.printStackTrace()
                runOnUiThread { goToDashboardActivity() }
            }
        }.start()
    }

    private fun goToDashboardActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}