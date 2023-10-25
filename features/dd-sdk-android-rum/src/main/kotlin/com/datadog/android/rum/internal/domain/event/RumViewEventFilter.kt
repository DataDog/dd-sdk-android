/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.internal.persistence.Deserializer
import kotlin.math.max

internal class RumViewEventFilter(
    private val eventMetaDeserializer: Deserializer<ByteArray, RumEventMeta>
) {

    fun filterOutRedundantViewEvents(batch: List<RawBatchEvent>): List<RawBatchEvent> {
        val maxDocVersionByViewId = mutableMapOf<String, Long>()
        val viewMetaByEvent = mutableMapOf<RawBatchEvent, RumEventMeta.View>()

        batch.forEach {
            val eventMeta = eventMetaDeserializer.deserialize(it.metadata)
            if (eventMeta is RumEventMeta.View) {
                viewMetaByEvent += it to eventMeta
                val viewId = eventMeta.viewId
                val documentVersion = eventMeta.documentVersion
                val maxDocVersionSeen = maxDocVersionByViewId[viewId]
                if (maxDocVersionSeen == null) {
                    maxDocVersionByViewId[viewId] = documentVersion
                } else {
                    maxDocVersionByViewId[viewId] = max(documentVersion, maxDocVersionSeen)
                }
            }
        }

        return batch.filter {
            if (viewMetaByEvent.containsKey(it)) {
                @Suppress("UnsafeThirdPartyFunctionCall") // we checked the key before
                val viewMeta = viewMetaByEvent.getValue(it)
                // we need to leave only view event with a max doc version for a give viewId in the
                // batch, because backend will do the same during the reduce process
                @Suppress("UnsafeThirdPartyFunctionCall") // if there is a meta, there is a max doc version
                viewMeta.documentVersion == maxDocVersionByViewId.getValue(viewMeta.viewId)
            } else {
                true
            }
        }
    }
}
