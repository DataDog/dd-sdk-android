/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.tests.elmyr

import com.datadog.android.internal.profiling.ProfilerEvent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.UUID

class ProfilerEventRumVitalEventFactory : ForgeryFactory<ProfilerEvent.RumVitalEvent> {
    override fun getForgery(forge: Forge): ProfilerEvent.RumVitalEvent {
        return ProfilerEvent.RumVitalEvent(
            rumContext = forge.getForgery(),
            id = forge.getForgery<UUID>().toString(),
            name = forge.aNullable { anAlphabeticalString() },
            type = forge.getForgery(),
            // int instead of long to avoid overflow in call sites if startMs + duration is used
            startMs = forge.aPositiveInt().toLong(),
            durationNs = forge.aPositiveInt().toLong()
        )
    }
}
