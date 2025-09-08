/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.os.Handler
import android.os.Looper
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.utils.subscribeToFirstDrawFinished
import kotlin.time.Duration.Companion.nanoseconds

internal class RumTTIDReporter(
    private val internalLogger: InternalLogger,
) {
    private val handler = Handler(Looper.getMainLooper())

    fun onAppStartupDetected(scenario: RumStartupScenario) {
        subscribeToFirstDrawFinished(handler, scenario.activity) {
            val duration = (System.nanoTime() - scenario.startTimeNanos).nanoseconds
            internalLogger.logMetric(
                messageBuilder = {
                    "test_app_startup"
                },
                additionalProperties = buildMap {
                    put("scenario", scenario.name())
                    put("duration", duration.inWholeNanoseconds.toDouble())
                    put("n_start", scenario.nStart)
                },
                samplingRate = 100f,
            )
        }
    }
}
