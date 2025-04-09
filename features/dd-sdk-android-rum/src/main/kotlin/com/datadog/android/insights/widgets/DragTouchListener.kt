/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.insights.widgets

import android.view.MotionEvent
import android.view.View
import com.datadog.android.insights.extensions.animateDragTo

internal class DragTouchListener(
    private val onUp: View.() -> Unit = {}
) : View.OnTouchListener {
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
                view.animateDragTo(
                    x = event.rawX - xDelta,
                    y = event.rawY - yDelta
                )
            }

            MotionEvent.ACTION_UP -> {
                if (!isMovementDetected) {
                    view.onUp()
                    view.performClick()
                }
            }
        }
        return true
    }
}
