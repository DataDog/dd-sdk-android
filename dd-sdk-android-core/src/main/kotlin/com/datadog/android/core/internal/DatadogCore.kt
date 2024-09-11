/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.context.TimeInfo
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureContextUpdateReceiver
import com.datadog.android.api.feature.FeatureEventReceiver
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.core.internal.lifecycle.ProcessLifecycleCallback
import com.datadog.android.core.internal.lifecycle.ProcessLifecycleMonitor
import com.datadog.android.core.internal.logger.SdkInternalLogger
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import com.datadog.android.core.internal.time.DefaultAppStartTimeProvider
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.core.internal.utils.submitSafe
import com.datadog.android.core.thread.FlushableExecutorService
import com.datadog.android.error.internal.CrashReportsFeature
import com.datadog.android.ndk.internal.NdkCrashHandler
import com.datadog.android.privacy.TrackingConsent
import com.google.gson.JsonObject
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Internal implementation of the [SdkCore] interface.
 * @param context the application's Android [Context]
 * @param instanceId the unique identifier for this instance
 * @param name the name of this instance
 * @param internalLoggerProvider Provider for [InternalLogger] instance.
 * @param executorServiceFactory Custom factory for executors, used only in unit-tests
 * @param buildSdkVersionProvider Build.VERSION.SDK_INT provider used for the test
 */
