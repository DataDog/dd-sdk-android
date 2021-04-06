/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.configuration

import android.os.Build
import android.os.Looper
import com.datadog.android.Datadog
import com.datadog.android.DatadogEndpoint
import com.datadog.android.DatadogInterceptor
import com.datadog.android.core.internal.event.NoOpEventMapper
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.event.EventMapper
import com.datadog.android.event.NoOpSpanEventMapper
import com.datadog.android.event.SpanEventMapper
import com.datadog.android.event.ViewEventMapper
import com.datadog.android.plugin.DatadogPlugin
import com.datadog.android.plugin.Feature as PluginFeature
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.internal.domain.event.RumEventMapper
import com.datadog.android.rum.internal.instrumentation.MainLooperLongTaskStrategy
import com.datadog.android.rum.internal.instrumentation.UserActionTrackingStrategyApi29
import com.datadog.android.rum.internal.instrumentation.UserActionTrackingStrategyLegacy
import com.datadog.android.rum.internal.instrumentation.gestures.DatadogGesturesTracker
import com.datadog.android.rum.internal.tracking.JetpackViewAttributesProvider
import com.datadog.android.rum.internal.tracking.UserActionTrackingStrategy
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.rum.tracking.TrackingStrategy
import com.datadog.android.rum.tracking.ViewAttributesProvider
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import java.net.MalformedURLException
import java.net.URL
import java.util.Locale

/**
 * An object describing the configuration of the Datadog SDK.
 *
 * This is necessary to initialize the SDK with the [Datadog.initialize] method.
 */
