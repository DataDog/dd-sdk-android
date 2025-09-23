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
import com.datadog.android.flags.featureflags.FlagsClientManager
import com.datadog.android.flags.featureflags.FlagsClient
import com.datadog.android.flags.featureflags.FlagsClientConfiguration
import com.datadog.android.flags.featureflags.internal.DatadogFlagsClient
import com.datadog.android.flags.featureflags.internal.model.FlagsContext
import com.datadog.android.flags.internal.FlagsFeature

/**
 * Entry point for the Flags feature.
 */
object Flags {

    internal const val FLAGS_EXECUTOR_NAME = "flags-executor"

    /**
     * Enables the Flags feature with default configuration.
     */
    @JvmStatic
    fun enable() {
        enable(FlagsConfiguration.DEFAULT_FEATURE_FLAGS_CONFIG, Datadog.getInstance())
    }

    /**
     * Enables the Flags feature with custom configuration.
     *
     * @param configuration configuration to use with feature flags and experiments.
     */
    @JvmStatic
    fun enable(configuration: FlagsConfiguration) {
        enable(configuration, Datadog.getInstance())
    }

    /**
     * Enables the Flags feature with custom configuration and SDK core.
     *
     * @param configuration configuration to use with feature flags and experiments.
     * @param sdkCore SDK instance to register feature in.
     */
    @JvmStatic
    fun enable(
        configuration: FlagsConfiguration,
        sdkCore: SdkCore
    ) {
        val flagsFeature = FlagsFeature(
            sdkCore as FeatureSdkCore
        )

        sdkCore.registerFeature(flagsFeature)

        createProvider(configuration, sdkCore, flagsFeature)?.let {
            FlagsClientManager.registerIfAbsent(
                client = it,
                sdkCore
            )
        }
    }

    private fun createProvider(
        configuration: FlagsConfiguration,
        sdkCore: FeatureSdkCore,
        flagsFeature: FlagsFeature
    ): FlagsClient? {
        val executorService = sdkCore.createSingleThreadExecutorService(
            executorContext = FLAGS_EXECUTOR_NAME
        )

        val datadogContext = (sdkCore as? InternalSdkCore)?.getDatadogContext()
        val internalLogger = sdkCore.internalLogger
        val applicationId = flagsFeature.applicationId
        val clientToken = datadogContext?.clientToken
        val site = datadogContext?.site?.name
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
            env = env,
            customExposureEndpoint = configuration.customExposureEndpoint,
            flaggingProxy = configuration.flaggingProxy
        )

        return DatadogFlagsClient(
            executorService = executorService,
            featureSdkCore = sdkCore,
            flagsContext = flagsContext,
            configuration = FlagsClientConfiguration.DEFAULT
        )
    }
}
