/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.RawBatchEvent
import com.google.gson.Gson
import com.google.gson.JsonElement

class DatadogCoreProxy(
    val core: InternalSdkCore,
) : InternalSdkCore by core {
    private val featureScopes = mutableMapOf<String, FeatureScopeInterceptor>()

    fun eventsWritten(featureName: String): String {
        val events = featureScopes[featureName]?.eventsWritten()?.toList() ?: emptyList<Any>()
        return Gson().toJson(events)
    }

    fun clearData(featureName: String) {
        featureScopes[featureName]?.clearData()
    }

    override fun registerFeature(feature: Feature) {
        core.registerFeature(feature)
        val featureScope = core.getFeature(feature.name)
        featureScopes[feature.name] = FeatureScopeInterceptor(featureScope!!, core, feature)
    }

    override fun getFeature(featureName: String): FeatureScope? {
        core.getFeature(featureName)
        return featureScopes[featureName]
    }
}

internal class FeatureScopeInterceptor(
    private val featureScope: FeatureScope,
    private val core: InternalSdkCore,
    private val feature: Feature,
    ) : FeatureScope by featureScope {

    private val eventsBatchInterceptor = EventBatchInterceptor()

    fun eventsWritten(): List<String> {
        return eventsBatchInterceptor.events
    }

    fun clearData() {
        eventsBatchInterceptor.clearData()
    }

    // region FeatureScope

    override fun withWriteContext(
        forceNewBatch: Boolean,
        callback: (DatadogContext, EventBatchWriter) -> Unit
    ) {
        featureScope.withWriteContext(forceNewBatch, callback)

        val context = core.getDatadogContext()!!
        callback(context, eventsBatchInterceptor)
    }

    override fun <T : Feature> unwrap(): T {
        @Suppress("UNCHECKED_CAST")
        return feature as T
    }

    // endregion
}


internal class EventBatchInterceptor: EventBatchWriter {
    internal val events = mutableListOf<String>()

    override fun currentMetadata(): ByteArray? {
        TODO("Not yet implemented")
    }

    fun clearData() {
        events.clear()
    }

    override fun write(
        event: RawBatchEvent,
        batchMetadata: ByteArray?
    ): Boolean {
        val eventContent = String(event.data)

        events.add(events.size,
            eventContent
        )

        return false
    }
}