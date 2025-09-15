/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags

import org.json.JSONObject

interface FlagsProvider {
    fun setContext(providerContext: ProviderContext)
    fun resolveBooleanValue(flagKey: String, defaultValue: Boolean): Boolean
    fun resolveStringValue(flagKey: String, defaultValue: String): String
    fun resolveNumberValue(flagKey: String, defaultValue: Number): Number
    fun resolveIntValue(flagKey: String, defaultValue: Int): Int
    fun resolveStructureValue(flagKey: String, defaultValue: JSONObject): JSONObject
}
