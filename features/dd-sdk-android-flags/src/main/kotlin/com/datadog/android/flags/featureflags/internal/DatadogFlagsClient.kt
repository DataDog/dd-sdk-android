/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.featureflags.FlagsClient
import com.datadog.android.flags.featureflags.FlagsClientConfiguration
import com.datadog.android.flags.featureflags.model.EvaluationDetails
import com.datadog.android.flags.featureflags.internal.evaluation.EvaluationsManager
import com.datadog.android.flags.featureflags.internal.model.FlagsContext
import com.datadog.android.flags.featureflags.internal.repository.DefaultFlagsRepository
import com.datadog.android.flags.featureflags.internal.repository.FlagsRepository
import com.datadog.android.flags.featureflags.internal.repository.NoOpFlagsRepository
import com.datadog.android.flags.featureflags.internal.repository.net.DefaultFlagsNetworkManager
import com.datadog.android.flags.featureflags.internal.repository.net.PrecomputeMapper
import com.datadog.android.flags.featureflags.model.EvaluationContext
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.ExecutorService

@Suppress("TooManyFunctions") // Required by mobile API spec
internal class DatadogFlagsClient(
    private val executorService: ExecutorService,
    private val featureSdkCore: FeatureSdkCore,
    private val flagsContext: FlagsContext,
    @Suppress("UnusedPrivateProperty") // Will be used for client-level configuration features
    private val configuration: FlagsClientConfiguration = FlagsClientConfiguration.DEFAULT,
    private var flagsRepository: FlagsRepository = NoOpFlagsRepository()
) : FlagsClient {

    private val evaluationsManager: EvaluationsManager

    init {
        if (flagsRepository is NoOpFlagsRepository) {
            flagsRepository = DefaultFlagsRepository(
                featureSdkCore = featureSdkCore
            )
        }

        // Create orchestrator with network manager and dependencies
        val flagsNetworkManager = DefaultFlagsNetworkManager(
            internalLogger = featureSdkCore.internalLogger,
            flagsContext = flagsContext
        )

        val precomputeMapper = PrecomputeMapper(featureSdkCore.internalLogger)

        // The orchestrator handles taking the EvaluationContext and setting new evaluations into the FlagRepository
        evaluationsManager = EvaluationsManager(
            executorService = executorService,
            internalLogger = featureSdkCore.internalLogger,
            flagsRepository = flagsRepository,
            flagsNetworkManager = flagsNetworkManager,
            precomputeMapper = precomputeMapper
        )
    }

    @Suppress("TooGenericExceptionCaught") // Need to catch any runtime exception from network/storage
    override fun setEvaluationContext(evaluationContext: EvaluationContext) {
        // Validate targeting key before proceeding
        if (evaluationContext.targetingKey.isBlank()) {
            featureSdkCore.internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.USER,
                messageBuilder = { "Cannot set context: targeting key cannot be blank" }
            )
            return
        }

        try {
            // Pass to orchestrator to handle network request and atomic storage
            evaluationsManager.updateEvaluationsForContext(evaluationContext)
        } catch (e: RuntimeException) {
            featureSdkCore.internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.USER,
                messageBuilder = { "Failed to set context: ${e.message}" },
                throwable = e
            )
        }
    }

    override fun getBooleanValue(flagKey: String, defaultValue: Boolean): Boolean {
        val precomputedFlag = flagsRepository.getPrecomputedFlag(flagKey)
        return precomputedFlag?.variationValue?.toBooleanStrictOrNull() ?: defaultValue
    }

    override fun getStringValue(flagKey: String, defaultValue: String): String {
        val precomputedData = flagsRepository.getPrecomputedFlag(flagKey)
        return precomputedData?.variationValue ?: defaultValue
    }

    override fun getIntValue(flagKey: String, defaultValue: Int): Int {
        val precomputedData = flagsRepository.getPrecomputedFlag(flagKey)
        return precomputedData?.variationValue?.toIntOrNull() ?: defaultValue
    }

    override fun getNumberValue(flagKey: String, defaultValue: Number): Number {
        val precomputedData = flagsRepository.getPrecomputedFlag(flagKey)
        return precomputedData?.variationValue?.toDoubleOrNull() ?: defaultValue
    }

    override fun getStructureValue(flagKey: String, defaultValue: JSONObject): JSONObject {
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

    override fun getBooleanDetails(flagKey: String, defaultValue: Boolean): EvaluationDetails {
        return flagsRepository.getPrecomputedFlag(flagKey)?.asEvaluationDetails(flagKey)
            ?: EvaluationDetails.defaultValue(flagKey, defaultValue)
    }

    override fun getStringDetails(flagKey: String, defaultValue: String): EvaluationDetails {
        return flagsRepository.getPrecomputedFlag(flagKey)?.asEvaluationDetails(flagKey)
            ?: EvaluationDetails.defaultValue(flagKey, defaultValue)
    }

    override fun getNumberDetails(flagKey: String, defaultValue: Number): EvaluationDetails {
        return flagsRepository.getPrecomputedFlag(flagKey)?.asEvaluationDetails(flagKey)
            ?: EvaluationDetails.defaultValue(flagKey, defaultValue)
    }

    override fun getIntDetails(flagKey: String, defaultValue: Int): EvaluationDetails {
        return flagsRepository.getPrecomputedFlag(flagKey)?.asEvaluationDetails(flagKey)
            ?: EvaluationDetails.defaultValue(flagKey, defaultValue)
    }

    override fun getStructureDetails(flagKey: String, defaultValue: JSONObject): EvaluationDetails {
        return flagsRepository.getPrecomputedFlag(flagKey)?.asEvaluationDetails(flagKey)
            ?: EvaluationDetails.defaultValue(flagKey, defaultValue)
    }
}
