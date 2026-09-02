/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View
import android.view.ViewTreeObserver
import com.datadog.android.api.InternalLogger
import java.util.WeakHashMap

internal class CompositionViewOnDrawInterceptor(
    private val windowSource: ActiveWindowSource,
    private val onWindowsChanged: CompositionChangeListener,
    private val internalLogger: InternalLogger
) {
    private val lock = Any()
    private val interceptedViews = WeakHashMap<View, ViewTreeObserver.OnDrawListener>()

    fun intercept(decorViews: List<View>) {
        val staleViews = synchronized(lock) { interceptedViews.keys.filterNot(decorViews::contains) }
        staleViews.forEach(::removeListener)
        val newViews = synchronized(lock) { decorViews.filterNot(interceptedViews::containsKey) }
        newViews.forEach(::addListener)
        windowSource.update(decorViews)
        onWindowsChanged.onWindowsChanged(decorViews)
    }

    fun stop() {
        val interceptedViewsSnapshot = synchronized(lock) { interceptedViews.keys.toList() }
        interceptedViewsSnapshot.forEach(::removeListener)
        synchronized(lock) { interceptedViews.clear() }
        windowSource.clear()
    }

    private fun addListener(view: View) {
        val observer = view.viewTreeObserver
        if (!observer.isAlive) return
        try {
            val listener = CompositionOnDrawListener(view, onWindowsChanged)
            observer.addOnDrawListener(listener)
            synchronized(lock) { interceptedViews[view] = listener }
        } catch (e: IllegalStateException) {
            logListenerFailure("add", e)
        }
    }

    private fun removeListener(view: View) {
        val listener = synchronized(lock) { interceptedViews.remove(view) } ?: return
        val observer = view.viewTreeObserver
        if (!observer.isAlive) return
        try {
            observer.removeOnDrawListener(listener)
        } catch (e: IllegalStateException) {
            logListenerFailure("remove", e)
        }
    }

    private fun logListenerFailure(operation: String, error: IllegalStateException) {
        internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.TELEMETRY,
            { "Unable to $operation composition onDrawListener on viewTreeObserver" },
            error
        )
    }
}
