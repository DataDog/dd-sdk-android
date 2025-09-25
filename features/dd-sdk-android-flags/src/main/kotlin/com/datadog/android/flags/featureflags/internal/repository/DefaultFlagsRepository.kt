/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.repository

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlag
import com.datadog.android.flags.featureflags.internal.persistence.FlagsPersistenceManager
import com.datadog.android.flags.featureflags.model.EvaluationContext
import com.datadog.android.flags.internal.FlagsFeature.Companion.FLAGS_FEATURE_NAME
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class DefaultFlagsRepository(
    private val featureSdkCore: FeatureSdkCore,
    private val instanceName: String,
    private val internalLogger: InternalLogger = featureSdkCore.internalLogger
) : FlagsRepository {
    private data class FlagsState(val context: EvaluationContext, val flags: Map<String, PrecomputedFlag>)
    private val atomicState = AtomicReference<FlagsState?>(null)

    private val isInitialized = AtomicBoolean(false)

    private val persistenceManager = FlagsPersistenceManager(
        dataStore = featureSdkCore.getFeature(FLAGS_FEATURE_NAME)?.dataStore,
        instanceName = instanceName,
        internalLogger = internalLogger
    ) { persistedState ->
        persistedState?.let {
            val loadedState = FlagsState(it.evaluationContext, it.flags)
            atomicState.set(loadedState)
        }
        isInitialized.set(true)
    }

    override fun setFlagsAndContext(context: EvaluationContext, flags: Map<String, PrecomputedFlag>) {
        val newState = FlagsState(context, flags)
        atomicState.set(newState)

        if (isInitialized.get()) {
            persistenceManager.saveFlagsState(context, flags)
        }
    }

    override fun getPrecomputedFlag(key: String): PrecomputedFlag? {
        val state = atomicState.get()
        if (state != null) {
            return state.flags[key]
        }
        internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            { WARN_CONTEXT_NOT_SET }
        )
        return null
    }

    override fun getEvaluationContext(): EvaluationContext? = atomicState.get()?.context

    @Suppress("ReturnCount")
    override fun getPrecomputedFlagWithContext(key: String): Pair<PrecomputedFlag, EvaluationContext>? {
        val state = atomicState.get() ?: return null
        val flag = state.flags[key] ?: return null
        return flag to state.context
    }

    companion object {
        const val WARN_CONTEXT_NOT_SET = "You must call FlagsClientManager.get().setEvaluationContext " +
            "in order to have flags available"
    }
}
