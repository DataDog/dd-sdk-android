/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.lint.InternalApi
import com.datadog.android.privacy.TrackingConsent
import com.google.gson.JsonObject
import java.io.File
import java.util.concurrent.ExecutorService

/**
 * FOR INTERNAL USAGE ONLY. THIS INTERFACE CONTENT MAY CHANGE WITHOUT NOTICE.
 */
interface InternalSdkCore : FeatureSdkCore {

    /**
     * Returns current state of network connection.
     */
    @InternalApi
    val networkInfo: NetworkInfo

    /**
     * Current tracking consent.
     */
    @InternalApi
    val trackingConsent: TrackingConsent

    /**
     * Root folder for the hosting SDK instance.
     */
    @InternalApi
    val rootStorageDir: File?

    /**
     * Shows if core is running in developer mode (some settings are overwritten to simplify
     * debugging during app development).
     */
    @InternalApi
    val isDeveloperModeEnabled: Boolean

    /**
     * Returns an instance of [FirstPartyHostHeaderTypeResolver] associated with the current
     * SDK instance.
     */
    val firstPartyHostResolver: FirstPartyHostHeaderTypeResolver

    /**
     * Reads last known RUM view event stored.
     */
    @get:WorkerThread
    @InternalApi
    val lastViewEvent: JsonObject?

    /**
     * Reads information about last fatal ANR sent.
     */
    @get:WorkerThread
    @InternalApi
    val lastFatalAnrSent: Long?

    /**
     * Provide the time the application started in nanoseconds from device boot, or our best guess
     * if the actual start time is not available. Note: since the implementation may rely on [System.nanoTime],
     * this property can only be used to measure elapsed time and is not related to any other notion of system
     * or wall-clock time. The value is the time since VM start.
     */
    @InternalApi
    val appStartTimeNs: Long

    /**
     * Writes current RUM view event to the dedicated file for the needs of NDK crash reporting.
     *
     * @param data Serialized RUM view event.
     */
    @InternalApi
    @WorkerThread
    fun writeLastViewEvent(data: ByteArray)

    /**
     * Deletes last RUM view event written.
     */
    @InternalApi
    @WorkerThread
    fun deleteLastViewEvent()

    /**
     * Writes timestamp of the last fatal ANR sent.
     */
    @InternalApi
    @WorkerThread
    fun writeLastFatalAnrSent(anrTimestamp: Long)

    /**
     * Get an executor service for persistence purposes.
     * @return the persistence executor to use for this SDK
     */
    @InternalApi
    @AnyThread
    fun getPersistenceExecutorService(): ExecutorService

    /**
     * @return all the registered features.
     */
    @InternalApi
    fun getAllFeatures(): List<FeatureScope>

    /**
     * @return the current DatadogContext, or null
     */
    @InternalApi
    fun getDatadogContext(): DatadogContext?
}
