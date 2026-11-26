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
import com.datadog.android.flags.internal.model.PrecomputedFlagEvaluationError
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
    private val cacheMutationLock = Any()
    private var stateSavedSinceLoadStarted = false

    // SDK versions without flag selection read only the unselected key for their opt-out mode and ignore the
    // flagSelection field, so selected state uses its own key and is never read by them as a full configuration.
    private val unselectedStateKey = stateKey(instanceName, experimentationOptOutMode, selected = false)
    private val selectedStateKey = stateKey(instanceName, experimentationOptOutMode, selected = true)
    private val unselectedDependentStateKey =
        stateKey(instanceName, experimentationOptOutMode, selected = false, dependent = true)
    private val selectedDependentStateKey =
        stateKey(instanceName, experimentationOptOutMode, selected = true, dependent = true)
    private val legacyStateKey: String = if (flagSelection == null) unselectedStateKey else selectedStateKey
    private val dependentStateKey: String =
        if (flagSelection == null) unselectedDependentStateKey else selectedDependentStateKey
    private val staleStateKeys: List<String> = if (flagSelection == null) {
        listOf(selectedStateKey, selectedDependentStateKey)
    } else {
        listOf(unselectedStateKey, unselectedDependentStateKey)
    }

    init {
        // One persisted state per instance: changing to or from a selection discards the other key's state.
        staleStateKeys.forEach { dataStore.removeValue(it) }
        loadFlagsState(onStateLoaded)
    }

    internal fun saveFlagsState(
        context: EvaluationContext,
        flags: Map<String, PrecomputedFlag>,
        flagEvaluationErrors: Map<String, PrecomputedFlagEvaluationError> = emptyMap(),
        currentTimestamp: Long,
        callback: DataStoreWriteCallback? = null,
        obfuscation: FlagKeyObfuscation? = null,
        observeFullEvaluationData: Boolean = false
    ) {
        val entry = FlagsStateEntry(
            evaluationContext = context,
            flags = flags,
            lastUpdateTimestamp = currentTimestamp,
            flagEvaluationErrors = flagEvaluationErrors,
            obfuscation = obfuscation,
            observeFullEvaluationData = observeFullEvaluationData,
            flagSelection = flagSelection
        )

        val hasDependencies = hasDependencies(flags, flagEvaluationErrors)
        val stateKey = if (hasDependencies) dependentStateKey else legacyStateKey
        val alternateStateKey = if (hasDependencies) legacyStateKey else dependentStateKey
        synchronized(cacheMutationLock) {
            stateSavedSinceLoadStarted = true
            // Remove the alternate representation first. A rollback must see no cache rather than consume a dependent
            // root from the legacy key, while this SDK must not prefer stale dependent state over an ordinary update.
            dataStore.removeValue(alternateStateKey)
            dataStore.setValue(
                key = stateKey,
                data = entry,
                serializer = serializer,
                callback = callback
            )
        }
    }

    private fun loadFlagsState(onStateLoaded: (FlagsStateEntry?) -> Unit) {
        loadFlagsState(
            dependentStateKey,
            onComplete = { dependentState, dependentReadFailed ->
                loadFlagsState(
                    legacyStateKey,
                    onComplete = { legacyState, legacyReadFailed ->
                        val validDependentState = dependentState?.takeIf { it.flagSelection == flagSelection }
                        val validLegacyState = legacyState?.takeIf { it.flagSelection == flagSelection }
                        val loadedState = newestState(validDependentState, validLegacyState)
                        synchronized(cacheMutationLock) {
                            if (!stateSavedSinceLoadStarted) {
                                removeLosingState(
                                    loadedState,
                                    validDependentState,
                                    dependentState,
                                    legacyState
                                )
                            }
                        }
                        if (loadedState == null && dependentReadFailed && legacyReadFailed) {
                            internalLogger.log(
                                InternalLogger.Level.WARN,
                                InternalLogger.Target.MAINTAINER,
                                { "No persisted flags state found or failed to load" }
                            )
                        }
                        onStateLoaded(loadedState)
                    }
                )
            }
        )
    }

    private fun loadFlagsState(
        key: String,
        onComplete: (FlagsStateEntry?, Boolean) -> Unit
    ) {
        dataStore.value(
            key = key,
            deserializer = deserializer,
            callback = object : DataStoreReadCallback<FlagsStateEntry> {
                override fun onSuccess(dataStoreContent: DataStoreContent<FlagsStateEntry>?) {
                    val loadedState = dataStoreContent?.data
                    onComplete(loadedState, false)
                }

                override fun onFailure() {
                    onComplete(null, true)
                }
            }
        )
    }

    private fun newestState(
        dependentState: FlagsStateEntry?,
        legacyState: FlagsStateEntry?
    ): FlagsStateEntry? = when {
        dependentState == null -> legacyState
        legacyState == null -> dependentState
        legacyState.lastUpdateTimestamp > dependentState.lastUpdateTimestamp -> legacyState
        else -> dependentState
    }

    private fun removeLosingState(
        loadedState: FlagsStateEntry?,
        validDependentState: FlagsStateEntry?,
        dependentState: FlagsStateEntry?,
        legacyState: FlagsStateEntry?
    ) {
        when {
            loadedState === validDependentState && loadedState != null -> {
                if (legacyState != null) dataStore.removeValue(legacyStateKey)
            }
            loadedState != null -> {
                if (dependentState != null) dataStore.removeValue(dependentStateKey)
            }
            else -> {
                if (dependentState != null) dataStore.removeValue(dependentStateKey)
                if (legacyState != null) dataStore.removeValue(legacyStateKey)
            }
        }
    }

    private fun hasDependencies(
        flags: Map<String, PrecomputedFlag>,
        errors: Map<String, PrecomputedFlagEvaluationError>
    ): Boolean {
        var hasDependencies = false
        for (flag in flags.values) {
            if (flag.dependencyEvaluations.isNotEmpty()) {
                hasDependencies = true
                break
            }
        }
        if (!hasDependencies) {
            for (error in errors.values) {
                if (error.dependencyEvaluations.isNotEmpty()) {
                    hasDependencies = true
                    break
                }
            }
        }
        return hasDependencies
    }

    companion object {
        internal fun stateKey(
            instanceName: String,
            mode: ExperimentationOptOutMode,
            selected: Boolean,
            dependent: Boolean = false
        ): String {
            val selection = if (selected) "selection-" else ""
            val modePart = when (mode) {
                ExperimentationOptOutMode.DISABLED -> ""
                ExperimentationOptOutMode.IMMEDIATE -> "immediate-v1-"
                ExperimentationOptOutMode.FUTURE_EXPERIMENTS_ONLY -> "future-v1-"
            }
            val dependentPart = if (dependent) "dependent-v1-" else ""
            return "flags-$selection$modePart${dependentPart}state-$instanceName"
        }
    }
}
