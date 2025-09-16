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
import com.datadog.android.flags.internal.FlagsFeature
import com.datadog.android.flags.internal.model.FlagsContext

/**
 * Entry point for the Flags feature
 */
object Flags {

    internal const val FLAGS_EXECUTOR_NAME = "flags-executor"
    internal const val ERROR_MISSING_CONTEXT_PARAMS = "Missing required context parameters: %s"

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
    @Suppress("UnusedPrivateMember") // todo: remove this when we start using the config
    fun enable(
        configuration: FlagsConfiguration,
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

    private fun createProvider(
        sdkCore: FeatureSdkCore,
        flagsFeature: FlagsFeature
    ): FlagsProvider? {
        val executorService = sdkCore.createSingleThreadExecutorService(
            executorContext = FLAGS_EXECUTOR_NAME
        )

        val datadogContext = (sdkCore as? InternalSdkCore)?.getDatadogContext()
        val internalLogger = sdkCore.internalLogger
        val applicationId = flagsFeature.applicationId
        val clientToken = datadogContext?.clientToken
        val site = datadogContext?.site?.name
        val env = datadogContext?.env

        if (clientToken == null || site == null || env == null) { // TODO how do we want to handle this?
            val missingParams = buildList {
                if (clientToken == null) add("clientToken")
                if (site == null) add("site")
                if (env == null) add("env")
            }.joinToString(", ")

            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { ERROR_MISSING_CONTEXT_PARAMS.format(missingParams) }
            )

            return null
        }

        val flagsContext = FlagsContext(
            applicationId = applicationId,
            clientToken = clientToken,
            site = site,
            env = env,
            targetingKey = "test_subject" // todo replace later
        )

        return DatadogFlagsProvider(
            executorService = executorService,
            featureSdkCore = sdkCore,
            flagsContext = flagsContext
        )
    }
}
