/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api

import androidx.annotation.AnyThread
import com.datadog.android.api.context.TimeInfo
import com.datadog.android.api.context.UserInfo
import com.datadog.android.privacy.TrackingConsent

/**
 * SdkCore is the entry point to register Datadog features to the core registry.
 */
@Suppress("ComplexInterface", "TooManyFunctions")
interface SdkCore {

    /**
     * Name of the current SDK instance.
     */
    val name: String

    /**
     * The current time (both device and server).
     */
    val time: TimeInfo

    /**
     * Name of the service (given during the SDK initialization, otherwise package name is used).
     */
    val service: String

    /**
     * Returns true if the core is active.
     */
    @AnyThread
    fun isCoreActive(): Boolean

    /**
     * Sets the tracking consent regarding the data collection for this instance of the Datadog SDK.
     *
     * @param consent which can take one of the values
     * ([TrackingConsent.PENDING], [TrackingConsent.GRANTED], [TrackingConsent.NOT_GRANTED])
     */
    @AnyThread
    fun setTrackingConsent(consent: TrackingConsent)

    /**
     * Sets the user information.
     *
     * @param id (nullable) a unique user identifier (relevant to your business domain)
     * @param name (nullable) the user name or alias
     * @param email (nullable) the user email
     * @param extraInfo additional information. An extra information can be
     * nested up to 8 levels deep. Keys using more than 8 levels will be sanitized by SDK.
     */
    @AnyThread
    fun setUserInfo(
        id: String? = null,
        name: String? = null,
        email: String? = null,
        extraInfo: Map<String, Any?> = emptyMap()
    )

    /**
     * Sets additional information on the [UserInfo] object
     *
     * If properties had originally been set with [SdkCore.setUserInfo], they will be preserved.
     * In the event of a conflict on key, the new property will prevail.
     *
     * @param extraInfo additional information. An extra information can be
     * nested up to 8 levels deep. Keys using more than 8 levels will be sanitized by SDK.
     */
    @AnyThread
    fun addUserProperties(extraInfo: Map<String, Any?>)

    /**
     * Clears all unsent data in all registered features.
     */
    @AnyThread
    fun clearAllData()
}
