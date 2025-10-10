/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.model

import com.datadog.android.DatadogSite
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.flags.FlagsConfiguration

/**
 * Internal context containing all configuration needed for the Flags feature.
 *
 * This serves as the single source of truth for both core SDK parameters and feature-level configuration.
 *
 * @param applicationId The Datadog application ID. May be null when the SDK is not fully initialized
 *                      or when running in certain test environments where app ID is not required.
 * @param clientToken The client token for authenticating requests to Datadog
 * @param site The Datadog site (e.g., US1, EU1) for routing requests
 * @param env The environment name (e.g., prod, staging) for context
 * @param customExposureEndpoint Custom endpoint URL for uploading exposure events. If null, the default endpoint will be used.
 */
internal data class FlagsContext(
    val applicationId: String?,
    val clientToken: String,
    val site: DatadogSite,
    val env: String,
    val customExposureEndpoint: String? = null
) {
    companion object {
        /**
         * Creates a [FlagsContext] from core SDK context and feature configuration.
         *
         * @param datadogContext The core SDK context containing authentication and routing info
         * @param applicationId The application ID (may be null if RUM context not yet available)
         * @param flagsConfiguration The feature-level configuration from user
         * @return A complete [FlagsContext] combining core and feature configuration
         */
        fun create(
            datadogContext: DatadogContext,
            applicationId: String?,
            flagsConfiguration: FlagsConfiguration
        ): FlagsContext = FlagsContext(
            applicationId = applicationId,
            clientToken = datadogContext.clientToken,
            site = datadogContext.site,
            env = datadogContext.env,
            customExposureEndpoint = flagsConfiguration.customExposureEndpoint
        )
    }
}
