/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log

import android.content.Context

class Logger
private constructor(
    val clientToken: String,
    val serviceName: String,
    val timestampsEnabled: Boolean,
    val userAgentEnabled: Boolean,
    val datadogLogsEnabled: Boolean,
    val logcatLogsEnabled: Boolean,
    val networkInfoEnabled: Boolean
) {

    // TODO xgouchet 2019/11/5 allow overriding the user agent ?
    private val userAgent: String = System.getProperty("http.agent").orEmpty()

    // region Builder

    /**
     * A Builder class for a [Logger].
     * @param context the application context
     * @param clientToken your API key of type Client Token
     */
    class Builder(
        context: Context,
        private val clientToken: String
    ) {

        private val withContext: Context = context.applicationContext
        private var serviceName: String = DEFAULT_SERVICE_NAME
        private var timestampsEnabled: Boolean = true
        private var userAgentEnabled: Boolean = true
        private var datadogLogsEnabled: Boolean = true
        private var logcatLogsEnabled: Boolean = false
        private var networkInfoEnabled: Boolean = false

        /**
         * Builds a [Logger] based on the current state of this Builder.
         */
        fun build(): Logger {

            // TODO register broadcast receiver

            return Logger(
                clientToken = clientToken,
                serviceName = serviceName,
                timestampsEnabled = timestampsEnabled,
                userAgentEnabled = userAgentEnabled,
                datadogLogsEnabled = datadogLogsEnabled,
                logcatLogsEnabled = logcatLogsEnabled,
                networkInfoEnabled = networkInfoEnabled
            )
        }

        /**
         * Sets the service name that will appear in your logs.
         * @param serviceName the service name (default = "android")
         */
        fun setServiceName(serviceName: String): Builder {
            this.serviceName = serviceName
            return this
        }

        /**
         * Enables timestamp to be automatically added in your logs.
         * @param enabled true by default
         */
        fun setTimestampsEnabled(enabled: Boolean): Builder {
            timestampsEnabled = enabled
            return this
        }

        /**
         * Enables the system User Agent to be automatically added in your logs.
         * @param enabled true by default
         */
        fun setUserAgentEnabled(enabled: Boolean): Builder {
            userAgentEnabled = enabled
            return this
        }

        /**
         * Enables your logs to be sent to the Datadog servers.
         * You can use this feature to disable Datadog logs based on a configuration or an application flavor.
         * @param enabled true by default
         */
        fun setDatadogLogsEnabled(enabled: Boolean): Builder {
            datadogLogsEnabled = enabled
            return this
        }

        /**
         * Enables your logs to be duplicated in LogCat.
         * @param enabled false by default
         */
        fun setLogcatLogsEnabled(enabled: Boolean): Builder {
            logcatLogsEnabled = enabled
            return this
        }

        /**
         * Enables network information to be automatically added in your logs.
         * @param enabled false by default
         */
        fun setNetworkInfoEnabled(enabled: Boolean): Builder {
            networkInfoEnabled = enabled
            return this
        }
    }

    // endregion

    companion object {
        const val DEFAULT_SERVICE_NAME = "android"
    }
}
