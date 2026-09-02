/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.ViewTreeObserver
import androidx.annotation.MainThread

/**
 * A draw listener that can also be asked for a snapshot outside of a draw pass.
 */
internal interface OnDemandCaptureListener : ViewTreeObserver.OnDrawListener {
    /**
     * Takes a snapshot right away, without going through the debouncer: a requested capture is not
     * a draw, and the frame skipping the debouncer applies to draws would silently drop it.
     * @return whether a snapshot was in fact taken and queued.
     */
    @MainThread
    fun captureNow(): Boolean
}
