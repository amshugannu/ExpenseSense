package com.example.es

import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView

class StackedLayoutManager(
    private val collapsedOffset: Int = 100,
    private var isExpanded: Boolean = false
) : RecyclerView.LayoutManager() {

    private var verticalOffset = 0
    private var dragPosition: Int = -1
    private var dragTopY: Float = -1f
    private var dragCenterY: Float = -1f

    private val tiltedStates = mutableMapOf<Int, Boolean>()

    var currentTargetPosition: Int = -1
        private set

    fun setDragState(dragPos: Int) {
        if (dragPosition != dragPos) {
            dragPosition = dragPos
            tiltedStates.clear()
        }
        requestLayout()
    }

    fun setDragCoordinates(topY: Float, centerY: Float) {
        dragTopY = topY
        dragCenterY = centerY
        requestLayout()
    }

    fun onDragFinished() {
        dragPosition = -1
        dragTopY = -1f
        dragCenterY = -1f
        tiltedStates.clear()
        requestLayout()
    }

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        return RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {

        if (itemCount == 0) {
            detachAndScrapAttachedViews(recycler)
            return
        }

        if (state.isPreLayout) return

        detachAndScrapAttachedViews(recycler)

        val totalWidth = width - paddingLeft - paddingRight

        for (i in 0 until itemCount) {

            val view = recycler.getViewForPosition(i)
            addView(view)

            measureChildWithMargins(view, 0, 0)

            val childHeight = getDecoratedMeasuredHeight(view)
            val childWidth = totalWidth

            val left = paddingLeft
            var shiftY = 0

            view.cameraDistance = 12000f

            if (dragPosition != -1) {

                if (i == dragPosition) {

                    view.rotationX = 0f
                    view.elevation = (itemCount * 10).toFloat()

                } else {

                    val headerHeight = collapsedOffset

                    val equilibriumTop =
                        if (isExpanded) {
                            paddingTop - verticalOffset + (i * (childHeight + 32))
                        } else {
                            paddingTop + (i * collapsedOffset)
                        }

                    val boundary = equilibriumTop + (headerHeight / 2)

                    var isTilted = tiltedStates[i] ?: false

                    if (!isTilted && dragCenterY > 0 && dragCenterY < boundary) {
                        isTilted = true
                    }

                    if (isTilted && dragTopY > 0 && dragTopY > boundary) {
                        isTilted = false
                    }

                    val previousTilt = tiltedStates[i]
                    tiltedStates[i] = isTilted

                    view.pivotY = 0f

                    if (previousTilt != isTilted) {

                        val rotation = if (isTilted) -30f else 0f
                        val translation = if (isTilted) 260f else 0f

                        view.animate()
                            .rotationX(rotation)
                            .translationY(translation)
                            .setDuration(120)
                            .setInterpolator(DecelerateInterpolator())
                            .start()
                    }

                    if (isTilted) {
                        shiftY = 260
                    }
                }

            } else {

                view.rotationX = 0f
                view.translationY = 0f
            }

            // ENFORCE STABLE SCALE (Option 1: best for reordering)
            view.scaleX = 1f
            view.scaleY = 1f

            val top =
                (if (isExpanded) {
                    paddingTop - verticalOffset + (i * (childHeight + 32))
                } else {
                    paddingTop + (i * collapsedOffset)
                }) + shiftY

            layoutDecoratedWithMargins(
                view,
                left,
                top,
                left + childWidth,
                top + childHeight
            )

            // Systematic Elevation: higher index = visually on top
            if (i != dragPosition) {
                view.elevation = (i * 2 + 2).toFloat()
            }
        }

        if (dragPosition != -1) {

            var firstTilted = -1

            for (i in 0 until itemCount) {
                if (i != dragPosition && tiltedStates[i] == true) {
                    firstTilted = i
                    break
                }
            }

            currentTargetPosition =
                if (firstTilted == -1) {
                    itemCount - 1
                } else if (firstTilted > dragPosition) {
                    firstTilted - 1
                } else {
                    firstTilted
                }
        }
    }

    override fun canScrollVertically(): Boolean = isExpanded

    override fun scrollVerticallyBy(
        dy: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {

        if (!isExpanded || itemCount == 0) return 0

        val lastView = getChildAt(childCount - 1) ?: return 0
        val lastBottom = getDecoratedBottom(lastView)

        val scrollDelta =
            if (verticalOffset + dy < 0) {
                -verticalOffset
            } else if (dy > 0 && lastBottom < height - paddingBottom) {
                0
            } else {
                dy
            }

        verticalOffset += scrollDelta
        offsetChildrenVertical(-scrollDelta)

        return scrollDelta
    }

    override fun canScrollHorizontally(): Boolean = false

    fun setExpanded(expanded: Boolean) {
        if (isExpanded != expanded) {
            isExpanded = expanded
            if (!expanded) verticalOffset = 0
            requestLayout()
        }
    }

    fun isExpanded() = isExpanded
}