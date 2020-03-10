package com.datadog.android.sdk.rules

import android.app.Activity
import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.rum.ActivityViewTrackingStrategy
import com.datadog.tools.unit.getFieldValue
import kotlin.collections.ArrayList

internal class RumMockServerActivityTestRule<T : Activity>(
    activityClass: Class<T>,
    keepRequests: Boolean = false
) : MockServerActivityTestRule<T>(activityClass, keepRequests) {

    // region ActivityTestRule

    override fun beforeActivityLaunched() {
        removeRumCallbacks()
        super.beforeActivityLaunched()
    }

    override fun afterActivityFinished() {
        removeRumCallbacks()
        super.afterActivityFinished()
    }

    // endregion

    // region Internal

    private fun removeRumCallbacks() {
        val application =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .applicationContext as Application
        val lifecycleCallbacks: ArrayList<Application.ActivityLifecycleCallbacks> =
            application.getFieldValue(
                "mActivityLifecycleCallbacks",
                Application::class.java
            )
        val activityViewTrackingStrategyClass = ActivityViewTrackingStrategy::class.java
        // we need this because the lifecycle callbacks are modified concurrently from other
        // unfinished tests
        // (it can happen that one Espresso test to start while other it is about to start)
        synchronized(lifecycleCallbacks) {
            var pointer = lifecycleCallbacks.size - 1
            while (pointer >= 0) {
                val callback = lifecycleCallbacks[pointer]
                if (activityViewTrackingStrategyClass.isAssignableFrom(callback::class.java)) {
                    application.unregisterActivityLifecycleCallbacks(callback)
                    pointer--
                }
                pointer--
            }
        }
    }

    // endregion
}
