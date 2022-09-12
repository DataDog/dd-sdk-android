/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("DEPRECATION")

package com.datadog.android.core.configuration

import android.os.Build
import android.os.Looper
import androidx.annotation.FloatRange
import com.datadog.android.Datadog
import com.datadog.android.DatadogEndpoint
import com.datadog.android.DatadogInterceptor
import com.datadog.android.DatadogSite
import com.datadog.android.core.internal.event.NoOpEventMapper
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.event.EventMapper
import com.datadog.android.event.NoOpSpanEventMapper
import com.datadog.android.event.SpanEventMapper
import com.datadog.android.event.ViewEventMapper
import com.datadog.android.log.model.LogEvent
import com.datadog.android.plugin.DatadogPlugin
import com.datadog.android.plugin.Feature as PluginFeature
import com.datadog.android.rum.RumMonitor
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
import com.datadog.android.rum.tracking.InteractionPredicate
import com.datadog.android.rum.tracking.NoOpInteractionPredicate
import com.datadog.android.rum.tracking.TrackingStrategy
import com.datadog.android.rum.tracking.ViewAttributesProvider
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import java.net.Proxy
import java.util.Locale
import okhttp3.Authenticator

/**
 * An object describing the configuration of the Datadog SDK.
 *
 * This is necessary to initialize the SDK with the [Datadog.initialize] method.
 */
