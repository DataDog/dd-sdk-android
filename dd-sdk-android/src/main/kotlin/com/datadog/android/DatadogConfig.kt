/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import com.datadog.android.rum.internal.instrumentation.GesturesTrackingStrategy
import com.datadog.android.rum.internal.instrumentation.gestures.DatadogGesturesTracker
import com.datadog.android.rum.tracking.UserActionTrackingStrategy
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import java.util.UUID

/**
 * An object describing the configuration of the Datadog SDK.
 *
 * This is necessary to initialize the SDK with the [Datadog.initialize] method.
 */
class DatadogConfig
private constructor(
    internal val needsClearTextHttp: Boolean,
    internal val logsConfig: FeatureConfig?,
    internal val tracesConfig: FeatureConfig?,
    internal val crashReportConfig: FeatureConfig?,
    internal val rumConfig: RumConfig?
) {

    internal data class FeatureConfig(
        val clientToken: String,
        val applicationId: UUID,
        val endpointUrl: String,
        val serviceName: String,
        val envName: String
    )

    internal data class RumConfig(
        val clientToken: String,
        val applicationId: UUID,
        val endpointUrl: String,
        val serviceName: String,
        val envName: String,
        val userActionTrackingStrategy: UserActionTrackingStrategy? = null,
        val viewTrackingStrategy: ViewTrackingStrategy? = null
    )

    // region Builder

    /**
     * A Builder class for a [DatadogConfig].
     * @param clientToken your API key of type Client Token
     * @param applicationId your applicationId for RUM events
     */
    @Suppress("TooManyFunctions")
    class Builder(clientToken: String, applicationId: UUID) {

        /**
         * A Builder class for a [DatadogConfig].
         * @param clientToken your API key of type Client Token
         */
        constructor(clientToken: String) :
                this(clientToken, UUID(0, 0))

        /**
         * A Builder class for a [DatadogConfig].
         * @param clientToken your API key of type Client Token
         * @param applicationId your applicationId for RUM events
         */
        constructor(clientToken: String, applicationId: String) :
                this(clientToken, UUID.fromString(applicationId))

        private var logsConfig: FeatureConfig = FeatureConfig(
            clientToken,
            applicationId,
            DatadogEndpoint.LOGS_US,
            DEFAULT_SERVICE_NAME,
            DEFAULT_ENV_NAME
        )
        private var tracesConfig: FeatureConfig = FeatureConfig(
            clientToken,
            applicationId,
            DatadogEndpoint.TRACES_US,
            DEFAULT_SERVICE_NAME,
            DEFAULT_ENV_NAME
        )
        private var crashReportConfig: FeatureConfig = FeatureConfig(
            clientToken,
            applicationId,
            DatadogEndpoint.LOGS_US,
            DEFAULT_SERVICE_NAME,
            DEFAULT_ENV_NAME
        )
        private var rumConfig: RumConfig = RumConfig(
            clientToken,
            applicationId,
            DatadogEndpoint.RUM_US,
            DEFAULT_SERVICE_NAME,
            DEFAULT_ENV_NAME
        )

        private var logsEnabled: Boolean = true
        private var tracesEnabled: Boolean = true
        private var crashReportsEnabled: Boolean = true
        private var rumEnabled: Boolean = applicationId != UUID(0, 0)
        private var needsClearTextHttp: Boolean = false

        /**
         * Builds a [DatadogConfig] based on the current state of this Builder.
         */
        fun build(): DatadogConfig {

            return DatadogConfig(
                needsClearTextHttp = needsClearTextHttp,
                logsConfig = if (logsEnabled) logsConfig else null,
                tracesConfig = if (tracesEnabled) tracesConfig else null,
                crashReportConfig = if (crashReportsEnabled) crashReportConfig else null,
                rumConfig = if (rumEnabled) rumConfig else null
            )
        }

        /**
         * Enables or disables the logs feature.
         * This feature is enabled by default, disabling it will prevent any logs to be sent to
         * Datadog servers.
         * @param enabled true by default
         */
        fun setLogsEnabled(enabled: Boolean): Builder {
            logsEnabled = enabled
            return this
        }

        /**
         * Enables or disables the tracing feature.
         * This feature is enabled by default, disabling it will prevent any spans and traces to
         * be sent to Datadog servers.
         * @param enabled true by default
         */
        fun setTracesEnabled(enabled: Boolean): Builder {
            tracesEnabled = enabled
            return this
        }

        /**
         * Enables or disables the crash report feature.
         * This feature is enabled by default, disabling it will prevent any crash report to be
         * sent to Datadog servers.
         * @param enabled true by default
         */
        fun setCrashReportsEnabled(enabled: Boolean): Builder {
            crashReportsEnabled = enabled
            return this
        }

        /**
         * Enables or disables the Real User Monitoring feature.
         * This feature is enabled by default, disabling it will prevent any RUM data to be
         * sent to Datadog servers.
         * @param enabled true by default
         */
        fun setRumEnabled(enabled: Boolean): Builder {
            rumEnabled = enabled
            return this
        }

        /**
         * Sets the service name that will appear in your logs, traces and crash reports.
         * @param serviceName the service name (default = "android")
         */
        fun setServiceName(serviceName: String): Builder {
            logsConfig = logsConfig.copy(serviceName = serviceName)
            tracesConfig = tracesConfig.copy(serviceName = serviceName)
            crashReportConfig = crashReportConfig.copy(serviceName = serviceName)
            rumConfig = rumConfig.copy(serviceName = serviceName)
            return this
        }

        /**
         * Sets the environment name that will appear in your logs, traces and crash reports.
         * This can be used to filter logs or traces and distinguish between your production and staging environment.
         * @param envName the environment name (default = "")
         */
        fun setEnvironmentName(envName: String): Builder {
            val validEnvName = envName.replace(Regex("[\"']+"), "")
            logsConfig = logsConfig.copy(envName = validEnvName)
            tracesConfig = tracesConfig.copy(envName = validEnvName)
            crashReportConfig = crashReportConfig.copy(envName = validEnvName)
            rumConfig = rumConfig.copy(envName = validEnvName)
            return this
        }

        /**
         * Let the SDK target Datadog's Europe server.
         *
         * Call this if you log on [app.datadoghq.eu](https://app.datadoghq.eu/).
         */
        fun useEUEndpoints(): Builder {
            logsConfig = logsConfig.copy(endpointUrl = DatadogEndpoint.LOGS_EU)
            tracesConfig = tracesConfig.copy(endpointUrl = DatadogEndpoint.TRACES_EU)
            crashReportConfig = crashReportConfig.copy(endpointUrl = DatadogEndpoint.LOGS_EU)
            rumConfig = rumConfig.copy(endpointUrl = DatadogEndpoint.RUM_EU)
            needsClearTextHttp = false
            return this
        }

        /**
         * Let the SDK target Datadog's US server.
         *
         * Call this if you log on [app.datadoghq.com](https://app.datadoghq.com/).
         */
        fun useUSEndpoints(): Builder {
            logsConfig = logsConfig.copy(endpointUrl = DatadogEndpoint.LOGS_US)
            tracesConfig = tracesConfig.copy(endpointUrl = DatadogEndpoint.TRACES_US)
            crashReportConfig = crashReportConfig.copy(endpointUrl = DatadogEndpoint.LOGS_US)
            rumConfig = rumConfig.copy(endpointUrl = DatadogEndpoint.RUM_US)
            needsClearTextHttp = false
            return this
        }

        /**
         * Let the SDK target a custom server for the logs feature.
         */
        fun useCustomLogsEndpoint(endpoint: String): Builder {
            logsConfig = logsConfig.copy(endpointUrl = endpoint)
            checkCustomEndpoint(endpoint)
            return this
        }

        /**
         * Let the SDK target a custom server for the tracing feature.
         */
        fun useCustomTracesEndpoint(endpoint: String): Builder {
            tracesConfig = tracesConfig.copy(endpointUrl = endpoint)
            checkCustomEndpoint(endpoint)
            return this
        }

        /**
         * Let the SDK target a custom server for the crash reports feature.
         */
        fun useCustomCrashReportsEndpoint(endpoint: String): Builder {
            crashReportConfig = crashReportConfig.copy(endpointUrl = endpoint)
            checkCustomEndpoint(endpoint)
            return this
        }

        /**
         * Let the SDK target a custom server for the RUM feature.
         */
        fun useCustomRumEndpoint(endpoint: String): Builder {
            rumConfig = rumConfig.copy(endpointUrl = endpoint)
            checkCustomEndpoint(endpoint)
            return this
        }

        /**
         * Enable the gestures auto tracker. By enabling this feature the SDK will intercept
         * tap events and automatically send those as RUM UserActions for you.
         */
        fun trackGestures(): Builder {
            rumConfig = rumConfig.copy(
                userActionTrackingStrategy = GesturesTrackingStrategy(DatadogGesturesTracker())
            )
            return this
        }

        /**
         * Sets the automatic view tracking strategy used by the SDK.
         * By default this is null.
         * @param strategy as the [ViewTrackingStrategy]
         * (e.g. FragmentViewTrackingStrategy, ActivityViewTrackingStrategy)
         */
        fun useViewTrackingStrategy(strategy: ViewTrackingStrategy): Builder {
            rumConfig = rumConfig.copy(viewTrackingStrategy = strategy)
            return this
        }

        private fun checkCustomEndpoint(endpoint: String) {
            if (endpoint.startsWith("http://")) {
                needsClearTextHttp = true
            }
        }
    }

    // endregion

    companion object {
        internal const val DEFAULT_SERVICE_NAME = "android"
        internal const val DEFAULT_ENV_NAME = ""
    }
}
