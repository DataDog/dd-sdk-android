/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import com.datadog.android.internal.utils.NextDrawListener.Companion.onNextDraw

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
