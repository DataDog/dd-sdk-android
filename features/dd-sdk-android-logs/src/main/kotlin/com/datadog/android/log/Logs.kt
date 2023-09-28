/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log

import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.log.internal.LogsFeature

/**
 * An entry point to Datadog Logs feature.
 */
object Logs {

    /**
     * Enables a Logs feature based on the configuration provided.
     *
     * @param logsConfiguration Configuration to use for the feature.
     * @param sdkCore SDK instance to register feature in. If not provided, default SDK instance
     * will be used.
     */
    @JvmOverloads
    @JvmStatic
    fun enable(logsConfiguration: LogsConfiguration, sdkCore: SdkCore = Datadog.getInstance()) {
        val logsFeature = LogsFeature(
            sdkCore = sdkCore as FeatureSdkCore,
            customEndpointUrl = logsConfiguration.customEndpointUrl,
            eventMapper = logsConfiguration.eventMapper
        )

        sdkCore.registerFeature(logsFeature)
    }

    /**
     * Identify whether a [Logs] has been enabled for the given SDK instance.
     *
     * This check is useful in scenarios where more than one component may be responsible
     * for enabling the feature
     *
     * @param sdkCore the [SdkCore] instance to check against. If not provided, default instance
     * will be checked.
     * @return whether Logs has been enabled
     */
    @JvmOverloads
    @JvmStatic
    fun isEnabled(sdkCore: SdkCore = Datadog.getInstance()): Boolean {
        return (sdkCore as FeatureSdkCore).getFeature(Feature.LOGS_FEATURE_NAME) != null
    }
}
