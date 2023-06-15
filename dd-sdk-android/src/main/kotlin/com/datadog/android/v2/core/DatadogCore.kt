/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.SdkFeature
import com.datadog.android.core.internal.lifecycle.ProcessLifecycleCallback
import com.datadog.android.core.internal.lifecycle.ProcessLifecycleMonitor
import com.datadog.android.core.internal.time.NoOpTimeProvider
import com.datadog.android.core.internal.utils.internalLogger
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.error.internal.CrashReportsFeature
import com.datadog.android.log.internal.LogsFeature
import com.datadog.android.plugin.DatadogContext
import com.datadog.android.plugin.DatadogRumContext
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.tracing.internal.TracingFeature
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureEventReceiver
import com.datadog.android.v2.api.FeatureScope
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.context.TimeInfo
import com.datadog.android.v2.api.context.UserInfo
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.android.webview.internal.log.WebViewLogsFeature
import com.datadog.android.webview.internal.rum.WebViewRumFeature
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Internal implementation of the [SdkCore] interface.
 * @param context the application's Android [Context]
 * @param credentials the Datadog credentials for this instance
 * @param configuration the Datadog configuration for this instance
 * @param instanceId the unique identifier for this instance
 */
@Suppress("TooManyFunctions")
internal class DatadogCore(
    internal val context: Context,
    internal val credentials: Credentials,
    configuration: Configuration,
    internal val instanceId: String
) : SdkCore {

    internal var libraryVerbosity = Int.MAX_VALUE

    internal lateinit var coreFeature: CoreFeature

    internal val features: MutableMap<String, SdkFeature> = mutableMapOf()

    internal var logsFeature: LogsFeature? = null
    internal var tracingFeature: TracingFeature? = null
    internal var rumFeature: RumFeature? = null
    internal var crashReportsFeature: CrashReportsFeature? = null
    internal var webViewLogsFeature: WebViewLogsFeature? = null
    internal var webViewRumFeature: WebViewRumFeature? = null

    // TODO RUMM-0000 handle context
    internal val contextProvider: ContextProvider?
        get() {
            return if (coreFeature.initialized.get()) {
                coreFeature.contextProvider
            } else {
                null
            }
        }

    init {
        val isDebug = isAppDebuggable(context)
        if (isEnvironmentNameValid(credentials.envName)) {
            initialize(context, credentials, configuration, isDebug)
        } else {
            @Suppress("ThrowingInternalException")
            throw IllegalArgumentException(MESSAGE_ENV_NAME_NOT_VALID)
        }
    }

    // region SdkCore

    /** @inheritDoc */
    override val time: TimeInfo
        get() {
            return with(coreFeature.timeProvider) {
                val deviceTimeMs = if (this is NoOpTimeProvider) {
                    System.currentTimeMillis()
                } else {
                    getDeviceTimestamp()
                }
                val serverTimeMs = if (this is NoOpTimeProvider) {
                    deviceTimeMs
                } else {
                    getServerTimestamp()
                }
                TimeInfo(
                    deviceTimeNs = TimeUnit.MILLISECONDS.toNanos(deviceTimeMs),
                    serverTimeNs = TimeUnit.MILLISECONDS.toNanos(serverTimeMs),
                    serverTimeOffsetNs = TimeUnit.MILLISECONDS
                        .toNanos(serverTimeMs - deviceTimeMs),
                    serverTimeOffsetMs = serverTimeMs - deviceTimeMs
                )
            }
        }

    /** @inheritDoc */
    override fun registerFeature(feature: Feature) {
        val sdkFeature = SdkFeature(
            coreFeature,
            feature
        )
        features[feature.name] = sdkFeature
        // TODO RUMM-2943 get rid of plugins -> only NDK crash reporting
        sdkFeature.initialize(
            this,
            context.applicationContext,
            if (feature is CrashReportsFeature) {
                feature.plugins
            } else {
                emptyList()
            }
        )
    }

    /** @inheritDoc */
    override fun getFeature(featureName: String): FeatureScope? {
        return features[featureName]
    }

    /** @inheritDoc */
    override fun setVerbosity(level: Int) {
        libraryVerbosity = level
    }

    /** @inheritDoc */
    override fun getVerbosity(): Int {
        return libraryVerbosity
    }

    /** @inheritDoc */
    override fun setTrackingConsent(consent: TrackingConsent) {
        coreFeature.trackingConsentProvider.setConsent(consent)
    }

    /** @inheritDoc */
    override fun setUserInfo(userInfo: UserInfo) {
        coreFeature.userInfoProvider.setUserInfo(userInfo)
    }

    /** @inheritDoc */
    override fun addUserProperties(extraInfo: Map<String, Any?>) {
        coreFeature.userInfoProvider.addUserProperties(extraInfo)
    }

    /** @inheritDoc */
    override fun clearAllData() {
        features.values.forEach {
            it.clearAllData()
        }
    }

    /** @inheritDoc */
    override fun stop() {
        features.forEach {
            it.value.stop()
            // TODO RUMM-0000 Temporary thing
            when (it.key) {
                Feature.LOGS_FEATURE_NAME -> logsFeature = null
                Feature.TRACING_FEATURE_NAME -> tracingFeature = null
                Feature.RUM_FEATURE_NAME -> rumFeature = null
                CrashReportsFeature.CRASH_FEATURE_NAME -> crashReportsFeature = null
                WebViewLogsFeature.WEB_LOGS_FEATURE_NAME -> webViewLogsFeature = null
                WebViewRumFeature.WEB_RUM_FEATURE_NAME -> webViewRumFeature = null
            }
        }
        features.clear()

        coreFeature.stop()
    }

    /** @inheritDoc */
    override fun flushStoredData() {
        // We need to drain and shutdown the executors first to make sure we avoid duplicated
        // data due to async operations.
        coreFeature.drainAndShutdownExecutors()

        features.values.forEach {
            it.flushStoredData()
        }
    }

    /** @inheritDoc */
    override fun updateFeatureContext(
        featureName: String,
        updateCallback: (context: MutableMap<String, Any?>) -> Unit
    ) {
        val feature = features[featureName] ?: return
        contextProvider?.let {
            // workaround for the backward compatibility with DatadogPlugin, we don't want to have
            // context update in plugins in the synchronized block
            val updatedContext = synchronized(feature) {
                val featureContext = it.getFeatureContext(featureName)
                val mutableContext = featureContext.toMutableMap()
                updateCallback(mutableContext)
                it.setFeatureContext(featureName, mutableContext)
                mutableContext
            }
            if (featureName == Feature.RUM_FEATURE_NAME) {
                updateContextInPlugins(updatedContext)
            }
        }
    }

    /** @inheritDoc */
    override fun getFeatureContext(featureName: String): Map<String, Any?> {
        return contextProvider?.getFeatureContext(featureName) ?: emptyMap()
    }

    /** @inheritDoc */
    override fun setEventReceiver(featureName: String, receiver: FeatureEventReceiver) {
        val feature = features[featureName]
        if (feature == null) {
            internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                MISSING_FEATURE_FOR_EVENT_RECEIVER.format(Locale.US, featureName)
            )
        } else {
            if (feature.eventReceiver.get() != null) {
                internalLogger.log(
                    InternalLogger.Level.INFO,
                    InternalLogger.Target.USER,
                    EVENT_RECEIVER_ALREADY_EXISTS.format(Locale.US, featureName)
                )
            }
            feature.eventReceiver.set(receiver)
        }
    }

    /** @inheritDoc */
    override fun removeEventReceiver(featureName: String) {
        features[featureName]?.eventReceiver?.set(null)
    }

    /**
     * Returns all registered features.
     */
    fun getAllFeatures(): List<FeatureScope> {
        return features.values.toList()
    }

    // endregion

    // region Internal Initialization

    private fun initialize(
        context: Context,
        credentials: Credentials,
        configuration: Configuration,
        isDebug: Boolean
    ) {
        val appContext = context.applicationContext

        var mutableConfig = configuration
        if (isDebug and configuration.coreConfig.enableDeveloperModeWhenDebuggable) {
            mutableConfig = modifyConfigurationForDeveloperDebug(configuration)
            setVerbosity(Log.VERBOSE)
        }

        // Special case -- needs to apply to the RUM config before initializing it.
        mutableConfig.additionalConfig[Datadog.DD_TELEMETRY_CONFIG_SAMPLE_RATE_TAG]?. let {
            if (it is Number && mutableConfig.rumConfig != null) {
                mutableConfig = mutableConfig.copy(
                    rumConfig = mutableConfig.rumConfig?.copy(
                        telemetryConfigurationSamplingRate = it.toFloat()
                    )
                )
            }
        }

        // always initialize Core Features first
        coreFeature = CoreFeature()
        coreFeature.initialize(
            appContext,
            instanceId,
            credentials,
            mutableConfig.coreConfig,
            TrackingConsent.PENDING
        )

        applyAdditionalConfiguration(mutableConfig.additionalConfig)

        initializeLogsFeature(mutableConfig.logsConfig)
        initializeTracingFeature(mutableConfig.tracesConfig)
        initializeRumFeature(mutableConfig.rumConfig)
        initializeCrashReportFeature(mutableConfig.crashReportConfig)

        coreFeature.ndkCrashHandler.handleNdkCrash(this)

        setupLifecycleMonitorCallback(appContext)

        setupShutdownHook()
        sendConfigurationTelemetryEvent(configuration)
    }

    private fun initializeLogsFeature(configuration: Configuration.Feature.Logs?) {
        if (configuration != null) {
            val logsFeature = LogsFeature(configuration)
            this.logsFeature = logsFeature
            registerFeature(logsFeature)

            val webViewLogsFeature = WebViewLogsFeature(configuration.endpointUrl)
            this.webViewLogsFeature = webViewLogsFeature
            registerFeature(webViewLogsFeature)
        }
    }

    private fun initializeCrashReportFeature(configuration: Configuration.Feature.CrashReport?) {
        if (configuration != null) {
            val crashReportsFeature = CrashReportsFeature(configuration.plugins)
            this.crashReportsFeature = crashReportsFeature
            registerFeature(crashReportsFeature)
        }
    }

    private fun initializeTracingFeature(configuration: Configuration.Feature.Tracing?) {
        if (configuration != null) {
            val tracingFeature = TracingFeature(configuration)
            this.tracingFeature = tracingFeature
            registerFeature(tracingFeature)
        }
    }

    private fun initializeRumFeature(configuration: Configuration.Feature.RUM?) {
        if (configuration != null) {
            if (coreFeature.rumApplicationId.isNullOrBlank()) {
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    WARNING_MESSAGE_APPLICATION_ID_IS_NULL
                )
            }
            val rumFeature = RumFeature(configuration, coreFeature)
            this.rumFeature = rumFeature
            registerFeature(rumFeature)

            val webViewRumFeature = WebViewRumFeature(configuration.endpointUrl, coreFeature)
            this.webViewRumFeature = webViewRumFeature
            registerFeature(webViewRumFeature)
        }
    }

    @Suppress("FunctionMaxLength")
    private fun modifyConfigurationForDeveloperDebug(configuration: Configuration): Configuration {
        return configuration.copy(
            coreConfig = configuration.coreConfig.copy(
                batchSize = BatchSize.SMALL,
                uploadFrequency = UploadFrequency.FREQUENT
            ),
            rumConfig = configuration.rumConfig?.copy(
                samplingRate = 100.0f
            )
        )
    }

    @Suppress("ComplexMethod")
    private fun applyAdditionalConfiguration(
        additionalConfiguration: Map<String, Any>
    ) {
        // NOTE: be careful with the logic in this method - it is a part of initialization sequence,
        // so some things may yet not be initialized -> not accessible, some things may already be
        // initialized and be not mutable anymore
        additionalConfiguration[Datadog.DD_SOURCE_TAG]?.let {
            if (it is String && it.isNotBlank()) {
                coreFeature.sourceName = it
            }
        }

        additionalConfiguration[Datadog.DD_SDK_VERSION_TAG]?.let {
            if (it is String && it.isNotBlank()) {
                coreFeature.sdkVersion = it
            }
        }

        additionalConfiguration[Datadog.DD_APP_VERSION_TAG]?.let {
            if (it is String && it.isNotBlank()) {
                coreFeature.packageVersionProvider.version = it
            }
        }
    }

    private fun setupLifecycleMonitorCallback(appContext: Context) {
        if (appContext is Application) {
            val callback = ProcessLifecycleCallback(coreFeature.networkInfoProvider, appContext)
            appContext.registerActivityLifecycleCallbacks(ProcessLifecycleMonitor(callback))
        }
    }

    private fun isEnvironmentNameValid(envName: String): Boolean {
        return envName.matches(Regex(ENV_NAME_VALIDATION_REG_EX))
    }

    private fun isAppDebuggable(context: Context): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private fun setupShutdownHook() {
        // Issue #154 (“Thread starting during runtime shutdown”)
        // Make sure we stop Datadog when the Runtime shuts down
        try {
            val hookRunnable = Runnable { stop() }

            @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
            val hook = Thread(hookRunnable, SHUTDOWN_THREAD_NAME)
            @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
            Runtime.getRuntime().addShutdownHook(hook)
        } catch (e: IllegalStateException) {
            // Most probably Runtime is already shutting down
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                "Unable to add shutdown hook, Runtime is already shutting down",
                e
            )
            stop()
        } catch (e: IllegalArgumentException) {
            // can only happen if hook is already added, or already running
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                "Shutdown hook was rejected",
                e
            )
        } catch (e: SecurityException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                "Security Manager denied adding shutdown hook ",
                e
            )
        }
    }

    @Suppress("FunctionMaxLength")
    private fun sendConfigurationTelemetryEvent(configuration: Configuration) {
        val runnable = Runnable {
            val monitor = GlobalRum.get() as? AdvancedRumMonitor
            monitor?.sendConfigurationTelemetryEvent(configuration)
        }
        coreFeature.uploadExecutorService.scheduleSafe(
            "Configuration telemetry",
            CONFIGURATION_TELEMETRY_DELAY_MS,
            TimeUnit.MILLISECONDS,
            runnable
        )
    }

    private fun updateContextInPlugins(rumContext: Map<String, Any?>) {
        val applicationId = rumContext["application_id"] as? String
        val sessionId = rumContext["session_id"] as? String
        val viewId = rumContext["view_id"] as? String
        val pluginContext = DatadogContext(
            DatadogRumContext(
                applicationId,
                sessionId,
                viewId
            )
        )
        // toSet is needed because some features share the same plugins
        features.values.flatMap {
            @Suppress("DEPRECATION")
            it.getPlugins()
        }.toSet().forEach {
            it.onContextChanged(pluginContext)
        }
    }

    // endregion

    companion object {
        internal const val SHUTDOWN_THREAD_NAME = "datadog_shutdown"

        internal const val ENV_NAME_VALIDATION_REG_EX = "[a-zA-Z0-9_:./-]{0,195}[a-zA-Z0-9_./-]"
        internal const val MESSAGE_ENV_NAME_NOT_VALID =
            "The environment name should contain maximum 196 of the following allowed characters " +
                "[a-zA-Z0-9_:./-] and should never finish with a semicolon." +
                "In this case the Datadog SDK will not be initialised."

        internal const val WARNING_MESSAGE_APPLICATION_ID_IS_NULL =
            "You're trying to enable RUM but no Application Id was provided. " +
                "Please pass this value into the Datadog Credentials:\n" +
                "val credentials = " +
                "Credentials" +
                "(\"<CLIENT_TOKEN>\", \"<ENVIRONMENT>\", \"<VARIANT>\", \"<APPLICATION_ID>\")\n" +
                "Datadog.initialize(context, credentials, configuration, trackingConsent);"

        internal const val MISSING_FEATURE_FOR_EVENT_RECEIVER =
            "Cannot add event receiver for feature \"%s\", it is not registered."
        internal const val EVENT_RECEIVER_ALREADY_EXISTS =
            "Feature \"%s\" already has event receiver registered, overwriting it."

        internal val CONFIGURATION_TELEMETRY_DELAY_MS = TimeUnit.SECONDS.toMillis(5)
    }
}
