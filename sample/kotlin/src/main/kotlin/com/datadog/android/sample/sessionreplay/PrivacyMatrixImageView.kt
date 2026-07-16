/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.sessionreplay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * A custom [View] used by [PrivacyMatrixFragment] to exercise `ImagePrivacy`'s three levels.
 * Draws a non-text graphic (a stand-in "image": a filled circle icon), optionally with a text
 * caption beneath it, depending on [includeText] — set programmatically per instance so several
 * instances of this same class — each with a different
 * [com.datadog.android.sessionreplay.ImagePrivacy] override applied via
 * `setSessionReplayImagePrivacy` — can sit on the same screen.
 *
 * Always `match_parent` width in the layout this is used in, so its dp bounds always exceed
 * [com.datadog.android.sessionreplay.IMAGE_DIMEN_CONSIDERED_PII_IN_DP] on the width axis alone —
 * deliberately, so `ImagePrivacy.MASK_LARGE_ONLY` always takes its "too large" branch here,
 * regardless of [includeText] (that check is purely size-based, not content-aware).
 */
class PrivacyMatrixImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var includeText: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)

        val cx = width / 2f
        val cy = height * if (includeText) ICON_CENTER_Y_WITH_TEXT_FRACTION else ICON_CENTER_Y_NO_TEXT_FRACTION
        val radius = min(width, height) * ICON_RADIUS_FRACTION

        paint.style = Paint.Style.FILL
        paint.color = ICON_COLOR
        canvas.drawCircle(cx, cy, radius, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, radius * ICON_INNER_RADIUS_FRACTION, paint)

        if (includeText) {
            paint.color = Color.BLACK
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = height * CAPTION_TEXT_HEIGHT_FRACTION
            canvas.drawText("Profile photo", cx, height * CAPTION_BASELINE_FRACTION, paint)
        }
    }

    private companion object {
        private const val ICON_COLOR = 0xFF1E88E5.toInt()
        private const val ICON_RADIUS_FRACTION = 0.28f
        private const val ICON_INNER_RADIUS_FRACTION = 0.4f
        private const val ICON_CENTER_Y_NO_TEXT_FRACTION = 0.5f
        private const val ICON_CENTER_Y_WITH_TEXT_FRACTION = 0.4f
        private const val CAPTION_TEXT_HEIGHT_FRACTION = 0.12f
        private const val CAPTION_BASELINE_FRACTION = 0.9f
    }
}
