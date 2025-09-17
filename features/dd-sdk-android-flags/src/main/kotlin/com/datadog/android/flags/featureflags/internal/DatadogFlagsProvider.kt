/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.featureflags.FlagsProvider
import com.datadog.android.flags.featureflags.internal.model.FlagsContext
import com.datadog.android.flags.featureflags.internal.repository.DefaultFlagsRepository
import com.datadog.android.flags.featureflags.internal.repository.FlagsRepository
import com.datadog.android.flags.featureflags.internal.repository.NoOpFlagsRepository
import com.datadog.android.flags.featureflags.model.ProviderContext
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.ExecutorService

internal class DatadogFlagsProvider(
    private val executorService: ExecutorService,
    private val featureSdkCore: FeatureSdkCore,
    private val flagsContext: FlagsContext,
    private var flagsRepository: FlagsRepository = NoOpFlagsRepository()
) : FlagsProvider {

    init {
        if (flagsRepository is NoOpFlagsRepository) {
            flagsRepository = DefaultFlagsRepository(
                featureSdkCore = featureSdkCore,
                executorService = executorService,
                flagsContext = flagsContext
            )
        }
    }

    override fun setContext(newContext: ProviderContext) {
        flagsRepository.updateProviderContext(newContext)
    }

    override fun resolveBooleanValue(
        flagKey: String,
        defaultValue: Boolean
    ): Boolean {
        val precomputedFlag = flagsRepository.getPrecomputedFlag(flagKey)
        return precomputedFlag?.variationValue?.toBooleanStrictOrNull() ?: defaultValue
    }

    override fun resolveStringValue(
        flagKey: String,
        defaultValue: String
    ): String {
        val precomputedData = flagsRepository.getPrecomputedFlag(flagKey)
        return precomputedData?.variationValue ?: defaultValue
    }

    override fun resolveIntValue(flagKey: String, defaultValue: Int): Int {
        val precomputedData = flagsRepository.getPrecomputedFlag(flagKey)
        return precomputedData?.variationValue?.toIntOrNull() ?: defaultValue
    }

    override fun resolveNumberValue(
        flagKey: String,
        defaultValue: Number
    ): Number {
        val precomputedData = flagsRepository.getPrecomputedFlag(flagKey)
        return precomputedData?.variationValue?.toDoubleOrNull() ?: defaultValue
    }

    override fun resolveStructureValue(
        flagKey: String,
        defaultValue: JSONObject
    ): JSONObject {
        val precomputedData = flagsRepository.getPrecomputedFlag(flagKey)
        return precomputedData?.variationValue?.let {
            try {
                JSONObject(it)
            } catch (e: JSONException) {
                featureSdkCore.internalLogger.log(
                    level = InternalLogger.Level.ERROR,
                    target = InternalLogger.Target.MAINTAINER,
                    messageBuilder = { "Failed to parse JSON for key: $flagKey" },
                    throwable = e
                )
                defaultValue
            }
        } ?: defaultValue
    }
}
