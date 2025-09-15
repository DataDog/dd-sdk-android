/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal

import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.featureflags.FlagsProvider
import com.datadog.android.flags.featureflags.ProviderContext
import com.datadog.android.flags.featureflags.internal.repository.DefaultFlagsRepository
import com.datadog.android.flags.featureflags.internal.repository.FlagsRepository
import com.datadog.android.flags.featureflags.internal.repository.NoOpFlagsRepository
import com.datadog.android.flags.internal.model.FlagsContext
import org.json.JSONObject
import java.util.concurrent.ExecutorService

internal class DatadogFlagsProvider(
    private val executorService: ExecutorService,
    private val configuration: FlagsConfiguration,
    private val featureSdkCore: FeatureSdkCore,
    private val flagsContext: FlagsContext,
    private var flagsRepository: FlagsRepository = NoOpFlagsRepository()
): FlagsProvider {

    init {
        flagsRepository = DefaultFlagsRepository(
            featureSdkCore = featureSdkCore,
            executorService = executorService,
            flagsContext = flagsContext
        )
    }

    override fun setContext(providerContext: ProviderContext) {
        TODO("Not yet implemented")
    }

    override fun resolveBooleanValue(
        flagKey: String,
        defaultValue: Boolean
    ): Boolean =
        flagsRepository.getBoolean(flagKey, defaultValue)

    override fun resolveStringValue(
        flagKey: String,
        defaultValue: String
    ): String =
        flagsRepository.getString(flagKey, defaultValue)

    override fun resolveIntValue(flagKey: String, defaultValue: Int): Int =
        flagsRepository.getInt(flagKey, defaultValue)

    override fun resolveNumberValue(
        flagKey: String,
        defaultValue: Number
    ): Number =
        flagsRepository.getDouble(flagKey, defaultValue.toDouble())

    override fun resolveStructureValue(
        flagKey: String,
        defaultValue: JSONObject
    ): JSONObject =
        flagsRepository.getJsonObject(flagKey, defaultValue)

    private fun notifyParameterExposed(flagKey: String) {
//        val event = ExposureEvent(
//            timeStamp = System.currentTimeMillis(),
//            flagKey = flagKey,
//            //TODO remaining params
//        )

//        featureSdkCore.getFeature(Feature.RUM_FEATURE_NAME)?.sendEvent(
//            mapOf(
//                "dd_exposure" to event
//            )
//        )
    }
}