/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.tests.elmyr

import com.datadog.android.internal.rum.RumSessionContext
import com.datadog.android.internal.rum.RumSessionStartReason
import com.datadog.android.internal.rum.RumSessionState
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.UUID

class RumSessionContextForgeryFactory : ForgeryFactory<RumSessionContext> {
    override fun getForgery(forge: Forge): RumSessionContext = RumSessionContext(
        id = forge.getForgery<UUID>().toString(),
        state = forge.aValueFrom(RumSessionState::class.java),
        startReason = forge.aValueFrom(RumSessionStartReason::class.java),
        isActive = forge.aBool(),
        sampleRate = forge.aFloat(min = 0f, max = 100f),
        hasTimeseries = forge.aBool()
    )
}
