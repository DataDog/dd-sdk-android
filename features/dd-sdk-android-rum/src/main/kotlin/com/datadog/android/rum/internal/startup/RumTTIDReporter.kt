/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.utils.subscribeToFirstDrawFinished
import kotlin.time.Duration.Companion.nanoseconds

internal class RumTTIDReporter(
    private val rumAppStartupDetector: RumAppStartupDetector,
    private val internalLogger: InternalLogger,
): RumAppStartupDetector.Listener {
    private val handler = Handler(Looper.getMainLooper())

    init {
        rumAppStartupDetector.addListener(this)
    }

    override fun onAppStartupDetected(scenario: RumStartupScenario) {
        subscribeToFirstDrawFinished(handler, scenario.activity) {
            val duration = (System.nanoTime() - scenario.startTimeNanos).nanoseconds
            Log.w("WAHAHA", "onAppStartupDetected ${scenario.name()} $duration")
            internalLogger.logMetric(
                messageBuilder = {
                    "test_app_startup"
                },
                additionalProperties = mapOf(
                    "scenario" to scenario.name(),
                    "duration" to duration.inWholeNanoseconds.toDouble(),
                ),
                samplingRate = 100f,
            )
        }
    }
}
