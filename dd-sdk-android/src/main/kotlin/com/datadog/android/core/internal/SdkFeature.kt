/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal

import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.plugin.DatadogPlugin
import com.datadog.android.plugin.DatadogPluginConfig

internal abstract class SdkFeature {
    private var featurePlugins: MutableList<DatadogPlugin> = mutableListOf()

    protected fun registerPlugins(
        plugins: List<DatadogPlugin>,
        config: DatadogPluginConfig,
        trackingConsentProvider: ConsentProvider
    ) {
        this.featurePlugins = plugins.toMutableList()
        plugins.forEach {
            it.register(config)
            trackingConsentProvider.registerCallback(it)
        }
    }

    protected fun unregisterPlugins() {
        featurePlugins.forEach {
            it.unregister()
        }
        featurePlugins.clear()
    }

    fun getPlugins(): List<DatadogPlugin> {
        return featurePlugins
    }
}
