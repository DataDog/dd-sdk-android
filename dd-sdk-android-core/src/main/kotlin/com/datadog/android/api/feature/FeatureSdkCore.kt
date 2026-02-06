/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.feature

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.internal.time.TimeProvider
import okhttp3.Call
import okhttp3.OkHttpClient
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService

/**
 * Extension of [SdkCore] containing the necessary methods for the features development.
 *
 * SDK core is always guaranteed to implement this interface.
 */
@Suppress("TooManyFunctions")
interface FeatureSdkCore : SdkCore {

    /**
     * Logger for the internal SDK purposes.
     */
    val internalLogger: InternalLogger

    /**
     * The [TimeProvider] used by this core instance for current timestamps.
     */
    val timeProvider: TimeProvider

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
     * Updates the context if exists with the new entries. If there is no context yet for the
     * provided [featureName], a new one will be created.
     *
     * @param featureName Feature name.
     * @param useContextThread Whenever update of the context should happen on the context processing thread or not. It
     * should be true for most of the cases related to the event processing. Be careful when setting it to false, valid
     * use-case can be like updating/reading feature context on the same (or already on the context) thread.
     * Defaults to true.
     * @param updateCallback Provides current feature context for the update. If there is no feature
     * with the given name registered, callback won't be called.
     */
    fun updateFeatureContext(
        featureName: String,
        useContextThread: Boolean = true,
        updateCallback: (context: MutableMap<String, Any?>) -> Unit
    )

    /**
     * Retrieves the context for the particular feature.
     *
     * @param featureName Feature name.
     * @param useContextThread Whenever context read should happen on the context processing thread or not. It
     * should be true for most of the cases related to the event processing. Be careful when setting it to false, valid
     * use-case can be like updating/reading feature context on the same (or already on the context) thread.
     * Defaults to true.
     * @return Context for the given feature or empty map if feature is not registered.
     */
    fun getFeatureContext(featureName: String, useContextThread: Boolean = true): Map<String, Any?>

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

    /**
     * Sets feature context update listener. Once subscribed, current context will be emitted
     * immdediately if it exists.
     *
     * @param listener Listener to remove.
     */
    fun setContextUpdateReceiver(listener: FeatureContextUpdateReceiver)

    /**
     * Removes feature context update listener.
     *
     * @param listener Listener to remove.
     */
    fun removeContextUpdateReceiver(listener: FeatureContextUpdateReceiver)

    /**
     * Returns a new single thread [ExecutorService], set up with backpressure and internal monitoring.
     *
     * @param executorContext Context to be used for logging and naming threads running on this executor.
     */
    fun createSingleThreadExecutorService(executorContext: String): ExecutorService

    /**
     * Returns a new [ScheduledExecutorService], set up with internal monitoring.
     * It will use a default of one thread and can spawn at most as many thread as there are CPU cores.
     *
     * @param executorContext Context to be used for logging and naming threads running on this executor.
     */
    fun createScheduledExecutorService(executorContext: String): ScheduledExecutorService

    /**
     * Creates an OkHttp [okhttp3.Call.Factory] with custom configuration that shares the SDK's
     * underlying thread pool and connection pool.
     *
     * This method allows features to configure their HTTP client while efficiently
     * sharing resources (dispatcher thread pool, connection pool) across the entire SDK.
     * Using a shared base client reduces resource consumption and improves performance
     * through better connection reuse.
     *
     * Example:
     * ```
     * val callFactory = sdkCore.createOkHttpCallFactory {
     *     callTimeout(45, TimeUnit.SECONDS)
     *     writeTimeout(30, TimeUnit.SECONDS)
     * }
     * ```
     *
     * @param block Configuration block to customize the [OkHttpClient.Builder]
     * @return A [Call.Factory] instance configured with shared resources
     */
    fun createOkHttpCallFactory(block: OkHttpClient.Builder.() -> Unit = {}): Call.Factory

    /**
     * Allows the given feature to set the anonymous ID for the SDK.
     *
     * @param anonymousId Anonymous ID to set. Can be null if feature is disabled.
     */
    fun setAnonymousId(anonymousId: UUID?)
}
