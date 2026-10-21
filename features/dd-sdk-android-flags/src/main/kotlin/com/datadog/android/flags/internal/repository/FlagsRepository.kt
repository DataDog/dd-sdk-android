/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.repository

import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.model.EvaluationContext

internal interface FlagsRepository {
    fun getPrecomputedFlag(key: String): PrecomputedFlag?
    fun getEvaluationContext(): EvaluationContext?

    /** Reads the context and nullable assignment after one bounded persistence wait. */
    fun getResolutionSnapshot(key: String): FlagsResolutionSnapshot?

    /** Installs memory only. The caller must complete storage and notifications outside its locks. */
    fun installFlagsAndContext(context: EvaluationContext, flags: Map<String, PrecomputedFlag>): FlagsInstallation

    /**
     * Runs [onInstalled] after attempting storage submission, including when submission fails.
     * For the first installation, it runs before publishing the first-flags result to listeners.
     */
    fun setFlagsAndContext(
        context: EvaluationContext,
        flags: Map<String, PrecomputedFlag>,
        onInstalled: () -> Unit = {}
    )
    fun getPrecomputedFlagWithContext(key: String): Pair<PrecomputedFlag, EvaluationContext>?
    val firstFlags: FirstFlagsLatch
    fun hasFlags(): Boolean
    fun hasLoadedFlagsForContext(context: EvaluationContext): Boolean
    fun getFlagsSnapshot(): Map<String, PrecomputedFlag>
}

internal data class FlagsResolutionSnapshot(val flag: PrecomputedFlag?, val context: EvaluationContext)

internal fun interface FlagsInstallation {
    fun complete(onInstalled: () -> Unit)
}
