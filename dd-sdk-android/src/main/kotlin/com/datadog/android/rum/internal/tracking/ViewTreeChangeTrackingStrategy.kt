package com.datadog.android.rum.internal.tracking

import android.app.Activity
import android.view.ViewTreeObserver
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.tracking.ActivityLifecycleTrackingStrategy
import com.datadog.android.rum.tracking.TrackingStrategy

internal class ViewTreeChangeTrackingStrategy :
    ActivityLifecycleTrackingStrategy(),
    TrackingStrategy,
    ViewTreeObserver.OnGlobalLayoutListener {

    // region ActivityLifecycleTrackingStrategy

    override fun onActivityStarted(activity: Activity) {
        super.onActivityStarted(activity)

        val viewTreeObserver = getViewTreeObserver(activity)
        viewTreeObserver?.addOnGlobalLayoutListener(this)
    }

    override fun onActivityStopped(activity: Activity) {
        super.onActivityStopped(activity)

        val viewTreeObserver = getViewTreeObserver(activity)
        viewTreeObserver?.removeOnGlobalLayoutListener(this)
    }

    // endregion

    // region ViewTreeObserver.OnGlobalLayoutListener

    override fun onGlobalLayout() {
        GlobalRum.addUserInteraction()
    }

    // endregion

    // region Internal

    private fun getViewTreeObserver(activity: Activity): ViewTreeObserver? {
        return activity.window?.decorView?.viewTreeObserver
    }

    // endregion
}
