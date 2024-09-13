/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.tests.elmyr

import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class InternalTelemetryDebugLogForgeryFactory : ForgeryFactory<InternalTelemetryEvent.Log.Debug> {

    override fun getForgery(forge: Forge): InternalTelemetryEvent.Log.Debug {
        return InternalTelemetryEvent.Log.Debug(
            message = forge.aString(),
            additionalProperties = forge.aMap { forge.aString() to forge.aString() }
        )
    }
}
