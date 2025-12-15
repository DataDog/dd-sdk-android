/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.repository

import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface FlagsRepository {
    fun getPrecomputedFlag(key: String): PrecomputedFlag?
    fun getEvaluationContext(): EvaluationContext?
    fun setFlagsAndContext(context: EvaluationContext, flags: Map<String, PrecomputedFlag>)
    fun getPrecomputedFlagWithContext(key: String): Pair<PrecomputedFlag, EvaluationContext>?
    fun hasFlags(): Boolean
    fun getFlagsSnapshot(): Map<String, PrecomputedFlag>?
}
