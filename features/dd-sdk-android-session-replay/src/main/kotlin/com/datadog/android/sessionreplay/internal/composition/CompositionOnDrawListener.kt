/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.MainThread

/** Identifies the decor view it was registered on, since [ViewTreeObserver.OnDrawListener.onDraw] carries no source. */
internal class CompositionOnDrawListener(
    private val view: View,
    private val onWindowsChanged: CompositionChangeListener
) : ViewTreeObserver.OnDrawListener {
    // listOf throws only for a null element; `view` is a non-null Kotlin type.
    @Suppress("UnsafeThirdPartyFunctionCall")
    @MainThread
    override fun onDraw() = onWindowsChanged.onWindowsChanged(listOf(view))
}
