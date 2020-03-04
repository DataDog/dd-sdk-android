package com.datadog.android.rum

import android.content.Context

/**
 * The TrackingStrategy interface.
 */
interface TrackingStrategy {

    /**
     * This method will register the tracking strategy to the current Context.
     * @param context as [Context]
     */
    fun register(context: Context)

    /**
     * This method will unregister the tracking strategy from the current Context.
     * @param context as [Context]
     */
    fun unregister(context: Context)
}
