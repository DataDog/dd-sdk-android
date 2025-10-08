/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.DatadogSite
import com.datadog.android.api.InternalLogger

/**
 * Gets the complete flags endpoint URL.
 * @param customerDomain The customer-specific subdomain prefix for the flags CDN (default: "preview").
 * This is used to construct the full host in the format: `<customerDomain>.ff-cdn.<site>.<tld>`
 * @param internalLogger Logger for errors
 * @return Complete flags endpoint URL or null if site not supported or customerDomain is empty
 */
@Suppress("ReturnCount")
internal fun DatadogSite.getFlagsEndpoint(customerDomain: String = "preview", internalLogger: InternalLogger): String? {
    if (customerDomain.isBlank()) {
        internalLogger.log(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.MAINTAINER,
            messageBuilder = { "Customer domain cannot be empty for flags endpoint" }
        )
        return null
    }
    val host = flagsHost(customerDomain, internalLogger) ?: return null
    return "https://$host$FLAGS_PATH"
}

/**
 * Extension function that returns the flags CDN host for this Datadog site.
 * @param customerDomain The customer-specific subdomain prefix for the flags CDN
 * @param internalLogger Logger for errors
 * @return Flags host string in format `<customerDomain>.ff-cdn.<site>.<tld>`, or null if site not supported
 */
private fun DatadogSite.flagsHost(customerDomain: String, internalLogger: InternalLogger): String? = when (this) {
    DatadogSite.US1_FED -> {
        internalLogger.log(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.MAINTAINER,
            messageBuilder = { "US1_FED is not yet supported for flags endpoints" }
        )
        null
    }

    DatadogSite.STAGING -> "$customerDomain.ff-cdn.datad0g.com"
    DatadogSite.EU1 -> buildFlagsHostString(customerDomain, tld = "eu")
    DatadogSite.US1 -> buildFlagsHostString(customerDomain)

    DatadogSite.US3 -> buildFlagsHostString(customerDomain, "us3", "com")
    DatadogSite.US5 -> buildFlagsHostString(customerDomain, "us5", "com")
    DatadogSite.AP1 -> buildFlagsHostString(customerDomain, "ap1", "com")
    DatadogSite.AP2 -> buildFlagsHostString(customerDomain, "ap2", "com")
}

private fun buildFlagsHostString(customerDomain: String, dc: String? = null, tld: String = "com"): String {
    val dcPiece = if (dc?.isNotEmpty() == true) {
        "$dc."
    } else {
        ""
    }

    return "$customerDomain.ff-cdn.${dcPiece}datadoghq.$tld"
}

private const val FLAGS_PATH = "/precompute-assignments"
