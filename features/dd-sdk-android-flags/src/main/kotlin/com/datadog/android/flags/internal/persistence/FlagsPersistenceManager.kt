/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.persistence

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.api.storage.datastore.DataStoreWriteCallback
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.flags.ExperimentationOptOutMode
import com.datadog.android.flags.internal.model.FlagKeyObfuscation
import com.datadog.android.flags.internal.model.FlagsStateEntry
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.selection.CanonicalFlagSelection
import com.datadog.android.flags.model.EvaluationContext

internal class FlagsPersistenceManager(
    private val dataStore: DataStoreHandler,
    instanceName: String,
    private val flagSelection: CanonicalFlagSelection?,
    private val internalLogger: InternalLogger,
    experimentationOptOutMode: ExperimentationOptOutMode = ExperimentationOptOutMode.DISABLED,
    onStateLoaded: (FlagsStateEntry?) -> Unit
) {
    private val serializer = FlagsStateSerializer(internalLogger)
    private val deserializer = FlagsStateDeserializer(internalLogger)

    // SDK versions without flag selection read only the unselected key for their opt-out mode and ignore the
    // flagSelection field, so selected state uses its own key and is never read by them as a full configuration.
    private val unselectedStateKey = stateKey(instanceName, experimentationOptOutMode, selected = false)
    private val selectedStateKey = stateKey(instanceName, experimentationOptOutMode, selected = true)
    private val flagsStateKey: String = if (flagSelection == null) unselectedStateKey else selectedStateKey

    init {
        // One persisted state per instance: changing to or from a selection discards the other key's state.
        dataStore.removeValue(if (flagSelection == null) selectedStateKey else unselectedStateKey)
        loadFlagsState(onStateLoaded)
    }

    internal fun saveFlagsState(
        context: EvaluationContext,
        flags: Map<String, PrecomputedFlag>,
        currentTimestamp: Long,
        callback: DataStoreWriteCallback? = null,
        obfuscation: FlagKeyObfuscation? = null
    ) {
        val entry = FlagsStateEntry(
            evaluationContext = context,
            flags = flags,
            lastUpdateTimestamp = currentTimestamp,
            obfuscation = obfuscation,
            flagSelection = flagSelection
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
        internal fun stateKey(instanceName: String, mode: ExperimentationOptOutMode, selected: Boolean): String {
            val selection = if (selected) "selection-" else ""
            val modePart = when (mode) {
                ExperimentationOptOutMode.DISABLED -> ""
                ExperimentationOptOutMode.IMMEDIATE -> "immediate-v1-"
                ExperimentationOptOutMode.FUTURE_EXPERIMENTS_ONLY -> "future-v1-"
            }
            return "flags-$selection${modePart}state-$instanceName"
        }
    }
}
