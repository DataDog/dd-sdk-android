/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.configuration

import com.datadog.android.core.internal.utils.devLogger
import java.net.MalformedURLException
import java.net.URL
import java.util.Locale

internal class HostsSanitizer {

    internal fun sanitizeHosts(
        hosts: List<String>,
        feature: String,
    ): List<String> {
        val validHostNameRegEx = Regex(VALID_HOSTNAME_REGEX)
        val validUrlRegex = Regex(URL_REGEX)
        return hosts.mapNotNull {
            if (it.matches(validUrlRegex)) {
                try {
                    val parsedUrl = URL(it)
                    devLogger.w(
                        WARNING_USING_URL.format(
                            Locale.US,
                            it,
                            feature,
                            parsedUrl.host
                        )
                    )
                    parsedUrl.host
                } catch (e: MalformedURLException) {
                    devLogger.e(ERROR_MALFORMED_URL.format(Locale.US, it, feature), e)
                    null
                }
            } else if (it.matches(validHostNameRegEx)) {
                it
            } else if (it.lowercase() == "localhost") {
                // special rule exception to accept `localhost` as a valid domain name
                it
            } else {
                devLogger.e(ERROR_MALFORMED_HOST_IP_ADDRESS.format(Locale.US, it, feature))
                null
            }
        }
    }

    internal companion object {
        private const val VALID_IP_REGEX: String =
            "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}" +
                "([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$"
        private const val VALID_DOMAIN_REGEX: String =
            "^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)+" +
                "([A-Za-z]|[A-Za-z][A-Za-z0-9-]*[A-Za-z0-9])$"
        private const val VALID_HOSTNAME_REGEX: String = "$VALID_IP_REGEX|$VALID_DOMAIN_REGEX"
        private const val URL_REGEX: String = "^(http|https)://(.*)"

        internal const val WARNING_USING_URL: String =
            "You are using a url \"%s\" instead of a host to setup %s tracking. " +
                "You should use instead a valid host name: \"%s\""

        internal const val ERROR_MALFORMED_URL: String = "You are using a malformed url \"%s\" " +
            "to setup %s tracking. It will be dropped. " +
            "Please try using a host name instead, e.g.: \"example.com\""

        internal const val ERROR_MALFORMED_HOST_IP_ADDRESS: String =
            "You are using a malformed host or ip address \"%s\" to setup %s tracking. " +
                "It will be dropped."
    }
}
