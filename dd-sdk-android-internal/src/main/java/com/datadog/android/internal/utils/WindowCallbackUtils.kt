/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

import android.os.Build
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.view.Window
import androidx.annotation.RequiresApi

fun Window.onContentChanged(block: () -> Boolean) {
    val callback = wrapCallback()
    callback.onContentChangedCallbacks += block
}

private fun Window.wrapCallback(): OnContentChangedWindowDelegateCallback {
    val currentCallback = callback
    return if (currentCallback is OnContentChangedWindowDelegateCallback) {
        currentCallback
    } else {
        val newCallback = OnContentChangedWindowDelegateCallback(currentCallback)
        callback = newCallback
        newCallback
    }
}

private class OnContentChangedWindowDelegateCallback(
    private val delegate: Window.Callback
) : Window.Callback by delegate {

    val onContentChangedCallbacks = mutableListOf<() -> Boolean>()

    override fun onContentChanged() {
        onContentChangedCallbacks.removeAll { callback ->
            !callback()
        }
        delegate.onContentChanged()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPointerCaptureChanged(hasCapture: Boolean) {
        delegate.onPointerCaptureChanged(hasCapture)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onProvideKeyboardShortcuts(
        data: List<KeyboardShortcutGroup?>?,
        menu: Menu?,
        deviceId: Int
    ) {
        delegate.onProvideKeyboardShortcuts(data, menu, deviceId)
    }
}
