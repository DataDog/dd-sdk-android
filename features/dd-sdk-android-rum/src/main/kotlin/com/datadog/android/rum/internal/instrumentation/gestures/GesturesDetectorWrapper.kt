/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent

internal class GesturesDetectorWrapper(
    private val gestureListener: GesturesListener,
    private val defaultGesturesDetector: GestureDetector
) {

    constructor(
        context: Context,
        gestureListener: GesturesListener
    ) : this(
        gestureListener,
        GestureDetector(context, gestureListener)
    )

    fun onTouchEvent(event: MotionEvent) {
        defaultGesturesDetector.onTouchEvent(event)
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_UP) {
            gestureListener.onUp(event)
        }
    }
}
