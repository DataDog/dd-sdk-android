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
import com.datadog.android.core.internal.lifecycle.ProcessLifecycleCallback
import com.datadog.android.core.internal.lifecycle.ProcessLifecycleMonitor
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.core.model.UserInfo
import com.datadog.android.error.internal.CrashReportsFeature
import com.datadog.android.log.internal.LogsFeature
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.tracing.internal.TracingFeature
import com.datadog.android.v2.api.SDKCore
import com.datadog.android.v2.api.SDKFeature
import com.datadog.android.v2.api.SDKFeatureStorageConfiguration
import com.datadog.android.v2.api.SDKFeatureUploadConfiguration
import com.datadog.android.webview.internal.log.WebViewLogsFeature
import com.datadog.android.webview.internal.rum.WebViewRumFeature

/**
 * Internal implementation of the [SDKCore] interface.
 * @param credentials the Datadog credentials for this instance
 */
class DatadogCore(
    context: Context,
    internal val credentials: Credentials,
    configuration: Configuration
) : SDKCore {

    internal var libraryVerbosity = Int.MAX_VALUE
    internal val startupTimeNs: Long = System.nanoTime()

    init {
        val isDebug = isAppDebuggable(context)
        if (isEnvironmentNameValid(credentials.envName)) {
            initialize(context, credentials, configuration, isDebug)
        } else {
            throw IllegalArgumentException(MESSAGE_ENV_NAME_NOT_VALID)
        }
    }

    // region SDKCore

    /** @inheritDoc */
    override fun registerFeature(
        featureName: String,
        storageConfiguration: SDKFeatureStorageConfiguration,
        uploadConfiguration: SDKFeatureUploadConfiguration
    ) {
        // TODO-2138
    }

    /** @inheritDoc */
    override fun getFeature(featureName: String): SDKFeature? {
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
        CoreFeature.trackingConsentProvider.setConsent(consent)
    }

    /** @inheritDoc */
    override fun setUserInfo(userInfo: UserInfo) {
        CoreFeature.userInfoProvider.setUserInfo(userInfo)
    }

    override fun stop() {
        LogsFeature.stop()
        TracingFeature.stop()
        RumFeature.stop()
        CrashReportsFeature.stop()
        CoreFeature.stop()
        WebViewLogsFeature.stop()
        WebViewRumFeature.stop()
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
        CoreFeature.initialize(
            appContext,
            credentials,
            mutableConfig.coreConfig,
            TrackingConsent.PENDING
        )

        applyAdditionalConfiguration(mutableConfig.additionalConfig)

        initializeLogsFeature(mutableConfig.logsConfig, appContext)
        initializeTracingFeature(mutableConfig.tracesConfig, appContext)
        initializeRumFeature(mutableConfig.rumConfig, appContext)
        initializeCrashReportFeature(mutableConfig.crashReportConfig, appContext)

        CoreFeature.ndkCrashHandler.handleNdkCrash(
            LogsFeature.persistenceStrategy.getWriter(),
            RumFeature.persistenceStrategy.getWriter()
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
            LogsFeature.initialize(appContext, configuration)
            WebViewLogsFeature.initialize(appContext, configuration)
        }
    }

    private fun initializeCrashReportFeature(
        configuration: Configuration.Feature.CrashReport?,
        appContext: Context
    ) {
        if (configuration != null) {
            CrashReportsFeature.initialize(appContext, configuration)
        }
    }

    private fun initializeTracingFeature(
        configuration: Configuration.Feature.Tracing?,
        appContext: Context
    ) {
        if (configuration != null) {
            TracingFeature.initialize(appContext, configuration)
        }
    }

    private fun initializeRumFeature(
        configuration: Configuration.Feature.RUM?,
        appContext: Context
    ) {
        if (configuration != null) {
            if (CoreFeature.rumApplicationId.isNullOrBlank()) {
                devLogger.w(WARNING_MESSAGE_APPLICATION_ID_IS_NULL)
            }
            RumFeature.initialize(appContext, configuration)
            WebViewRumFeature.initialize(appContext, configuration)
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

    private fun applyAdditionalConfiguration(
        additionalConfiguration: Map<String, Any>
    ) {
        // NOTE: be careful with the logic in this method - it is a part of initialization sequence,
        // so some things may yet not be initialized -> not accessible, some things may already be
        // initialized and be not mutable anymore
        additionalConfiguration[Datadog.DD_SOURCE_TAG]?.let {
            if (it is String && it.isNotBlank()) {
                CoreFeature.sourceName = it
            }
        }

        additionalConfiguration[Datadog.DD_SDK_VERSION_TAG]?.let {
            if (it is String && it.isNotBlank()) {
                CoreFeature.sdkVersion = it
            }
        }
    }

    private fun setupLifecycleMonitorCallback(appContext: Context) {
        if (appContext is Application) {
            val callback = ProcessLifecycleCallback(CoreFeature.networkInfoProvider, appContext)
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
