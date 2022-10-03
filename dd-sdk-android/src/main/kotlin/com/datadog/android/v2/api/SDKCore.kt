/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.api

import com.datadog.android.Datadog
import com.datadog.android.core.model.UserInfo
import com.datadog.android.privacy.TrackingConsent
import com.datadog.tools.annotation.NoOpImplementation

/**
 * SDKCore is the entry point to register Datadog features to the core registry.
 */
@Suppress("ComplexInterface", "TooManyFunctions")
@NoOpImplementation
interface SDKCore {

    /**
     * Registers a feature to this instance of the Datadog SDK.
     *
     * @param featureName the name of the feature
     */
    fun registerFeature(
        featureName: String,
        storageConfiguration: FeatureStorageConfiguration,
        uploadConfiguration: FeatureUploadConfiguration
    )

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

    /**
     * Sets the context for the particular feature.
     *
     * @param feature Feature name.
     * @param context Context to set.
     */
    fun setFeatureContext(feature: String, context: Map<String, Any?>)

    /**
     * Updates the context if exists with the new entries. If there is no context yet for the
     * provided [feature] name, a new one will be created.
     *
     * @param entries Entries to add to the existing or new context.
     */
    fun updateFeatureContext(feature: String, entries: Map<String, Any?>)
}
