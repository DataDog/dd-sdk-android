/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.tests.elmyr

import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class InternalTelemetryApiUsageForgeryFactory : ForgeryFactory<InternalTelemetryEvent.ApiUsage> {

    override fun getForgery(forge: Forge): InternalTelemetryEvent.ApiUsage {
        return forge.anElementFrom(
            InternalTelemetryEvent.ApiUsage.AddViewLoadingTime(
                overwrite = forge.aBool(),
                noView = forge.aBool(),
                noActiveView = forge.aBool(),
                additionalProperties = forge.aMap { aString() to aString() }.toMutableMap()
            ),
            InternalTelemetryEvent.ApiUsage.AddOperationStepVital(
                actionType = forge.aValueFrom(
                    InternalTelemetryEvent.ApiUsage.AddOperationStepVital.ActionType::class.java
                ),
                additionalProperties = forge.aMap { aString() to aString() }.toMutableMap()
            ),
            InternalTelemetryEvent.ApiUsage.TrackWebView(
                additionalProperties = forge.aMap { aString() to aString() }.toMutableMap()
            )
        )
    }
}
