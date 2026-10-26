/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.stub

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureEventReceiver
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.api.storage.datastore.DataStoreHandler
import org.mockito.kotlin.mock

@Suppress("UnsafeThirdPartyFunctionCall")
internal class StubFeatureScope(
    private val feature: Feature,
    private val datadogContextProvider: () -> DatadogContext,
    private val dataStoreProvider: () -> DataStoreHandler? = { null },
    private val eventReceiverProvider: () -> FeatureEventReceiver? = { null },
    // Stub-only: recorded invocations would keep the feature (and its core) alive after the test
    private val mockFeatureScope: FeatureScope = mock(stubOnly = true)
) : FeatureScope by mockFeatureScope {

    private val eventsWritten = mutableListOf<Pair<RawBatchEvent, ByteArray?>>()

    private val eventBatchWriter = object : EventBatchWriter {
        override fun write(event: RawBatchEvent, batchMetadata: ByteArray?, eventType: EventType): Boolean {
            eventsWritten.add(event to batchMetadata)
            return true
        }
    }

    private val eventsReceived = mutableListOf<Any>()

    // region Stub

    fun eventsWritten(): List<StubEvent> {
        return eventsWritten.map { (event, batchMetadata) ->
            StubEvent(String(event.data), String(event.metadata), String(batchMetadata ?: ByteArray(0)))
        }
    }

    fun eventsReceived(): List<Any> {
        return eventsReceived
    }

    // endregion

    // region FeatureScope

    override val dataStore: DataStoreHandler
        get() = dataStoreProvider() ?: mockFeatureScope.dataStore

    override fun withWriteContext(
        withFeatureContexts: Set<String>,
        callback: (DatadogContext, EventWriteScope) -> Unit
    ) {
        callback(
            datadogContextProvider()
        ) { it.invoke(eventBatchWriter) }
    }

    override fun withContext(withFeatureContexts: Set<String>, callback: (datadogContext: DatadogContext) -> Unit) {
        callback(datadogContextProvider())
    }

    override fun withWriteContextSync(
        withFeatureContexts: Set<String>,
        callback: (DatadogContext, EventWriteScope) -> Unit
    ): Boolean {
        callback(
            datadogContextProvider()
        ) { it.invoke(eventBatchWriter) }
        return true
    }

    override fun sendEvent(event: Any) {
        eventsReceived.add(event)
        eventReceiverProvider()?.onReceive(event)
    }

    override fun <T : Feature> unwrap(): T {
        @Suppress("UNCHECKED_CAST")
        return feature as T
    }

    // endregion
}
