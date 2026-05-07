/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import android.os.Build
import androidx.annotation.RequiresApi
import com.datadog.android.internal.lifecycle.ProcessLifecycleMonitor

/**
 * Bridges process-level [ProcessLifecycleMonitor.Callback] events into
 * [ContinuousProfilingScheduler.onForeground] / [ContinuousProfilingScheduler.onBackground].
 *
 * Reusing [ProcessLifecycleMonitor] keeps profiling aligned with the foreground/background
 * state observed by the rest of the SDK (uploaders, batch metrics).
 */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
internal class ProfilingLifecycleCallback(
    private val scheduler: ContinuousProfilingScheduler
) : ProcessLifecycleMonitor.Callback {

    override fun onStarted() {
        scheduler.onForeground()
    }

    override fun onResumed() {
        // NO - OP
    }

    override fun onStopped() {
        scheduler.onBackground()
    }

    override fun onPaused() {
        // NO - OP
    }
}
