/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal

import com.datadog.android.flags.featureflags.FlagsClient
import com.datadog.android.flags.featureflags.model.EvaluationContext
import org.json.JSONObject

/**
 * No-operation implementation of [FlagsClient] used as a fallback when the real client
 * cannot be initialized or is unavailable.
 *
 * All flag resolution methods return their provided default values without performing
 * any operations. The [setContext] method silently ignores context updates.
 * This implementation is thread-safe and designed for graceful degradation scenarios.
 */
internal class NoOpFlagsClient : FlagsClient {

    /**
     * No-op implementation that ignores context updates.
     * @param context Ignored evaluation context.
     */
    override fun setContext(context: EvaluationContext) {}

    /**
     * Returns the provided default value without any flag evaluation.
     * @param flagKey Ignored flag key.
     * @param defaultValue The value to return.
     * @return The provided default value.
     */
    override fun resolveBooleanValue(flagKey: String, defaultValue: Boolean): Boolean = defaultValue

    /**
     * Returns the provided default value without any flag evaluation.
     * @param flagKey Ignored flag key.
     * @param defaultValue The value to return.
     * @return The provided default value.
     */
    override fun resolveStringValue(flagKey: String, defaultValue: String): String = defaultValue

    /**
     * Returns the provided default value without any flag evaluation.
     * @param flagKey Ignored flag key.
     * @param defaultValue The value to return.
     * @return The provided default value.
     */
    override fun resolveNumberValue(flagKey: String, defaultValue: Number): Number = defaultValue

    /**
     * Returns the provided default value without any flag evaluation.
     * @param flagKey Ignored flag key.
     * @param defaultValue The value to return.
     * @return The provided default value.
     */
    override fun resolveIntValue(flagKey: String, defaultValue: Int): Int = defaultValue

    /**
     * Returns the provided default value without any flag evaluation.
     * @param flagKey Ignored flag key.
     * @param defaultValue The value to return.
     * @return The provided default value.
     */
    override fun resolveStructureValue(flagKey: String, defaultValue: JSONObject): JSONObject = defaultValue
}
