/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.repository.net

import com.datadog.android.api.InternalLogger
import org.json.JSONException
import org.json.JSONObject

internal class EndpointsHelper(private val internalLogger: InternalLogger) {

    internal fun buildEndpointHost(site: String, customerDomain: String = "preview"): String = when (site) {
        DOMAIN_GOV -> {
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                messageBuilder = { ERROR_GOV_NOT_SUPPORTED }
            )
            ""
        }
        DOMAIN_D0G -> {
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
                ""
            }
        }
        else -> {
            val supportedSites = siteConfig.keys.joinToString(", ")
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                messageBuilder = { "Unsupported site: $site. Supported sites: $supportedSites" }
            )
            ""
        }
    }

    private val siteConfig: Map<String, JSONObject> = mapOf(
        "US1" to JSONObject(),
        "US3" to createSiteConfigObject(dc = "us3"),
        "US5" to createSiteConfigObject(dc = "us5"),
        "AP1" to createSiteConfigObject(dc = "ap1"),
        "AP2" to createSiteConfigObject(dc = "ap2"),
        "EU" to createSiteConfigObject(tld = "eu")
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
        internal const val DOMAIN_GOV = "ddog-gov.com"
        internal const val DOMAIN_D0G = "datad0g.com"
        private const val ERROR_GOV_NOT_SUPPORTED = "ddog-gov.com is not supported for flagging endpoints"
    }
}
