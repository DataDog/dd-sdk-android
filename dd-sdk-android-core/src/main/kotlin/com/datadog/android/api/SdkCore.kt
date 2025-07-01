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
     * Clear the current user information.
     *
     * User information will be set to null.
     * After calling this api, Logs, Traces, RUM Events will not include the user information anymore.
     *
     * Any active RUM Session, active RUM View at the time of call will have their `usr` attribute cleared.
     *
     * If you want to retain the current `usr` on the active RUM session,
     * you need to stop the session first by using `GlobalRumMonitor.get().stopSession()`
     *
     * If you want to retain the current `usr` on the active RUM views,
     * you need to stop the view first by using `GlobalRumMonitor.get().stopView()`
     */
    @AnyThread
    fun clearUserInfo()

    /**
     * Clears all unsent data in all registered features.
     */
    @AnyThread
    fun clearAllData()

    /**
     * Sets current account information.
     *
     * Those will be added to logs, traces and RUM events automatically.
     *
     * @param id Account ID.
     * @param name representing the account, if exists.
     * @param extraInfo Account's custom attributes, if exists.
     */
    fun setAccountInfo(
        id: String,
        name: String? = null,
        extraInfo: Map<String, Any?> = emptyMap()
    )

    /** Add custom attributes to the current account information
     *
     * This extra info will be added to already existing extra info that is added
     * to logs traces and RUM events automatically.
     *
     * @param extraInfo Account's additional custom attributes.
     */
    fun addAccountExtraInfo(
        extraInfo: Map<String, Any?> = emptyMap()
    )

    /** Clear the current account information
     *
     * Account information will set to null
     * Following Logs, Traces, RUM Events will not include the account information anymore.
     *
     * Any active RUM Session, active RUM View at the time of call will have their `account` attribute cleared
     *
     * If you want to retain the current `account` on the active RUM session,
     * you need to stop the session first by using `GlobalRumMonitor.get().stopSession()`
     *
     * If you want to retain the current `account` on the active RUM views,
     * you need to stop the view first by using `GlobalRumMonitor.get().stopView()`
     */
    fun clearAccountInfo()
}
