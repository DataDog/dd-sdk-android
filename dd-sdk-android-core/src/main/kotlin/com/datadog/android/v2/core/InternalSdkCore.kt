/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.lint.InternalApi
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.v2.api.FeatureScope
import com.datadog.android.v2.api.FeatureSdkCore
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.api.context.NetworkInfo
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
     * Writes current RUM view event to the dedicated file for the needs of NDK crash reporting.
     *
     * @param data Serialized RUM view event.
     */
    @InternalApi
    @WorkerThread
    fun writeLastViewEvent(data: ByteArray)

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
