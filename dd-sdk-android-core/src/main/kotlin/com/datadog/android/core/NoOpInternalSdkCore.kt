/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.context.TimeInfo
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureEventReceiver
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.core.internal.net.DefaultFirstPartyHostHeaderTypeResolver
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.privacy.TrackingConsent
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * A no-op implementation of [SdkCore].
 */
internal object NoOpInternalSdkCore : InternalSdkCore {

    override val name: String = "no-op"

    override val time: TimeInfo = with(System.currentTimeMillis()) {
        TimeInfo(
            deviceTimeNs = TimeUnit.MILLISECONDS.toNanos(this),
            serverTimeNs = TimeUnit.MILLISECONDS.toNanos(this),
            serverTimeOffsetNs = 0L,
            serverTimeOffsetMs = 0L
        )
    }

    override val service: String
        get() = ""

    override val internalLogger: InternalLogger
        get() = SdkInternalLogger(this)

    // region InternalSdkCore

    override fun getOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }

    override val networkInfo: NetworkInfo
        get() = NetworkInfo(NetworkInfo.Connectivity.NETWORK_OTHER)
    override val trackingConsent: TrackingConsent
        get() = TrackingConsent.NOT_GRANTED
    override val rootStorageDir: File?
        get() = null
    override val isDeveloperModeEnabled: Boolean
        get() = false
    override val firstPartyHostResolver: FirstPartyHostHeaderTypeResolver
        get() = DefaultFirstPartyHostHeaderTypeResolver(emptyMap())

    // endregion

    // region SdkCore

    override fun setTrackingConsent(consent: TrackingConsent) = Unit

    override fun setUserInfo(
        id: String?,
        name: String?,
        email: String?,
        extraInfo: Map<String, Any?>
    ) = Unit

    override fun addUserProperties(extraInfo: Map<String, Any?>) = Unit

    override fun clearAllData() = Unit

    // endregion

    // region FeatureSdkCore

    override fun registerFeature(feature: Feature) = Unit

    override fun getFeature(featureName: String): FeatureScope? = null

    override fun updateFeatureContext(
        featureName: String,
        updateCallback: (MutableMap<String, Any?>) -> Unit
    ) = Unit

    override fun getFeatureContext(featureName: String): Map<String, Any?> = emptyMap()

    override fun setEventReceiver(featureName: String, `receiver`: FeatureEventReceiver) = Unit

    override fun removeEventReceiver(featureName: String) = Unit

    // endregion

    // region InternalSdkCore

    override fun writeLastViewEvent(data: ByteArray) = Unit

    override fun getPersistenceExecutorService(): ExecutorService = NoOpExecutorService()

    override fun getAllFeatures(): List<FeatureScope> = emptyList()

    override fun getDatadogContext(): DatadogContext? = null

    // endregion

    class NoOpExecutorService : ExecutorService {
        override fun execute(command: Runnable?) = Unit

        override fun shutdown() = Unit

        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()

        override fun isShutdown(): Boolean = true

        override fun isTerminated(): Boolean = true

        override fun awaitTermination(timeout: Long, unit: TimeUnit?): Boolean = true

        override fun <T : Any?> submit(task: Callable<T>?): Future<T>? = null

        override fun <T : Any?> submit(task: Runnable?, result: T): Future<T>? = null

        override fun submit(task: Runnable?): Future<*>? = null

        override fun <T : Any?> invokeAll(
            tasks: MutableCollection<out Callable<T>>?
        ): MutableList<Future<T>> = mutableListOf()

        override fun <T : Any?> invokeAll(
            tasks: MutableCollection<out Callable<T>>?,
            timeout: Long,
            unit: TimeUnit?
        ): MutableList<Future<T>> = mutableListOf()

        override fun <T : Any?> invokeAny(tasks: MutableCollection<out Callable<T>>?): T? = null

        override fun <T : Any?> invokeAny(
            tasks: MutableCollection<out Callable<T>>?,
            timeout: Long,
            unit: TimeUnit?
        ): T? = null
    }
}
