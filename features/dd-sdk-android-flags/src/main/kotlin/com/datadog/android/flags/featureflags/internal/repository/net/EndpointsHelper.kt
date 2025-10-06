/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.repository.net

import com.datadog.android.DatadogSite
import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.featureflags.internal.model.FlagsContext
import org.json.JSONException
import org.json.JSONObject

internal class EndpointsHelper(private val flagsContext: FlagsContext, private val internalLogger: InternalLogger) {

    /**
     * Gets the endpoint URL for feature flag requests.
     * Uses the configured flagging proxy URL if provided, otherwise builds from the Datadog site.
     *
     * @return The complete endpoint URL for flagging requests, or null if the endpoint cannot be built.
     */
    internal fun getFlaggingEndpoint(): String? {
        // Use flagging proxy URL if provided, otherwise build from site
        return flagsContext.flaggingProxyUrl
            ?: buildEndpointHost(flagsContext.site)?.let { it ->
                "https://$it$FLAGS_ENDPOINT"
            }
    }

    internal fun buildEndpointHost(site: DatadogSite, customerDomain: String = "preview"): String? = when (site) {
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
                val dc = siteConfiguration.optString("dc", "")
                val tld = siteConfiguration.optString("tld", "com")

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

    private val siteConfig: Map<DatadogSite, JSONObject> = mapOf(
        DatadogSite.US1 to JSONObject(), // us1 host is customer-domain.ff-cdn.datadoghq.com, so no for a DC param.
        DatadogSite.US3 to createSiteConfigObject(dc = "us3"),
        DatadogSite.US5 to createSiteConfigObject(dc = "us5"),
        DatadogSite.AP1 to createSiteConfigObject(dc = "ap1"),
        DatadogSite.AP2 to createSiteConfigObject(dc = "ap2"),
        DatadogSite.EU1 to createSiteConfigObject(tld = "eu")
    )

    private fun createSiteConfigObject(dc: String? = null, tld: String? = null): JSONObject = try {
        JSONObject().apply {
            dc?.let { put("dc", it) }
            tld?.let { put("tld", it) }
        }
    } catch (_: JSONException) {
        // should never happen
        @Suppress("TodoWithoutTask")
        // TODO log this?
        JSONObject()
    }

    internal companion object {
        private const val ERROR_GOV_NOT_SUPPORTED = "ddog-gov.com is not supported for flagging endpoints"
        private const val FLAGS_ENDPOINT = "/precompute-assignments"
    }
}
