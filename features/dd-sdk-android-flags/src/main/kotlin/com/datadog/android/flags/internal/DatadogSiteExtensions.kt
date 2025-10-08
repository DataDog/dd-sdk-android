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
 * @param customerDomain Customer domain prefix (default: "preview")
 * @param internalLogger Logger for errors
 * @return Complete flags endpoint URL or null if site not supported
 */
internal fun DatadogSite.getFlagsEndpoint(customerDomain: String = "preview", internalLogger: InternalLogger): String? {
    val host = flagsHost(customerDomain, internalLogger) ?: return null
    return "https://$host$FLAGS_PATH"
}

/**
 * Extension function that returns the flags CDN host for this Datadog site.
 * @param customerDomain Customer domain prefix
 * @param internalLogger Logger for errors
 * @return Flags host string or null if not supported
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

/**
 * Constructs the full flags host string from components.
 */
private fun buildFlagsHostString(customerDomain: String, dc: String? = null, tld: String = "com"): String {
    val dcPiece = if (dc?.isNotEmpty() == true) {
        "$dc."
    } else {
        ""
    }

    return "$customerDomain.ff-cdn.${dcPiece}datadoghq.$tld"
}

private const val FLAGS_PATH = "/precompute-assignments"
