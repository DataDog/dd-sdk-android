package com.datadog.android.rum.gestures

import android.view.MotionEvent
import android.view.Window
import androidx.core.view.GestureDetectorCompat
import com.datadog.android.core.internal.utils.sdkLogger
import java.lang.Exception

internal class WindowCallbackWrapper(
    val wrappedCallback: Window.Callback,
    val gesturesDetector: GestureDetectorCompat
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
                "$TAG: error while processing the MotionEvent", e
            )
        } finally {
            sdkLogger.v("$TAG: Recycling the MotionEvent copy")
            copy.recycle()
        }
        return wrappedCallback.dispatchTouchEvent(event)
    }

    // endregion

    // region Internal

    internal fun copyEvent(event: MotionEvent?) = MotionEvent.obtain(event)

    // endregion

    companion object {
        const val TAG = "WindowCallbackWrapper"
    }
}
