/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.utils.window

import android.app.Activity
import android.view.Window
import com.datadog.android.internal.utils.DDCoreSubscription
import com.datadog.android.rum.internal.FixedWindowCallback
import java.util.WeakHashMap
import kotlin.collections.getOrPut
import kotlin.let

internal interface RumWindowCallbackListener {
    fun onContentChanged()
}

internal interface RumWindowCallbacksRegistry {
    fun addListener(activity: Activity, listener: RumWindowCallbackListener)
    fun removeListener(activity: Activity, listener: RumWindowCallbackListener)
}

internal class RumWindowCallbacksRegistryImpl : RumWindowCallbacksRegistry {
    private val callbacks = WeakHashMap<Activity, RumWindowCallback>()

    override fun addListener(activity: Activity, listener: RumWindowCallbackListener) {
        val callback = callbacks.getOrPut(activity) {
            activity.window.wrapCallback()
        }

        callback.addListener(listener)
    }

    override fun removeListener(activity: Activity, listener: RumWindowCallbackListener) {
        callbacks[activity]?.let {
            it.removeListener(listener)

            if (it.subscription.listenersCount == 0) {
                activity.window.tryToRemoveCallback()
                callbacks.remove(activity)
            }
        }
    }
}

private fun Window.wrapCallback(): RumWindowCallback {
    val currentCallback = callback
    val newCallback = RumWindowCallback(
        wrapped = currentCallback
    )
    callback = newCallback
    return newCallback
}

private fun Window.tryToRemoveCallback() {
    val currentCallback = callback
    if (currentCallback is RumWindowCallback) {
        callback = currentCallback.wrapped
    }
}

private class RumWindowCallback(
    val wrapped: Window.Callback
) : FixedWindowCallback(wrapped) {

    val subscription = DDCoreSubscription.create<RumWindowCallbackListener>()

    fun addListener(listener: RumWindowCallbackListener) {
        subscription.addListener(listener)
    }

    fun removeListener(listener: RumWindowCallbackListener) {
        subscription.removeListener(listener)
    }

    override fun onContentChanged() {
        super.onContentChanged()

        subscription.notifyListeners {
            this@notifyListeners.onContentChanged()
        }
    }
}
