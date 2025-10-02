/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.os.Handler
import android.os.Looper
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.rum.internal.utils.window.RumWindowCallbacksRegistryImpl

internal interface RumTTIDReporter {
    interface Listener {
        fun onTTIDCalculated(info: RumTTIDInfo)
    }

    fun onAppStartupDetected(scenario: RumStartupScenario)

    companion object {
        fun create(sdkCore: InternalSdkCore, listener: Listener): RumTTIDReporter {
            return RumTTIDReporterImpl(
                internalLogger = sdkCore.internalLogger,
                timeProviderNanos = { System.nanoTime() },
                windowCallbacksRegistry = RumWindowCallbacksRegistryImpl(),
                handler = Handler(Looper.getMainLooper()),
                listener = listener
            )
        }
    }
}
