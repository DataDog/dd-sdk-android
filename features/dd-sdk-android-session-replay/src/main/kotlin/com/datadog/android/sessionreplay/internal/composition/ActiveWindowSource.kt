/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View
import java.lang.ref.WeakReference

/**
 * The window set the traversal implementation reads when a generation starts. Interception updates
 * it from the looper that discovered the windows, while every window's own UI thread can reach this
 * class through its draw callbacks, so each access is guarded rather than assumed confined.
 */
internal class ActiveWindowSource {
    private val lock = Any()
    private var windows: List<WeakReference<View>> = emptyList()

    fun update(views: List<View>) {
        val references = views.map(::WeakReference)
        synchronized(lock) { windows = references }
    }

    fun currentWindows(): List<View> =
        synchronized(lock) { windows }.mapNotNull(WeakReference<View>::get)

    fun clear() {
        synchronized(lock) { windows = emptyList() }
    }
}
