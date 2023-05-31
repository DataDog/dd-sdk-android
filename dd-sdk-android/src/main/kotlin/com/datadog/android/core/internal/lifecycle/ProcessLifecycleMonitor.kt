/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.annotation.MainThread
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class ProcessLifecycleMonitor(val callback: Callback) :
    Application.ActivityLifecycleCallbacks {

    val activitiesResumedCounter = AtomicInteger(0)
    val activitiesStartedCounter = AtomicInteger(0)
    val wasPaused = AtomicBoolean(true)
    val wasStopped = AtomicBoolean(true)

    @MainThread
    override fun onActivityPaused(activity: Activity) {
        if (activitiesResumedCounter.decrementAndGet() == 0 &&
            !wasPaused.getAndSet(true)
        ) {
            // trigger on process paused
            callback.onPaused()
        }
    }

    @MainThread
    override fun onActivityStarted(activity: Activity) {
        if (activitiesStartedCounter.incrementAndGet() == 1 &&
            wasStopped.getAndSet(false)
        ) {
            // trigger on process started
            callback.onStarted()
        }
    }

    @MainThread
    override fun onActivityDestroyed(activity: Activity) {
        //  NO-OP
    }

    @MainThread
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        //  NO-OP
    }

    @MainThread
    override fun onActivityStopped(activity: Activity) {
        if (activitiesStartedCounter.decrementAndGet() == 0 && wasPaused.get()) {
            // trigger on process stopped
            callback.onStopped()
            wasStopped.set(true)
        }
    }

    @MainThread
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        //  NO-OP
    }

    @MainThread
    override fun onActivityResumed(activity: Activity) {
        if (activitiesResumedCounter.incrementAndGet() == 1 &&
            wasPaused.getAndSet(false)
        ) {
            callback.onResumed()
        }
    }

    internal interface Callback {
        fun onStarted()

        fun onResumed()

        fun onStopped()

        fun onPaused()
    }
}
