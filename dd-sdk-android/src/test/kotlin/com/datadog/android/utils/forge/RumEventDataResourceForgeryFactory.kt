/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.internal.domain.RumEventData
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class RumEventDataResourceForgeryFactory :
    ForgeryFactory<RumEventData.Resource> {
    override fun getForgery(forge: Forge): RumEventData.Resource {
        return RumEventData.Resource(
            kind = forge.aValueFrom(RumResourceKind::class.java),
            method = forge.anElementFrom("GET", "PUT", "POST", "DELETE", "PATCH"),
            durationNanoSeconds = forge.aPositiveLong(),
            url = forge.aStringMatching("https://[a-z]+.com/[a-z0-9_/]+")
        )
    }
}
