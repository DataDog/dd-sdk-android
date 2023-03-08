/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import android.content.Context
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.core.internal.utils.internalLogger
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.context.UserInfo
import com.datadog.android.v2.core.DatadogCore
import com.datadog.android.v2.core.NoOpSdkCore
import com.datadog.android.v2.core.internal.HashGenerator
import com.datadog.android.v2.core.internal.SdkCoreRegistry
import com.datadog.android.v2.core.internal.Sha256HashGenerator
import java.util.concurrent.atomic.AtomicBoolean

/**
 * This class initializes the Datadog SDK, and sets up communication with the server.
 */
@Suppress("DEPRECATION") // TODO RUMM-3103 remove deprecated references
@SuppressWarnings("TooManyFunctions")
object Datadog {

    internal val registry = SdkCoreRegistry(internalLogger)

    /**
     * Temporary thing, until global registry is implemented.
     */
    @Deprecated("Will be removed in RUMM-3103")
    var globalSdkCore: SdkCore = NoOpSdkCore()
        internal set

    internal var hashGenerator: HashGenerator = Sha256HashGenerator()

    @Deprecated("Will be removed in RUMM-3103")
    internal val initialized = AtomicBoolean(false)

    // region Initialization

    /**
     * Initializes a named instance of the Datadog SDK.
     * @param instanceName the name of the instance (or null to initialize the default instance)
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
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    MESSAGE_ALREADY_INITIALIZED
                )
                return existing
            }
        }

        val sdkInstanceId = hashGenerator.generate(
            "${credentials.clientToken}${configuration.coreConfig.site.siteName}"
        )

        if (sdkInstanceId == null) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                CANNOT_CREATE_SDK_INSTANCE_ID_ERROR
            )
            return null
        }

        val sdkCore = DatadogCore(context, credentials, configuration, sdkInstanceId)
        sdkCore.setTrackingConsent(trackingConsent)
        registry.register(instanceName, sdkCore)

        // TODO RUMM-3103 remove this
        if (instanceName == null) {
            globalSdkCore = sdkCore
            initialized.set(true)
        }
        return sdkCore
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
     * Checks if a default instance of the Datadog SDK was already initialized.
     * @return true if a default instance of the SDK was initialized, false otherwise
     */
    @JvmStatic
    fun isInitialized(): Boolean {
        return initialized.get()
    }

    /**
     * Retrieve the initialized SDK instance attached to the given name,
     * or the default instance if the name is null.
     * @param instanceName the name of the instance to retrieve,
     * or null to get the default instance
     * @return the existing instance linked with the given name, or null
     */
    @JvmStatic
    @JvmOverloads
    fun getInstance(instanceName: String? = null): SdkCore? {
        return registry.getInstance(instanceName)
    }

    // endregion

    // region Global methods

    /**
     * Clears all data that has not already been sent to Datadog servers.
     */
    @JvmStatic
    @Deprecated(
        "RUMM-3103 use getInstance().clearAllData()",
        ReplaceWith(
            "Datadog.getInstance()?.clearAllData()"
        )
    )
    fun clearAllData() {
        // TODO RUMM-3103 deprecate this
        globalSdkCore.clearAllData()
    }