class Configuration
internal constructor(
    internal var coreConfig: Core,
    internal val logsConfig: Feature.Logs?,
    internal val tracesConfig: Feature.Tracing?,
    internal val crashReportConfig: Feature.CrashReport?,
    internal val rumConfig: Feature.RUM?,
    internal val internalLogsConfig: Feature.InternalLogs?,
    internal val additionalConfig: Map<String, Any>
) {

    internal data class Core(
        var needsClearTextHttp: Boolean,
        val firstPartyHosts: List<String>,
        val batchSize: BatchSize,
        val uploadFrequency: UploadFrequency
    )

    internal sealed class Feature {
        abstract val endpointUrl: String
        abstract val plugins: List<DatadogPlugin>

        internal data class Logs(
            override val endpointUrl: String,
            override val plugins: List<DatadogPlugin>
        ) : Feature()

        internal data class InternalLogs(
            val internalClientToken: String,
            override val endpointUrl: String,
            override val plugins: List<DatadogPlugin>
        ) : Feature()

        internal data class CrashReport(
            override val endpointUrl: String,
            override val plugins: List<DatadogPlugin>
        ) : Feature()

        internal data class Tracing(
            override val endpointUrl: String,
            override val plugins: List<DatadogPlugin>,
            val spanEventMapper: SpanEventMapper
        ) : Feature()

        internal data class RUM(
            override val endpointUrl: String,
            override val plugins: List<DatadogPlugin>,
            val samplingRate: Float,
            val userActionTrackingStrategy: UserActionTrackingStrategy?,
            val viewTrackingStrategy: ViewTrackingStrategy?,
            val longTaskTrackingStrategy: TrackingStrategy?,
            val rumEventMapper: EventMapper<RumEvent>
        ) : Feature()
    }

    // region Builder

    /**
     * A Builder class for a [Configuration].
     * @param logsEnabled whether Logs are tracked and sent to Datadog
     * @param tracesEnabled whether Spans are tracked and sent to Datadog
     * @param crashReportsEnabled whether crashes are tracked and sent to Datadog
     * @param rumEnabled whether RUM events are tracked and sent to Datadog
     */
    @Suppress("TooManyFunctions")
    class Builder(
        val logsEnabled: Boolean,
        val tracesEnabled: Boolean,
        val crashReportsEnabled: Boolean,
        val rumEnabled: Boolean
    ) {
        private var logsConfig: Feature.Logs = DEFAULT_LOGS_CONFIG
        private var tracesConfig: Feature.Tracing = DEFAULT_TRACING_CONFIG
        private var crashReportConfig: Feature.CrashReport = DEFAULT_CRASH_CONFIG
        private var rumConfig: Feature.RUM = DEFAULT_RUM_CONFIG
        private var internalLogsConfig: Feature.InternalLogs? = null
        private var additionalConfig: Map<String, Any> = emptyMap()

        private var coreConfig = DEFAULT_CORE_CONFIG

        /**
         * Builds a [Configuration] based on the current state of this Builder.
         */
        fun build(): Configuration {
            return Configuration(
                coreConfig = coreConfig,
                logsConfig = if (logsEnabled) logsConfig else null,
                tracesConfig = if (tracesEnabled) tracesConfig else null,
                crashReportConfig = if (crashReportsEnabled) crashReportConfig else null,
                rumConfig = if (rumEnabled) rumConfig else null,
                internalLogsConfig = internalLogsConfig,
                additionalConfig = additionalConfig
            )
        }

        /**
         * Sets the list of first party hosts.
         * Requests made to a URL with any one of these hosts (or any subdomain) will:
         * - be considered a first party resource and categorised as such in your RUM dashboard;
         * - be wrapped in a Span and have trace id injected to get a full flame-graph in APM.
         * @param hosts a list of all the hosts that you own.
         * See [DatadogInterceptor]
         */
        fun setFirstPartyHosts(hosts: List<String>): Builder {
            coreConfig = coreConfig.copy(firstPartyHosts = sanitizeHosts(hosts))
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
            applyIfFeatureEnabled(PluginFeature.LOG, "useCustomLogsEndpoint") {
                logsConfig = logsConfig.copy(endpointUrl = endpoint)
                checkCustomEndpoint(endpoint)
            }
            return this
        }

        /**
         * Let the SDK target a custom server for the tracing feature.
         */
        fun useCustomTracesEndpoint(endpoint: String): Builder {
            applyIfFeatureEnabled(PluginFeature.TRACE, "useCustomTracesEndpoint") {
                tracesConfig = tracesConfig.copy(endpointUrl = endpoint)
                checkCustomEndpoint(endpoint)
            }
            return this
        }

        /**
         * Let the SDK target a custom server for the crash reports feature.
         */
        fun useCustomCrashReportsEndpoint(endpoint: String): Builder {
            applyIfFeatureEnabled(PluginFeature.CRASH, "useCustomCrashReportsEndpoint") {
                crashReportConfig = crashReportConfig.copy(endpointUrl = endpoint)
                checkCustomEndpoint(endpoint)
            }
            return this
        }

        /**
         * Let the SDK target a custom server for the RUM feature.
         */
        fun useCustomRumEndpoint(endpoint: String): Builder {
            applyIfFeatureEnabled(PluginFeature.RUM, "useCustomRumEndpoint") {
                rumConfig = rumConfig.copy(endpointUrl = endpoint)
                checkCustomEndpoint(endpoint)
            }
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
            val strategy = provideUserTrackingStrategy(touchTargetExtraAttributesProviders)
            applyIfFeatureEnabled(PluginFeature.RUM, "trackInteractions") {
                rumConfig = rumConfig.copy(userActionTrackingStrategy = strategy)
            }
            return this
        }

        /**
         * Enable long operations on the main thread to be tracked automatically.
         * Any long running operation on the main thread will appear as Long Tasks in Datadog
         * RUM Explorer
         * @param longTaskThresholdMs the threshold in milliseconds above which a task running on
         * the Main thread [Looper] is considered as a long task (default 100ms)
         */
        @JvmOverloads
        fun trackLongTasks(longTaskThresholdMs: Long = DEFAULT_LONG_TASK_THRESHOLD_MS): Builder {
            applyIfFeatureEnabled(PluginFeature.RUM, "trackLongTasks") {
                rumConfig = rumConfig.copy(
                    longTaskTrackingStrategy = MainLooperLongTaskStrategy(longTaskThresholdMs)
                )
            }
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
            applyIfFeatureEnabled(PluginFeature.RUM, "useViewTrackingStrategy") {
                rumConfig = rumConfig.copy(viewTrackingStrategy = strategy)
            }
            return this
        }

        /**
         * Adds a plugin to a specific feature. This plugin will only be registered if the feature
         * was enabled.
         * @param plugin a [DatadogPlugin]
         * @param feature the feature for which this plugin should be registered
         * @see [Feature.Logs]
         * @see [Feature.CrashReport]
         * @see [Feature.Tracing]
         * @see [Feature.RUM]
         */
        fun addPlugin(plugin: DatadogPlugin, feature: PluginFeature): Builder {
            applyIfFeatureEnabled(feature, "addPlugin") {
                when (feature) {
                    PluginFeature.RUM -> rumConfig = rumConfig.copy(
                        plugins = rumConfig.plugins + plugin
                    )
                    PluginFeature.TRACE -> tracesConfig = tracesConfig.copy(
                        plugins = tracesConfig.plugins + plugin
                    )
                    PluginFeature.LOG -> logsConfig = logsConfig.copy(
                        plugins = logsConfig.plugins + plugin
                    )
                    PluginFeature.CRASH -> crashReportConfig = crashReportConfig.copy(
                        plugins = crashReportConfig.plugins + plugin
                    )
                }
            }
            return this
        }

        /**
         * Defines the batch size (impacts the size and number of requests performed by Datadog).
         * @param batchSize the desired batch size
         */
        fun setBatchSize(batchSize: BatchSize): Builder {
            coreConfig = coreConfig.copy(batchSize = batchSize)
            return this
        }

        /**
         * Defines the preferred upload frequency.
         * @param uploadFrequency the desired upload frequency policy
         */
        fun setUploadFrequency(uploadFrequency: UploadFrequency): Builder {
            coreConfig = coreConfig.copy(uploadFrequency = uploadFrequency)
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
            applyIfFeatureEnabled(PluginFeature.RUM, "sampleRumSessions") {
                rumConfig = rumConfig.copy(samplingRate = samplingRate)
            }
            return this
        }

        /**
         * Sets the [ViewEventMapper] for the RUM [ViewEvent]. You can use this interface implementation
         * to modify the [ViewEvent] attributes before serialisation.
         *
         * @param eventMapper the [ViewEventMapper] implementation.
         */
        fun setRumViewEventMapper(eventMapper: ViewEventMapper): Builder {
            applyIfFeatureEnabled(PluginFeature.RUM, "setRumViewEventMapper") {
                rumConfig = rumConfig.copy(
                    rumEventMapper = getRumEventMapper().copy(viewEventMapper = eventMapper)
                )
            }
            return this
        }

        /**
         * Sets the [EventMapper] for the RUM [ResourceEvent]. You can use this interface implementation
         * to modify the [ResourceEvent] attributes before serialisation.
         *
         * @param eventMapper the [EventMapper] implementation.
         */
        fun setRumResourceEventMapper(eventMapper: EventMapper<ResourceEvent>): Builder {
            applyIfFeatureEnabled(PluginFeature.RUM, "setRumResourceEventMapper") {
                rumConfig = rumConfig.copy(
                    rumEventMapper = getRumEventMapper().copy(resourceEventMapper = eventMapper)
                )
            }
            return this
        }

        /**
         * Sets the [EventMapper] for the RUM [ActionEvent]. You can use this interface implementation
         * to modify the [ActionEvent] attributes before serialisation.
         *
         * @param eventMapper the [EventMapper] implementation.
         */
        fun setRumActionEventMapper(eventMapper: EventMapper<ActionEvent>): Builder {
            applyIfFeatureEnabled(PluginFeature.RUM, "setRumActionEventMapper") {
                rumConfig = rumConfig.copy(
                    rumEventMapper = getRumEventMapper().copy(actionEventMapper = eventMapper)
                )
            }
            return this
        }

        /**
         * Sets the [EventMapper] for the RUM [ErrorEvent]. You can use this interface implementation
         * to modify the [ErrorEvent] attributes before serialisation.
         *
         * @param eventMapper the [EventMapper] implementation.
         */
        fun setRumErrorEventMapper(eventMapper: EventMapper<ErrorEvent>): Builder {
            applyIfFeatureEnabled(PluginFeature.RUM, "setRumErrorEventMapper") {
                rumConfig = rumConfig.copy(
                    rumEventMapper = getRumEventMapper().copy(errorEventMapper = eventMapper)
                )
            }
            return this
        }

        /**
         * Sets the [EventMapper] for the RUM [LongTaskEvent]. You can use this interface implementation
         * to modify the [LongTaskEvent] attributes before serialisation.
         *
         * @param eventMapper the [EventMapper] implementation.
         */
        fun setRumLongTaskEventMapper(eventMapper: EventMapper<LongTaskEvent>): Builder {
            applyIfFeatureEnabled(PluginFeature.RUM, "setRumLongTaskEventMapper") {
                rumConfig = rumConfig.copy(
                    rumEventMapper = getRumEventMapper().copy(longTaskEventMapper = eventMapper)
                )
            }
            return this
        }

        /**
         * Sets the [SpanEventMapper] for the Trace [com.datadog.android.tracing.model.SpanEvent].
         * You can use this interface implementation to modify the
         * [com.datadog.android.tracing.model.SpanEvent] attributes before serialisation.
         *
         * @param eventMapper the [SpanEventMapper] implementation.
         */
        fun setSpanEventMapper(eventMapper: SpanEventMapper): Builder {
            applyIfFeatureEnabled(PluginFeature.TRACE, "setSpanEventMapper") {
                tracesConfig = tracesConfig.copy(spanEventMapper = eventMapper)
            }
            return this
        }

        /**
         * Enables the internal logs to be sent to a dedicated Datadog Org.
         * @param clientToken the client token to use for internal logs
         * @param endpointUrl the endpoint url to sent the internal logs to
         * (one of [DatadogEndpoint.LOGS_US], [DatadogEndpoint.LOGS_EU], [DatadogEndpoint.LOGS_GOV])
         */
        fun setInternalLogsEnabled(
            clientToken: String,
            endpointUrl: String
        ): Builder {
            internalLogsConfig = Feature.InternalLogs(
                clientToken,
                endpointUrl,
                emptyList()
            )
            return this
        }

        /**
         * Allows to provide additional configuration values which can be used by the SDK.
         * @param additionalConfig Additional configuration values.
         */
        fun setAdditionalConfiguration(additionalConfig: Map<String, Any>): Builder {
            return apply {
                this.additionalConfig = additionalConfig
            }
        }

        private fun checkCustomEndpoint(endpoint: String) {
            if (endpoint.startsWith("http://")) {
                coreConfig = coreConfig.copy(needsClearTextHttp = true)
            }
        }

        private fun getRumEventMapper(): RumEventMapper {
            val rumEventMapper = rumConfig.rumEventMapper
            return if (rumEventMapper is RumEventMapper) {
                rumEventMapper
            } else {
                RumEventMapper()
            }
        }

        private fun applyIfFeatureEnabled(
            feature: PluginFeature,
            method: String,
            block: () -> Unit
        ) {
            val featureEnabled = when (feature) {
                PluginFeature.LOG -> logsEnabled
                PluginFeature.TRACE -> tracesEnabled
                PluginFeature.CRASH -> crashReportsEnabled
                PluginFeature.RUM -> rumEnabled
            }
            if (featureEnabled) {
                block()
            } else {
                devLogger.e(ERROR_FEATURE_DISABLED.format(Locale.US, feature.featureName, method))
            }
        }
    }

    // endregion

    companion object {
        internal const val DEFAULT_SAMPLING_RATE: Float = 100f
        internal const val DEFAULT_LONG_TASK_THRESHOLD_MS = 100L

        internal val DEFAULT_CORE_CONFIG = Core(
            needsClearTextHttp = false,
            firstPartyHosts = emptyList(),
            batchSize = BatchSize.MEDIUM,
            uploadFrequency = UploadFrequency.AVERAGE
        )
        internal val DEFAULT_LOGS_CONFIG = Feature.Logs(
            endpointUrl = DatadogEndpoint.LOGS_US,
            plugins = emptyList()
        )
        internal val DEFAULT_CRASH_CONFIG = Feature.CrashReport(
            endpointUrl = DatadogEndpoint.LOGS_US,
            plugins = emptyList()
        )
        internal val DEFAULT_TRACING_CONFIG = Feature.Tracing(
            endpointUrl = DatadogEndpoint.TRACES_US,
            plugins = emptyList(),
            spanEventMapper = NoOpSpanEventMapper()
        )
        internal val DEFAULT_RUM_CONFIG = Feature.RUM(
            endpointUrl = DatadogEndpoint.RUM_US,
            plugins = emptyList(),
            samplingRate = DEFAULT_SAMPLING_RATE,
            userActionTrackingStrategy = provideUserTrackingStrategy(emptyArray()),
            viewTrackingStrategy = ActivityViewTrackingStrategy(false),
            longTaskTrackingStrategy = MainLooperLongTaskStrategy(DEFAULT_LONG_TASK_THRESHOLD_MS),
            rumEventMapper = NoOpEventMapper()
        )

        internal val ERROR_FEATURE_DISABLED = "The %s feature has been disabled in your " +
            "Configuration.Builder, but you're trying to edit the RUM configuration with the " +
            "%s() method."

        private val URL_REGEX = "^(http|https)://(.*)"

        private const val VALID_IP_REGEX =
            "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}" +
                "([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$"
        private const val VALID_DOMAIN_REGEX =
            "^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)+" +
                "([A-Za-z]|[A-Za-z][A-Za-z0-9-]*[A-Za-z0-9])$"
        private const val VALID_HOSTNAME_REGEX = "$VALID_IP_REGEX|$VALID_DOMAIN_REGEX"

        internal const val WARNING_USING_URL_FOR_HOST =
            "You are using an url: %s for declaring the first " +
                "party hosts. You should use instead a valid" +
                " host name: %s."

        internal const val ERROR_MALFORMED_URL =
            "The url: %s is malformed. It will be dropped"

        internal const val ERROR_MALFORMED_HOST_IP_ADDRESS =
            "The host name or ip address used for first party " +
                "hosts: %s was malformed. It will be dropped."

        internal fun sanitizeHosts(hosts: List<String>): List<String> {
            val validHostNameRegEx = Regex(VALID_HOSTNAME_REGEX)
            val validUrlRegex = Regex(URL_REGEX)
            return hosts.mapNotNull {
                if (it.matches(validUrlRegex)) {
                    try {
                        val parsedUrl = URL(it)
                        devLogger.w(
                            WARNING_USING_URL_FOR_HOST.format(
                                Locale.US,
                                it,
                                parsedUrl.host
                            )
                        )
                        parsedUrl.host
                    } catch (e: MalformedURLException) {
                        devLogger.e(ERROR_MALFORMED_URL.format(Locale.US, it), e)
                        null
                    }
                } else if (it.matches(validHostNameRegEx)) {
                    it
                } else if (it.toLowerCase(Locale.ENGLISH) == "localhost") {
                    // special rule exception to accept `localhost` as a valid domain name
                    it
                } else {
                    devLogger.e(ERROR_MALFORMED_HOST_IP_ADDRESS.format(Locale.US, it))
                    null
                }
            }
        }

        private fun provideUserTrackingStrategy(
            touchTargetExtraAttributesProviders: Array<ViewAttributesProvider>
        ): UserActionTrackingStrategy {
            val gesturesTracker = provideGestureTracker(touchTargetExtraAttributesProviders)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                UserActionTrackingStrategyApi29(gesturesTracker)
            } else {
                UserActionTrackingStrategyLegacy(gesturesTracker)
            }
        }

        private fun provideGestureTracker(
            customProviders: Array<ViewAttributesProvider>
        ): DatadogGesturesTracker {
            val defaultProviders = arrayOf(JetpackViewAttributesProvider())
            val providers = customProviders + defaultProviders
            return DatadogGesturesTracker(providers)
        }
    }
}
