/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal.utils.forge

import com.datadog.android.okhttp.TraceContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class TraceContextFactory : ForgeryFactory<TraceContext> {

    override fun getForgery(forge: Forge): TraceContext {
        val fakeTraceId = forge.aStringMatching("[0-9a-f]{16}")
        val fakeSpanId = forge.aStringMatching("[0-9a-f]{16}")
        val fakeSamplingPriority = forge.anInt()
        return TraceContext(
            traceId = fakeTraceId,
            spanId = fakeSpanId,
            samplingPriority = fakeSamplingPriority
        )
    }
}
