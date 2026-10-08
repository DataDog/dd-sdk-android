/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.MainThread
import java.lang.ref.WeakReference

/** Identifies the decor view it was registered on, since [ViewTreeObserver.OnDrawListener.onDraw] carries no source. */
internal class CompositionOnDrawListener(
    view: View,
    private val onWindowsChanged: CompositionChangeListener
) : ViewTreeObserver.OnDrawListener {
    // The interceptor owns this listener as a map value; retaining its weak key here would
    // keep the decor view (and its Activity) alive until explicit lifecycle cleanup.
    private val viewReference = WeakReference(view)

    // listOf receives the non-null result of dereferencing the source view.
    @Suppress("UnsafeThirdPartyFunctionCall")
    @MainThread
    override fun onDraw() {
        val view = viewReference.get() ?: return
        onWindowsChanged.onWindowsChanged(listOf(view))
    }
}
