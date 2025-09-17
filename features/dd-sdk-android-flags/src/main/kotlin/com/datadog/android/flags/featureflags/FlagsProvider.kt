/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags

import com.datadog.android.flags.featureflags.model.ProviderContext
import org.json.JSONObject

/**
 * An interface for defining Flags providers.
 */
interface FlagsProvider {
    /**
     * Set the context for the provider.
     * @param newContext The new context to use for the provider.
     */
    fun setContext(newContext: ProviderContext)

    /**
     * Resolve a Boolean flag value.
     * @param flagKey The name of the key to query.
     * @param defaultValue The value to return if the key cannot be retrieved.
     * @return The value of the flag, or the default value if it cannot be retrieved.
     */
    fun resolveBooleanValue(flagKey: String, defaultValue: Boolean): Boolean

    /**
     * Resolve a String flag value.
     * @param flagKey The name of the key to query.
     * @param defaultValue The value to return if the key cannot be retrieved.
     * @return The value of the flag, or the default value if it cannot be retrieved.
     */
    fun resolveStringValue(flagKey: String, defaultValue: String): String

    /**
     * Resolve a Number flag value.
     * @param flagKey The name of the key to query.
     * @param defaultValue The value to return if the key cannot be retrieved.
     * @return The value of the flag, or the default value if it cannot be retrieved.
     */
    fun resolveNumberValue(flagKey: String, defaultValue: Number): Number

    /**
     * Resolve a Int flag value.
     * @param flagKey The name of the key to query.
     * @param defaultValue The value to return if the key cannot be retrieved.
     * @return The value of the flag, or the default value if it cannot be retrieved.
     */
    fun resolveIntValue(flagKey: String, defaultValue: Int): Int

    /**
     * Resolve a Structure flag value.
     * @param flagKey The name of the key to query.
     * @param defaultValue The value to return if the key cannot be retrieved.
     * @return The value of the flag, or the default value if it cannot be retrieved.
     */
    fun resolveStructureValue(flagKey: String, defaultValue: JSONObject): JSONObject
}
