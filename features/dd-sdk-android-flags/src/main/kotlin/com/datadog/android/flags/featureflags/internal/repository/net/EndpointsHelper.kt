/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.repository.net

import com.datadog.android.api.InternalLogger
import org.json.JSONObject

internal class EndpointsHelper(
    private val internalLogger: InternalLogger
) {

    internal fun buildEndpointHost(site: String, customerDomain: String = "preview"): String {
        if (site == DOMAIN_GOV) {
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                messageBuilder = { ERROR_GOV_NOT_SUPPORTED }
            )
            return ""
        }

        if (site == DOMAIN_D0G) {
            return "$customerDomain.ff-cdn.datad0g.com"
        }

        if (site in siteConfig) {
            val config = siteConfig[site]!!
            val dc = config.optString("dc", "")
            val tld = config.optString("tld", "com")

            // customer domain is for future use
            // ff-cdn is the subdomain pointing to the CDN servers
            // dc is the datacenter, if specified
            // tld is the top level domain, changes for eu DCs
            return "$customerDomain.ff-cdn.${if (dc.isNotEmpty()) "$dc." else ""}datadoghq.$tld"
        } else {
            val supportedSites = siteConfig.keys.joinToString(", ")
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                messageBuilder = { ERROR_UNSUPPORTED_SITE.format(site, supportedSites) }
            )
            return ""
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

    private fun createSiteConfigObject(dc: String? = null, tld: String? = null): JSONObject {
        return JSONObject().apply {
            dc?.let { put("dc", it) }
            tld?.let { put("tld", it) }
        }
    }

    private companion object {
        private const val DOMAIN_GOV = "ddog-gov.com"
        private const val DOMAIN_D0G = "datad0g.com"
        private const val ERROR_GOV_NOT_SUPPORTED = "ddog-gov.com is not supported for flagging endpoints"
        private const val ERROR_UNSUPPORTED_SITE = "Unsupported site: %s. Supported sites: %s"
    }
}
