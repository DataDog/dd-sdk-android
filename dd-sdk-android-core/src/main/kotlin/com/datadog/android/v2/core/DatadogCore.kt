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
import androidx.annotation.WorkerThread
import com.datadog.android.Datadog
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.SdkFeature
import com.datadog.android.core.internal.lifecycle.ProcessLifecycleCallback
import com.datadog.android.core.internal.lifecycle.ProcessLifecycleMonitor
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.core.internal.persistence.file.FileWriter
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReaderWriter
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.error.internal.CrashReportsFeature
import com.datadog.android.ndk.DatadogNdkCrashHandler
import com.datadog.android.ndk.NdkCrashHandler
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureEventReceiver
import com.datadog.android.v2.api.FeatureScope
import com.datadog.android.v2.api.FeatureSdkCore
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.api.context.NetworkInfo
import com.datadog.android.v2.api.context.TimeInfo
import com.datadog.android.v2.api.context.UserInfo
import com.datadog.android.v2.core.internal.ContextProvider
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/**
 * Internal implementation of the [SdkCore] interface.
 * @param context the application's Android [Context]
 * @param credentials the Datadog credentials for this instance
 * @param instanceId the unique identifier for this instance
 * @param name the name of this instance
 * @param internalLoggerProvider Provider for [InternalLogger] instance.
 */
@Suppress("TooManyFunctions")
internal class DatadogCore(
    context: Context,
    internal val credentials: Credentials,
    internal val instanceId: String,
    override val name: String,
    internalLoggerProvider: (FeatureSdkCore) -> InternalLogger = { SdkInternalLogger(it) }
) : InternalSdkCore {

    internal lateinit var coreFeature: CoreFeature

    internal val features: MutableMap<String, SdkFeature> = mutableMapOf()

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

    private val ndkLastViewEventFileWriter: FileWriter by lazy {
        BatchFileReaderWriter.create(
            internalLogger = internalLogger,
            encryption = coreFeature.localDataEncryption
        )
    }

    init {
        if (!isEnvironmentNameValid(credentials.env)) {
            @Suppress("ThrowingInternalException")
            throw IllegalArgumentException(MESSAGE_ENV_NAME_NOT_VALID)
        }
    }

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
        private set

    /** @inheritDoc */
    override fun registerFeature(feature: Feature) {
        val sdkFeature = SdkFeature(
            coreFeature,
            feature,
            internalLogger
        )
        features[feature.name] = sdkFeature
        sdkFeature.initialize(context)

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
                MISSING_FEATURE_FOR_EVENT_RECEIVER.format(Locale.US, featureName)
            )
        } else {
            if (feature.eventReceiver.get() != null) {
                internalLogger.log(
                    InternalLogger.Level.WARN,
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

    // endregion

    // region InternalSdkCore

    override val networkInfo: NetworkInfo
        get() = coreFeature.networkInfoProvider.getLatestNetworkInfo()

    override val trackingConsent: TrackingConsent
        get() = coreFeature.trackingConsentProvider.getConsent()

    override val rootStorageDir: File
        get() = coreFeature.storageDir

    @WorkerThread
    override fun writeLastViewEvent(data: ByteArray) {
        // directory structure may not exist: currently it is a file which is located in NDK reports
        // folder, so if NDK reporting plugin is not initialized, this NDK reports dir won't exist
        // as well (and no need to write).
        val lastViewEventFile = DatadogNdkCrashHandler.getLastViewEventFile(coreFeature.storageDir)
        if (lastViewEventFile.parentFile?.existsSafe(internalLogger) == true) {
            ndkLastViewEventFileWriter.writeData(lastViewEventFile, data, false)
        } else {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                LAST_VIEW_EVENT_DIR_MISSING_MESSAGE.format(Locale.US, lastViewEventFile.parent)
            )
        }
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
        val isDebug = isAppDebuggable(context)

        var mutableConfig = configuration
        if (isDebug and configuration.coreConfig.enableDeveloperModeWhenDebuggable) {
            mutableConfig = modifyConfigurationForDeveloperDebug(configuration)
            isDeveloperModeEnabled = true
            Datadog.setVerbosity(Log.VERBOSE)
        }

        // always initialize Core Features first
        coreFeature = CoreFeature(internalLogger)
        coreFeature.initialize(
            context,
            instanceId,
            credentials,
            mutableConfig.coreConfig,
            TrackingConsent.PENDING
        )

        applyAdditionalConfiguration(mutableConfig.additionalConfig)

        initializeCrashReportFeature(mutableConfig.crashReportConfig)

        setupLifecycleMonitorCallback(context)

        setupShutdownHook()
        sendCoreConfigurationTelemetryEvent(configuration)
    }

    private fun initializeCrashReportFeature(configuration: Configuration.Feature.CrashReport?) {
        if (configuration != null) {
            val crashReportsFeature = CrashReportsFeature(this)
            registerFeature(crashReportsFeature)
        }
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
            val callback = ProcessLifecycleCallback(
                coreFeature.networkInfoProvider,
                appContext,
                internalLogger
            )
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
    private fun sendCoreConfigurationTelemetryEvent(configuration: Configuration) {
        val runnable = Runnable {
            val rumFeature = getFeature(Feature.RUM_FEATURE_NAME) ?: return@Runnable
            val coreConfigurationEvent = mapOf(
                "type" to "telemetry_configuration",
                "track_errors" to (configuration.crashReportConfig != null),
                "batch_size" to configuration.coreConfig.batchSize.windowDurationMs,
                "batch_upload_frequency" to configuration.coreConfig.uploadFrequency.baseStepMs,
                "use_proxy" to (configuration.coreConfig.proxy != null),
                "use_local_encryption" to (configuration.coreConfig.encryption != null)
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

        coreFeature.stop()
    }

    /**
     * Flushes all stored data (send everything right now).
     */
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
        internal const val EVENT_RECEIVER_ALREADY_EXISTS =
            "Feature \"%s\" already has event receiver registered, overwriting it."

        const val LAST_VIEW_EVENT_DIR_MISSING_MESSAGE = "Directory structure %s for writing" +
            " last view event doesn't exist."

        internal val CONFIGURATION_TELEMETRY_DELAY_MS = TimeUnit.SECONDS.toMillis(5)
    }
}
