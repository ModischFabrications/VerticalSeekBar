@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.lukelorusso.verticalseekbar

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.layout_verticalseekbar.view.*
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A nicer, redesigned and vertical SeekBar
 */
open class VerticalSeekBar constructor(context: Context, attrs: AttributeSet) : FrameLayout(context, attrs) {

    companion object {
        const val DEFAULT_DRAWABLE_BACKGROUND: String = "#f6f6f6"
        const val DEFAULT_DRAWABLE_PROGRESS_START: String = "#4D88E1"
        const val DEFAULT_DRAWABLE_PROGRESS_END: String = "#7BA1DB"
    }

    private var onProgressChangeListener: ((Int) -> Unit)? = null

    private var yDelta: Int = 0
    private var minLayoutWidth: Int = 0
    private var minLayoutHeight: Int = 0
    private var maxPlaceholderDrawable: Drawable? = null
    private var minPlaceholderDrawable: Drawable? = null
    private var drawableBackgroundDrawable: Drawable? = null
    private var drawableProgressDrawable: Drawable? = null
    private var drawableProgressStartColor: Int = Color.parseColor(DEFAULT_DRAWABLE_PROGRESS_START)
    private var drawableProgressEndColor: Int = Color.parseColor(DEFAULT_DRAWABLE_PROGRESS_END)
    private var thumbContainerColor: Int = Color.WHITE
    private var thumbPlaceholderDrawable: Drawable? = null
    private var drawableWidth = 0
    var progress: Int = 50
        set(value) {
            if (field != value) {
                onProgressChangeListener?.invoke(value)
            }
            field = value
            updateViews(value)
        }

    init {
        init(context, attrs)
    }

    @Suppress("PLUGIN_WARNING")
    private fun init(context: Context, attrs: AttributeSet) {
        inflate(context, R.layout.layout_verticalseekbar, this)

        val attributes = context.obtainStyledAttributes(attrs, R.styleable.VerticalSeekBar, 0, 0)
        try {
            attributes.getLayoutDimension(R.styleable.VerticalSeekBar_android_layout_width, minLayoutWidth).apply {
                container.layoutParams.width = if (this != -1 && this < minLayoutWidth) minLayoutWidth // wrap_content
                else this
            }
            attributes.getLayoutDimension(R.styleable.VerticalSeekBar_android_layout_height, minLayoutHeight).apply {
                container.layoutParams.height =
                    if (this != -1 && this < minLayoutHeight) minLayoutHeight // wrap_content
                    else this
            }
            drawableBackgroundDrawable = attributes.getDrawable(R.styleable.VerticalSeekBar_vsb_drawable_background)
            drawableProgressDrawable = attributes.getDrawable(R.styleable.VerticalSeekBar_vsb_drawable_progress)
            minPlaceholderDrawable = attributes.getDrawable(R.styleable.VerticalSeekBar_vsb_min_placeholder_src)
            maxPlaceholderDrawable = attributes.getDrawable(R.styleable.VerticalSeekBar_vsb_max_placeholder_src)
            drawableProgressStartColor =
                attributes.getColor(R.styleable.VerticalSeekBar_vsb_drawable_progress_gradient_start, drawableProgressStartColor)
            drawableProgressEndColor =
                attributes.getColor(R.styleable.VerticalSeekBar_vsb_drawable_progress_gradient_end, drawableProgressEndColor)
            thumbContainerColor =
                attributes.getColor(R.styleable.VerticalSeekBar_vsb_thumb_container_tint, thumbContainerColor)
            thumbPlaceholderDrawable = attributes.getDrawable(R.styleable.VerticalSeekBar_vsb_thumb_placeholder_src)
            drawableWidth =
                attributes.getDimensionPixelSize(R.styleable.VerticalSeekBar_vsb_drawable_width, container.layoutParams.width)
            attributes.getInt(R.styleable.VerticalSeekBar_vsb_progress, progress).also {
                progress = when {
                    it < 0 -> 0
                    it > 100 -> 100
                    else -> it
                }
            }

        } finally {
            attributes.recycle()
        }

        // Customizing drawableCardView
        drawableCardView.layoutParams.width = drawableWidth

        // Customizing drawableBackground
        drawableBackground.background =
            drawableBackgroundDrawable ?: ColorDrawable(Integer.decode(DEFAULT_DRAWABLE_BACKGROUND))

        // Generating drawableProgress gradient
        if (drawableProgressDrawable == null) drawableProgressDrawable = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(drawableProgressStartColor, drawableProgressEndColor)
        ).apply { cornerRadius = 0f }
        drawableProgress.background = drawableProgressDrawable

