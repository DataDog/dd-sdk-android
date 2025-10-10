/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.rum.internal.domain.event.ResourceTiming
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class ResourceTimingForgeryFactory :
    ForgeryFactory<ResourceTiming> {
    override fun getForgery(forge: Forge): ResourceTiming {
        return ResourceTiming(
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
