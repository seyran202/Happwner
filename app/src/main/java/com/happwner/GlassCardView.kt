package com.happwner

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import androidx.core.graphics.ColorUtils
import com.google.android.material.card.MaterialCardView
import kotlin.math.max
import kotlin.math.min

/**
 * Apple-style glassmorphism surface.
 *
 * A MaterialCardView is kept as the base class so existing click handling,
 * checked states and rounded clipping keep working. The card itself stays
 * fully transparent and this view paints the frosted glass layer:
 *
 *  - 70% translucent tint ([R.attr.happGlassBg])
 *  - diagonal light refraction gradient
 *  - soft top-left sheen and accent glow in the bottom-right corner
 *  - 1dp light-catching border
 *
 * Because the app backdrop is a soft, pre-blurred mesh gradient, the result has
 * the same visual signature as a real backdrop blur without the cost of
 * capturing and blurring the window on every frame.
 */
@SuppressLint("CustomViewStyleable")
class GlassCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : MaterialCardView(context, attrs) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val clipPath = Path()
    private val bounds = RectF()

    private var cornerRadius = dp(22f)
    private var glassBg = Color.WHITE
    private var glassBorder = Color.WHITE
    private var glassHighlight = Color.WHITE
    private var accentTint = Color.TRANSPARENT
    private var isNight = false

    init {
        setWillNotDraw(false)

        val a = context.obtainStyledAttributes(attrs, androidx.cardview.R.styleable.CardView)
        cornerRadius = a.getDimension(
            androidx.cardview.R.styleable.CardView_cardCornerRadius,
            cornerRadius
        )
        a.recycle()

        glassBg = colorAttr(R.attr.happGlassBg, 0xB3FFFFFF.toInt())
        glassBorder = colorAttr(R.attr.happGlassBorder, 0xE6FFFFFF.toInt())
        glassHighlight = colorAttr(R.attr.happGlassHighlight, 0x8CFFFFFF.toInt())
        val accent = colorAttr(R.attr.happAccent, 0xFF007AFF.toInt())
        accentTint = ColorUtils.setAlphaComponent(accent, 0x14)

        isNight = (resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        // The glass layer is painted by this view, not by the card background.
        setCardBackgroundColor(Color.TRANSPARENT)
        strokeWidth = 0
        cardElevation = resources.getDimension(R.dimen.glass_shadow_elevation)
        useCompatPadding = false
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }

        // iOS-like subtle press feedback inside the glass.
        rippleColor = ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(accent, 0x1F)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        cornerRadius = min(cornerRadius, min(w, h) / 2f)
        bounds.set(0f, 0f, w.toFloat(), h.toFloat())

        clipPath.reset()
        clipPath.addRoundRect(bounds, cornerRadius, cornerRadius, Path.Direction.CW)

        val topTint = ColorUtils.blendARGB(glassBg, Color.WHITE, if (isNight) 0.14f else 0.30f)
        val bottomTint = ColorUtils.blendARGB(glassBg, Color.BLACK, if (isNight) 0.10f else 0.04f)
        fillPaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(topTint, glassBg, bottomTint),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )

        val borderBright = ColorUtils.blendARGB(glassBorder, Color.WHITE, 0.35f)
        val borderDim = ColorUtils.setAlphaComponent(glassBorder, 0x33)
        borderPaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(borderBright, glassBorder, borderDim),
            null,
            Shader.TileMode.CLAMP
        )

        sheenPaint.shader = RadialGradient(
            w * 0.16f, 0f, max(w, h) * 0.85f,
            glassHighlight,
            ColorUtils.setAlphaComponent(glassHighlight, 0),
            Shader.TileMode.CLAMP
        )

        glowPaint.shader = RadialGradient(
            w * 0.92f, h.toFloat(), max(w, h) * 0.7f,
            accentTint,
            ColorUtils.setAlphaComponent(accentTint, 0),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (width > 0 && height > 0) {
            canvas.drawPath(clipPath, fillPaint)

            val save = canvas.save()
            canvas.clipPath(clipPath)
            canvas.drawRect(bounds, sheenPaint)
            canvas.drawRect(bounds, glowPaint)
            canvas.restoreToCount(save)

            canvas.drawPath(clipPath, borderPaint)
        }
        super.onDraw(canvas)
    }

    private fun colorAttr(attr: Int, fallback: Int): Int {
        val ta = context.obtainStyledAttributes(intArrayOf(attr))
        return try {
            ta.getColor(0, fallback)
        } finally {
            ta.recycle()
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
