/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal

import com.datadog.android.flags.featureflags.FlagsProvider
import org.json.JSONObject

internal class NoOpFlagsProvider : FlagsProvider {
    override fun setContext(targetingKey: String, attributes: Map<String, Any>) {}

    override fun resolveBooleanValue(flagKey: String, defaultValue: Boolean): Boolean = defaultValue

    override fun resolveStringValue(flagKey: String, defaultValue: String): String = defaultValue

    override fun resolveNumberValue(flagKey: String, defaultValue: Number): Number = defaultValue

    override fun resolveIntValue(flagKey: String, defaultValue: Int): Int = defaultValue

    override fun resolveStructureValue(flagKey: String, defaultValue: JSONObject): JSONObject = defaultValue
}
