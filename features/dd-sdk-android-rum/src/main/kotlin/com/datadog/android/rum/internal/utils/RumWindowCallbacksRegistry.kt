/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.utils

import android.app.Activity
import android.view.Window
import com.datadog.android.internal.utils.DDCoreSubscription
import com.datadog.android.rum.internal.instrumentation.gestures.FixedWindowCallback
import java.util.WeakHashMap

internal interface RumWindowCallbackListener {
    fun onContentChanged()
}

internal interface RumWindowCallbacksRegistry {
    fun addListener(activity: Activity, listener: RumWindowCallbackListener)
    fun removeListener(activity: Activity, listener: RumWindowCallbackListener)
}

internal class RumWindowCallbacksRegistryImpl: RumWindowCallbacksRegistry {
    private val callbacks = WeakHashMap<Activity, RumTTIDReportedWindowCallback>()

    override fun addListener(activity: Activity, listener: RumWindowCallbackListener) {
        val callback = callbacks.getOrPut(activity) {
            activity.window.wrapCallback()
        }

        callback.addListener(listener)
    }

    override fun removeListener(activity: Activity, listener: RumWindowCallbackListener) {
        callbacks[activity]?.let {
            it.removeListener(listener)
            if (it.subscription.size == 0) {
                activity.window.removeCallback()
            }
        }
    }
}

private fun Window.wrapCallback(): RumTTIDReportedWindowCallback {
    val currentCallback = callback
    val newCallback = RumTTIDReportedWindowCallback(
        wrapped = currentCallback,
    )
    callback = newCallback
    return newCallback
}

private fun Window.removeCallback() {
    val currentCallback = callback
    if (currentCallback is RumTTIDReportedWindowCallback) {
        callback = currentCallback.wrapped
    }
}

private class RumTTIDReportedWindowCallback(
    val wrapped: Window.Callback,
) : FixedWindowCallback(wrapped) {

    val subscription = DDCoreSubscription.create<RumWindowCallbackListener>()

    fun addListener(listener: RumWindowCallbackListener) {
        subscription.addListener(listener)
    }

    fun removeListener(listener: RumWindowCallbackListener) {
        subscription.removeListener(listener)
    }

    override fun onContentChanged() {
        subscription.notifyListeners {
            this@notifyListeners.onContentChanged()
        }
    }
}
