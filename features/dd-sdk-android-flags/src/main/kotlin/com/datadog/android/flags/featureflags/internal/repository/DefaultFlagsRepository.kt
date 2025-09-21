/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.repository

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlag
import com.datadog.android.flags.featureflags.model.EvaluationContext
import java.util.concurrent.atomic.AtomicReference

internal class DefaultFlagsRepository(
    private val featureSdkCore: FeatureSdkCore,
    private val internalLogger: InternalLogger = featureSdkCore.internalLogger
) : FlagsRepository {
    // Atomic state - ensures context and flags are always consistent
    private data class FlagsState(val context: EvaluationContext, val flags: Map<String, PrecomputedFlag>)
    private val atomicState = AtomicReference<FlagsState?>(null)

    override fun setFlagsAndContext(context: EvaluationContext, flags: Map<String, PrecomputedFlag>) {
        val newState = FlagsState(context, flags)
        atomicState.set(newState)

        internalLogger.log(
            InternalLogger.Level.DEBUG,
            InternalLogger.Target.MAINTAINER,
            { "Set flags and context: ${flags.size} flags for context: ${context.targetingKey}" }
        )
    }

    override fun getPrecomputedFlag(key: String): PrecomputedFlag? {
        // Check atomic state first
        val state = atomicState.get()
        if (state != null) {
            return state.flags[key]
        }
        // Log that no flag state and no context is available
        internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            { WARN_CONTEXT_NOT_SET }
        )
        return null
    }

    override fun getEvaluationContext(): EvaluationContext? = atomicState.get()?.context

    override fun getPrecomputedFlagWithContext(key: String): Pair<PrecomputedFlag, EvaluationContext>? {
        val state = atomicState.get()
        if (state != null) {
            val flag = state.flags[key]
            if (flag != null) {
                return flag to state.context
            }
        }
        return null
    }

    companion object {
        const val WARN_CONTEXT_NOT_SET = "You must call FlagsClient.get().setContext in order to have flags available"
    }
}
