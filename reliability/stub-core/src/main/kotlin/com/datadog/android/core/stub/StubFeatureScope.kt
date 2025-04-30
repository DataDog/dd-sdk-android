/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.stub

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.RawBatchEvent
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever

@Suppress("CheckInternal", "UnsafeThirdPartyFunctionCall")
internal class StubFeatureScope(
    private val feature: Feature,
    private val datadogContextProvider: () -> DatadogContext,
    private val mockFeatureScope: FeatureScope = mock()
) : FeatureScope by mockFeatureScope {

    private val eventBatchWriter: EventBatchWriter = mock<EventBatchWriter>().also {
        whenever(it.write(any(), anyOrNull(), any())) doReturn true
    }

    private val eventsReceived = mutableListOf<Any>()

    // region Stub

    fun eventsWritten(): List<StubEvent> {
        val details = mockingDetails(eventBatchWriter)
        return details.invocations
            .filter { it.method.name == "write" }
            .map { invocation ->
                val event = invocation.arguments[0] as? RawBatchEvent
                val batchMetadata = invocation.arguments[1] as? ByteArray ?: ByteArray(0)

                check(event != null) { "Unexpected null event, arguments were ${invocation.arguments.joinToString()}" }
                val eventContent = String(event.data)
                val eventMetadata = String(event.metadata)
                StubEvent(eventContent, eventMetadata, String(batchMetadata))
            }
    }

    fun eventsReceived(): List<Any> {
        return eventsReceived
    }

    // endregion

    // region FeatureScope

    override fun withWriteContext(
        callback: (DatadogContext, EventBatchWriter) -> Unit
    ) {
        callback(datadogContextProvider(), eventBatchWriter)
    }

    override fun sendEvent(event: Any) {
        eventsReceived.add(event)
    }

    override fun <T : Feature> unwrap(): T {
        @Suppress("UNCHECKED_CAST")
        return feature as T
    }

    // endregion
}
