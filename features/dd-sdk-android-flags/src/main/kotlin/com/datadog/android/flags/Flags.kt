/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.featureflags.FlagsClient
import com.datadog.android.flags.featureflags.FlagsProvider
import com.datadog.android.flags.featureflags.internal.DatadogFlagsProvider
import com.datadog.android.flags.internal.FlagsFeature
import com.datadog.android.flags.internal.model.FlagsContext

/**
 * Entry point for the Flags feature
 */
object Flags {

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
        configuration: FlagsConfiguration,
        sdkCore: SdkCore = Datadog.getInstance()
    ) {
        val flagsFeature = FlagsFeature(
            sdkCore as FeatureSdkCore,
            configuration = configuration
        )

        sdkCore.registerFeature(flagsFeature)

        createProvider(sdkCore, flagsFeature, configuration)?.let {
            FlagsClient.registerIfAbsent(
                provider = it,
                sdkCore
            )
        }
    }

    private fun createProvider(
        sdkCore: FeatureSdkCore,
        flagsFeature: FlagsFeature,
        configuration: FlagsConfiguration
    ): FlagsProvider? {
        val executorService = sdkCore.createSingleThreadExecutorService(
            executorContext = "flags-executor"
        )

        val applicationId = flagsFeature.applicationId
        val clientToken = flagsFeature.clientToken
        val site = flagsFeature.site
        val env = flagsFeature.env

        if (clientToken == null || site == null || env == null) {
            // TODO: do something
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
            configuration = configuration,
            featureSdkCore = sdkCore,
            flagsContext = flagsContext
        )
    }
}
