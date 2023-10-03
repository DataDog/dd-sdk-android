/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.EventBatchWriter
import org.mockito.Mockito.mock
import org.mockito.kotlin.mockingDetails

internal class StubFeatureScope(
    private val feature: Feature,
    private val datadogContext: DatadogContext,
    private val mockFeatureScope: FeatureScope = mock()
) : FeatureScope by mockFeatureScope {

    private val eventBatchWriter: EventBatchWriter = mock()

    // region Stub

    fun eventsWritten(): List<StubEvent> {
        val details = mockingDetails(eventBatchWriter)
        return details.invocations
            .filter { it.method.name == "write" }
            .map { invocation ->
                val eventData = invocation.arguments[0] as? ByteArray
                val eventMetaData = invocation.arguments[1] as? ByteArray
                check(eventData != null) { "Unexpected null argument" }
                StubEvent(String(eventData), eventMetaData?.let { String(it) })
            }
    }

    // endregion

    // region FeatureScope

    override fun withWriteContext(forceNewBatch: Boolean, callback: (DatadogContext, EventBatchWriter) -> Unit) {
        callback(datadogContext, eventBatchWriter)
    }

    // endregion
}