    // Stop all Datadog work (for test purposes).
    internal fun stop() {
        if (initialized.get()) {
            globalSdkCore.stop()
            initialized.set(false)
            globalSdkCore = NoOpSdkCore()
            registry.unregister()
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
            globalSdkCore.getFeature(Feature.RUM_FEATURE_NAME)
                ?.sendEvent(
                    mapOf(
                        "type" to "flush_and_stop_monitor"
                    )
                )
            globalSdkCore.flushStoredData()
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
    @Deprecated(
        "RUMM-3103 use getInstance().setVerbosity(level)",
        ReplaceWith(
            "Datadog.getInstance()?.setVerbosity(level)"
        )
    )
    fun setVerbosity(level: Int) {
        globalSdkCore.setVerbosity(level)
    }

    /**
     * Sets the tracking consent regarding the data collection for the Datadog library.
     *
     * @param consent which can take one of the values
     * ([TrackingConsent.PENDING], [TrackingConsent.GRANTED], [TrackingConsent.NOT_GRANTED])
     */
    @JvmStatic
    @Deprecated(
        "RUMM-3103 use getInstance().setTrackingConsent()",
        ReplaceWith(
            "Datadog.getInstance()?.setTrackingConsent(consent)"
        )
    )
    fun setTrackingConsent(consent: TrackingConsent) {
        globalSdkCore.setTrackingConsent(consent)
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
    @Deprecated(
        "RUMM-3103 use getInstance().setUserInfo()",
        ReplaceWith(
            "Datadog.getInstance()?.setUserInfo(id, name, email, extraInfo)"
        )
    )
    fun setUserInfo(
        id: String? = null,
        name: String? = null,
        email: String? = null,
        extraInfo: Map<String, Any?> = emptyMap()
    ) {
        globalSdkCore.setUserInfo(
            UserInfo(
                id,
                name,
                email,
                extraInfo
            )
        )
    }

    /**
     * Sets additional information on the [UserInfo] object
     *
     * If properties had originally been set with [Datadog.setUserInfo], they will be preserved.
     * In the event of a conflict on key, the new property will prevail.
     *
     * @param extraInfo additional information. An extra information can be
     * nested up to 8 levels deep. Keys using more than 8 levels will be sanitized by SDK.
     */
    @JvmStatic
    @JvmOverloads
    @Deprecated(
        "RUMM-3103 use getInstance().addUserExtraInfo()",
        ReplaceWith(
            "Datadog.getInstance()?.addUserExtraInfo(extraInfo)"
        )
    )
    fun addUserExtraInfo(
        extraInfo: Map<String, Any?> = emptyMap()
    ) {
        globalSdkCore.addUserProperties(extraInfo)
    }

    /**
     * TODO RUMM-0000 Temporary thing until we decide on the SDK instance handling.
     *
     * @param feature Feature to register.
     */
    @JvmStatic
    @Deprecated(
        "RUMM-3103 use getInstance().registerFeature()",
        ReplaceWith(
            "Datadog.getInstance()?.registerFeature(feature)"
        )
    )
    fun registerFeature(feature: Feature) {
        globalSdkCore.registerFeature(feature)
    }

    /**
     * For Datadog internal use only.
     *
     * @see _InternalProxy
     */
    @Suppress("ObjectPropertyName", "ObjectPropertyNaming")
    val _internal: _InternalProxy by lazy {
        _InternalProxy(
            globalSdkCore,
            (globalSdkCore as? DatadogCore)?.coreFeature
        )
    }

    // endregion

    // region Constants

    internal const val MESSAGE_ALREADY_INITIALIZED =
        "The Datadog library has already been initialized."

    internal const val MESSAGE_SDK_INITIALIZATION_GUIDE =
        "Please add the following code in your application's onCreate() method:\n" +
            "val credentials = Credentials" +
            "(\"<CLIENT_TOKEN>\", \"<ENVIRONMENT>\", \"<VARIANT>\", \"<APPLICATION_ID>\")\n" +
            "Datadog.initialize(context, credentials, configuration, trackingConsent);"

    internal const val MESSAGE_NOT_INITIALIZED = "Datadog has not been initialized.\n " +
        MESSAGE_SDK_INITIALIZATION_GUIDE

    internal const val CANNOT_CREATE_SDK_INSTANCE_ID_ERROR =
        "Cannot create SDK instance ID, stopping SDK initialization."

    internal const val DD_SOURCE_TAG = "_dd.source"
    internal const val DD_SDK_VERSION_TAG = "_dd.sdk_version"
    internal const val DD_APP_VERSION_TAG = "_dd.version"

    // endregion
}