@Suppress("TooManyFunctions")
internal class DatadogCore(
    context: Context,
    internal val instanceId: String,
    override val name: String,
    internalLoggerProvider: (FeatureSdkCore) -> InternalLogger = { SdkInternalLogger(it) },
    // only for unit tests
    private val executorServiceFactory: FlushableExecutorService.Factory? = null,
    private val buildSdkVersionProvider: BuildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT
) : InternalSdkCore {

    internal lateinit var coreFeature: CoreFeature

    private lateinit var shutdownHook: Thread

    internal val features: MutableMap<String, SdkFeature> = ConcurrentHashMap()

    internal val context: Context = context.applicationContext

    internal val contextProvider: ContextProvider?
        get() {
            return if (coreFeature.initialized.get()) {
                coreFeature.contextProvider
            } else {
                null
            }
        }

    internal val isActive: Boolean
        get() = coreFeature.initialized.get()

    private var processLifecycleMonitor: ProcessLifecycleMonitor? = null

    // region SdkCore

    /** @inheritDoc */
    override val time: TimeInfo
        get() {
            return with(coreFeature.timeProvider) {
                val deviceTimeMs = getDeviceTimestamp()
                val serverTimeMs = getServerTimestamp()
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
    override val service: String
        get() = coreFeature.serviceName

    /** @inheritDoc */
    override val firstPartyHostResolver: FirstPartyHostHeaderTypeResolver
        get() = coreFeature.firstPartyHostHeaderTypeResolver

    /** @inheritDoc */
    override val internalLogger: InternalLogger = internalLoggerProvider(this)

    /** @inheritDoc */
    override var isDeveloperModeEnabled: Boolean = false
        internal set

    /** @inheritDoc */
    override fun registerFeature(feature: Feature) {
        val sdkFeature = SdkFeature(
            coreFeature,
            feature,
            internalLogger
        )
        features[feature.name] = sdkFeature
        sdkFeature.initialize(context, instanceId)

        when (feature.name) {
            Feature.LOGS_FEATURE_NAME -> {
                coreFeature.ndkCrashHandler
                    .handleNdkCrash(this, NdkCrashHandler.ReportTarget.LOGS)
            }

            Feature.RUM_FEATURE_NAME -> {
                coreFeature.ndkCrashHandler
                    .handleNdkCrash(this, NdkCrashHandler.ReportTarget.RUM)
            }
        }
    }

    /** @inheritDoc */
    override fun getFeature(featureName: String): FeatureScope? {
        return features[featureName]
    }

    /** @inheritDoc */
    @AnyThread
    override fun setTrackingConsent(consent: TrackingConsent) {
        coreFeature.trackingConsentProvider.setConsent(consent)
    }

    /** @inheritDoc */
    @AnyThread
    override fun setUserInfo(
        id: String?,
        name: String?,
        email: String?,
        extraInfo: Map<String, Any?>
    ) {
        coreFeature.userInfoProvider.setUserInfo(id, name, email, extraInfo)
    }

    /** @inheritDoc */
    @AnyThread
    override fun addUserProperties(extraInfo: Map<String, Any?>) {
        coreFeature.userInfoProvider.addUserProperties(extraInfo)
    }

    /** @inheritDoc */
    @AnyThread
    override fun clearAllData() {
        features.values.forEach {
            it.clearAllData()
        }
        getPersistenceExecutorService().submitSafe("Clear all data", internalLogger) {
            coreFeature.deleteLastViewEvent()
            coreFeature.deleteLastFatalAnrSent()
        }
    }

    /** @inheritDoc */
    override fun updateFeatureContext(
        featureName: String,
        updateCallback: (context: MutableMap<String, Any?>) -> Unit
    ) {
        val feature = features[featureName] ?: return
        contextProvider?.let {
            synchronized(feature) {
                val featureContext = it.getFeatureContext(featureName)
                val mutableContext = featureContext.toMutableMap()
                updateCallback(mutableContext)
                it.setFeatureContext(featureName, mutableContext)
                // notify all the other features
                features.filter { it.key != featureName }
                    .forEach { (_, feature) ->
                        feature.notifyContextUpdated(featureName, mutableContext.toMap())
                    }
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
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { MISSING_FEATURE_FOR_EVENT_RECEIVER.format(Locale.US, featureName) }
            )
        } else {
            if (feature.eventReceiver.get() != null) {
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    { EVENT_RECEIVER_ALREADY_EXISTS.format(Locale.US, featureName) }
                )
            }
            feature.eventReceiver.set(receiver)
        }
    }

    override fun setContextUpdateReceiver(featureName: String, listener: FeatureContextUpdateReceiver) {
        val feature = features[featureName]
        if (feature == null) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { MISSING_FEATURE_FOR_CONTEXT_UPDATE_LISTENER.format(Locale.US, featureName) }
            )
        } else {
            feature.setContextUpdateListener(listener)
        }
    }

    override fun removeContextUpdateReceiver(featureName: String, listener: FeatureContextUpdateReceiver) {
        features[featureName]?.removeContextUpdateListener(listener)
    }

    /** @inheritDoc */
    override fun removeEventReceiver(featureName: String) {
        features[featureName]?.eventReceiver?.set(null)
    }

    /** @inheritDoc */
    override fun createSingleThreadExecutorService(executorContext: String): ExecutorService {
        return coreFeature.createExecutorService(executorContext)
    }

    /** @inheritDoc */
    override fun createScheduledExecutorService(executorContext: String): ScheduledExecutorService {
        return coreFeature.createScheduledExecutorService(executorContext)
    }

    // endregion

    // region InternalSdkCore

    override val networkInfo: NetworkInfo
        get() = coreFeature.networkInfoProvider.getLatestNetworkInfo()

    override val trackingConsent: TrackingConsent
        get() = coreFeature.trackingConsentProvider.getConsent()

    override val rootStorageDir: File
        get() = coreFeature.storageDir

    @get:WorkerThread
    override val lastViewEvent: JsonObject?
        get() = coreFeature.lastViewEvent

    @get:WorkerThread
    override val lastFatalAnrSent: Long?
        get() = coreFeature.lastFatalAnrSent

    override val appStartTimeNs: Long
        get() = coreFeature.appStartTimeNs

    @WorkerThread
    override fun writeLastViewEvent(data: ByteArray) {
        // we need to write it only if we are going to read ApplicationExitInfo (available on
        // API 30+) or if there is NDK crash tracking enabled
        if (buildSdkVersionProvider.version >= Build.VERSION_CODES.R ||
            features.containsKey(Feature.NDK_CRASH_REPORTS_FEATURE_NAME)
        ) {
            coreFeature.writeLastViewEvent(data)
        } else {
            internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.MAINTAINER,
                { NO_NEED_TO_WRITE_LAST_VIEW_EVENT }
            )
        }
    }

    @WorkerThread
    override fun deleteLastViewEvent() {
        coreFeature.deleteLastViewEvent()
    }

    @WorkerThread
    override fun writeLastFatalAnrSent(anrTimestamp: Long) {
        coreFeature.writeLastFatalAnrSent(anrTimestamp)
    }

    override fun getPersistenceExecutorService(): ExecutorService {
        return coreFeature.persistenceExecutorService
    }

    override fun getAllFeatures(): List<FeatureScope> {
        return features.values.toList()
    }

    override fun getDatadogContext(): DatadogContext? {
        return contextProvider?.context
    }

    // endregion

    // region Internal

    internal fun initialize(configuration: Configuration) {
        if (!isEnvironmentNameValid(configuration.env)) {
            @Suppress("ThrowingInternalException")
            throw IllegalArgumentException(MESSAGE_ENV_NAME_NOT_VALID)
        }

        val isDebug = isAppDebuggable(context)

        var mutableConfig = configuration
        if (isDebug and configuration.coreConfig.enableDeveloperModeWhenDebuggable) {
            mutableConfig = modifyConfigurationForDeveloperDebug(configuration)
            isDeveloperModeEnabled = true
            Datadog.setVerbosity(Log.VERBOSE)
        }

        // always initialize Core Features first
        val flushableExecutorServiceFactory =
            executorServiceFactory ?: CoreFeature.DEFAULT_FLUSHABLE_EXECUTOR_SERVICE_FACTORY
        coreFeature = CoreFeature(
            internalLogger,
            DefaultAppStartTimeProvider(),
            flushableExecutorServiceFactory,
            CoreFeature.DEFAULT_SCHEDULED_EXECUTOR_SERVICE_FACTORY
        )
        coreFeature.initialize(
            context,
            instanceId,
            mutableConfig,
            TrackingConsent.PENDING
        )

        applyAdditionalConfiguration(mutableConfig.additionalConfig)

        if (mutableConfig.crashReportsEnabled) {
            initializeCrashReportFeature()
        }

        setupLifecycleMonitorCallback(context)

        setupShutdownHook()
        sendCoreConfigurationTelemetryEvent(configuration)
    }

    private fun initializeCrashReportFeature() {
        val crashReportsFeature = CrashReportsFeature(this)
        registerFeature(crashReportsFeature)
    }

    @Suppress("FunctionMaxLength")
    private fun modifyConfigurationForDeveloperDebug(configuration: Configuration): Configuration {
        return configuration.copy(
            coreConfig = configuration.coreConfig.copy(
                batchSize = BatchSize.SMALL,
                uploadFrequency = UploadFrequency.FREQUENT
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
            processLifecycleMonitor = ProcessLifecycleMonitor(
                ProcessLifecycleCallback(
                    appContext,
                    name,
                    internalLogger
                )
            ).apply {
                appContext.registerActivityLifecycleCallbacks(this)
            }
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
            shutdownHook = Thread(hookRunnable, SHUTDOWN_THREAD_NAME)
            @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
            Runtime.getRuntime().addShutdownHook(shutdownHook)
        } catch (e: IllegalStateException) {
            // Most probably Runtime is already shutting down
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Unable to add shutdown hook, Runtime is already shutting down" },
                e
            )
            stop()
        } catch (e: IllegalArgumentException) {
            // can only happen if hook is already added, or already running
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Shutdown hook was rejected" },
                e
            )
        } catch (e: SecurityException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Security Manager denied adding shutdown hook " },
                e
            )
        }
    }

    private fun removeShutdownHook() {
        if (this::shutdownHook.isInitialized) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook)
            } catch (e: IllegalStateException) {
                // Most probably Runtime is already shutting down
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.MAINTAINER,
                    { "Unable to remove shutdown hook, Runtime is already shutting down" },
                    e
                )
            } catch (e: SecurityException) {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.MAINTAINER,
                    { "Security Manager denied removing shutdown hook " },
                    e
                )
            }
        }
    }

    @Suppress("FunctionMaxLength")
    private fun sendCoreConfigurationTelemetryEvent(configuration: Configuration) {
        val runnable = Runnable {
            val rumFeature = getFeature(Feature.RUM_FEATURE_NAME) ?: return@Runnable
            val coreConfigurationEvent = mapOf(
                "type" to "telemetry_configuration",
                "track_errors" to (configuration.crashReportsEnabled),
                "batch_size" to configuration.coreConfig.batchSize.windowDurationMs,
                "batch_upload_frequency" to configuration.coreConfig.uploadFrequency.baseStepMs,
                "use_proxy" to (configuration.coreConfig.proxy != null),
                "use_local_encryption" to (configuration.coreConfig.encryption != null),
                "batch_processing_level" to configuration.coreConfig.batchProcessingLevel.maxBatchesPerUploadJob,
                "use_persistence_strategy_factory" to (configuration.coreConfig.persistenceStrategyFactory != null)
            )
            rumFeature.sendEvent(coreConfigurationEvent)
        }

        coreFeature.uploadExecutorService.scheduleSafe(
            "Configuration telemetry",
            CONFIGURATION_TELEMETRY_DELAY_MS,
            TimeUnit.MILLISECONDS,
            internalLogger,
            runnable
        )
    }

    /**
     * Stops all process for this instance of the Datadog SDK.
     */
    internal fun stop() {
        features.forEach {
            it.value.stop()
        }
        features.clear()

        if (context is Application && processLifecycleMonitor != null) {
            context.unregisterActivityLifecycleCallbacks(processLifecycleMonitor)
        }

        coreFeature.stop()
        isDeveloperModeEnabled = false

        removeShutdownHook()
    }

    /**
     * Flushes all stored data (send everything right now).
     */
    @WorkerThread
    internal fun flushStoredData() {
        // We need to drain and shutdown the executors first to make sure we avoid duplicated
        // data due to async operations.
        coreFeature.drainAndShutdownExecutors()

        features.values.forEach {
            it.flushStoredData()
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

        internal const val MISSING_FEATURE_FOR_EVENT_RECEIVER =
            "Cannot add event receiver for feature \"%s\", it is not registered."
        internal const val MISSING_FEATURE_FOR_CONTEXT_UPDATE_LISTENER =
            "Cannot add event listener for feature \"%s\", it is not registered."
        internal const val EVENT_RECEIVER_ALREADY_EXISTS =
            "Feature \"%s\" already has event receiver registered, overwriting it."

        internal const val NO_NEED_TO_WRITE_LAST_VIEW_EVENT =
            "No need to write last RUM view event: NDK" +
                " crash reports feature is not enabled and API is below 30."

        internal val CONFIGURATION_TELEMETRY_DELAY_MS = TimeUnit.SECONDS.toMillis(5)

        // fallback for APIs below Android N, see [DefaultAppStartTimeProvider].
        internal val startupTimeNs: Long = System.nanoTime()
    }
}
