package com.github.kr328.clash.design.component

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import com.github.kr328.clash.common.compat.getDrawableCompat

class ProxyView(
    context: Context,
    config: ProxyViewConfig,
) : View(context) {
    constructor(context: Context) : this(context, ProxyViewConfig(context, false))

    init {
        background = context.getDrawableCompat(config.clickableBackground)
    }

    var state: ProxyViewState? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val state = state ?: return super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val width = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.UNSPECIFIED ->
                resources.displayMetrics.widthPixels
            MeasureSpec.AT_MOST, MeasureSpec.EXACTLY ->
                MeasureSpec.getSize(widthMeasureSpec)
            else ->
                throw IllegalArgumentException("invalid measure spec")
        }

        state.paint.apply {
            reset()

            textSize = state.config.textSize

            getTextBounds("Stub!", 0, 1, state.rect)
        }

        val textHeight = state.rect.height()
        val exceptHeight = if (state.oneLine) {
            (state.config.layoutPadding * 2 +
                    state.config.contentPadding * 2 +
                    textHeight).toInt()
        } else {
            (state.config.layoutPadding * 2 +
                    state.config.contentPadding * 2 +
                    textHeight * 2 +
                    state.config.textMargin).toInt()
        }

        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.UNSPECIFIED ->
                exceptHeight
            MeasureSpec.AT_MOST, MeasureSpec.EXACTLY ->
                exceptHeight.coerceAtMost(MeasureSpec.getSize(heightMeasureSpec))
            else ->
                throw IllegalArgumentException("invalid measure spec")
        }

        setMeasuredDimension(width, height)
    }

    override fun draw(canvas: Canvas) {
        val state = state ?: return super.draw(canvas)

        if (state.update(false))
            postInvalidate()

        val width = width.toFloat()
        val height = height.toFloat()

        val paint = state.paint

        paint.reset()

        canvas.apply {
            if (state.config.singleLine) {
                if (Color.alpha(state.background) > 0) {
                    val path = state.path

                    path.reset()
                    path.addRoundRect(
                        state.config.layoutPadding,
                        state.config.layoutPadding,
                        width - state.config.layoutPadding,
                        height - state.config.layoutPadding,
                        state.config.cardRadius,
                        state.config.cardRadius,
                        Path.Direction.CW,
                    )

                    paint.color = state.background
                    paint.style = Paint.Style.FILL
                    drawPath(path, paint)
                }

                if (!state.isSelected) {
                    drawRegionBorder(canvas, state, width, height)
                }
            } else {
                paint.color = state.background
                paint.style = Paint.Style.FILL

                val path = state.path

                path.reset()

                path.addRoundRect(
                    state.config.layoutPadding,
                    state.config.layoutPadding,
                    width - state.config.layoutPadding,
                    height - state.config.layoutPadding,
                    state.config.cardRadius,
                    state.config.cardRadius,
                    Path.Direction.CW,
                )

                paint.setShadowLayer(
                    state.config.cardRadius,
                    state.config.cardOffset,
                    state.config.cardOffset,
                    state.config.shadow
                )

                drawPath(path, paint)

                clipPath(path)
            }
        }

        super.draw(canvas)
    }

    private fun drawRegionBorder(
        canvas: Canvas,
        state: ProxyViewState,
        width: Float,
        height: Float,
    ) {
        val paint = state.paint
        val left = state.config.layoutPadding
        val right = width - state.config.layoutPadding
        val top = if (state.regionGroupFirst) state.config.layoutPadding else 0f
        val bottom = if (state.regionGroupLast) height - state.config.layoutPadding else height
        val radius = state.config.cardRadius
        val path = state.path

        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = state.config.regionBorderWidth
        paint.color = state.regionBorder

        path.reset()

        when {
            state.regionGroupFirst && state.regionGroupLast -> path.addRoundRect(
                left,
                top,
                right,
                bottom,
                radius,
                radius,
                Path.Direction.CW,
            )
            state.regionGroupFirst -> {
                path.moveTo(left, bottom)
                path.lineTo(left, top + radius)
                path.quadTo(left, top, left + radius, top)
                path.lineTo(right - radius, top)
                path.quadTo(right, top, right, top + radius)
                path.lineTo(right, bottom)
            }
            state.regionGroupLast -> {
                path.moveTo(left, top)
                path.lineTo(left, bottom - radius)
                path.quadTo(left, bottom, left + radius, bottom)
                path.lineTo(right - radius, bottom)
                path.quadTo(right, bottom, right, bottom - radius)
                path.lineTo(right, top)
            }
            else -> {
                path.moveTo(left, top)
                path.lineTo(left, bottom)
                path.moveTo(right, top)
                path.lineTo(right, bottom)
            }
        }

        canvas.drawPath(path, paint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val state = state ?: return

        val paint = state.paint

        val width = width.toFloat()
        val height = height.toFloat()

        paint.textSize = state.config.textSize

        // measure delay text bounds
        val delayCount = paint.breakText(
            state.delayText,
            false,
            (width - state.config.layoutPadding * 2 - state.config.contentPadding * 2)
                .coerceAtLeast(0f),
            null
        )

        state.paint.getTextBounds(state.delayText, 0, delayCount, state.rect)

        val delayWidth = state.rect.width()

        val mainTextWidth = (width -
                state.config.layoutPadding * 2 -
                state.config.contentPadding * 2 -
                delayWidth -
                state.config.textMargin * 2
                )
            .coerceAtLeast(0f)

        // measure title text bounds
        val titleCount = paint.breakText(
            state.title,
            false,
            mainTextWidth,
            null,
        )

        // measure subtitle text bounds
        val subtitleCount = paint.breakText(
            state.subtitle,
            false,
            mainTextWidth,
            null,
        )

        // text draw measure
        val textOffset = (paint.descent() + paint.ascent()) / 2

        paint.reset()

        paint.textSize = state.config.textSize
        paint.isAntiAlias = true
        paint.color = state.controls

        if (state.oneLine && state.isSelected) {
            val left = state.config.layoutPadding + state.config.selectionIndicatorInset
            val top = state.config.layoutPadding + state.config.contentPadding / 2
            val right = left + state.config.selectionIndicatorWidth
            val bottom = height - top

            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(
                left,
                top,
                right,
                bottom,
                state.config.selectionIndicatorWidth,
                state.config.selectionIndicatorWidth,
                paint,
            )
        }

        // draw delay
        canvas.apply {
            val x = width - state.config.layoutPadding - state.config.contentPadding - delayWidth
            val y = height / 2f - textOffset

            drawText(state.delayText, 0, delayCount, x, y, paint)
        }

        // draw title
        canvas.apply {
            val x = state.config.layoutPadding + state.config.contentPadding
            val y = if (state.oneLine) {
                height / 2f - textOffset
            } else {
                state.config.layoutPadding +
                        (height - state.config.layoutPadding * 2) / 3f - textOffset
            }

            drawText(state.title, 0, titleCount, x, y, paint)
        }

        // draw subtitle
        if (!state.oneLine) {
            canvas.apply {
                val x = state.config.layoutPadding + state.config.contentPadding
                val y = state.config.layoutPadding +
                        (height - state.config.layoutPadding * 2) / 3f * 2 - textOffset

                drawText(state.subtitle, 0, subtitleCount, x, y, paint)
            }
        }
    }
}
