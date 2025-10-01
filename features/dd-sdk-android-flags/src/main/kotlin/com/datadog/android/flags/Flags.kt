/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.flags.featureflags.FlagsClient
import com.datadog.android.flags.featureflags.FlagsProvider
import com.datadog.android.flags.featureflags.internal.DatadogFlagsProvider
import com.datadog.android.flags.featureflags.internal.evaluation.EvaluationsManager
import com.datadog.android.flags.featureflags.internal.model.FlagsContext
import com.datadog.android.flags.featureflags.internal.repository.DefaultFlagsRepository
import com.datadog.android.flags.featureflags.internal.repository.net.DefaultFlagsNetworkManager
import com.datadog.android.flags.featureflags.internal.repository.net.PrecomputeMapper
import com.datadog.android.flags.internal.FlagsFeature

/**
 * Entry point for the Flags feature.
 */
object Flags {

    internal const val FLAGS_EXECUTOR_NAME = "flags-executor"

    /**
     * Enables the Flags feature.
     *
     * @param configuration configuration to use with feature flags and experiments. If not provided, a default
     * configuration will be used.
     * @param sdkCore SDK instance to register feature in. If not provided, a default SDK instance
     * will be used.
     */
    @JvmOverloads
    @JvmStatic
    fun enable(
        @Suppress("UNUSED_PARAMETER") configuration: FlagsConfiguration,
        sdkCore: SdkCore = Datadog.getInstance()
    ) {
        val flagsFeature = FlagsFeature(
            sdkCore as FeatureSdkCore
        )

        sdkCore.registerFeature(flagsFeature)

        createProvider(sdkCore, flagsFeature)?.let {
            FlagsClient.registerIfAbsent(
                provider = it,
                sdkCore
            )
        }
    }

    private fun createProvider(sdkCore: FeatureSdkCore, flagsFeature: FlagsFeature): FlagsProvider? {
        val executorService = sdkCore.createSingleThreadExecutorService(
            executorContext = FLAGS_EXECUTOR_NAME
        )

        val datadogContext = (sdkCore as? InternalSdkCore)?.getDatadogContext()
        val internalLogger = sdkCore.internalLogger
        val applicationId = flagsFeature.applicationId
        val clientToken = datadogContext?.clientToken
        val site = datadogContext?.site
        val env = datadogContext?.env

        @Suppress("TodoWithoutTask") // TODO how do we want to handle this?
        if (clientToken == null || site == null || env == null) {
            val missingParams = listOfNotNull(
                "clientToken".takeIf { clientToken == null },
                "site".takeIf { site == null },
                "env".takeIf { env == null }
            ).joinToString(", ")

            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Missing required context parameters: $missingParams" }
            )

            return null
        }

        val flagsContext = FlagsContext(
            applicationId = applicationId,
            clientToken = clientToken,
            site = site,
            env = env
        )

        val flagsRepository = DefaultFlagsRepository(
            featureSdkCore = sdkCore
        )

        val flagsNetworkManager = DefaultFlagsNetworkManager(
            internalLogger = sdkCore.internalLogger,
            flagsContext = flagsContext
        )

        val precomputeMapper = PrecomputeMapper(sdkCore.internalLogger)

        val evaluationsManager = EvaluationsManager(
            executorService = executorService,
            internalLogger = sdkCore.internalLogger,
            flagsRepository = flagsRepository,
            flagsNetworkManager = flagsNetworkManager,
            precomputeMapper = precomputeMapper
        )

        return DatadogFlagsProvider(
            featureSdkCore = sdkCore,
            evaluationsManager = evaluationsManager,
            flagsRepository = flagsRepository
        )
    }
}
