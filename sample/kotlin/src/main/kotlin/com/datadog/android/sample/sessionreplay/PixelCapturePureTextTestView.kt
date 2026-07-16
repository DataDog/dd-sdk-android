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

/**
 * A custom [View] used to verify the experimental
 * [com.datadog.android.sessionreplay.internal.recorder.ImageContentDetector] path: this draws
 * **only** text, nothing else — no rings, no icons, no drawables — the one shape of capture that
 * should stay visible even under `ImagePrivacy.MASK_ALL` (a pure-text capture isn't an image),
 * unlike [PixelCaptureTestView] right above it in the same fragment, which mixes text with
 * plenty of non-text ink (the rings) and so should still fall back to a placeholder under that
 * same setting.
 *
 * Navigation: Home → Session Replay → Unsupported Views
 */
class PixelCapturePureTextTestView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Same reasoning as PixelCaptureTestView: no android:background means the *isolated*
        // capture has no real backdrop otherwise — fill explicitly so the text has real contrast
        // to be OCR'd against in the captured bitmap itself.
        canvas.drawColor(Color.WHITE)

        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = height * TEXT_HEIGHT_FRACTION
        canvas.drawText(
            "Order #48213 confirmed",
            width * TEXT_MARGIN_FRACTION,
            paint.textSize * TEXT_BASELINE_FRACTION,
            paint
        )
    }

    private companion object {
        private const val TEXT_HEIGHT_FRACTION = 0.4f
        private const val TEXT_MARGIN_FRACTION = 0.08f
        private const val TEXT_BASELINE_FRACTION = 1.3f
    }
}
