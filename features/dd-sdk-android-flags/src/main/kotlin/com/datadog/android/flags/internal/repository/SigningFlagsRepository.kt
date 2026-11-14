/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.repository

import com.datadog.android.flags.ResponseSigningMode
import com.datadog.android.flags.internal.aggregation.EvaluationTargetingKey
import com.datadog.android.flags.internal.model.FlagEvaluationSnapshot
import com.datadog.android.flags.internal.model.FlagKeyObfuscation
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.signing.ResponseSigningState
import com.datadog.android.flags.internal.signing.VerifiedResponse
import com.datadog.android.flags.model.EvaluationContext
import java.util.concurrent.atomic.AtomicReference

/** Verified state is never persisted or replaced by an unsigned observation. */
internal class SigningFlagsRepository(
    private val ordinaryFactory: (String) -> FlagsRepository,
    private val signing: ResponseSigningState,
    private val deliverFirstFlags: (Runnable) -> Unit
) : FlagsRepository {
    private data class VerifiedFlags(
        val context: EvaluationContext,
        val flags: Map<String, PrecomputedFlag>,
        val obfuscation: FlagKeyObfuscation?,
        val proof: VerifiedResponse,
        val observeFullEvaluationData: Boolean
    ) {
        val telemetryTargetingKey = EvaluationTargetingKey.create(context.targetingKey, observeFullEvaluationData)
    }
    private val verified = AtomicReference<VerifiedFlags?>(null)
    private val required = signing.configuration.mode == ResponseSigningMode.REQUIRED
    override val firstFlags = FirstFlagsLatch()
    private val empty = NoOpFlagsRepository()
    private var ordinaryScope: String? = null
    private var scopedOrdinary: FlagsRepository = empty

    private fun ordinary(): FlagsRepository = synchronized(signing.lock) {
        val scope = signing.currentScope()
        if (required || scope == null) return empty
        if (ordinaryScope != scope) {
            scopedOrdinary = ordinaryFactory(scope)
            ordinaryScope = scope
            scopedOrdinary.firstFlags.whenComplete { keys ->
                synchronized(signing.lock) {
                    if (signing.currentScope() == scope) firstFlags.complete(keys, deliverFirstFlags)
                }
            }
        }
        scopedOrdinary
    }

    override fun waitForPersistenceLoad() = ordinary().waitForPersistenceLoad()

    private fun current(): VerifiedFlags? = verified.get()?.takeIf { signing.permits(it.context, it.proof) }

    override fun installFlagsAndContext(
        context: EvaluationContext,
        flags: Map<String, PrecomputedFlag>,
        obfuscation: FlagKeyObfuscation?,
        observeFullEvaluationData: Boolean
    ): FlagsInstallation = if (required) {
        FlagsInstallation { }
    } else {
        ordinary().installFlagsAndContext(context, flags, obfuscation, observeFullEvaluationData)
    }

    override fun installSignedFlagsAndContext(
        context: EvaluationContext,
        flags: Map<String, PrecomputedFlag>,
        obfuscation: FlagKeyObfuscation?,
        proof: VerifiedResponse?,
        observeFullEvaluationData: Boolean
    ): FlagsInstallation? {
        if (proof != null && signing.permits(context, proof)) {
            verified.set(VerifiedFlags(context, flags, obfuscation, proof, observeFullEvaluationData))
        } else if (required) {
            return null
        }
        return if (required) {
            FlagsInstallation { onInstalled ->
                onInstalled()
                firstFlags.complete(if (obfuscation == null) flags.keys else emptySet(), deliverFirstFlags)
            }
        } else {
            ordinary().installFlagsAndContext(context, flags, obfuscation, observeFullEvaluationData)
        }
    }

    override fun restoreVerifiedFlags(): FlagsInstallation? = if (required) {
        null
    } else {
        current()?.let {
            ordinary().installFlagsAndContext(it.context, it.flags, it.obfuscation, it.observeFullEvaluationData)
        }
    }

    @Suppress("ReturnCount") // Stop before resolving a flag when no admissible snapshot exists.
    override fun getEvaluationSnapshot(key: String): FlagEvaluationSnapshot? {
        if (!required) return ordinary().getEvaluationSnapshot(key)
        val state = current() ?: return null
        val encodedKey = state.obfuscation?.encode(key) ?: if (state.obfuscation == null) key else return null
        return FlagEvaluationSnapshot(
            state.flags[encodedKey],
            state.context,
            state.observeFullEvaluationData,
            state.telemetryTargetingKey
        )
    }

    override fun getEvaluationContext(): EvaluationContext? =
        if (required) current()?.context else ordinary().getEvaluationContext()

    override fun getAssignmentsSnapshot(): FlagsAssignmentsSnapshot? = if (required) {
        current()?.takeIf { it.obfuscation == null }?.let { FlagsAssignmentsSnapshot(it.flags, it.context) }
    } else {
        ordinary().getAssignmentsSnapshot()
    }

    override fun hasConfiguration(): Boolean = if (required) current() != null else ordinary().hasConfiguration()

    override fun hasConfigurationForContext(context: EvaluationContext): Boolean =
        if (required) {
            current()?.let { it.context == context } == true
        } else {
            ordinary().hasConfigurationForContext(context)
        }
}
