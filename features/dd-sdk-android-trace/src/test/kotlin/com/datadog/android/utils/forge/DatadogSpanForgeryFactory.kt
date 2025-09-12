/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.utils.forge

import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanContext
import com.datadog.android.trace.internal.DatadogTraceIdAdapter
import com.datadog.trace.api.DDTraceId
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

internal class DatadogSpanForgeryFactory : ForgeryFactory<DatadogSpan> {

    override fun getForgery(forge: Forge): DatadogSpan {
        val rootSpan = forge.createSpan(context = forge.getForgery<DatadogSpanContext>())
        return forge.createSpan(context = forge.getForgery<DatadogSpanContext>(), localSpan = rootSpan)
    }

    private fun Forge.createSpan(context: DatadogSpanContext, localSpan: DatadogSpan? = null) = mock<DatadogSpan> {
        on { isRootSpan } doReturn aBool()
        on { isError } doReturn aBool()
        on { resourceName } doReturn aString()
        on { serviceName } doReturn aString()
        on { operationName } doReturn aString()
        on { traceId } doReturn DatadogTraceIdAdapter(getForgery<DDTraceId>())
        on { parentSpanId } doReturn aLong()
        on { samplingPriority } doReturn anInt()
        on { durationNano } doReturn aLong()
        on { startTimeNanos } doReturn aLong()
        on { localRootSpan } doReturn localSpan
        on { context() } doReturn context
    }
}
