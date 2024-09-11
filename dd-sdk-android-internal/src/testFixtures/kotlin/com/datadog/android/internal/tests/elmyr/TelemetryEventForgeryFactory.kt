/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.tests.elmyr

import com.datadog.android.internal.telemetry.TelemetryEvent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class TelemetryEventForgeryFactory : ForgeryFactory<TelemetryEvent> {

    override fun getForgery(forge: Forge): TelemetryEvent {
        val random = forge.anInt(min = 0, max = 6)
        return when (random) {
            0 -> forge.getForgery<TelemetryEvent.Log.Debug>()

            1 -> forge.getForgery<TelemetryEvent.Log.Error>()

            2 -> forge.getForgery<TelemetryEvent.Configuration>()
            3 -> TelemetryEvent.InterceptorInstantiated
            4 -> forge.getForgery<TelemetryEvent.Metric>()
            else -> forge.getForgery<TelemetryEvent.ApiUsage>()
        }
    }
}
