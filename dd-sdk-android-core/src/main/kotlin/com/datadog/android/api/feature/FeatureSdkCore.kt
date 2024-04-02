/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.feature

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService

/**
 * Extension of [SdkCore] containing the necessary methods for the features development.
 *
 * SDK core is always guaranteed to implement this interface.
 */
interface FeatureSdkCore : SdkCore {

    /**
     * Logger for the internal SDK purposes.
     */
    val internalLogger: InternalLogger

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
     * Sets context update receiver for the given feature.
     *
     * @param featureName Feature name.
     * @param listener Listener to remove.
     */
    fun setContextUpdateReceiver(featureName: String, listener: FeatureContextUpdateReceiver)

    /**
     * Removes context update listener for the given feature.
     *
     * @param featureName Feature name.
     * @param listener Listener to remove.
     */
    fun removeContextUpdateReceiver(featureName: String, listener: FeatureContextUpdateReceiver)

    /**
     * Removes events receive for the given feature.
     *
     * @param featureName Feature name.
     */
    fun removeEventReceiver(featureName: String)

    /**
     * Returns a new single thread [ExecutorService], set up with backpressure and internal monitoring.
     */
    fun createSingleThreadExecutorService(): ExecutorService

    /**
     * Returns a new [ScheduledExecutorService], set up with internal monitoring.
     * It will use a default of one thread and can spawn at most as many thread as there are CPU cores.
     */
    fun createScheduledExecutorService(): ScheduledExecutorService
}
