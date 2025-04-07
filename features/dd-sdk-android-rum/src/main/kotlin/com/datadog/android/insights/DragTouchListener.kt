/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.instant.insights

import android.view.MotionEvent
import android.view.View
import androidx.core.view.marginBottom
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
import androidx.core.view.marginTop
import kotlin.math.max
import kotlin.math.min

class DragTouchListener : View.OnTouchListener {
    private var xDelta = 0f
    private var yDelta = 0f
    private var isMovementDetected = false

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                xDelta = event.rawX - view.x
                yDelta = event.rawY - view.y
                isMovementDetected = false
            }

            MotionEvent.ACTION_MOVE -> {
                isMovementDetected = true
                view.dragTo(
                    x = event.rawX - xDelta,
                    y = event.rawY - yDelta
                )
            }

            MotionEvent.ACTION_UP -> {
                view.rotate()

                if (!isMovementDetected) {
                    view.performClick()
                }
            }
        }
        return true
    }

    private fun View.dragTo(
        x: Float,
        y: Float
    ) {
        animate()
            .x(
                x.clip(
                    width,
                    marginStart,
                    resources.displayMetrics.widthPixels - marginEnd
                )
            )
            .y(
                y.clip(
                    height,
                    marginTop,
                    resources.displayMetrics.heightPixels - marginBottom
                )
            )
            .setDuration(0)
            .start()
    }

    private fun View.rotate() {
        animate().cancel()
        animate()
            .rotationBy(360 - (rotation % 360))
            .setDuration(500)
            .start()
    }

    private fun Float.clip(size: Int, min: Int, max: Int): Float {
        return max(min(this, (max - size).toFloat()), min.toFloat())
    }
}
