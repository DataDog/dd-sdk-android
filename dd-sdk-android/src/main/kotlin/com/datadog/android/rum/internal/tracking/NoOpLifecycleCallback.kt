package com.datadog.android.rum.internal.tracking

import android.app.Activity

internal class NoOpLifecycleCallback :
    FragmentLifecycleCallbacks<Activity> {

    override fun register(activity: Activity) {
        // No Op
    }

    override fun unregister(activity: Activity) {
        // No Op
    }
}
