package com.datadog.android.rum.gestures

import android.app.Activity

internal interface GesturesTracker {

    fun startTracking(activity: Activity)
    fun stopTracking(activity: Activity)
}
