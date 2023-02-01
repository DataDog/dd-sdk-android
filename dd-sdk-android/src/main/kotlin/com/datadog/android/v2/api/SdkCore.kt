/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.api

import com.datadog.android.Datadog
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.v2.api.context.TimeInfo
import com.datadog.android.v2.api.context.UserInfo

/**
 * SdkCore is the entry point to register Datadog features to the core registry.
 */
@Suppress("ComplexInterface", "TooManyFunctions")
interface SdkCore {

    /**
     * The current time (both device and server).
     */
    val time: TimeInfo

    /**
     * Name of the service (given during the SDK initialization, otherwise package name is used).
     */
    val service: String

    /**
     * Registers a feature to this instance of the Datadog SDK.
     *
     * @param feature the feature to be registered.
     */
    fun registerFeature(feature: Feature)

    /**
     * Retrieves a registered feature.
     *
     * @param featureName the name of the feature to retrieve
     * @return the registered feature with the given name, or null
     */
    fun getFeature(featureName: String): FeatureScope?

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
    fun setVerbosity(level: Int)

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
    fun getVerbosity(): Int

    /**
     * Sets the tracking consent regarding the data collection for this instance of the Datadog SDK.
     *
     * @param consent which can take one of the values
     * ([TrackingConsent.PENDING], [TrackingConsent.GRANTED], [TrackingConsent.NOT_GRANTED])
     */
    fun setTrackingConsent(consent: TrackingConsent)

    /**
     * Sets the user information.
     *
     * @param userInfo the new user info to set, or null
     */
    fun setUserInfo(userInfo: UserInfo)

    /**
     * Sets additional information on the [UserInfo] object
     *
     * If properties had originally been set with [Datadog.setUserInfo], they will be preserved.
     * In the event of a conflict on key, the new property will prevail.
     *
     * @param extraInfo additional information. An extra information can be
     * nested up to 8 levels deep. Keys using more than 8 levels will be sanitized by SDK.
     */
    fun addUserProperties(extraInfo: Map<String, Any?>)

    /**
     * Stops all process for this instance of the Datadog SDK.
     */
    fun stop()

    /**
     * Clears all unsent data in all registered features.
     */
    fun clearAllData()

    /**
     * Flushes all stored data (send everything right now).
     */
    fun flushStoredData()

    // TODO RUMM-0000 Should feature context methods be moved into the FeatureScope maybe?
    /**
     * Updates the context if exists with the new entries. If there is no context yet for the
     * provided [featureName], a new one will be created.
     *
     * @param featureName Feature name.
     * @param updateCallback Provides current feature context for the update. If there is no feature
     * with the given name registered, callback won't be called.
     */
    fun updateFeatureContext(
        featureName: String,
        updateCallback: (context: MutableMap<String, Any?>) -> Unit
    )

    /**
     * Retrieves the context for the particular feature.
     *
     * @param featureName Feature name.
     * @return Context for the given feature or empty map if feature is not registered.
     */
    fun getFeatureContext(featureName: String): Map<String, Any?>

    /**
     * Sets event receiver for the given feature.
     *
     * @param featureName Feature name.
     * @param receiver Event receiver.
     */
    fun setEventReceiver(featureName: String, receiver: FeatureEventReceiver)

    /**
     * Removes events receive for the given feature.
     *
     * @param featureName Feature name.
     */
    fun removeEventReceiver(featureName: String)
}
