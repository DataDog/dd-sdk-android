/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.view.KeyEvent
import android.view.MenuItem
import android.view.MotionEvent
import android.view.Window
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import java.lang.Exception

internal class WindowCallbackWrapper(
    val wrappedCallback: Window.Callback,
    val gesturesDetector: GesturesDetectorWrapper
) : Window.Callback by wrappedCallback {

    // region Window.Callback

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        // we copy it and delegate it to the gesture detector for analysis
        val copy = copyEvent(event)
        @Suppress("TooGenericExceptionCaught")
        try {
            gesturesDetector.onTouchEvent(copy)
        } catch (e: Exception) {
            sdkLogger.e(
                "$TAG: error while processing the MotionEvent",
                e
            )
        } finally {
            sdkLogger.v("$TAG: Recycling the MotionEvent copy")
            copy.recycle()
        }
        return wrappedCallback.dispatchTouchEvent(event)
    }

    override fun onMenuItemSelected(featureId: Int, item: MenuItem): Boolean {
        val resourceId = resourceIdName(item.itemId)
        val attributes = mutableMapOf<String, Any?>(
            RumAttributes.ACTION_TARGET_CLASS_NAME to item.javaClass.canonicalName,
            RumAttributes.ACTION_TARGET_RESOURCE_ID to resourceId,
            RumAttributes.ACTION_TARGET_TITLE to item.title
        )
        GlobalRum.get().addUserAction(RumActionType.TAP, targetName(item, resourceId), attributes)
        return wrappedCallback.onMenuItemSelected(featureId, item)
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event?.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            // TODO RUMM-495 add a BACK action to the json schema
            GlobalRum.get().addUserAction(RumActionType.CUSTOM, "back", emptyMap())
        }
        return wrappedCallback.dispatchKeyEvent(event)
    }

    // endregion

    // region Internal

    internal fun copyEvent(event: MotionEvent?) = MotionEvent.obtain(event)

    // endregion

    companion object {
        const val TAG = "WindowCallbackWrapper"
    }
}
