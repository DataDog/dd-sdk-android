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
import com.datadog.android.core.internal.persistence.NoOpDataWriter
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.core.model.UserInfo
import com.datadog.android.error.internal.CrashReportsFeature
import com.datadog.android.log.internal.LogsFeature
import com.datadog.android.log.model.LogEvent
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.sessionreplay.SessionReplayLifecycleCallback
import com.datadog.android.sessionreplay.internal.SessionReplayContextProvider
import com.datadog.android.sessionreplay.internal.SessionReplayFeature
import com.datadog.android.tracing.internal.TracingFeature
import com.datadog.android.v2.api.FeatureScope
import com.datadog.android.v2.api.FeatureStorageConfiguration
import com.datadog.android.v2.api.FeatureUploadConfiguration
import com.datadog.android.v2.api.SDKCore
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.android.webview.internal.log.WebViewLogsFeature
import com.datadog.android.webview.internal.rum.WebViewRumFeature
import com.datadog.opentracing.DDSpan
import com.google.gson.JsonObject

/**
 * Internal implementation of the [SDKCore] interface.
 * @param credentials the Datadog credentials for this instance
 */
internal class DatadogCore(
    context: Context,
    internal val credentials: Credentials,
    configuration: Configuration,
    internal val instanceId: String
) : SDKCore {

    internal var libraryVerbosity = Int.MAX_VALUE

    internal lateinit var coreFeature: CoreFeature

    internal var logsFeature: SdkFeature<LogEvent, Configuration.Feature.Logs>? = null
    internal var tracingFeature: SdkFeature<DDSpan, Configuration.Feature.Tracing>? = null
    internal var rumFeature: SdkFeature<Any, Configuration.Feature.RUM>? = null
    internal var crashReportsFeature: SdkFeature<LogEvent, Configuration.Feature.CrashReport>? =
        null
    internal var webViewLogsFeature: SdkFeature<JsonObject, Configuration.Feature.Logs>? = null
    internal var webViewRumFeature: SdkFeature<Any, Configuration.Feature.RUM>? = null
    internal var sessionReplayFeature: SdkFeature<Any, Configuration.Feature.SessionReplay>? = null

    // TODO RUMM-0000 handle context
    internal val contextProvider: ContextProvider?
        get() {
            return if (coreFeature.initialized.get()) {
                return coreFeature.contextProvider
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

    // region SDKCore

    /** @inheritDoc */
    override fun registerFeature(
        featureName: String,
        storageConfiguration: FeatureStorageConfiguration,
        uploadConfiguration: FeatureUploadConfiguration
    ) {
        // TODO-2138
    }

    /** @inheritDoc */
    override fun getFeature(featureName: String): FeatureScope? {
        // TODO-2138
        return null
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
        logsFeature?.clearAllData()
        crashReportsFeature?.clearAllData()
        rumFeature?.clearAllData()
        tracingFeature?.clearAllData()
        webViewLogsFeature?.clearAllData()
        webViewRumFeature?.clearAllData()
        sessionReplayFeature?.clearAllData()
    }

    /** @inheritDoc */
    override fun stop() {
        logsFeature?.stop()
        logsFeature = null
        tracingFeature?.stop()
        tracingFeature = null
        rumFeature?.stop()
        rumFeature = null
        crashReportsFeature?.stop()
        crashReportsFeature = null
        webViewLogsFeature?.stop()
        webViewLogsFeature = null
        webViewRumFeature?.stop()
        webViewRumFeature = null
        sessionReplayFeature?.stop()
        sessionReplayFeature = null

        coreFeature.stop()
    }

    /** @inheritDoc */
    override fun flushStoredData() {
        // We need to drain and shutdown the executors first to make sure we avoid duplicated
        // data due to async operations.
        coreFeature.drainAndShutdownExecutors()

        logsFeature?.flushStoredData()
        tracingFeature?.flushStoredData()
        rumFeature?.flushStoredData()
        crashReportsFeature?.flushStoredData()
        webViewLogsFeature?.flushStoredData()
        webViewRumFeature?.flushStoredData()
        sessionReplayFeature?.flushStoredData()
    }

    /** @inheritDoc */
    override fun setFeatureContext(feature: String, context: Map<String, Any?>) {
        contextProvider?.setFeatureContext(feature, context)
    }

    /**
     * Returns all registered features.
     */
    fun getAllFeatures(): List<FeatureScope> {
        // TODO-2138
        // should it be a part of SDKCore?
        return emptyList()
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

        initializeLogsFeature(mutableConfig.logsConfig, appContext)
        initializeTracingFeature(mutableConfig.tracesConfig, appContext)
        initializeRumFeature(mutableConfig.rumConfig, appContext)
        initializeCrashReportFeature(mutableConfig.crashReportConfig, appContext)
        initializeSessionReplayFeature(mutableConfig.sessionReplayConfig, appContext)

        coreFeature.ndkCrashHandler.handleNdkCrash(
            logsFeature?.persistenceStrategy?.getWriter() ?: NoOpDataWriter(),
            rumFeature?.persistenceStrategy?.getWriter() ?: NoOpDataWriter()
        )

        setupLifecycleMonitorCallback(appContext)

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
            sdkLogger.e("Unable to add shutdown hook, Runtime is already shutting down", e)
            stop()
        } catch (e: IllegalArgumentException) {
            // can only happen if hook is already added, or already running
            sdkLogger.e("Shutdown hook was rejected", e)
        } catch (e: SecurityException) {
            sdkLogger.e("Security Manager denied adding shutdown hook ", e)
        }
    }

    private fun initializeLogsFeature(
        configuration: Configuration.Feature.Logs?,
        appContext: Context
    ) {
        if (configuration != null) {
            logsFeature = LogsFeature(coreFeature)
            webViewLogsFeature = WebViewLogsFeature(coreFeature)
            logsFeature?.initialize(appContext, configuration)
            webViewLogsFeature?.initialize(appContext, configuration)
        }
    }

    private fun initializeCrashReportFeature(
        configuration: Configuration.Feature.CrashReport?,
        appContext: Context
    ) {
        if (configuration != null) {
            crashReportsFeature = CrashReportsFeature(coreFeature)
            crashReportsFeature?.initialize(appContext, configuration)
        }
    }

    private fun initializeTracingFeature(
        configuration: Configuration.Feature.Tracing?,
        appContext: Context
    ) {
        if (configuration != null) {
            tracingFeature = TracingFeature(coreFeature)
            tracingFeature?.initialize(appContext, configuration)
        }
    }

    private fun initializeRumFeature(
        configuration: Configuration.Feature.RUM?,
        appContext: Context
    ) {
        if (configuration != null) {
            if (coreFeature.rumApplicationId.isNullOrBlank()) {
                devLogger.w(WARNING_MESSAGE_APPLICATION_ID_IS_NULL)
            }
            rumFeature = RumFeature(coreFeature)
            webViewRumFeature = WebViewRumFeature(coreFeature)
            rumFeature?.initialize(appContext, configuration)
            webViewRumFeature?.initialize(appContext, configuration)
        }
    }

    private fun initializeSessionReplayFeature(
        configuration: Configuration.Feature.SessionReplay?,
        appContext: Context
    ) {
        if (configuration != null) {
            sessionReplayFeature = SessionReplayFeature(
                coreFeature,
                SessionReplayLifecycleCallback(SessionReplayContextProvider())
            )
            sessionReplayFeature?.initialize(appContext, configuration)
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
    }
}
