/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.app.Activity
import android.content.Context
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.rum.DdRumContentProvider
import kotlin.time.Duration.Companion.seconds

internal interface RumAppStartupDetector {
    interface Listener {
        fun onAppStartupDetected(scenario: RumStartupScenario)
    }

    fun addListener(listener: Listener)
    fun removeListener(listener: Listener)

    fun onStop()

    companion object {
        fun create(context: Context, sdkCore: InternalSdkCore): RumAppStartupDetector {
            val impl = RumAppStartupDetectorImpl(
                context = context,
                appStartupTimeProvider = { sdkCore.appStartTimeNs },
                processImportanceProvider = { DdRumContentProvider.processImportance },
                timeProviderNanos = { System.nanoTime() }
            )
            return impl
        }
    }
}
