/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.callback

import android.os.Build
import android.view.MotionEvent

internal object MotionEventUtils {

    // For Android SDK < 29 we will have to know the target View on which this event happened
    // and then to compute the absolute as target.absoluteX,Y + event.getX,Y(pointerIndex)
    // This will not be handled now as it is too complex and not very optimised. For now we will
    // not support multi finger gestures in the player for version < 29.
    fun getPointerAbsoluteX(event: MotionEvent, pointerIndex: Int): Float {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            event.getRawX(pointerIndex)
        } else {
            return event.rawX
        }
    }

    fun getPointerAbsoluteY(event: MotionEvent, pointerIndex: Int): Float {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            event.getRawY(pointerIndex)
        } else {
            return event.rawY
        }
    }
}
