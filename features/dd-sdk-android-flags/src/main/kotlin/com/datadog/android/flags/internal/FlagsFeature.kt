/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature.Companion.FLAGS_FEATURE_NAME
import com.datadog.android.api.feature.Feature.Companion.RUM_FEATURE_NAME
import com.datadog.android.api.feature.FeatureContextUpdateReceiver
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.internal.net.ExposuresRequestFactory
import com.datadog.android.flags.internal.net.PrecomputedAssignmentsRequestFactory
import com.datadog.android.flags.internal.storage.ExposureEventRecordWriter
import com.datadog.android.flags.internal.storage.NoOpRecordWriter
import com.datadog.android.flags.internal.storage.RecordWriter
import com.datadog.android.log.LogAttributes.RUM_APPLICATION_ID

/**
 * Type alias for a function that logs a message with a given level.
 *
 * Used to bridge between module components and a graceful policy-enabled logger.
 */
internal typealias LogWithPolicy = (String, InternalLogger.Level) -> Unit

internal class FlagsFeature(private val sdkCore: FeatureSdkCore, internal val flagsConfiguration: FlagsConfiguration) :
    StorageBackedFeature,
    FeatureContextUpdateReceiver {

    @Volatile
    internal var applicationId: String? = null

    @Volatile
    internal var processor: EventsProcessor = NoOpEventsProcessor()

    @Volatile
    internal var dataWriter: RecordWriter = NoOpRecordWriter()

    @Volatile
    private var isInitialized = false

    /**
     * Indicates whether the app is running in debug mode.
     * Set once during onInitialize() and never changes for the process lifetime.
     */
    @Volatile
    private var isDebugBuild: Boolean = false

    /**
     * Registry of [FlagsClient] instances by name.
     * This map stores all clients created for this feature instance.
     */
    private val registeredClients: MutableMap<String, FlagsClient> = mutableMapOf()

    // region Storage Feature

    override val storageConfiguration = FeatureStorageConfiguration.DEFAULT

    override val requestFactory =
        ExposuresRequestFactory(
            internalLogger = sdkCore.internalLogger,
            customExposureEndpoint = flagsConfiguration.customExposureEndpoint
        )

    // endregion

    // region Domain Objects

    internal val precomputedRequestFactory =
        PrecomputedAssignmentsRequestFactory(
            internalLogger = sdkCore.internalLogger
        )

    // endregion

    override val name: String = FLAGS_FEATURE_NAME

    // region Context Listener
    override fun onContextUpdate(featureName: String, context: Map<String, Any?>) {
        if (featureName == RUM_FEATURE_NAME && applicationId == null) {
            applicationId = context[RUM_APPLICATION_ID]?.toString()
        }
    }

    override fun onInitialize(appContext: Context) {
        if (isInitialized) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { "onInitialize called multiple times - ignoring duplicate call" }
            )
            return
        }
        isDebugBuild = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        isInitialized = true
        sdkCore.setContextUpdateReceiver(this)
        dataWriter = createDataWriter()
        processor = ExposureEventsProcessor(
            writer = dataWriter,
            timeProvider = sdkCore.timeProvider
        )
    }

    override fun onStop() {
        sdkCore.removeContextUpdateReceiver(this)
        dataWriter = NoOpRecordWriter()
        isInitialized = false // Allow re-initialization if feature is restarted
        synchronized(registeredClients) {
            registeredClients.clear()
        }
    }

    // endregion

    private fun createDataWriter(): RecordWriter = ExposureEventRecordWriter(sdkCore)

    // region FlagsClient Management

    /**
     * Gets a registered [FlagsClient] by name.
     *
     * @param name The client name to lookup
     * @return The registered [FlagsClient], or null if not found
     */
    internal fun getClient(name: String): FlagsClient? = synchronized(registeredClients) { registeredClients[name] }

    internal fun getOrRegisterNewClient(name: String, newClientFactory: () -> FlagsClient): FlagsClient {
        val existingClient = synchronized(registeredClients) {
            registeredClients[name]
        }
        if (existingClient != null) {
            logErrorWithPolicy(
                message = "Attempted to create a FlagsClient named '$name', but one already " +
                    "exists. The existing client will be used, and new configuration " +
                    "will be ignored.",
                level = InternalLogger.Level.WARN,
                shouldCrashInStrict = true
            )
            return existingClient
        }

        // Need to check again since we dropped the lock to log above.
        return synchronized(registeredClients) {
            registeredClients[name] ?: run {
                val newClient = newClientFactory()
                registeredClients[name] = newClient
                newClient
            }
        }
    }

    internal fun unregisterClient(name: String) = registeredClients.remove(name)

    internal fun clearClients() = registeredClients.clear()

    // endregion

    /**
     * Logs an error message according to the graceful mode policy.
     *
     * Policy Selection:
     * - Release builds: Graceful (log through SDK logger - conditional)
     * - Debug + gracefulMode enabled: Error (log to Android Logcat)
     * - Debug + gracefulMode disabled: Strict (crash immediately)
     *
     * @param message The error message
     * @param level The log level for conditional logging (graceful policy)
     * @param shouldCrashInStrict If true, crashes in strict policy
     */
    internal fun logErrorWithPolicy(
        message: String,
        level: InternalLogger.Level = InternalLogger.Level.ERROR,
        shouldCrashInStrict: Boolean = false
    ) {
        val formattedMessage = "$LOG_TAG $message"

        when {
            // Release build: Always graceful (conditional log through SDK)
            !isDebugBuild -> {
                sdkCore.internalLogger.log(
                    level,
                    InternalLogger.Target.USER,
                    { formattedMessage }
                )
            }

            // Debug + gracefulMode enabled: Error policy (log to Android Logcat)
            flagsConfiguration.gracefulModeEnabled -> {
                Log.e(LOG_TAG, message)
            }

            // Debug + gracefulMode disabled: Strict policy (crash if requested)
            else -> {
                if (shouldCrashInStrict) {
                    @Suppress("UnsafeThirdPartyFunctionCall") // Intentional crash for strict mode
                    error(formattedMessage)
                } else {
                    // For NoOpFlagsClient method calls (already past creation/retrieval)
                    Log.e(LOG_TAG, message)
                }
            }
        }
    }

    internal companion object {
        private const val LOG_TAG = "[Datadog Flags]"
    }
}
