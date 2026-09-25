/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.repository

import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.tools.annotation.NoOpImplementation

@Suppress("TooManyFunctions") // Assignment reads, writes and internal lifecycle notifications.
@NoOpImplementation
internal interface FlagsRepository {
    fun close()
    fun hasLoadedConfiguration(): Boolean
    fun addConfigurationChangeListener(listener: () -> Unit)
    fun removeConfigurationChangeListener(listener: () -> Unit)
    fun getPrecomputedFlag(key: String): PrecomputedFlag?
    fun getEvaluationContext(): EvaluationContext?
    fun setFlagsAndContext(context: EvaluationContext, flags: Map<String, PrecomputedFlag>)
    fun getPrecomputedFlagWithContext(key: String): Pair<PrecomputedFlag, EvaluationContext>?
    fun hasFlags(): Boolean
    fun hasLoadedFlagsForContext(context: EvaluationContext): Boolean
    fun getFlagsSnapshot(): Map<String, PrecomputedFlag>
}
