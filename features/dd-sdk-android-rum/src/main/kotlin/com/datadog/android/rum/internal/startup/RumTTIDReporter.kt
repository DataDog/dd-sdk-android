/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.datadog.android.internal.utils.subscribeToFirstDrawFinished

internal class RumTTIDReporter(
    private val rumAppStartupDetector: RumAppStartupDetector
): RumAppStartupDetector.Listener {
    private val handler = Handler(Looper.getMainLooper())

    init {
        rumAppStartupDetector.addListener(this)
    }

    override fun onAppStartupDetected(scenario: RumStartupScenario) {
        subscribeToFirstDrawFinished(handler, scenario.activity) {
            Log.w("WAHAHA", "onAppStartupDetected ${scenario.javaClass.name} ${scenario.activityName}")
        }
    }
}
