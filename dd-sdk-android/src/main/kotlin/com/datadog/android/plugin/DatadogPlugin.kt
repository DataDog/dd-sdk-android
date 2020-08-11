/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.plugin

/**
 * DatadogPlugin interface. You can attach as many as you want for any existing feature in the
 * SDK.
 * @see [Feature.LOG]
 * @see [Feature.CRASH]
 * @see [Feature.TRACE]
 * @see [Feature.RUM]
 */
interface DatadogPlugin {

    /**
     * Registers this plugin. This will be called when the feature for which this plugin
     * was assigned will be initialised.
     * @param config the [DatadogPluginConfig]
     */
    fun register(config: DatadogPluginConfig)

    /**
     * Unregisters this plugin. This will be called when the feature for which this plugin
     * was assigned will be stopped.
     */
    fun unregister()

    /**
     * Notify that the current context of the library was updated by one or more of the features.
     * This method is always called from a worker thread.
     * @param context the updated [DatadogContext].
     *
     */
    fun onContextChanged(context: DatadogContext)
}
