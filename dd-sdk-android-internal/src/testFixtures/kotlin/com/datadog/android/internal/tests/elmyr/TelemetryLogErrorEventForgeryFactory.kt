/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.tests.elmyr

import com.datadog.android.internal.telemetry.TelemetryEvent
import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class TelemetryLogErrorEventForgeryFactory : ForgeryFactory<TelemetryEvent.Log.Error> {

    override fun getForgery(forge: Forge): TelemetryEvent.Log.Error {
        return TelemetryEvent.Log.Error(
            message = forge.aString(),
            additionalProperties = forge.aMap { forge.aString() to forge.aString() },
            error = forge.aNullable { forge.aThrowable() },
            stacktrace = forge.aNullable { forge.aString() },
            kind = forge.aNullable { forge.aString() }
        )
    }
}
