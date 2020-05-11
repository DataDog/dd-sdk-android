/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.rum.internal.domain.event.RumEventData
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class RumEventDataResourceTimingForgeryFactory :
    ForgeryFactory<RumEventData.Resource.Timing> {
    override fun getForgery(forge: Forge): RumEventData.Resource.Timing {
        return RumEventData.Resource.Timing(
            dnsStart = forge.aPositiveLong(),
            dnsDuration = forge.aPositiveLong(),
            connectStart = forge.aPositiveLong(),
            connectDuration = forge.aPositiveLong(),
            sslStart = forge.aPositiveLong(),
            sslDuration = forge.aPositiveLong(),
            firstByteStart = forge.aPositiveLong(),
            firstByteDuration = forge.aPositiveLong(),
            downloadStart = forge.aPositiveLong(),
            downloadDuration = forge.aPositiveLong()
        )
    }
}
