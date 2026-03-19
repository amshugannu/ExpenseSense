package com.example.es

import android.graphics.Canvas
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

import android.os.Handler
import android.os.Looper

class SwipeRevealCallback(
    private val adapter: AccountAdapter,
    private val swipeLimitPx: Float
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

    private val handler = Handler(Looper.getMainLooper())
    private var closeRunnable: Runnable? = null


    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // We don't want to dismiss the item, so we reset the swipe state
        // and handle the "reveal" manually in onChildDraw
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 2f // Prevent dismissal
    override fun getSwipeEscapeVelocity(defaultValue: Float): Float = defaultValue * 10f // Prevent dismissal

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (viewHolder is AccountAdapter.AccountViewHolder) {
            val foregroundView = viewHolder.foregroundView
            
            // Limit the swipe to swipeLimitPx
            val translationX = if (abs(dX) > swipeLimitPx) -swipeLimitPx else dX
            
            if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                // If currently swiped by user, follow their touch
                if (isCurrentlyActive) {
                    foregroundView.translationX = translationX
                } else {
                    // Settle stage: if it was swiped past threshold, keep it open
                    if (abs(foregroundView.translationX) > swipeLimitPx / 2) {
                        foregroundView.translationX = -swipeLimitPx
                    } else {
                        // Otherwise, follow dX back to 0
                        foregroundView.translationX = translationX
                    }
                }
            } else {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        if (viewHolder is AccountAdapter.AccountViewHolder) {
            val foregroundView = viewHolder.foregroundView
            val position = viewHolder.adapterPosition
            
            // If swiped enough, set as open position
            if (abs(foregroundView.translationX) > swipeLimitPx / 2) {
                foregroundView.translationX = -swipeLimitPx
                
                // If swiped another item, close previous
                if (adapter.openPosition != -1 && adapter.openPosition != position) {
                    val prevOpen = adapter.openPosition
                    adapter.openPosition = position
                    adapter.notifyItemChanged(prevOpen)
                } else {
                    adapter.openPosition = position
                }

                // Start 3-second auto-close timer
                closeRunnable?.let { handler.removeCallbacks(it) }
                closeRunnable = Runnable { closeAnyOpenItem() }
                handler.postDelayed(closeRunnable!!, 3000)

            } else {
                foregroundView.translationX = 0f
                if (adapter.openPosition == position) {
                    adapter.openPosition = -1
                }
                // Cancel timer if manually closed
                closeRunnable?.let { handler.removeCallbacks(it) }
                closeRunnable = null
            }
        }
    }

    fun closeAnyOpenItem() {
        closeRunnable?.let {
            handler.removeCallbacks(it)
            closeRunnable = null
        }
        if (adapter.openPosition != -1) {
            val prevOpen = adapter.openPosition
            adapter.openPosition = -1
            adapter.notifyItemChanged(prevOpen)
        }
    }
}