data class Configuration
internal constructor(
    internal val coreConfig: Core,
    internal val logsConfig: Feature.Logs?,
    internal val tracesConfig: Feature.Tracing?,
    internal val crashReportConfig: Feature.CrashReport?,
    internal val rumConfig: Feature.RUM?,
    internal val sessionReplayConfig: Feature.SessionReplay?,
    internal val additionalConfig: Map<String, Any>
) {

    internal data class Core(
        val needsClearTextHttp: Boolean,
        val enableDeveloperModeWhenDebuggable: Boolean,
        val firstPartyHosts: List<String>,
        val batchSize: BatchSize,
        val uploadFrequency: UploadFrequency,
        val proxy: Proxy?,
        val proxyAuth: Authenticator,
        val securityConfig: SecurityConfig,
        val webViewTrackingHosts: List<String>,
        val site: DatadogSite
    )

    internal sealed class Feature {
        abstract val endpointUrl: String
        abstract val plugins: List<DatadogPlugin>

        internal data class Logs(
            override val endpointUrl: String,
            override val plugins: List<DatadogPlugin>,
            val logsEventMapper: EventMapper<LogEvent>
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
            val telemetrySamplingRate: Float,
            val userActionTrackingStrategy: UserActionTrackingStrategy?,
            val viewTrackingStrategy: ViewTrackingStrategy?,
            val longTaskTrackingStrategy: TrackingStrategy?,
            val rumEventMapper: EventMapper<Any>,
            val backgroundEventTracking: Boolean,
            val vitalsMonitorUpdateFrequency: VitalsUpdateFrequency
        ) : Feature()

        internal data class SessionReplay(
            override val endpointUrl: String,
            override val plugins: List<DatadogPlugin> = emptyList(),
            val privacy: SessionReplayPrivacy
        ) : Feature()
    }

    // region Builder

    /**
     * A Builder class for a [Configuration].
     * @param logsEnabled whether Logs are tracked and sent to Datadog
     * @param tracesEnabled whether Spans are tracked and sent to Datadog
     * @param crashReportsEnabled whether crashes are tracked and sent to Datadog
     * @param rumEnabled whether RUM events are tracked and sent to Datadog
     * @param sessionReplayEnabled whether RUM Session Replay is enabled or not
     */
    @Suppress("TooManyFunctions")
    class Builder(
        val logsEnabled: Boolean,
        val tracesEnabled: Boolean,
        val crashReportsEnabled: Boolean,
        val rumEnabled: Boolean,
        val sessionReplayEnabled: Boolean
    ) {
        private var logsConfig: Feature.Logs = DEFAULT_LOGS_CONFIG
        private var tracesConfig: Feature.Tracing = DEFAULT_TRACING_CONFIG
        private var crashReportConfig: Feature.CrashReport = DEFAULT_CRASH_CONFIG
        private var rumConfig: Feature.RUM = DEFAULT_RUM_CONFIG
        private var sessionReplayConfig: Feature.SessionReplay = DEFAULT_SESSION_REPLAY_CONFIG
        private var additionalConfig: Map<String, Any> = emptyMap()

        private var coreConfig = DEFAULT_CORE_CONFIG

        internal var hostsSanitizer = HostsSanitizer()

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
                sessionReplayConfig = if (sessionReplayEnabled) sessionReplayConfig else null,
                additionalConfig = additionalConfig
            )
        }

        /**
         * Sets the DataDog SDK to be more verbose when an application is set to `debuggable`.
         * This is equivalent to setting:
         *   sampleRumSessions(100)
         *   setBatchSize(BatchSize.SMALL)
         *   setUploadFrequency(UploadFrequency.FREQUENT)
         *   Datadog.setVerbosity(Log.VERBOSE)
         * These settings will override your configuration, but only when the application is `debuggable`
         * @param developerModeEnabled Enable or disable extra debug info when an app is debuggable
         */
        @Suppress("FunctionMaxLength")
        fun setUseDeveloperModeWhenDebuggable(developerModeEnabled: Boolean): Builder {
            coreConfig = coreConfig.copy(enableDeveloperModeWhenDebuggable = developerModeEnabled)
            return this
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
            coreConfig = coreConfig.copy(
                firstPartyHosts = hostsSanitizer.sanitizeHosts(
                    hosts,
                    NETWORK_REQUESTS_TRACKING_FEATURE_NAME
                )
            )
            return this
        }

        /**
         * Sets the list of WebView tracked hosts.
         * When a WebView loads a URL from any of these hosts, and the page has Datadog's
         * Browser SDK setup, then the Logs and RUM Events from the webview will be tracked in
         * the same session..
         * @param hosts a list of all the hosts that you want to track when loaded in the
         * WebView.
         */
        fun setWebViewTrackingHosts(hosts: List<String>): Builder {
            coreConfig = coreConfig.copy(
                webViewTrackingHosts = hostsSanitizer.sanitizeHosts(
                    hosts,
                    WEB_VIEW_TRACKING_FEATURE_NAME
                )
            )
            return this
        }

        /**
         * Let the SDK target your preferred Datadog's site.
         */
        fun useSite(site: DatadogSite): Builder {
            logsConfig = logsConfig.copy(endpointUrl = site.logsEndpoint())
            tracesConfig = tracesConfig.copy(endpointUrl = site.tracesEndpoint())
            crashReportConfig = crashReportConfig.copy(endpointUrl = site.logsEndpoint())
            rumConfig = rumConfig.copy(endpointUrl = site.rumEndpoint())
            coreConfig = coreConfig.copy(needsClearTextHttp = false, site = site)
            sessionReplayConfig = sessionReplayConfig.copy(
                endpointUrl = site.sessionReplayEndpoint()
            )
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
         * Let the SDK target a custom server for the Session Replay feature.
         */
        fun useSessionReplayEndpoint(endpoint: String): Builder {
            applyIfFeatureEnabled(
                PluginFeature.SESSION_REPLAY,
                "useCustomSessionReplayEndpoint"
            ) {
                sessionReplayConfig = sessionReplayConfig.copy(endpointUrl = endpoint)
                checkCustomEndpoint(endpoint)
            }
            return this
        }

        /**
         * Enable the user interaction automatic tracker. By enabling this feature the SDK will intercept
         * UI interaction events (e.g.: taps, scrolls, swipes) and automatically send those as RUM UserActions for you.
         * @param touchTargetExtraAttributesProviders an array with your own implementation of the
         * target attributes provider.
         * @param interactionPredicate an interface to provide custom values for the
         * actions events properties.
         * @see [ViewAttributesProvider]
         * @see [InteractionPredicate]
         */
        @JvmOverloads
        fun trackInteractions(
            touchTargetExtraAttributesProviders: Array<ViewAttributesProvider> = emptyArray(),
            interactionPredicate: InteractionPredicate = NoOpInteractionPredicate()
        ): Builder {
            val strategy = provideUserTrackingStrategy(
                touchTargetExtraAttributesProviders,
                interactionPredicate
            )
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
         * By default [ActivityViewTrackingStrategy] will be used.
         * @param strategy as the [ViewTrackingStrategy]
         * Note: If [null] is passed, the RUM Monitor will let you handle View events manually.
         * This means that you should call [RumMonitor.startView] and [RumMonitor.stopView]
         * yourself. A view should be started when it becomes visible and interactive
         * (equivalent to `onResume`) and be stopped when it's paused (equivalent to `onPause`).
         * @see [com.datadog.android.rum.tracking.ActivityViewTrackingStrategy]
         * @see [com.datadog.android.rum.tracking.FragmentViewTrackingStrategy]
         * @see [com.datadog.android.rum.tracking.MixedViewTrackingStrategy]
         * @see [com.datadog.android.rum.tracking.NavigationViewTrackingStrategy]
         */
        fun useViewTrackingStrategy(strategy: ViewTrackingStrategy?): Builder {
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
        @Deprecated(message = PLUGINS_DEPRECATED_WARN_MESSAGE)
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
                    else -> {
                        devLogger.w(PLUGINS_DEPRECATED_WARN_MESSAGE)
                    }
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
         */
        fun sampleRumSessions(@FloatRange(from = 0.0, to = 100.0) samplingRate: Float): Builder {
            applyIfFeatureEnabled(PluginFeature.RUM, "sampleRumSessions") {
                rumConfig = rumConfig.copy(samplingRate = samplingRate)
            }
            return this
        }

        /**
         * Sets the sampling rate for Internal Telemetry (info related to the work of the
         * SDK internals). Default value is 20.
         *
         * @param samplingRate the sampling rate must be a value between 0 and 100. A value of 0
         * means no telemetry will be sent, 100 means all telemetry will be kept.
         */
        fun sampleTelemetry(@FloatRange(from = 0.0, to = 100.0) samplingRate: Float): Builder {
            applyIfFeatureEnabled(PluginFeature.RUM, "sampleTelemetry") {
                rumConfig = rumConfig.copy(telemetrySamplingRate = samplingRate)
            }
            return this
        }

        /**
         * Enables/Disables tracking RUM event when no Activity is in foreground.
         *
         * By default, background events are not tracked. Enabling this feature might increase the
         * number of sessions tracked and impact your billing.
         *
         * @param enabled whether background events should be tracked in RUM.
         */
        fun trackBackgroundRumEvents(enabled: Boolean): Builder {
            applyIfFeatureEnabled(PluginFeature.RUM, "trackBackgroundRumEvents") {
                rumConfig = rumConfig.copy(backgroundEventTracking = enabled)
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
         * Sets the [EventMapper] for the [LogEvent].
         * You can use this interface implementation to modify the
         * [LogEvent] attributes before serialisation.
         *
         * @param eventMapper the [EventMapper] implementation.
         */
        fun setLogEventMapper(eventMapper: EventMapper<LogEvent>): Builder {
            applyIfFeatureEnabled(PluginFeature.LOG, "setLogEventMapper") {
                logsConfig = logsConfig.copy(logsEventMapper = eventMapper)
            }
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

        /**
         * Enables a custom proxy for uploading tracked data to Datadog's intake.
         * @param proxy the [Proxy] configuration
         * @param authenticator the optional [Authenticator] for the proxy
         */
        fun setProxy(proxy: Proxy, authenticator: Authenticator?): Builder {
            coreConfig = coreConfig.copy(
                proxy = proxy,
                proxyAuth = authenticator ?: Authenticator.NONE
            )
            return this
        }

        /**
         * Allows to set the necessary security configuration (used to control local
         * data storage encryption, for example).
         * @param config Security config to use. If not provided, default one will be used (no
         * encryption for local data storage).
         */
        fun setSecurityConfig(config: SecurityConfig): Builder {
            coreConfig = coreConfig.copy(
                securityConfig = config
            )
            return this
        }

        /**
         * Sets the privacy rule for the Session Replay feature.
         * If not specified all the elements will be masked by default (MASK_ALL).
         * @see SessionReplayPrivacy.ALLOW_ALL
         * @see SessionReplayPrivacy.MASK_ALL
         */
        fun setSessionReplayPrivacy(privacy: SessionReplayPrivacy): Builder {
            sessionReplayConfig = sessionReplayConfig.copy(privacy = privacy)
            return this
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
                PluginFeature.SESSION_REPLAY -> sessionReplayEnabled
            }
            if (featureEnabled) {
                @Suppress("UnsafeThirdPartyFunctionCall") // internal safe call
                block()
            } else {
                devLogger.e(ERROR_FEATURE_DISABLED.format(Locale.US, feature.featureName, method))
            }
        }

        /**
         * Allows to specify the frequency at which to update the mobile vitals
         * data provided in the RUM [ViewEvent].
         * @param frequency as [VitalsUpdateFrequency]
         * @see [VitalsUpdateFrequency]
         */
        fun setVitalsUpdateFrequency(frequency: VitalsUpdateFrequency): Builder {
            rumConfig = rumConfig.copy(vitalsMonitorUpdateFrequency = frequency)
            return this
        }
    }

    // endregion

    companion object {
        internal const val DEFAULT_SAMPLING_RATE: Float = 100f
        internal const val DEFAULT_TELEMETRY_SAMPLING_RATE: Float = 20f
        internal const val DEFAULT_LONG_TASK_THRESHOLD_MS = 100L
        internal const val PLUGINS_DEPRECATED_WARN_MESSAGE = "Datadog Plugins won't work in SDK " +
            "v2, you'll need to write your own Feature"

        internal val DEFAULT_CORE_CONFIG = Core(
            needsClearTextHttp = false,
            enableDeveloperModeWhenDebuggable = false,
            firstPartyHosts = emptyList(),
            batchSize = BatchSize.MEDIUM,
            uploadFrequency = UploadFrequency.AVERAGE,
            proxy = null,
            proxyAuth = Authenticator.NONE,
            securityConfig = SecurityConfig.DEFAULT,
            webViewTrackingHosts = emptyList(),
            site = DatadogSite.US1
        )
        internal val DEFAULT_LOGS_CONFIG = Feature.Logs(
            endpointUrl = DatadogEndpoint.LOGS_US1,
            plugins = emptyList(),
            logsEventMapper = NoOpEventMapper()
        )
        internal val DEFAULT_CRASH_CONFIG = Feature.CrashReport(
            endpointUrl = DatadogEndpoint.LOGS_US1,
            plugins = emptyList()
        )
        internal val DEFAULT_TRACING_CONFIG = Feature.Tracing(
            endpointUrl = DatadogEndpoint.TRACES_US1,
            plugins = emptyList(),
            spanEventMapper = NoOpSpanEventMapper()
        )
        internal val DEFAULT_RUM_CONFIG = Feature.RUM(
            endpointUrl = DatadogEndpoint.RUM_US1,
            plugins = emptyList(),
            samplingRate = DEFAULT_SAMPLING_RATE,
            telemetrySamplingRate = DEFAULT_TELEMETRY_SAMPLING_RATE,
            userActionTrackingStrategy = provideUserTrackingStrategy(
                emptyArray(),
                NoOpInteractionPredicate()
            ),
            viewTrackingStrategy = ActivityViewTrackingStrategy(false),
            longTaskTrackingStrategy = MainLooperLongTaskStrategy(
                DEFAULT_LONG_TASK_THRESHOLD_MS
            ),
            rumEventMapper = NoOpEventMapper(),
            backgroundEventTracking = false,
            vitalsMonitorUpdateFrequency = VitalsUpdateFrequency.AVERAGE
        )
        internal val DEFAULT_SESSION_REPLAY_CONFIG = Feature.SessionReplay(
            endpointUrl = DatadogEndpoint.SESSION_REPLAY_US1,
            plugins = emptyList(),
            privacy = SessionReplayPrivacy.MASK_ALL
        )

        internal const val ERROR_FEATURE_DISABLED = "The %s feature has been disabled in your " +
            "Configuration.Builder, but you're trying to edit the RUM configuration with the " +
            "%s() method."

        internal const val WEB_VIEW_TRACKING_FEATURE_NAME = "WebView"
        internal const val NETWORK_REQUESTS_TRACKING_FEATURE_NAME = "Network requests"

        private fun provideUserTrackingStrategy(
            touchTargetExtraAttributesProviders: Array<ViewAttributesProvider>,
            interactionPredicate: InteractionPredicate
        ): UserActionTrackingStrategy {
            val gesturesTracker =
                provideGestureTracker(touchTargetExtraAttributesProviders, interactionPredicate)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                UserActionTrackingStrategyApi29(gesturesTracker)
            } else {
                UserActionTrackingStrategyLegacy(gesturesTracker)
            }
        }

        private fun provideGestureTracker(
            customProviders: Array<ViewAttributesProvider>,
            interactionPredicate: InteractionPredicate
        ): DatadogGesturesTracker {
            val defaultProviders = arrayOf(JetpackViewAttributesProvider())
            val providers = customProviders + defaultProviders
            return DatadogGesturesTracker(providers, interactionPredicate)
        }
    }
}
