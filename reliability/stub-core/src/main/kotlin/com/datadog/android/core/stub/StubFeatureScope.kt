/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.stub

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import org.mockito.Mockito.mock

@Suppress("CheckInternal", "UnsafeThirdPartyFunctionCall")
internal class StubFeatureScope(
    private val feature: Feature,
    private val datadogContextProvider: () -> DatadogContext,
    private val mockFeatureScope: FeatureScope = mock()
) : FeatureScope by mockFeatureScope {

    class StubWriter : EventBatchWriter {
        val events: MutableList<StubEvent> = mutableListOf()

        override fun currentMetadata(): ByteArray? {
            return null
        }

        override fun write(event: RawBatchEvent, batchMetadata: ByteArray?, eventType: EventType): Boolean {
            events.add(StubEvent(String(event.data), String(event.metadata), String(batchMetadata ?: ByteArray(0))))
            return true
        }
    }

    private val eventBatchWriter: StubWriter = StubWriter()
    private val eventsReceived = mutableListOf<Any>()

    // region Stub

    fun eventsWritten(): List<StubEvent> {
        return eventBatchWriter.events
    }

    fun eventsReceived(): List<Any> {
        return eventsReceived
    }

    // endregion

    // region FeatureScope

    override fun withWriteContext(
        withFeatureContexts: Set<String>,
        callback: (DatadogContext, EventWriteScope) -> Unit
    ) {
        callback(
            datadogContextProvider(),
            { it.invoke(eventBatchWriter) }
        )
    }

    override fun withContext(withFeatureContexts: Set<String>, callback: (datadogContext: DatadogContext) -> Unit) {
        callback(datadogContextProvider())
    }

    override fun getWriteContextSync(withFeatureContexts: Set<String>): Pair<DatadogContext, EventWriteScope>? {
        return datadogContextProvider() to { it.invoke(eventBatchWriter) }
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
