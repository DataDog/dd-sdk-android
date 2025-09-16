/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags

import org.json.JSONObject

class NoOpFlagsProvider : FlagsProvider {
    override fun setContext(newContext: ProviderContext) {}

    override fun resolveBooleanValue(flagKey: String, defaultValue: Boolean): Boolean {
        return defaultValue
    }

    override fun resolveStringValue(flagKey: String, defaultValue: String): String {
        return defaultValue
    }

    override fun resolveNumberValue(flagKey: String, defaultValue: Number): Number {
        return defaultValue
    }

    override fun resolveIntValue(flagKey: String, defaultValue: Int): Int {
        return defaultValue
    }

    override fun resolveStructureValue(flagKey: String, defaultValue: JSONObject): JSONObject {
        return defaultValue
    }
}
