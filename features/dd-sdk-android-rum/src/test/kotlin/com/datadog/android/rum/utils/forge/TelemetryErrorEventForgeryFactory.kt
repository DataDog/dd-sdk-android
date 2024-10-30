/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.internal.utils.loggableStackTrace
import com.datadog.android.telemetry.model.TelemetryErrorEvent
import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import fr.xgouchet.elmyr.jvm.ext.aTimestamp
import java.util.UUID

internal class TelemetryErrorEventForgeryFactory : ForgeryFactory<TelemetryErrorEvent> {

    override fun getForgery(forge: Forge): TelemetryErrorEvent {
        val throwable = forge.aThrowable()

        return TelemetryErrorEvent(
            date = forge.aTimestamp(),
            source = forge.aValueFrom(TelemetryErrorEvent.Source::class.java),
            service = forge.anAlphabeticalString(),
            version = forge.anAlphabeticalString(),
            application = forge.aNullable {
                TelemetryErrorEvent.Application(
                    forge.getForgery<UUID>().toString()
                )
            },
            session = forge.aNullable {
                TelemetryErrorEvent.Session(
                    forge.getForgery<UUID>().toString()
                )
            },
            view = forge.aNullable {
                TelemetryErrorEvent.View(
                    forge.getForgery<UUID>().toString()
                )
            },
            action = forge.aNullable {
                TelemetryErrorEvent.Action(
                    forge.getForgery<UUID>().toString()
                )
            },
            dd = TelemetryErrorEvent.Dd(),
            telemetry = TelemetryErrorEvent.Telemetry(
                message = forge.anAlphabeticalString(),
                error = TelemetryErrorEvent.Error(
                    stack = forge.aNullable { throwable.loggableStackTrace() },
                    kind = forge.aNullable {
                        throwable.javaClass.canonicalName ?: throwable.javaClass.simpleName
                    }
                )
            )
        )
    }
}
