/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.rum.internal.utils.window.RumWindowCallbacksRegistryImpl

internal interface RumFirstDrawTimeReporter {
    interface Callback {
        fun onFirstFrameDrawn(timestampNs: Long)
    }

    fun subscribeToFirstFrameDrawn(activity: Activity, callback: Callback)

    companion object {
        fun create(sdkCore: InternalSdkCore): RumFirstDrawTimeReporter {
            return RumFirstDrawTimeReporterImpl(
                internalLogger = sdkCore.internalLogger,
                timeProviderNs = { sdkCore.timeProvider.getDeviceElapsedTimeNs() },
                windowCallbacksRegistry = RumWindowCallbacksRegistryImpl(),
                handler = Handler(Looper.getMainLooper())
            )
        }
    }
}
