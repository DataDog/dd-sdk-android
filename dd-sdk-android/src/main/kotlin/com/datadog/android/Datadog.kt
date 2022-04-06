/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
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
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.monitor.DatadogRumMonitor
import com.datadog.android.tracing.internal.TracingFeature
import com.datadog.android.webview.internal.log.WebViewLogsFeature
import com.datadog.android.webview.internal.rum.WebViewRumFeature
import java.lang.IllegalArgumentException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * This class initializes the Datadog SDK, and sets up communication with the server.
 */
@Suppress("TooManyFunctions")
object Datadog {

    internal val initialized = AtomicBoolean(false)
    internal val startupTimeNs: Long = System.nanoTime()

    internal var libraryVerbosity = Int.MAX_VALUE
        private set
    internal var isDebug = false

    // region Initialization

    /**
     * Initializes the Datadog SDK.
     * @param context your application context
     * @param credentials your organization credentials
     * @param configuration the configuration for the SDK library
     * @param trackingConsent as the initial state of the tracking consent flag.
     * @see [Credentials]
     * @see [Configuration]
     * @see [TrackingConsent]
     * @throws IllegalArgumentException if the env name is using illegal characters and your
     * application is in debug mode otherwise returns false and stops initializing the SDK
     */
    @Suppress("LongMethod")
    @JvmStatic
    fun initialize(
        context: Context,
        credentials: Credentials,
        configuration: Configuration,
        trackingConsent: TrackingConsent
    ) {
        if (initialized.get()) {
            devLogger.w(MESSAGE_ALREADY_INITIALIZED)
            return
        }

        val appContext = context.applicationContext
        // the logic in this function depends on this value so always resolve isDebug first
        isDebug = resolveIsDebug(context)

        if (!validateEnvironmentName(credentials.envName)) {
            return
        }

        var mutableConfig = configuration
        if (isDebug and configuration.coreConfig.enableDeveloperModeWhenDebuggable) {
            mutableConfig = modifyConfigurationForDeveloperDebug(configuration)
            setVerbosity(Log.VERBOSE)
        }

        // always initialize Core Features first
        CoreFeature.initialize(appContext, credentials, mutableConfig.coreConfig, trackingConsent)

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

        initialized.set(true)

        // Issue #154 (“Thread starting during runtime shutdown”)
        // Make sure we stop Datadog when the Runtime shuts down
        try {
            val hookRunnable = Runnable { stop() }

            @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
            val hook = Thread(hookRunnable, SHUTDOWN_THREAD)
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

    /**
     * Checks if the Datadog SDK was already initialized.
     * @return true if the SDK was initialized, false otherwise
     */
    @JvmStatic
    fun isInitialized(): Boolean {
        return initialized.get()
    }

    // endregion

    // region Global methods

    /**
     * Clears all data that has not already been sent to Datadog servers.
     */
    @JvmStatic
    fun clearAllData() {
        LogsFeature.clearAllData()
        CrashReportsFeature.clearAllData()
        RumFeature.clearAllData()
        TracingFeature.clearAllData()
        WebViewLogsFeature.clearAllData()
        WebViewRumFeature.clearAllData()
    }

    // Stop all Datadog work (for test purposes).
    internal fun stop() {
        if (initialized.get()) {
            LogsFeature.stop()
            TracingFeature.stop()
            RumFeature.stop()
            CrashReportsFeature.stop()
            CoreFeature.stop()
            WebViewLogsFeature.stop()
            WebViewRumFeature.stop()
            isDebug = false
            initialized.set(false)
        }
    }

    // Executes all the pending queues in the upload/persistence executors.
    // Tries to send all the granted data for each feature and then clears the folders and shuts
    // down the persistence and the upload executors.
    // You should not use this method in production code. By calling this method you will basically
    // stop the SDKs persistence - upload streams and will leave it in an inconsistent state. This
    // method is mainly for test purposes.
    @Suppress("unused")
    private fun flushAndShutdownExecutors() {
        // Note for the future: if we decide to make this a public feature,
        // we need to drain, execute and flush from a background thread or ensure we're
        // not in the main thread!
        if (initialized.get()) {
            (GlobalRum.get() as? DatadogRumMonitor)?.let {
                it.stopKeepAliveCallback()
                it.drainExecutorService()
            }
            // We need to drain and shutdown the executors first to make sure we avoid duplicated
            // data due to async operations.
            CoreFeature.drainAndShutdownExecutors()
            LogsFeature.flushStoredData()
            TracingFeature.flushStoredData()
            RumFeature.flushStoredData()
            CrashReportsFeature.flushStoredData()
            WebViewLogsFeature.flushStoredData()
            WebViewRumFeature.flushStoredData()
        }
    }

    /**
     * Sets the verbosity of the Datadog library.
     *
     * Messages with a priority level equal or above the given level will be sent to Android's
     * Logcat.
     *
     * @param level one of the Android [android.util.Log] constants
     * ([android.util.Log.VERBOSE], [android.util.Log.DEBUG], [android.util.Log.INFO],
     * [android.util.Log.WARN], [android.util.Log.ERROR], [android.util.Log.ASSERT]).
     */
    @JvmStatic
    fun setVerbosity(level: Int) {
        libraryVerbosity = level
    }

    /**
     * Sets the tracking consent regarding the data collection for the Datadog library.
     *
     * @param consent which can take one of the values
     * ([TrackingConsent.PENDING], [TrackingConsent.GRANTED], [TrackingConsent.NOT_GRANTED])
     */
    @JvmStatic
    fun setTrackingConsent(consent: TrackingConsent) {
        CoreFeature.trackingConsentProvider.setConsent(consent)
    }

    /**
     * Sets the user information.
     *
     * @param id (nullable) a unique user identifier (relevant to your business domain)
     * @param name (nullable) the user name or alias
     * @param email (nullable) the user email
     * @param extraInfo additional information. An extra information can be
     * nested up to 8 levels deep. Keys using more than 8 levels will be sanitized by SDK.
     */
    @JvmStatic
    @JvmOverloads
    fun setUserInfo(
        id: String? = null,
        name: String? = null,
        email: String? = null,
        extraInfo: Map<String, Any?> = emptyMap()
    ) {
        CoreFeature.userInfoProvider.setUserInfo(
            UserInfo(
                id,
                name,
                email,
                extraInfo
            )
        )
    }

    /**
     * Utility setting to inspect the active RUM View.
     * If set, a debugging outline will be displayed on top of the application, describing the name
     * of the active RUM View. May be used to debug issues with RUM instrumentation in your app.
     *
     * @param enable if enabled, then app will show an overlay describing the active RUM view.
     */
    @JvmStatic
    fun enableRumDebugging(enable: Boolean) {
        if (enable) {
            RumFeature.enableDebugging()
        } else {
            RumFeature.disableDebugging()
        }
    }

    // endregion

    // region Internal Initialization

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
        additionalConfiguration[DD_SOURCE_TAG]?.let {
            if (it is String && it.isNotBlank()) {
                CoreFeature.sourceName = it
            }
        }

        additionalConfiguration[DD_SDK_VERSION_TAG]?.let {
            if (it is String && it.isNotBlank()) {
                CoreFeature.sdkVersion = it
            }
        }
    }

    @Suppress("ThrowingInternalException")
    private fun validateEnvironmentName(envName: String): Boolean {
        if (!envName.matches(Regex(ENV_NAME_VALIDATION_REG_EX))) {
            if (isDebug) {
                throw IllegalArgumentException(MESSAGE_ENV_NAME_NOT_VALID)
            } else {
                devLogger.e(MESSAGE_ENV_NAME_NOT_VALID)
                return false
            }
        }
        return true
    }

    private fun setupLifecycleMonitorCallback(appContext: Context) {
        if (appContext is Application) {
            val callback = ProcessLifecycleCallback(CoreFeature.networkInfoProvider, appContext)
            appContext.registerActivityLifecycleCallbacks(ProcessLifecycleMonitor(callback))
        }
    }

    private fun resolveIsDebug(context: Context): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    // endregion

    // region Constants

    internal const val MESSAGE_ALREADY_INITIALIZED =
        "The Datadog library has already been initialized."
    internal const val WARNING_MESSAGE_APPLICATION_ID_IS_NULL =
        "You're trying to enable RUM but no Application Id was provided. " +
            "Please pass this value into the Datadog Credentials:\n" +
            "val credentials = " +
            "Credentials" +
            "(\"<CLIENT_TOKEN>\", \"<ENVIRONMENT>\", \"<VARIANT>\", \"<APPLICATION_ID>\")\n" +
            "Datadog.initialize(context, credentials, configuration, trackingConsent);"

    internal const val MESSAGE_SDK_INITIALIZATION_GUIDE =
        "Please add the following code in your application's onCreate() method:\n" +
            "val credentials = Credentials" +
            "(\"<CLIENT_TOKEN>\", \"<ENVIRONMENT>\", \"<VARIANT>\", \"<APPLICATION_ID>\")\n" +
            "Datadog.initialize(context, credentials, configuration, trackingConsent);"

    internal const val MESSAGE_NOT_INITIALIZED = "Datadog has not been initialized.\n " +
        MESSAGE_SDK_INITIALIZATION_GUIDE

    internal const val SHUTDOWN_THREAD = "datadog_shutdown"
    internal const val ENV_NAME_VALIDATION_REG_EX = "[a-zA-Z0-9_:./-]{0,195}[a-zA-Z0-9_./-]"
    internal const val MESSAGE_ENV_NAME_NOT_VALID =
        "The environment name should contain maximum 196 of the following allowed characters " +
            "[a-zA-Z0-9_:./-] and should never finish with a semicolon." +
            "In this case the Datadog SDK will not be initialised."

    internal const val DD_SOURCE_TAG = "_dd.source"
    internal const val DD_SDK_VERSION_TAG = "_dd.sdk_version"

    // endregion
}