        // Applying custom placeholders
        maxPlaceholder.setImageDrawable(maxPlaceholderDrawable) // can also be null
        minPlaceholder.setImageDrawable(minPlaceholderDrawable) // can also be null
        thumbPlaceholderDrawable?.also { thumbPlaceholder.setImageDrawable(it) } // CANNOT be null

        // Let's shape the thumb
        val states = arrayOf(
            intArrayOf(android.R.attr.state_enabled), // enabled
            intArrayOf(-android.R.attr.state_enabled), // disabled
            intArrayOf(-android.R.attr.state_checked), // unchecked
            intArrayOf(android.R.attr.state_pressed)  // pressed
        )
        val colors = arrayOf(
            thumbContainerColor,
            thumbContainerColor,
            thumbContainerColor,
            thumbContainerColor
        ).toIntArray()
        thumbCardView.backgroundTintList = ColorStateList(states, colors)
        thumbCardView.measure(0, 0)
        thumb.layoutParams.apply {
            val increase = (thumbCardView.elevation + context.dpToPixel(1F)).roundToInt()
            width = thumbCardView.measuredWidth + increase
            height = thumbCardView.measuredHeight + increase
            (thumbCardView.layoutParams as LayoutParams).topMargin = increase / 2
        }

        // Adding some margin to drawableCardView, maxPlaceholder and minPlaceholder
        maxPlaceholder.measure(0, 0)
        minPlaceholder.measure(0, 0)
        (drawableCardView.layoutParams as LayoutParams).apply {
            val thumbCardViewHalfHeight = thumbCardView.measuredHeight / 2
            val maxPlaceholderHalfHeight = maxPlaceholder.measuredHeight / 2
            val minPlaceholderHalfHeight = minPlaceholder.measuredHeight / 2
            topMargin = max(thumbCardViewHalfHeight, maxPlaceholderHalfHeight)
            bottomMargin = max(thumbCardViewHalfHeight, minPlaceholderHalfHeight)
            (maxPlaceholderLayout.layoutParams as LayoutParams).topMargin =
                thumbCardViewHalfHeight - maxPlaceholderHalfHeight
            (minPlaceholderLayout.layoutParams as LayoutParams).bottomMargin =
                thumbCardViewHalfHeight - minPlaceholderHalfHeight

        }

        // Here's where the magic happens
        thumb.setOnTouchListener { thumbView, event ->
            val rawY = event.rawY.roundToInt()
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> { // here we calculate the displacement (yDelta)
                    yDelta = rawY - (thumbView.layoutParams as LayoutParams).topMargin
                }
                MotionEvent.ACTION_MOVE -> { // here we update progress
                    val positionY = rawY - yDelta
                    val fillHeight = height - thumbView.height
                    when {
                        positionY in 1 until fillHeight -> progress = 100 - (positionY * 100 / fillHeight)
                        positionY <= 0 -> progress = 100
                        positionY >= fillHeight -> progress = 0
                    }
                }
            }
            true
        }
    }

    fun setOnProgressChangeListener(listener: ((Int) -> Unit)?) {
        this.onProgressChangeListener = listener
    }

    //region PROTECTED METHODS
    protected fun Context.dpToPixel(dp: Float): Float =
        dp * (resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)

    protected fun Context.pixelToDp(px: Float): Float =
        px / (resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)
    //endregion

    /**
     * Inside here the views are repositioned based on the new value
     */
    private fun updateViews(value: Int) {
        post {
            val fillHeight = height - thumb.height
            val marginByProgress = fillHeight - (value * fillHeight / 100)
            thumb.layoutParams =
                (thumb.layoutParams as LayoutParams).apply { topMargin = marginByProgress }
            drawableProgress.translationY = (drawableBackground.height * (100 - value) / 100).toFloat()
            invalidate()
        }
    }

}