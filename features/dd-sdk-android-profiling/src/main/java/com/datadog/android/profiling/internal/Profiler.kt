/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import android.content.Context
import com.datadog.android.api.InternalLogger
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface Profiler {

    var internalLogger: InternalLogger?

    fun start(appContext: Context, sdkInstanceNames: Set<String>)

    fun stop(sdkInstanceName: String)

    fun isRunning(sdkInstanceName: String): Boolean

    fun registerProfilingCallback(sdkInstanceName: String, callback: ProfilerCallback)

    fun unregisterProfilingCallback(sdkInstanceName: String)
}
