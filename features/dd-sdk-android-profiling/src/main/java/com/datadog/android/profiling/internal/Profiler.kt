/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import android.content.Context
import com.datadog.android.api.InternalLogger
import com.datadog.android.profiling.internal.time.MutableTimeProvider
import java.util.concurrent.ScheduledExecutorService

@Suppress("TooManyFunctions")
internal interface Profiler {

    val timeProvider: MutableTimeProvider

    var internalLogger: InternalLogger?

    val scheduledExecutorService: ScheduledExecutorService

    /**
     * Whether the OOM system trigger is currently registered and can deliver a result. This is
     * `false` not only on API levels below Cinnamon Bun, but also whenever the trigger
     * registration is currently torn down — e.g. ANR triggers disabled via configuration, or
     * an untracked RUM session. Persisting an OOM RUM error as a gating marker while this is
     * `false` would wait for a result that can never arrive.
     */
    val isOomTriggerActive: Boolean

    fun start(
        appContext: Context,
        startReason: ProfilingStartReason,
        additionalAttributes: Map<String, String>,
        durationMs: Int = 0
    )

    fun stop()

    fun isRunning(): Boolean

    fun registerProfilingCallback(appContext: Context, callback: ProfilerCallback)

    fun unregisterProfilingCallback(appContext: Context)

    fun registerProfilerStatusListener(listener: ProfilingStatusListener)

    fun unregisterProfilerStatusListener(listener: ProfilingStatusListener)

    /**
     * Sets which profiling trigger types (ANR, out of memory, memory anomaly) are enabled.
     * Applied the next time triggers are registered for a RUM session (see
     * [setTriggersEnabled]); trigger types not supported by the device API level are
     * ignored.
     */
    fun setEnabledTriggers(
        anrTriggerEnabled: Boolean,
        oomTriggerEnabled: Boolean,
        anomalyTriggerEnabled: Boolean
    )

    /**
     * Enables or disables system profiling triggers for the current RUM session.
     */
    fun setTriggersEnabled(appContext: Context, enabled: Boolean)

    /**
     * Controls whether an app launch profiling session should extend past the 10-second
     * TTID threshold. Set to `true` when continuous profiling is enabled for the session
     * so the launch window merges into the first continuous cycle.
     */
    fun setExtendLaunchSession(extend: Boolean)

    /**
     * Resolves the version of the profiling system package, if it is not known yet, and keeps it
     * for the whole process lifetime. The profiler can be started before the SDK is initialized,
     * so it resolves the version on its own; this only makes sure it is also known when profiling
     * never starts.
     */
    fun resolveProfilingPackageVersionCode(appContext: Context)
}
