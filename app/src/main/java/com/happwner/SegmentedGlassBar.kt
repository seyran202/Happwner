package com.happwner

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlin.math.PI
import kotlin.math.sin

/**
 * Floating glass segment bar with a sliding blue pill and spring animations.
 *
 * It replaces the old pair of filled buttons without changing what they do:
 * the click listeners stay in MainActivity, this view only owns the selected
 * state, the pill position and all motion.
 */
class SegmentedGlassBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    enum class Segment { PROFILES, SUBSCRIPTION }

    private lateinit var pill: View
    private lateinit var profilesSegment: View
    private lateinit var subscriptionSegment: View
    private lateinit var profilesIcon: ImageView
    private lateinit var subscriptionIcon: ImageView
    private lateinit var profilesLabel: TextView
    private lateinit var subscriptionLabel: TextView

    private var selected: Segment = Segment.PROFILES
    private var pillWidth: Int = 0

    private var pillAnimator: ValueAnimator? = null
    private var stretchAnimator: ValueAnimator? = null
    private var colorAnimator: ValueAnimator? = null

    private val selectedColor = ContextCompat.getColor(context, R.color.apple_blue)
    private val unselectedColor = ContextCompat.getColor(context, R.color.text_secondary)
    private val argb = ArgbEvaluator()

    init {
        setWillNotDraw(false)
        clipChildren = true
        clipToPadding = true
        clipToOutline = true
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        pill = findViewById(R.id.segmentPill)
        profilesSegment = findViewById(R.id.segmentProfiles)
        subscriptionSegment = findViewById(R.id.segmentSubscription)
        profilesIcon = findViewById(R.id.iconProfiles)
        subscriptionIcon = findViewById(R.id.iconSubscription)
        profilesLabel = findViewById(R.id.labelProfiles)
        subscriptionLabel = findViewById(R.id.labelSubscription)

        installPressFeedback(profilesSegment)
        installPressFeedback(subscriptionSegment)

        post {
            positionPill(selected, animate = false)
            applyColors(selected, 1f)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (!::pill.isInitialized) return

        val newWidth = (width - paddingLeft - paddingRight) / 2
        if (newWidth > 0 && newWidth != pillWidth) {
            pillWidth = newWidth
            val lp = pill.layoutParams as LayoutParams
            lp.width = pillWidth
            lp.height = height - paddingTop - paddingBottom
            lp.leftMargin = paddingLeft
            lp.topMargin = paddingTop
            pill.layoutParams = lp
            pill.translationX = if (selected == Segment.PROFILES) 0f else pillWidth.toFloat()
        }
    }

    /** Moves the blue pill to [segment] and crossfades the icon/label colors. */
    fun select(segment: Segment, animate: Boolean) {
        val changed = selected != segment
        selected = segment

        profilesSegment.isSelected = segment == Segment.PROFILES
        subscriptionSegment.isSelected = segment == Segment.SUBSCRIPTION

        positionPill(segment, animate && changed)
        animateColors(segment, if (animate) 240L else 0L)

        if (changed) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    /** Used for the temporary "Copied" feedback on the Subscription segment. */
    fun setSubscriptionLabel(label: CharSequence) {
        if (::subscriptionLabel.isInitialized) subscriptionLabel.text = label
    }

    private fun positionPill(segment: Segment, animate: Boolean) {
        if (pillWidth <= 0) return
        val target = if (segment == Segment.PROFILES) 0f else pillWidth.toFloat()

        pillAnimator?.cancel()
        if (!animate) {
            pill.translationX = target
            pill.scaleX = 1f
            pill.scaleY = 1f
            return
        }

        pillAnimator = ValueAnimator.ofFloat(pill.translationX, target).apply {
            duration = 320L
            interpolator = OvershootInterpolator(0.35f)
            addUpdateListener { pill.translationX = it.animatedValue as Float }
            start()
        }

        // Subtle liquid stretch while the pill travels between segments.
        stretchAnimator?.cancel()
        stretchAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320L
            addUpdateListener {
                val t = it.animatedValue as Float
                val stretch = sin(PI * t).toFloat()
                pill.scaleX = 1f + 0.03f * stretch
                pill.scaleY = 1f - 0.02f * stretch
            }
            start()
        }
    }

    private fun animateColors(segment: Segment, duration: Long) {
        colorAnimator?.cancel()
        if (duration <= 0L) {
            applyColors(segment, 1f)
            return
        }
        colorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener { applyColors(segment, it.animatedValue as Float) }
            start()
        }
    }

    private fun applyColors(segment: Segment, fraction: Float) {
        val profilesFraction = if (segment == Segment.PROFILES) fraction else 1f - fraction
        val subscriptionFraction = 1f - profilesFraction
        setSegmentColor(profilesIcon, profilesLabel, profilesFraction)
        setSegmentColor(subscriptionIcon, subscriptionLabel, subscriptionFraction)
    }

    private fun setSegmentColor(icon: ImageView, label: TextView, selectedFraction: Float) {
        val color = argb.evaluate(selectedFraction, unselectedColor, selectedColor) as Int
        icon.setColorFilter(color)
        label.setTextColor(color)
    }

    private fun installPressFeedback(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.animate()
                    .scaleX(0.96f)
                    .scaleY(0.96f)
                    .setDuration(90L)
                    .start()

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220L)
                    .setInterpolator(OvershootInterpolator(1.2f))
                    .start()
            }
            false
        }
    }
}
