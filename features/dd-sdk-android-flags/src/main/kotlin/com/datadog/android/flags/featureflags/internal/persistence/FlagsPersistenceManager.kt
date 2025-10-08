/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.persistence

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.api.storage.datastore.DataStoreWriteCallback
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.flags.featureflags.internal.model.FlagsStateEntry
import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlag
import com.datadog.android.flags.featureflags.model.EvaluationContext

internal class FlagsPersistenceManager(
    private val dataStore: DataStoreHandler,
    private val instanceName: String,
    private val internalLogger: InternalLogger,
    onStateLoaded: (FlagsStateEntry?) -> Unit
) {
    private val serializer = FlagsStateSerializer(internalLogger)
    private val deserializer = FlagsStateDeserializer(internalLogger)

    init {
        loadFlagsState(onStateLoaded)
    }

    private val flagsStateKey: String = "$FLAGS_STATE_KEY_PREFIX-$instanceName"

    internal fun saveFlagsState(
        context: EvaluationContext,
        flags: Map<String, PrecomputedFlag>,
        callback: DataStoreWriteCallback? = null
    ) {
        val entry = FlagsStateEntry(
            evaluationContext = context,
            flags = flags,
            lastUpdateTimestamp = System.currentTimeMillis()
        )

        dataStore.setValue(
            key = flagsStateKey,
            data = entry,
            serializer = serializer,
            callback = callback
        )
    }

    private fun loadFlagsState(onStateLoaded: (FlagsStateEntry?) -> Unit) {
        dataStore.value(
            key = flagsStateKey,
            deserializer = deserializer,
            callback = object : DataStoreReadCallback<FlagsStateEntry> {
                override fun onSuccess(dataStoreContent: DataStoreContent<FlagsStateEntry>?) {
                    val loadedState = dataStoreContent?.data
                    onStateLoaded(loadedState)
                }

                override fun onFailure() {
                    internalLogger.log(
                        InternalLogger.Level.WARN,
                        InternalLogger.Target.MAINTAINER,
                        { "No persisted flags state found or failed to load" }
                    )
                    onStateLoaded(null)
                }
            }
        )
    }

    companion object {
        private const val FLAGS_STATE_KEY_PREFIX = "flags-state"
    }
}
