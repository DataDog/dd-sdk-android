package com.datadog.android.sdk.rules

import android.app.Activity
import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.rum.ActivityViewTrackingStrategy
import com.datadog.android.rum.GlobalRum
import com.datadog.tools.unit.createInstance
import com.datadog.tools.unit.getFieldValue
import com.datadog.tools.unit.getStaticValue
import com.datadog.tools.unit.setStaticValue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.ArrayList

internal class RumMockServerActivityTestRule<T : Activity>(
    activityClass: Class<T>,
    keepRequests: Boolean = false
) : MockServerActivityTestRule<T>(activityClass, keepRequests) {

    // region ActivityTestRule

    override fun beforeActivityLaunched() {
//        // This needs to be called here due to the way Espresso handles the activities.
//        // There are cases when one activity is about to be finished and we are stopping the RumFeature
//        // but in the same time another activity starts and wants to set the RumMonitor, in that moment
//        // the Rum.Builder() returns NoOpMonitor because the RumFeature is disabled
//        resetRumMonitor()
        removeRumCallbacks()
        super.beforeActivityLaunched()
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

    private fun resetRumMonitor() {
        val noOpMonitor = createInstance(
            "com.datadog.android.rum.internal.monitor.NoOpRumMonitor"
        )
        GlobalRum::class.java.setStaticValue("monitor", noOpMonitor)
        val isRegistered: AtomicBoolean = GlobalRum::class.java.getStaticValue("isRegistered")
        isRegistered.set(false)
    }

    // endregion
}
