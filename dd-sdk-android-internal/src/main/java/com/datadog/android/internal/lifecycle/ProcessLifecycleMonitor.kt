/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.annotation.MainThread
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Monitors the app process lifecycle by tracking [android.app.Activity] starts and stops,
 * and delivers single process-level [Callback] events when the app enters or leaves the foreground.
 *
 * @param callback the receiver for process-level lifecycle events.
 */
class ProcessLifecycleMonitor(val callback: Callback) :
    Application.ActivityLifecycleCallbacks {

    /** Number of activities currently in the resumed state. */
    val activitiesResumedCounter: AtomicInteger = AtomicInteger(0)

    /** Number of activities currently in the started (visible) state. */
    val activitiesStartedCounter: AtomicInteger = AtomicInteger(0)

    /** Whether the process was last observed in the paused state. */
    val wasPaused: AtomicBoolean = AtomicBoolean(true)

    /** Whether the process was last observed in the stopped state. */
    val wasStopped: AtomicBoolean = AtomicBoolean(true)

    /** @inheritDoc */
    @MainThread
    override fun onActivityPaused(activity: Activity) {
        if (activitiesResumedCounter.decrementAndGet() == 0 &&
            !wasPaused.getAndSet(true)
        ) {
            // trigger on process paused
            callback.onPaused()
        }
    }

    /** @inheritDoc */
    @MainThread
    override fun onActivityStarted(activity: Activity) {
        if (activitiesStartedCounter.incrementAndGet() == 1 &&
            wasStopped.getAndSet(false)
        ) {
            // trigger on process started
            callback.onStarted()
        }
    }

    /** @inheritDoc */
    @MainThread
    override fun onActivityDestroyed(activity: Activity) {
        //  NO-OP
    }

    /** @inheritDoc */
    @MainThread
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        //  NO-OP
    }

    /** @inheritDoc */
    @MainThread
    override fun onActivityStopped(activity: Activity) {
        if (activitiesStartedCounter.decrementAndGet() == 0 && wasPaused.get()) {
            // trigger on process stopped
            callback.onStopped()
            wasStopped.set(true)
        }
    }

    /** @inheritDoc */
    @MainThread
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        //  NO-OP
    }

    /** @inheritDoc */
    @MainThread
    override fun onActivityResumed(activity: Activity) {
        if (activitiesResumedCounter.incrementAndGet() == 1 &&
            wasPaused.getAndSet(false)
        ) {
            callback.onResumed()
        }
    }

    /**
     * Receives process-level lifecycle events from [ProcessLifecycleMonitor].
     * Each method is called at most once per foreground/background transition,
     * regardless of how many activities are involved.
     */
    interface Callback {

        /** Called when the app process enters the foreground (first activity started). */
        fun onStarted()

        /** Called when the app process is fully resumed (first activity resumed). */
        fun onResumed()

        /** Called when the app process leaves the foreground (last activity stopped). */
        fun onStopped()

        /** Called when the app process is fully paused (last activity paused). */
        fun onPaused()
    }
}
