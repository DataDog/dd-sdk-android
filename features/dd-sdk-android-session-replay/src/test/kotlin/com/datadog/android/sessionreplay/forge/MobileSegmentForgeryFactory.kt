/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.forge

import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.UUID

internal class MobileSegmentForgeryFactory : ForgeryFactory<MobileSegment> {
    override fun getForgery(forge: Forge): MobileSegment {
        return MobileSegment(
            application = MobileSegment.Application(forge.getForgery<UUID>().toString()),
            session = MobileSegment.Session(forge.getForgery<UUID>().toString()),
            view = MobileSegment.View(forge.getForgery<UUID>().toString()),
            start = forge.aPositiveLong(),
            end = forge.aPositiveLong(),
            recordsCount = forge.aPositiveLong(),
            indexInView = forge.aNullable { forge.aPositiveLong() },
            hasFullSnapshot = forge.aNullable { forge.aBool() },
            source = forge.aValueFrom(MobileSegment.Source::class.java),
            // we only need this in the SessionReplayRequestFactory tests which doesn't
            // care about the records in the segment. We are not going to fake this data
            // as will require to duplicate all the factories from SR module.
            records = emptyList()
        )
    }
}
