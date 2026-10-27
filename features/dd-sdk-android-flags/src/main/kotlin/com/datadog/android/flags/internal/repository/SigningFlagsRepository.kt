/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.repository

import com.datadog.android.flags.ResponseSigningMode
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
        val proof: VerifiedResponse
    )
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

    override fun setFlagsAndContext(
        context: EvaluationContext,
        flags: Map<String, PrecomputedFlag>,
        obfuscation: FlagKeyObfuscation?,
        onInstalled: () -> Unit
    ) {
        if (!required) ordinary().setFlagsAndContext(context, flags, obfuscation, onInstalled)
    }

    override fun setSignedFlagsAndContext(
        context: EvaluationContext,
        flags: Map<String, PrecomputedFlag>,
        obfuscation: FlagKeyObfuscation?,
        proof: VerifiedResponse?,
        onInstalled: () -> Unit
    ) {
        if (proof != null && signing.permits(context, proof)) {
            verified.set(VerifiedFlags(context, flags, obfuscation, proof))
        } else if (required) {
            return
        }
        if (required) {
            onInstalled()
            firstFlags.complete(if (obfuscation == null) flags.keys else emptySet(), deliverFirstFlags)
        } else {
            ordinary().setFlagsAndContext(context, flags, obfuscation, onInstalled)
        }
    }

    override fun restoreVerifiedFlags() {
        if (!required) current()?.let { ordinary().setFlagsAndContext(it.context, it.flags, it.obfuscation) }
    }

    @Suppress("ReturnCount") // Stop before resolving a flag when no admissible snapshot exists.
    override fun getPrecomputedFlagWithContext(key: String): Pair<PrecomputedFlag?, EvaluationContext>? {
        if (!required) return ordinary().getPrecomputedFlagWithContext(key)
        val state = current() ?: return null
        val encodedKey = state.obfuscation?.encode(key) ?: if (state.obfuscation == null) key else return null
        return state.flags[encodedKey] to state.context
    }

    override fun getPrecomputedFlag(key: String): PrecomputedFlag? =
        if (required) getPrecomputedFlagWithContext(key)?.first else ordinary().getPrecomputedFlag(key)

    override fun getEvaluationContext(): EvaluationContext? =
        if (required) current()?.context else ordinary().getEvaluationContext()

    override fun getFlagsSnapshot(): Map<String, PrecomputedFlag> =
        if (required) {
            current()?.takeIf {
                it.obfuscation == null
            }?.flags ?: emptyMap()
        } else {
            ordinary().getFlagsSnapshot()
        }

    override fun hasFlags(): Boolean = if (required) current()?.flags?.isNotEmpty() == true else ordinary().hasFlags()

    override fun hasLoadedFlagsForContext(context: EvaluationContext): Boolean =
        if (required) {
            current()?.let { it.context == context && it.flags.isNotEmpty() } == true
        } else {
            ordinary().hasLoadedFlagsForContext(context)
        }
}
