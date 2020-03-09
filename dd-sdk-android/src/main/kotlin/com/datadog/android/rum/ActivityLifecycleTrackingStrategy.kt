package com.datadog.android.rum

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.datadog.android.core.internal.utils.devLogger

/**
 * The ActivityLifecycleTrackingStrategy as an [Application.ActivityLifecycleCallbacks]
 * based implementation of the [TrackingStrategy].
 */
abstract class ActivityLifecycleTrackingStrategy : Application.ActivityLifecycleCallbacks,
    TrackingStrategy {

    // region TrackingStrategy

    override fun register(context: Context) {
        if (context is Application) {
            context.registerActivityLifecycleCallbacks(this)
        } else {
            devLogger.e(
                "In order to use the RUM automatic tracking feature you will have" +
                        "to use the Application context when initializing the SDK"
            )
        }
    }

    override fun unregister(context: Context) {
        if (context is Application) {
            context.unregisterActivityLifecycleCallbacks(this)
        }
    }

    // endregion

    // region Application.ActivityLifecycleCallbacks

    override fun onActivityPaused(activity: Activity) {
        // No Op
    }

    override fun onActivityStarted(activity: Activity) {
        // No Op
    }

    override fun onActivityDestroyed(activity: Activity) {
        // No Op
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // No Op
    }

    override fun onActivityStopped(activity: Activity) {
        // No Op
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // No Op
    }

    override fun onActivityResumed(activity: Activity) {
        // No Op
    }

    // endregion
}
