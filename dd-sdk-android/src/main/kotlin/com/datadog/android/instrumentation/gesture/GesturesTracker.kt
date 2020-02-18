package com.datadog.android.instrumentation.gesture

import android.app.Activity

internal interface GesturesTracker {

    fun startTracking(activity: Activity)
    fun stopTracking(activity: Activity)
}
