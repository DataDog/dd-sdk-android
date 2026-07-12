/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package androidx.compose.ui.platform

import android.content.Context
import android.view.ViewGroup

/**
 * Test-only stand-in for the real `androidx.compose.ui.platform.AndroidComposeView` — this
 * module has no compile-time dependency on Jetpack Compose, so this exists purely to give
 * [com.datadog.android.sessionreplay.internal.recorder.CompositionTreeBuilder]'s by-fully-qualified-name
 * detection a real (non-mocked) object whose `javaClass.name` matches the real class, the same
 * way the real one always carries an internal `AndroidViewsHandler` child even with nothing
 * drawn through it.
 */
internal class AndroidComposeView(context: Context) : ViewGroup(context) {
    override fun getChildCount(): Int = 1
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) = Unit
}
