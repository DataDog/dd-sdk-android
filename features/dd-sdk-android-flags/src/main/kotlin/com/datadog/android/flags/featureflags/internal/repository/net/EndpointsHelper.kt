/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.repository.net

import com.datadog.android.DatadogSite
import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.featureflags.internal.model.FlagsContext

internal object EndpointsHelper {
    /**
     * Gets the endpoint URL for feature flag requests.
     * Uses the configured custom flag endpoint if provided, otherwise builds from the Datadog site.
     *
     * @param flagsContext The flags context containing site and custom endpoint configuration
     * @param internalLogger The logger for error reporting
     * @return The complete endpoint URL for flagging requests, or null if the endpoint cannot be built.
     */
    internal fun getFlaggingEndpoint(flagsContext: FlagsContext, internalLogger: InternalLogger): String? {
        // Use custom flag endpoint if provided, otherwise build from site
        return flagsContext.customFlagEndpoint
            ?: buildEndpointHost(flagsContext.site, internalLogger)?.let {
                "https://$it$FLAGS_ENDPOINT"
            }
    }

    internal fun buildEndpointHost(
        site: DatadogSite,
        internalLogger: InternalLogger,
        customerDomain: String = "preview"
    ): String? = when (site) {
        DatadogSite.US1_FED -> {
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                messageBuilder = { ERROR_GOV_NOT_SUPPORTED }
            )
            null
        }
        DatadogSite.STAGING -> {
            "$customerDomain.ff-cdn.datad0g.com"
        }
        DatadogSite.EU1 -> {
            buildHost(customerDomain, "", "eu")
        }
        DatadogSite.US1 -> {
            buildHost(customerDomain, "", "com")
        }
        DatadogSite.US3 -> {
            buildHost(customerDomain, "us3", "com")
        }
        DatadogSite.US5 -> {
            buildHost(customerDomain, "us5", "com")
        }
        DatadogSite.AP1 -> {
            buildHost(customerDomain, "ap1", "com")
        }
        DatadogSite.AP2 -> {
            buildHost(customerDomain, "ap2", "com")
        }
    }
    private fun buildHost(customerDomain: String, dc: String?, tld: String = "com"): String =
        "$customerDomain.ff-cdn.${if (dc?.isNotEmpty() == true) "$dc." else ""}datadoghq.$tld"

    private const val ERROR_GOV_NOT_SUPPORTED = "US1_FED is not yet supported for flagging endpoints"
    private const val FLAGS_ENDPOINT = "/precompute-assignments"
}
