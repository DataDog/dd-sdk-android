package com.datadog.android.core.internal.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class ProcessLifecycleMonitor(val callback: Callback) :
    Application.ActivityLifecycleCallbacks {

    val activitiesResumedCounter = AtomicInteger(0)
    val activitiesStartedCounter = AtomicInteger(0)
    val wasPaused = AtomicBoolean(true)
    val wasStopped = AtomicBoolean(true)

    override fun onActivityPaused(activity: Activity) {
        if (activitiesResumedCounter.decrementAndGet() == 0 &&
            !wasPaused.getAndSet(true)) {
            // trigger on process paused
            callback.onPaused()
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (activitiesStartedCounter.incrementAndGet() == 1 &&
            wasStopped.getAndSet(false)
        ) {
            // trigger on process started
            callback.onStarted()
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        //  NO-OP
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        //  NO-OP
    }

    override fun onActivityStopped(activity: Activity) {
        if (activitiesStartedCounter.decrementAndGet() == 0 && wasPaused.get()) {
            // trigger on process stopped
            callback.onStopped()
            wasStopped.set(true)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        //  NO-OP
    }

    override fun onActivityResumed(activity: Activity) {
        if (activitiesResumedCounter.incrementAndGet() == 1 &&
            wasPaused.getAndSet(false)
        ) {
            callback.onResumed()
        }
    }

    interface Callback {
        fun onStarted()

        fun onResumed()

        fun onStopped()

        fun onPaused()
    }
}
