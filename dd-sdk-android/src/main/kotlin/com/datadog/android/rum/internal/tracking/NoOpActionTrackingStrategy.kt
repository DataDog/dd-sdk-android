package com.datadog.android.rum.internal.tracking

import android.content.Context
import com.datadog.android.rum.tracking.UserActionTrackingStrategy

internal class NoOpActionTrackingStrategy : UserActionTrackingStrategy {
    override fun register(context: Context) {
        // No Op
    }

    override fun unregister(context: Context?) {
        // No Op
    }
}
