/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.repository.store

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlag
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

internal class FlagsStoreManager(
    private val internalLogger: InternalLogger
) : StoreManager {
    private val featureFlagState = AtomicReference<Map<String, PrecomputedFlag>>(emptyMap())

    override fun getBooleanValue(key: String, defaultValue: Boolean): Boolean {
        return featureFlagState.get()[key]?.variationValue?.toBoolean() ?: defaultValue
    }

    override fun getStringValue(key: String, defaultValue: String): String {
        return featureFlagState.get()[key]?.variationValue ?: defaultValue
    }

    override fun getIntValue(key: String, defaultValue: Int): Int {
        return featureFlagState.get()[key]?.variationValue?.toIntOrNull() ?: defaultValue
    }

    override fun getDoubleValue(key: String, defaultValue: Double): Double {
        return featureFlagState.get()[key]?.variationValue?.toDoubleOrNull() ?: defaultValue
    }

    override fun getJsonObjectValue(key: String, defaultValue: JSONObject): JSONObject {
        return featureFlagState.get()[key]?.variationValue?.let {
            try {
                JSONObject(it)
            } catch (e: Exception) {
                internalLogger.log(
                    level = InternalLogger.Level.ERROR,
                    target = InternalLogger.Target.MAINTAINER,
                    messageBuilder = { "Failed to parse JSON for key: $key" },
                    throwable = e
                )
                defaultValue
            }
        } ?: defaultValue
    }

    override fun updateFlagsState(flags: Map<String, PrecomputedFlag>) {
        featureFlagState.set(flags)
    }
}
