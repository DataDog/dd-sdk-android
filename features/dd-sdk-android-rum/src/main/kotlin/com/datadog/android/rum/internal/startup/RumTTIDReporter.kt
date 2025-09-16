/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.os.Handler
import android.os.Looper
import com.datadog.android.rum.internal.utils.RumWindowCallbacksRegistryImpl
import kotlin.time.Duration

internal interface RumTTIDReporter {
    interface Listener {
        fun onTTIDCalculated(scenario: RumStartupScenario, duration: Duration)
    }

    fun onAppStartupDetected(scenario: RumStartupScenario)

    companion object {
        fun create(listener: Listener): RumTTIDReporter {
            return RumTTIDReporterImpl(
                timeProviderNanos = { System.nanoTime() },
                windowCallbacksRegistry = RumWindowCallbacksRegistryImpl(),
                handler = Handler(Looper.getMainLooper()),
                listener = listener
            )
        }
    }
}
