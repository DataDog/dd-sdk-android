/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import android.os.Build
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.plugin.DatadogPlugin
import com.datadog.android.plugin.Feature
import com.datadog.android.rum.internal.instrumentation.GesturesTrackingStrategy
import com.datadog.android.rum.internal.instrumentation.GesturesTrackingStrategyApi29
import com.datadog.android.rum.internal.instrumentation.gestures.DatadogGesturesTracker
import com.datadog.android.rum.internal.instrumentation.gestures.GesturesTracker
import com.datadog.android.rum.internal.tracking.JetpackViewAttributesProvider
import com.datadog.android.rum.internal.tracking.UserActionTrackingStrategy
import com.datadog.android.rum.tracking.ViewAttributesProvider
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import java.util.UUID

/**
 * An object describing the configuration of the Datadog SDK.
 *
 * This is necessary to initialize the SDK with the [Datadog.initialize] method.
 */
class DatadogConfig
private constructor(
    internal val logsConfig: FeatureConfig?,
    internal val tracesConfig: FeatureConfig?,
    internal val crashReportConfig: FeatureConfig?,
    internal val rumConfig: RumConfig?,
    internal var coreConfig: CoreConfig
) {

    internal data class CoreConfig(
        var needsClearTextHttp: Boolean = false,
        val serviceName: String? = null
    )

    internal data class FeatureConfig(
        val clientToken: String,
        val applicationId: UUID,
        val endpointUrl: String,
        val envName: String,
        val plugins: List<DatadogPlugin> = emptyList()
    )

    internal data class RumConfig(
        val clientToken: String,
        val applicationId: UUID,
        val endpointUrl: String,
        val envName: String,
        val samplingRate: Float = 100.0f,
        val gesturesTracker: GesturesTracker? = null,
        val userActionTrackingStrategy: UserActionTrackingStrategy? = null,
        val viewTrackingStrategy: ViewTrackingStrategy? = null,
        val plugins: List<DatadogPlugin> = emptyList()
    )

    // region Builder

    /**
     * A Builder class for a [DatadogConfig].
     * @param clientToken your API key of type Client Token
     * @param envName the environment name special attribute that will be sent with each event.
     * This can be used to filter your events on different environments
     * (e.g. "staging" vs. "production").
     * @param applicationId your applicationId for RUM events

     */
    @Suppress("TooManyFunctions")
    class Builder(clientToken: String, envName: String, applicationId: UUID) {

        /**
         * A Builder class for a [DatadogConfig].
         * @param clientToken your API key of type Client Token
         * @param envName the environment name special attribute that will be sent with each event.
         * This can be used to filter your events on different environments
         * (e.g. "staging" vs. "production").
         */
        constructor(clientToken: String, envName: String) :
            this(clientToken, envName, UUID(0, 0))

        /**
         * A Builder class for a [DatadogConfig].
         * @param clientToken your API key of type Client Token
         * @param envName the environment name special attribute that will be sent with each event.
         * This can be used to filter your events on different environments
         * (e.g. "staging" vs. "production").
         * @param applicationId your applicationId for RUM events
         */
        constructor(clientToken: String, envName: String, applicationId: String) :
            this(clientToken, envName, UUID.fromString(applicationId))

        private var logsConfig: FeatureConfig = FeatureConfig(
            clientToken,
            applicationId,
            DatadogEndpoint.LOGS_US,
            envName
        )
        private var tracesConfig: FeatureConfig = FeatureConfig(
            clientToken,
            applicationId,
            DatadogEndpoint.TRACES_US,
            envName
        )
        private var crashReportConfig: FeatureConfig = FeatureConfig(
            clientToken,
            applicationId,
            DatadogEndpoint.LOGS_US,
            envName
        )
        private var rumConfig: RumConfig = RumConfig(
            clientToken,
            applicationId,
            DatadogEndpoint.RUM_US,
            envName
        )

        private var coreConfig = CoreConfig()

        private var logsEnabled: Boolean = true
        private var tracesEnabled: Boolean = true
        private var crashReportsEnabled: Boolean = true
        private var rumEnabled: Boolean = applicationId != UUID(0, 0)

        /**
         * Builds a [DatadogConfig] based on the current state of this Builder.
         */
        fun build(): DatadogConfig {

            return DatadogConfig(
                logsConfig = if (logsEnabled) logsConfig else null,
                tracesConfig = if (tracesEnabled) tracesConfig else null,
                crashReportConfig = if (crashReportsEnabled) crashReportConfig else null,
                rumConfig = if (rumEnabled) rumConfig else null,
                coreConfig = coreConfig
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
            if (enabled && rumConfig.applicationId == UUID(0, 0)) {
                devLogger.w(RUM_NOT_INITIALISED_WARNING_MESSAGE)
                return this
            }
            rumEnabled = enabled
            return this
        }

        /**
         * Sets the service name that will appear in your logs, traces and crash reports.
         * @param serviceName the service name (default = "android")
         */
        fun setServiceName(serviceName: String): Builder {
            coreConfig = coreConfig.copy(serviceName = serviceName)
            return this
        }

        /**
         * Sets the environment name that will appear in your logs, traces and crash reports.
         * This can be used to filter logs or traces and distinguish between your production
         * and staging environment.
         * @param envName the environment name (default = "")
         */
        @Deprecated("This property is now mandatory for initializing the SDK")
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
            coreConfig = coreConfig.copy(needsClearTextHttp = false)
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
            coreConfig = coreConfig.copy(needsClearTextHttp = false)
            return this
        }

        /**
         * Let the SDK target Datadog's Gov server.
         *
         * Call this if you log on [app.ddog-gov.com/](https://app.ddog-gov.com/).
         */
        fun useGovEndpoints(): Builder {
            logsConfig = logsConfig.copy(endpointUrl = DatadogEndpoint.LOGS_GOV)
            tracesConfig = tracesConfig.copy(endpointUrl = DatadogEndpoint.TRACES_GOV)
            crashReportConfig = crashReportConfig.copy(endpointUrl = DatadogEndpoint.LOGS_GOV)
            rumConfig = rumConfig.copy(endpointUrl = DatadogEndpoint.RUM_GOV)
            coreConfig = coreConfig.copy(needsClearTextHttp = false)
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
         * Enable the user interaction automatic tracker. By enabling this feature the SDK will intercept
         * UI interaction events (e.g.: taps, scrolls, swipes) and automatically send those as RUM UserActions for you.
         * @param touchTargetExtraAttributesProviders an array with your own implementation of the
         * target attributes provider.
         * @see [ViewAttributesProvider]
         */
        @JvmOverloads
        fun trackInteractions(
            touchTargetExtraAttributesProviders: Array<ViewAttributesProvider> = emptyArray()
        ): Builder {
            val gesturesTracker = gestureTracker(touchTargetExtraAttributesProviders)
            rumConfig = rumConfig.copy(
                gesturesTracker = gesturesTracker,
                userActionTrackingStrategy = provideUserTrackingStrategy(
                    gesturesTracker
                )
            )
            return this
        }

        /**
         * Sets the automatic view tracking strategy used by the SDK.
         * By default no view will be tracked.
         * @param strategy as the [ViewTrackingStrategy]
         * Note: By default, the RUM Monitor will let you handle View events manually.
         * This means that you should call [RumMonitor.startView] and [RumMonitor.stopView]
         * yourself. A view should be started when it becomes visible and interactive
         * (equivalent to `onResume`) and be stopped when it's paused (equivalent to `onPause`).
         * @see [com.datadog.android.rum.tracking.ActivityViewTrackingStrategy]
         * @see [com.datadog.android.rum.tracking.FragmentViewTrackingStrategy]
         * @see [com.datadog.android.rum.tracking.MixedViewTrackingStrategy]
         * @see [com.datadog.android.rum.tracking.NavigationViewTrackingStrategy]

         */
        fun useViewTrackingStrategy(strategy: ViewTrackingStrategy): Builder {
            rumConfig = rumConfig.copy(viewTrackingStrategy = strategy)
            return this
        }

        /**
         * Adds a plugin to a specific feature. This plugin will only be registered if the feature
         * was enabled.
         * @param plugin a [DatadogPlugin]
         * @param feature the feature for which this plugin should be registered
         * @see [Feature.LOG]
         * @see [Feature.CRASH]
         * @see [Feature.TRACE]
         * @see [Feature.RUM]
         */
        fun addPlugin(plugin: DatadogPlugin, feature: Feature): Builder {
            when (feature) {
                Feature.RUM -> rumConfig = rumConfig.copy(plugins = rumConfig.plugins + plugin)
                Feature.TRACE -> tracesConfig =
                    tracesConfig.copy(plugins = tracesConfig.plugins + plugin)
                Feature.LOG -> logsConfig = logsConfig.copy(plugins = logsConfig.plugins + plugin)
                Feature.CRASH -> crashReportConfig =
                    crashReportConfig.copy(plugins = crashReportConfig.plugins + plugin)
            }

            return this
        }

        /**
         * Sets the sampling rate for RUM Sessions.
         *
         * @param samplingRate the sampling rate must be a value between 0 and 100. A value of 0
         * means no RUM event will be sent, 100 means all sessions will be kept.
         *
         */
        fun sampleRumSessions(samplingRate: Float): Builder {
            rumConfig = rumConfig.copy(samplingRate = samplingRate)
            return this
        }

        private fun checkCustomEndpoint(endpoint: String) {
            if (endpoint.startsWith("http://")) {
                coreConfig = coreConfig.copy(needsClearTextHttp = true)
            }
        }

        private fun provideUserTrackingStrategy(
            gesturesTracker: GesturesTracker
        ):
            UserActionTrackingStrategy {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                GesturesTrackingStrategyApi29(gesturesTracker)
            } else {
                GesturesTrackingStrategy(gesturesTracker)
            }
        }

        private fun gestureTracker(customProviders: Array<ViewAttributesProvider>):
            DatadogGesturesTracker {
            val defaultProviders = arrayOf(JetpackViewAttributesProvider())
            val providers = customProviders + defaultProviders
            return DatadogGesturesTracker(providers)
        }

        companion object {
            internal const val RUM_NOT_INITIALISED_WARNING_MESSAGE =
                "You're trying to enable RUM but no Application Id was provided. " +
                    "Please use the following line to create your DatadogConfig:\n" +
                    "val config = " +
                    "DatadogConfig.Builder" +
                    "(\"<CLIENT_TOKEN>\", \"<ENVIRONMENT>\", \"<APPLICATION_ID>\").build()"
        }
    }

    // endregion
}
