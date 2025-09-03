/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import androidx.annotation.RequiresApi
import com.datadog.android.internal.utils.NextDrawListener.Companion.onNextDraw

class WindowDelegateCallback constructor(
    private val delegate: Window.Callback
) : Window.Callback by delegate {

    internal val onContentChangedCallbacks = mutableListOf<() -> Boolean>()

    override fun onContentChanged() {
        onContentChangedCallbacks.removeAll { callback ->
            !callback()
        }
        delegate.onContentChanged()
    }
}

fun Window.onDecorViewReady(callback: () -> Unit) {
    if (peekDecorView() == null) {
        onContentChanged {
            callback()
            return@onContentChanged false
        }
    } else {
        callback()
    }
}

fun Window.onContentChanged(block: () -> Boolean) {
    val callback = wrapCallback()
    callback.onContentChangedCallbacks += block
}

private fun Window.wrapCallback(): WindowDelegateCallback {
    val currentCallback = callback
    return if (currentCallback is WindowDelegateCallback) {
        currentCallback
    } else {
        val newCallback = WindowDelegateCallback(currentCallback)
        callback = newCallback
        newCallback
    }
}

class NextDrawListener(
    val view: View,
    val onDrawCallback: () -> Unit
) : ViewTreeObserver.OnDrawListener {

    private val handler = Handler(Looper.getMainLooper())
    private var invoked = false

    override fun onDraw() {
        if (invoked) return
        invoked = true
        onDrawCallback()
        handler.post {
            if (view.viewTreeObserver.isAlive) {
                view.viewTreeObserver.removeOnDrawListener(this)
            }
        }
    }

    companion object {
        fun View.onNextDraw(onDrawCallback: () -> Unit) {
            viewTreeObserver.addOnDrawListener(
                NextDrawListener(this, onDrawCallback)
            )
        }
    }
}

fun subscribeToFirstDrawFinished(handler: Handler, activity: Activity, block: () -> Unit) {
    val window = activity.window

    window.onDecorViewReady {
        window.decorView.onNextDraw {
            handler.sendMessageAtFrontOfQueue(
                Message.obtain(handler, block).apply {
                    isAsynchronous = true
                }
            )
        }
    }
}
