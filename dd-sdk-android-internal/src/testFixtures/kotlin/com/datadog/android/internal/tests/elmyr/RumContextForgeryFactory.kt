/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.tests.elmyr

import com.datadog.android.internal.rum.RumContext
import com.datadog.android.internal.rum.RumSessionStartReason
import com.datadog.android.internal.rum.RumSessionState
import com.datadog.android.internal.rum.RumViewType
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.UUID

class RumContextForgeryFactory : ForgeryFactory<RumContext> {
    override fun getForgery(forge: Forge): RumContext {
        return RumContext(
            applicationId = forge.getForgery<UUID>().toString(),
            sessionId = forge.getForgery<UUID>().toString(),
            isSessionActive = forge.aBool(),
            viewId = forge.aNullable { getForgery<UUID>().toString() },
            viewName = forge.aNullable { forge.anAlphaNumericalString() },
            viewUrl = forge.aStringMatching("http(s?)://[a-z]+\\.com/[a-z]+"),
            actionId = forge.aNullable { getForgery<UUID>().toString() },
            sessionState = forge.aValueFrom(RumSessionState::class.java),
            sessionStartReason = forge.aValueFrom(RumSessionStartReason::class.java),
            viewType = forge.aValueFrom(RumViewType::class.java),
            syntheticsTestId = forge.aNullable { forge.anAlphaNumericalString() },
            syntheticsResultId = forge.aNullable { forge.anAlphaNumericalString() },
            hasTimeseries = forge.aBool(),
            sessionSampleRate = forge.aFloat(min = 0f, max = 100f)
        )
    }
}
