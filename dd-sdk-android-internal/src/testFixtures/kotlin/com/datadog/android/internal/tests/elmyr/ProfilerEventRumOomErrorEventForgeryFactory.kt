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

class ProfilerEventRumOomErrorEventForgeryFactory : ForgeryFactory<ProfilerEvent.RumOomErrorEvent> {
    override fun getForgery(forge: Forge): ProfilerEvent.RumOomErrorEvent {
        return ProfilerEvent.RumOomErrorEvent(
            id = forge.getForgery<UUID>().toString(),
            // int instead of long to avoid overflow in call sites if timestamp is used
            timestamp = forge.aPositiveInt().toLong(),
            rumContext = forge.getForgery()
        )
    }
}
