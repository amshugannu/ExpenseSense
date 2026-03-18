package com.example.es

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.widget.ImageButton
import android.widget.Button
import android.widget.LinearLayout
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var fabAdd: FloatingActionButton
    
    // Focus Overlay Views
    private lateinit var focusOverlay: android.view.ViewGroup
    private lateinit var focusDimView: View
    private lateinit var focusedCardContainer: android.view.ViewGroup
    

    private lateinit var overlayActionsContainer: android.view.ViewGroup
    private lateinit var btnOverlayEdit: Button
    private lateinit var btnOverlayDelete: Button

    // State for restoring card
    private var originalCardView: View? = null
    private var originalCardPosition = IntArray(2)
    private var clonedCardView: View? = null

    // Callback set by HomeFragment so FAB routes through it
    private var addExpenseAction: (() -> Unit)? = null

    fun setAddExpenseLauncher(action: (() -> Unit)?) {
        addExpenseAction = action
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNavigationView = findViewById(R.id.bottomNavigationView)
        fabAdd = findViewById(R.id.fabAdd)

        focusOverlay = findViewById(R.id.focusOverlay)
        focusDimView = findViewById(R.id.focusDimView)
        focusedCardContainer = findViewById(R.id.focusedCardContainer)
        

        overlayActionsContainer = findViewById(R.id.overlayActionsContainer)
        btnOverlayEdit = findViewById(R.id.btnOverlayEdit)
        btnOverlayDelete = findViewById(R.id.btnOverlayDelete)

        focusDimView.setOnClickListener { hideCardFocus() }


        // Load default fragment and apply home nav state
        if (savedInstanceState == null) {
            updateStatusBar(false)
            applyHomeNav()
            loadFragment(HomeFragment())
        }

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    updateStatusBar(false)
                    applyHomeNav()
                    loadFragment(HomeFragment())
                    true
                }
                R.id.nav_analytics -> {
                    updateStatusBar(false)
                    applyNormalNav()
                    loadFragment(AnalyticsFragment())
                    true
                }
                R.id.nav_wallet -> {
                    updateStatusBar(true)
                    applyNormalNav()
                    loadFragment(WalletFragment())
                    true
                }
                R.id.nav_profile -> {
                    updateStatusBar(true)
                    applyNormalNav()
                    loadFragment(ProfileFragment())
                    true
                }
                R.id.nav_placeholder -> {
                    // Spacer item — do nothing
                    false
                }
                else -> false
            }
        }

        // FAB delegates to HomeFragment when available, otherwise opens AddExpenseActivity directly
        fabAdd.setOnClickListener {
            addExpenseAction?.invoke()
                ?: startActivity(Intent(this, AddExpenseActivity::class.java))
        }

        checkAndCreateDefaultAccount()
    }

    private fun checkAndCreateDefaultAccount() {
        val prefs = getSharedPreferences("ExpenseSensePrefs", MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean("isFirstRun_Accounts", true)

        if (isFirstRun) {
            val database = AppDatabase.getDatabase(this)
            val repository = AccountRepository(database.accountDao())
            
            // Create default Cash account
            val defaultAccount = Account("Cash", "Wallet", 0.0)
            repository.saveAccount(defaultAccount)

            prefs.edit().putBoolean("isFirstRun_Accounts", false).apply()
        }
    }

    /**
     * Home screen:
     * • Switch to 5-item menu (center placeholder creates the visual gap)
     * • Show the FAB floating above the center gap
     */
    private fun applyHomeNav() {
        if (bottomNavigationView.menu.size() != 5) {
            val currentId = bottomNavigationView.selectedItemId
            bottomNavigationView.menu.clear()
            bottomNavigationView.inflateMenu(R.menu.bottom_nav_menu_home)
            // Re-select home after menu swap
            bottomNavigationView.selectedItemId = R.id.nav_home
            bottomNavigationView.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_home -> {
                        updateStatusBar(false)
                        applyHomeNav(); loadFragment(HomeFragment()); true
                    }
                    R.id.nav_analytics -> {
                        updateStatusBar(false)
                        applyNormalNav(); loadFragment(AnalyticsFragment()); true
                    }
                    R.id.nav_wallet -> {
                        updateStatusBar(true)
                        applyNormalNav(); loadFragment(WalletFragment()); true
                    }
                    R.id.nav_profile -> {
                        updateStatusBar(true)
                        applyNormalNav(); loadFragment(ProfileFragment()); true
                    }
                    R.id.nav_placeholder -> false
                    else -> false
                }
            }
        }
        showFab()
    }

    /**
     * Non-home screens:
     * • Switch to normal 4-item menu (evenly spread)
     * • Hide the FAB
     */
    private fun applyNormalNav() {
        if (bottomNavigationView.menu.size() != 4) {
            bottomNavigationView.menu.clear()
            bottomNavigationView.inflateMenu(R.menu.bottom_nav_menu)
            bottomNavigationView.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_home -> {
                        updateStatusBar(false)
                        applyHomeNav(); loadFragment(HomeFragment()); true
                    }
                    R.id.nav_analytics -> {
                        updateStatusBar(false)
                        applyNormalNav(); loadFragment(AnalyticsFragment()); true
                    }
                    R.id.nav_wallet -> {
                        updateStatusBar(true)
                        applyNormalNav(); loadFragment(WalletFragment()); true
                    }
                    R.id.nav_profile -> {
                        updateStatusBar(true)
                        applyNormalNav(); loadFragment(ProfileFragment()); true
                    }
                    else -> false
                }
            }
        }
        hideFab()
    }

    private fun updateStatusBar(isHeaderMatch: Boolean) {
        val window = window
        val decorView = window.decorView
        val wic = androidx.core.view.WindowInsetsControllerCompat(window, decorView)
        
        // Ensure the window draws the system bar backgrounds
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        
        if (isHeaderMatch) {
            // Match the teal header color (#2ABFBF)
            window.statusBarColor = android.graphics.Color.parseColor("#2ABFBF")
            wic.isAppearanceLightStatusBars = false // White text/icons
        } else {
            // White status bar for light screens
            window.statusBarColor = android.graphics.Color.WHITE
            wic.isAppearanceLightStatusBars = true // Dark text/icons
        }
    }

    override fun onBackPressed() {
        if (focusOverlay.visibility == View.VISIBLE) {
            hideCardFocus()
        } else {
            super.onBackPressed()
        }
    }

    private fun loadFragment(fragment: Fragment) {
        addExpenseAction = null // Reset custom FAB action on page change
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.fragment_enter,   // new fragment enters
                R.anim.fragment_exit     // old fragment exits
            )
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    /**
     * Expand from center — circle grows outward from a single point.
     * OvershootInterpolator gives a subtle elastic pop at the end.
     */
    private fun showFab() {
        fabAdd.scaleX = 0f
        fabAdd.scaleY = 0f
        fabAdd.visibility = View.VISIBLE
        fabAdd.isClickable = true
        fabAdd.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(320)
            .setInterpolator(OvershootInterpolator(1.6f))
            .start()
    }

    /**
     * Shrink to center — circle collapses inward until it vanishes completely.
     */
    private fun hideFab() {
        fabAdd.isClickable = false
        fabAdd.animate()
            .scaleX(0f)
            .scaleY(0f)
            .setDuration(230)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { 
                fabAdd.visibility = View.GONE 
            }
            .start()
    }

    /**
     * Shows a 3D focused card above a global blur/dim overlay using a cloning approach
     */
    fun showCardFocus(cardView: View, card: Card, balanceDisplay: String, onEdit: () -> Unit, onDelete: () -> Unit) {
        if (clonedCardView != null) return

        originalCardView = cardView
        cardView.getLocationOnScreen(originalCardPosition)

        // 1. Prepare Overlay
        focusOverlay.visibility = View.VISIBLE
        focusDimView.animate().alpha(1f).setDuration(300).start()
        
        // Apply Blur Effect (RenderEffect for API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blur = RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP)
            findViewById<View>(R.id.fragmentContainer).setRenderEffect(blur)
            findViewById<View>(R.id.bottomNavigationView).setRenderEffect(blur)
        }

        // 2. Clone the card view
        val inflater = android.view.LayoutInflater.from(this)
        val clone = inflater.inflate(R.layout.item_card, focusedCardContainer, false)
        clonedCardView = clone

        // Bind data to clone (Mirroring CardAdapter logic)
        val ivCardBg: android.widget.ImageView = clone.findViewById(R.id.ivCardBg)
        val tvCardName: android.widget.TextView = clone.findViewById(R.id.tvCardNameDisplay)
        val tvCardNumber: android.widget.TextView = clone.findViewById(R.id.tvCardNumberDisplay)
        val tvCardHolder: android.widget.TextView = clone.findViewById(R.id.tvCardHolderDisplay)
        val tvBalance: android.widget.TextView = clone.findViewById(R.id.tvBalanceDisplay)

        if (card.cardType == "Credit") {
            ivCardBg.setImageResource(R.drawable.defaultcreditcard)
        } else {
            ivCardBg.setImageResource(R.drawable.defaultdebitcard)
        }
        tvBalance.text = balanceDisplay
        tvCardName.text = card.cardName
        tvCardNumber.text = maskCardNumber(card.cardNumber)
        tvCardHolder.text = card.cardHolderName.uppercase()

        // Position clone at original orientation (for animation start)
        val lp = android.widget.FrameLayout.LayoutParams(cardView.width, cardView.height)
        focusedCardContainer.addView(clone, lp)
        
        // Initial position for the clone (where the original card is)
        clone.translationX = originalCardPosition[0].toFloat()
        clone.translationY = originalCardPosition[1].toFloat()

        // Use transient state to prevent RecyclerView from recycling this view while we're using it
        cardView.setHasTransientState(true)
        
        // Hide original card smoothly using alpha
        cardView.animate().alpha(0f).setDuration(150).start()

        // 3. Animate to Center
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        
        val centerX = (screenWidth - cardView.width) / 2f
        val centerY = (screenHeight - cardView.height) / 2f - 100f // Move slightly up to make room for buttons
        
        clone.animate()
            .translationX(centerX)
            .translationY(centerY)
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(450)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
            .withStartAction {
                clone.elevation = 150f 
            }
            .withEndAction {
                showFocusActions(onEdit, onDelete)
            }
            .start()
    }

    private fun showFocusActions(onEdit: () -> Unit, onDelete: () -> Unit) {


        overlayActionsContainer.visibility = View.VISIBLE
        overlayActionsContainer.alpha = 0f
        overlayActionsContainer.translationY = 50f
        overlayActionsContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        btnOverlayEdit.setOnClickListener {
            onEdit()
            hideCardFocus()
        }
        btnOverlayDelete.setOnClickListener {
            deleteFocusedCard(onDelete)
        }
    }

    private fun deleteFocusedCard(onDelete: () -> Unit) {
        val clone = clonedCardView ?: return
        
        // Premium Delete Animation: Shake -> Shrink -> Fade/Drop
        clone.animate()
            .translationXBy(20f).setDuration(50).withEndAction {
                clone.animate().translationXBy(-40f).setDuration(50).withEndAction {
                    clone.animate().translationXBy(20f).setDuration(50).withEndAction {
                        // Shake done, now shrink and drop
                        clone.animate()
                            .scaleX(0.5f)
                            .scaleY(0.5f)
                            .alpha(0f)
                            .translationYBy(300f)
                            .setDuration(400)
                            .setInterpolator(android.view.animation.AccelerateInterpolator())
                            .withEndAction {
                                onDelete()
                                // Since we are deleting, we don't return to original position
                                resetOverlayState(isDeletion = true)
                            }
                            .start()
                        
                        // Fade out actions too

                        overlayActionsContainer.animate().alpha(0f).translationY(50f).setDuration(200).start()
                    }.start()
                }.start()
            }.start()
    }

    private fun resetOverlayState(isDeletion: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            findViewById<View>(R.id.fragmentContainer).setRenderEffect(null)
            findViewById<View>(R.id.bottomNavigationView).setRenderEffect(null)
        }
        
        focusDimView.animate().alpha(0f).setDuration(300).withEndAction {
            focusOverlay.visibility = View.GONE
            overlayActionsContainer.visibility = View.GONE
        }.start()

        clonedCardView?.let { focusedCardContainer.removeView(it) }
        clonedCardView = null
        
        if (!isDeletion) {
            originalCardView?.alpha = 1f
        }
        
        originalCardView?.setHasTransientState(false)
        originalCardView = null
    }

    private fun maskCardNumber(number: String): String {
        if (number.length < 4) return number
        val lastFour = number.takeLast(4)
        return "**** **** **** $lastFour"
    }

    fun hideCardFocus() {
        val clone = clonedCardView ?: return
        
        val originalView = originalCardView
        
        // Remove Blur
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            findViewById<View>(R.id.fragmentContainer).setRenderEffect(null)
            findViewById<View>(R.id.bottomNavigationView).setRenderEffect(null)
        }

        focusDimView.animate().alpha(0f).setDuration(300).withEndAction {
            focusOverlay.visibility = View.GONE
            overlayActionsContainer.visibility = View.GONE
        }.start()

        // Fade out buttons

        overlayActionsContainer.animate().alpha(0f).translationY(50f).setDuration(200).start()

        // Smoothly fade the original card back in
        originalView?.animate()?.alpha(1f)?.setDuration(350)?.start()

        clone.animate()
            .translationX(originalCardPosition[0].toFloat())
            .translationY(originalCardPosition[1].toFloat())
            .scaleX(1f)
            .scaleY(1f)
            .alpha(0f) // Fade clone out as it returns
            .setDuration(350)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                originalView?.visibility = View.VISIBLE
                originalView?.alpha = 1f
                originalView?.setHasTransientState(false) // Release transient state
                
                focusedCardContainer.removeView(clone)
                clonedCardView = null
                originalCardView = null
            }
            .start()
    }
}