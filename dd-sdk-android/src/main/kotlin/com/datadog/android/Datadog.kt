/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import android.content.Context
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.core.internal.utils.telemetry
import com.datadog.android.core.model.UserInfo
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.monitor.DatadogRumMonitor
import com.datadog.android.sessionreplay.internal.SessionReplayFeature
import com.datadog.android.v2.api.NoOpSDKCore
import com.datadog.android.v2.api.SDKCore
import com.datadog.android.v2.core.DatadogCore
import com.datadog.android.v2.core.internal.HashGenerator
import com.datadog.android.v2.core.internal.Sha256HashGenerator
import java.util.concurrent.atomic.AtomicBoolean

/**
 * This class initializes the Datadog SDK, and sets up communication with the server.
 */
@SuppressWarnings("TooManyFunctions")
object Datadog {

    internal var globalSDKCore: SDKCore = NoOpSDKCore()

    internal var hashGenerator: HashGenerator = Sha256HashGenerator()

    internal val initialized = AtomicBoolean(false)

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

        val sdkInstanceId = hashGenerator.generate(
            "${credentials.clientToken}${configuration.coreConfig.site.siteName}"
        )

        if (sdkInstanceId == null) {
            devLogger.e(CANNOT_CREATE_SDK_INSTANCE_ID_ERROR)
            return
        }

        globalSDKCore = DatadogCore(context, credentials, configuration, sdkInstanceId)
        globalSDKCore.setTrackingConsent(trackingConsent)

        initialized.set(true)
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
        globalSDKCore.clearAllData()
    }

    // Stop all Datadog work (for test purposes).
    internal fun stop() {
        if (initialized.get()) {
            globalSDKCore.stop()
            initialized.set(false)
            globalSDKCore = NoOpSDKCore()
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
            globalSDKCore.flushStoredData()
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
        globalSDKCore.setVerbosity(level)
    }

    /**
     * Sets the tracking consent regarding the data collection for the Datadog library.
     *
     * @param consent which can take one of the values
     * ([TrackingConsent.PENDING], [TrackingConsent.GRANTED], [TrackingConsent.NOT_GRANTED])
     */
    @JvmStatic
    fun setTrackingConsent(consent: TrackingConsent) {
        globalSDKCore.setTrackingConsent(consent)
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
        globalSDKCore.setUserInfo(
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
    fun addUserExtraInfo(
        extraInfo: Map<String, Any?> = emptyMap()
    ) {
        globalSDKCore.addUserProperties(extraInfo)
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
        val rumFeature = ((globalSDKCore as? DatadogCore)?.rumFeature) as? RumFeature
        if (enable) {
            rumFeature?.enableDebugging()
        } else {
            rumFeature?.disableDebugging()
        }
    }

    /**
     * Stops the session recording.
     *
     * Session Replay feature will only work for recorded
     * sessions.
     */
    fun stopSessionRecording() {
        ((globalSDKCore as? DatadogCore)?.sessionReplayFeature as? SessionReplayFeature)
            ?.stopRecording()
    }

    /**
     * Starts/resumes the session recording.
     *
     * Session Replay feature will only work for recorded
     * sessions.
     */
    fun startSessionRecording() {
        ((globalSDKCore as? DatadogCore)?.sessionReplayFeature as? SessionReplayFeature)
            ?.startRecording()
    }

    /**
     * For Datadog internal use only.
     *
     * @see _InternalProxy
     */
    @Suppress("ObjectPropertyNaming")
    val _internal: _InternalProxy by lazy {
        _InternalProxy(
            telemetry,
            (globalSDKCore as? DatadogCore)?.coreFeature
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
