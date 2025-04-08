/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.content.Context
import android.view.MotionEvent
import androidx.core.view.GestureDetectorCompat

internal class GesturesDetectorWrapper(
    private val gestureListener: GesturesListener,
    private val defaultGesturesDetector: GestureDetectorCompat
) {

    constructor(
        context: Context,
        gestureListener: GesturesListener
    ) : this(
        gestureListener,
        GestureDetectorCompat(context, gestureListener)
    )

    fun onTouchEvent(event: MotionEvent) {
        if (defaultGesturesDetector.onTouchEvent(event)) {
            return
        }
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_UP) {
            gestureListener.onUp(event)
        }
    }
}
