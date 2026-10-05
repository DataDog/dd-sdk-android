/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.repository

import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.model.EvaluationContext

/** Keeps context-operation bookkeeping running when no flag storage is available. */
internal class NoOpFlagsRepository : FlagsRepository {
    override fun getPrecomputedFlag(key: String): PrecomputedFlag? = null

    override fun getEvaluationContext(): EvaluationContext? = null

    override fun setFlagsAndContext(
        context: EvaluationContext,
        flags: Map<String, PrecomputedFlag>,
        onInstalled: () -> Unit
    ) {
        onInstalled()
    }

    override fun getPrecomputedFlagWithContext(key: String): Pair<PrecomputedFlag, EvaluationContext>? = null

    override fun firstFlags(): FirstFlagsLatch = FirstFlagsLatch()

    override fun hasFlags(): Boolean = false

    override fun hasLoadedFlagsForContext(context: EvaluationContext): Boolean = false

    override fun getFlagsSnapshot(): Map<String, PrecomputedFlag> = emptyMap()
}
