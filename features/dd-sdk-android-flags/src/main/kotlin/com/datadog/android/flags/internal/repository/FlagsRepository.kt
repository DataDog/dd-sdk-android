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
    fun setRequestedContext(context: EvaluationContext)

    /**
     * Runs [onInstalled] after attempting storage submission, including when submission fails.
     * For the first installation, it runs before handing publication to [dispatchFirstFlags].
     * Callers holding an outer lock must defer that publication until after releasing the lock.
     */
    fun setFlagsAndContext(
        context: EvaluationContext,
        flags: Map<String, PrecomputedFlag>,
        dispatchFirstFlags: (() -> Unit) -> Unit = { it() },
        onInstalled: () -> Unit = {}
    )
    fun getPrecomputedFlagWithContext(key: String): FlagWithContext?
    fun waitForFlags(): FirstFlagsLatch
    fun hasFlags(): Boolean
    fun hasLoadedFlagsForContext(context: EvaluationContext): Boolean
    fun getFlagsSnapshot(): Map<String, PrecomputedFlag>
}
