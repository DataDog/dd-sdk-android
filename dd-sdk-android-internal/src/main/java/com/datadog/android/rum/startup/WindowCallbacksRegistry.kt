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

/**
 * Listener that is notified when the content of an activity's window changes.
 *
 * Used to detect when the decor view becomes available after [Activity.setContentView] is called.
 */
interface WindowCallbackListener {
    /**
     * Called when the window's content has changed (i.e., [Window.Callback.onContentChanged]).
     */
    fun onContentChanged()
}

/**
 * Manages [WindowCallbackListener] registrations on a per-[Activity] basis.
 *
 * Wraps the activity's [Window.Callback] so that content-change events can be forwarded to
 * registered listeners without replacing any existing callback logic.
 */
interface WindowCallbacksRegistry {
    /**
     * Registers [listener] to receive content-change callbacks for [activity].
     *
     * @param activity The activity whose window callback should be observed.
     * @param listener The listener to register.
     */
    fun addListener(activity: Activity, listener: WindowCallbackListener)

    /**
     * Unregisters [listener] from content-change callbacks for [activity].
     *
     * If no more listeners remain for the activity, the wrapped window callback is removed.
     *
     * @param activity The activity whose window callback is being observed.
     * @param listener The listener to unregister.
     */
    fun removeListener(activity: Activity, listener: WindowCallbackListener)
}

/**
 * Default implementation of [WindowCallbacksRegistry].
 *
 * Maintains a weak mapping from [Activity] to a wrapped [Window.Callback] so that listeners
 * can be added and removed without leaking activity references.
 */
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

@Suppress("PackageNameVisibility")
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
