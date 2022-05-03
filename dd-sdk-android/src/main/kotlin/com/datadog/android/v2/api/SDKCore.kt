/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.api

import com.datadog.android.core.model.UserInfo
import com.datadog.android.privacy.TrackingConsent
import com.datadog.tools.annotation.NoOpImplementation

/**
 * SDKCore is the entry point to register Datadog features to the core registry.
 */
@NoOpImplementation
interface SDKCore {

    /**
     * Registers a feature to this instance of the Datadog SDK.
     *
     * @param featureName the name of the feature
     */
    fun registerFeature(
        featureName: String,
        storageConfiguration: SDKFeatureStorageConfiguration,
        uploadConfiguration: SDKFeatureUploadConfiguration
    )

    /**
     * Retrieves a registered feature.
     *
     * @param featureName the name of the feature to retrieve
     * @return the registered feature with the given name, or null
     */
    fun getFeature(featureName: String): SDKFeature?

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
     * Stops all process for this instance of the Datadog SDK.
     */
    fun stop()
}
