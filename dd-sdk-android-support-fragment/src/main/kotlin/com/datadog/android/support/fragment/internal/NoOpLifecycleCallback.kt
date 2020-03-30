package com.datadog.android.support.fragment.internal

import android.app.Activity

internal object NoOpLifecycleCallback : LifecycleCallbacks<Activity> {
    override fun register(activity: Activity) {
        // No Op
    }

    override fun unregister(activity: Activity) {
        // No Op
    }
}
