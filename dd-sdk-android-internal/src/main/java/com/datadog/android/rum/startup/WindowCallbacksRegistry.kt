/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.startup

import android.app.Activity
import android.view.Window
import com.datadog.android.internal.utils.DDCoreSubscription
import com.datadog.android.internal.utils.FixedWindowCallback
import java.util.WeakHashMap
import kotlin.collections.getOrPut
import kotlin.let

interface WindowCallbackListener {
    fun onContentChanged()
}

interface WindowCallbacksRegistry {
    fun addListener(activity: Activity, listener: WindowCallbackListener)
    fun removeListener(activity: Activity, listener: WindowCallbackListener)
}

class WindowCallbacksRegistryImpl : WindowCallbacksRegistry {
    private val callbacks = WeakHashMap<Activity, WindowCallback>()

    override fun addListener(activity: Activity, listener: WindowCallbackListener) {
        val callback = callbacks.getOrPut(activity) {
            activity.window.wrapCallback()
        }

        callback.addListener(listener)
    }

    override fun removeListener(activity: Activity, listener: WindowCallbackListener) {
        callbacks[activity]?.let {
            it.removeListener(listener)

            if (it.subscription.listenersCount == 0) {
                activity.window.tryToRemoveCallback()
                callbacks.remove(activity)
            }
        }
    }

    private fun Window.wrapCallback(): WindowCallback {
        val currentCallback = callback
        val newCallback = WindowCallback(
            wrapped = currentCallback
        )
        callback = newCallback
        return newCallback
    }

    private fun Window.tryToRemoveCallback() {
        val currentCallback = callback
        if (currentCallback is WindowCallback && currentCallback in callbacks.values) {
            callback = currentCallback.wrapped
        }
    }
}

private class WindowCallback(
    val wrapped: Window.Callback
) : FixedWindowCallback(wrapped) {

    val subscription = DDCoreSubscription.create<WindowCallbackListener>()

    fun addListener(listener: WindowCallbackListener) {
        subscription.addListener(listener)
    }

    fun removeListener(listener: WindowCallbackListener) {
        subscription.removeListener(listener)
    }

    override fun onContentChanged() {
        super.onContentChanged()

        subscription.notifyListeners {
            this@notifyListeners.onContentChanged()
        }
    }
}
