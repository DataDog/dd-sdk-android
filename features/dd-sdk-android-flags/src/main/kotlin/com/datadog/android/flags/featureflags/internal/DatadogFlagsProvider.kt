/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.featureflags.FlagsProvider
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

internal class DatadogFlagsProvider(
    private val executorService: ExecutorService,
    private val featureSdkCore: FeatureSdkCore,
    private val flagsContext: FlagsContext,
    private var flagsRepository: FlagsRepository = NoOpFlagsRepository()
) : FlagsProvider {

    private val flagsOrchestrator: EvaluationsManager

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
        flagsOrchestrator = EvaluationsManager(
            executorService = executorService,
            internalLogger = featureSdkCore.internalLogger,
            flagsRepository = flagsRepository,
            flagsNetworkManager = flagsNetworkManager,
            precomputeMapper = precomputeMapper
        )
    }

    override fun setContext(evaluationContext: EvaluationContext) {
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
            flagsOrchestrator.updateEvaluationsForContext(evaluationContext)
        } catch (e: Exception) {
            featureSdkCore.internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.USER,
                messageBuilder = { "Failed to set context: ${e.message}" },
                throwable = e
            )
        }
    }

    override fun resolveBooleanValue(flagKey: String, defaultValue: Boolean): Boolean {
        val precomputedFlag = flagsRepository.getPrecomputedFlag(flagKey)
        return precomputedFlag?.variationValue?.toBooleanStrictOrNull() ?: defaultValue
    }

    override fun resolveStringValue(flagKey: String, defaultValue: String): String {
        val precomputedData = flagsRepository.getPrecomputedFlag(flagKey)
        return precomputedData?.variationValue ?: defaultValue
    }

    override fun resolveIntValue(flagKey: String, defaultValue: Int): Int {
        val precomputedData = flagsRepository.getPrecomputedFlag(flagKey)
        return precomputedData?.variationValue?.toIntOrNull() ?: defaultValue
    }

    override fun resolveNumberValue(flagKey: String, defaultValue: Number): Number {
        val precomputedData = flagsRepository.getPrecomputedFlag(flagKey)
        return precomputedData?.variationValue?.toDoubleOrNull() ?: defaultValue
    }

    override fun resolveStructureValue(flagKey: String, defaultValue: JSONObject): JSONObject {
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
