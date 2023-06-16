/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log

import com.datadog.android.Datadog
import com.datadog.android.log.internal.LogsFeature
import com.datadog.android.v2.api.FeatureSdkCore
import com.datadog.android.v2.api.SdkCore

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
}
