/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import android.content.Context
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.core.internal.utils.unboundInternalLogger
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.core.DatadogCore
import com.datadog.android.v2.core.NoOpSdkCore
import com.datadog.android.v2.core.internal.HashGenerator
import com.datadog.android.v2.core.internal.SdkCoreRegistry
import com.datadog.android.v2.core.internal.Sha256HashGenerator
import java.util.Locale

/**
 * This class initializes the Datadog SDK, and sets up communication with the server.
 */
object Datadog {

    internal val registry = SdkCoreRegistry(unboundInternalLogger)

    internal var hashGenerator: HashGenerator = Sha256HashGenerator()

    internal var libraryVerbosity = Int.MAX_VALUE

    // region Initialization

    /**
     * Initializes a named instance of the Datadog SDK.
     * @param instanceName the name of the instance (or null to initialize the default instance).
     * Note that the instance name should be stable across builds.
     * @param context your application context
     * @param credentials your organization credentials
     * @param configuration the configuration for the SDK library
     * @param trackingConsent as the initial state of the tracking consent flag
     * @return the initialized SDK instance, or null if something prevents the SDK from
     * being initialized
     * @see [Credentials]
     * @see [Configuration]
     * @see [TrackingConsent]
     * @throws IllegalArgumentException if the env name is using illegal characters and your
     * application is in debug mode otherwise returns null and stops initializing the SDK
     */
    @Suppress("ReturnCount")
    @JvmStatic
    fun initialize(
        instanceName: String?,
        context: Context,
        credentials: Credentials,
        configuration: Configuration,
        trackingConsent: TrackingConsent
    ): SdkCore? {
        synchronized(registry) {
            val existing = registry.getInstance(instanceName)
            if (existing != null) {
                unboundInternalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    MESSAGE_ALREADY_INITIALIZED
                )
                return existing
            }

            val sdkInstanceId = hashGenerator.generate(
                "$instanceName/${configuration.coreConfig.site.siteName}"
            )

            if (sdkInstanceId == null) {
                unboundInternalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    CANNOT_CREATE_SDK_INSTANCE_ID_ERROR
                )
                return null
            }

            val sdkInstanceName = instanceName ?: SdkCoreRegistry.DEFAULT_INSTANCE_NAME
            val sdkCore = DatadogCore(
                context,
                credentials,
                sdkInstanceId,
                sdkInstanceName
            ).apply {
                initialize(configuration)
            }
            sdkCore.setTrackingConsent(trackingConsent)
            registry.register(sdkInstanceName, sdkCore)

            return sdkCore
        }
    }

    /**
     * Initializes the Datadog SDK.
     * @param context your application context
     * @param credentials your organization credentials
     * @param configuration the configuration for the SDK library
     * @param trackingConsent as the initial state of the tracking consent flag
     * @return the initialized SDK instance, or null if something prevents the SDK from
     * being initialized
     * @see [Credentials]
     * @see [Configuration]
     * @see [TrackingConsent]
     * @throws IllegalArgumentException if the env name is using illegal characters and your
     * application is in debug mode otherwise returns null and stops initializing the SDK
     */
    @JvmStatic
    fun initialize(
        context: Context,
        credentials: Credentials,
        configuration: Configuration,
        trackingConsent: TrackingConsent
    ): SdkCore? {
        return initialize(null, context, credentials, configuration, trackingConsent)
    }

    /**
     * Retrieve the initialized SDK instance attached to the given name,
     * or the default instance if the name is null.
     * @param instanceName the name of the instance to retrieve,
     * or null to get the default instance
     * @return the existing instance linked with the given name, or no-op instance if instance
     * with given name is not yet initialized.
     */
    @JvmStatic
    @JvmOverloads
    fun getInstance(instanceName: String? = null): SdkCore {
        return synchronized(registry) {
            val sdkInstanceName = instanceName ?: SdkCoreRegistry.DEFAULT_INSTANCE_NAME
            val sdkInstance = registry.getInstance(sdkInstanceName)
            if (sdkInstance == null) {
                unboundInternalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    MESSAGE_SDK_NOT_INITIALIZED.format(Locale.US, sdkInstanceName)
                )
                NoOpSdkCore()
            } else {
                sdkInstance
            }
        }
    }

    /**
     * Checks if SDK instance with a given name is initialized.
     * @param instanceName the name of the instance to retrieve,
     * or null to check the default instance
     * @return whenever the instance with given name is initialized or not.
     */
    @JvmStatic
    @JvmOverloads
    fun isInitialized(instanceName: String? = null): Boolean {
        return synchronized(registry) {
            registry.getInstance(instanceName) != null
        }
    }

    /**
     * Stop the initialized SDK instance attached to the given name,
     * or the default instance if the name is null.
     * @param instanceName the name of the instance to stop,
     * or null to stop the default instance
     */
    @JvmStatic
    @JvmOverloads
    fun stopInstance(instanceName: String? = null) {
        synchronized(registry) {
            val instance = registry.unregister(instanceName)
            (instance as? DatadogCore)?.stop()
        }
    }

    /**
     * Sets the verbosity of this instance of the Datadog SDK.
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
     * Gets the verbosity of this instance of the Datadog SDK.
     *
     * Messages with a priority level equal or above the given level will be sent to Android's
     * Logcat.
     *
     * @returns level one of the Android [android.util.Log] constants
     * ([android.util.Log.VERBOSE], [android.util.Log.DEBUG], [android.util.Log.INFO],
     * [android.util.Log.WARN], [android.util.Log.ERROR], [android.util.Log.ASSERT]).
     */
    @JvmStatic
    fun getVerbosity(): Int = libraryVerbosity

    // endregion

    // region Global methods

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
        synchronized(registry) {
            val sdkCore = registry.getInstance()
            if (sdkCore != null) {
                sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
                    ?.sendEvent(
                        mapOf(
                            "type" to "flush_and_stop_monitor"
                        )
                    )
                (sdkCore as? DatadogCore)?.flushStoredData()
            }
        }
    }

    /**
     * For Datadog internal use only.
     *
     * @see _InternalProxy
     */
    @Suppress("FunctionNaming", "FunctionName")
    @JvmSynthetic
    fun _internalProxy(instanceName: String? = null): _InternalProxy {
        return _InternalProxy(getInstance(instanceName))
    }

    // endregion

    // region Constants

    internal const val MESSAGE_ALREADY_INITIALIZED =
        "The Datadog library has already been initialized."

    internal const val MESSAGE_SDK_NOT_INITIALIZED = "SDK instance with name %s is not found," +
        " returning no-op implementation. Please make sure to call" +
        " Datadog.initialize([instanceName]) before getting the instance."

    internal const val CANNOT_CREATE_SDK_INSTANCE_ID_ERROR =
        "Cannot create SDK instance ID, stopping SDK initialization."

    internal const val DD_SOURCE_TAG = "_dd.source"
    internal const val DD_SDK_VERSION_TAG = "_dd.sdk_version"
    internal const val DD_APP_VERSION_TAG = "_dd.version"

    // endregion
}
