/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.configuration

import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import com.datadog.android.rum.NoOpRumResourceAttributesProvider
import com.datadog.android.rum.RumResourceAttributesProvider
import com.datadog.android.rum.internal.net.RumNetworkInstrumentation

/**
 * Configuration that allows to configure RUM resource tracking for network requests.
 */
class RumNetworkInstrumentationConfiguration {
    private var sdkInstanceName: String? = null
    private var resourceAttributesProvider: RumResourceAttributesProvider = NoOpRumResourceAttributesProvider()

    /**
     * Set the SDK instance name to bind to, the default value is null.
     * @param sdkInstanceName SDK instance name to bind to, the default value is null.
     * Instrumentation won't be working until SDK instance is ready.
     */
    fun setSdkInstanceName(sdkInstanceName: String) = apply {
        this.sdkInstanceName = sdkInstanceName
    }

    /**
     * Sets the [RumResourceAttributesProvider] to use to provide custom attributes to the RUM.
     * By default it won't attach any custom attributes.
     * @param rumResourceAttributesProvider the [RumResourceAttributesProvider] to use.
     */
    fun setRumResourceAttributesProvider(
        rumResourceAttributesProvider: RumResourceAttributesProvider
    ) = apply {
        this.resourceAttributesProvider = rumResourceAttributesProvider
    }

    internal fun createInstrumentation(
        instrumentationName: String,
        libraryType: InternalTelemetryEvent.ApiUsage.NetworkInstrumentation.LibraryType
    ) = RumNetworkInstrumentation(
        sdkInstanceName = sdkInstanceName,
        networkInstrumentationName = instrumentationName,
        rumResourceAttributesProvider = resourceAttributesProvider,
        libraryType = libraryType
    )
}
