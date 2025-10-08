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
     * Configuration for a Datadog site endpoint.
     * @param dc The datacenter identifier (e.g., "us3", "ap1"), null if not needed
     * @param tld The top-level domain (e.g., "com", "eu"), defaults to "com"
     */
    internal data class SiteConfig(
        val dc: String? = null,
        val tld: String = "com"
    )

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
        in siteConfig -> {
            val siteConfiguration = siteConfig[site]
            if (siteConfiguration != null) {
                val dc = siteConfiguration.dc ?: ""
                val tld = siteConfiguration.tld

                // customer domain is for future use
                // ff-cdn is the subdomain pointing to the CDN servers
                // dc is the datacenter, if specified
                // tld is the top level domain, changes for eu DCs
                "$customerDomain.ff-cdn.${if (dc.isNotEmpty()) "$dc." else ""}datadoghq.$tld"
            } else {
                // This should never happen since we're in the 'in siteConfig' branch,
                // but handle it safely just in case
                internalLogger.log(
                    level = InternalLogger.Level.ERROR,
                    target = InternalLogger.Target.MAINTAINER,
                    messageBuilder = { "Site configuration unexpectedly null for site: $site" }
                )
                null
            }
        }
        else -> {
            val supportedSites = siteConfig.keys.joinToString(", ")
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                messageBuilder = { "Unsupported site: $site. Supported sites: $supportedSites" }
            )
            null
        }
    }

    private val siteConfig: Map<DatadogSite, SiteConfig> = mapOf(
        DatadogSite.US1 to SiteConfig(), // us1 host is customer-domain.ff-cdn.datadoghq.com, so no DC param needed
        DatadogSite.US3 to SiteConfig(dc = "us3"),
        DatadogSite.US5 to SiteConfig(dc = "us5"),
        DatadogSite.AP1 to SiteConfig(dc = "ap1"),
        DatadogSite.AP2 to SiteConfig(dc = "ap2"),
        DatadogSite.EU1 to SiteConfig(tld = "eu")
    )

    private const val ERROR_GOV_NOT_SUPPORTED = "ddog-gov.com is not supported for flagging endpoints"
    private const val FLAGS_ENDPOINT = "/precompute-assignments"
}
