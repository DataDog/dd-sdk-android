/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.forge

import com.datadog.android.profiling.internal.trigger.PendingOomGatingEvent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class PendingOomGatingEventForgeryFactory : ForgeryFactory<PendingOomGatingEvent> {
    override fun getForgery(forge: Forge): PendingOomGatingEvent {
        return PendingOomGatingEvent(
            rumErrorId = forge.anAlphabeticalString(),
            timestampMs = forge.aLong(),
            rumContext = forge.getForgery()
        )
    }
}
