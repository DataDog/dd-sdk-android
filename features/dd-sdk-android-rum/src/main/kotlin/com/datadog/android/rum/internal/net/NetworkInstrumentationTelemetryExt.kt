/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.net

import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import com.datadog.android.lint.InternalApi
import com.datadog.android.rum.internal.monitor.AdvancedNetworkRumMonitor
import com.datadog.android.rum.resource.ResourceHeadersExtractor
import com.datadog.android.rum.resource.ResourceHeadersExtractor.Companion.DEFAULT_REQUEST_HEADERS
import com.datadog.android.rum.resource.ResourceHeadersExtractor.Companion.DEFAULT_RESPONSE_HEADERS

/**
 * For internal usage only. Reports the telemetry emitted when a network instrumentation is ready.
 */
@InternalApi
fun AdvancedNetworkRumMonitor.reportNetworkInstrumentationConfigured(
    type: InternalTelemetryEvent.ApiUsage.NetworkInstrumentation.LibraryType,
    resourceHeadersExtractor: ResourceHeadersExtractor?
) {
    notifyInterceptorInstantiated()
    reportNetworkingLibraryType(type)
    resourceHeadersExtractor?.toTrackingMode()?.let(::notifyResourceHeadersTrackingConfigured)
}

private fun ResourceHeadersExtractor.toTrackingMode(): InternalTelemetryEvent.ResourceHeadersTrackingConfigured.Mode =
    if (
        allowedRequestHeaders == DEFAULT_REQUEST_HEADERS &&
        allowedResponseHeaders == DEFAULT_RESPONSE_HEADERS
    ) {
        InternalTelemetryEvent.ResourceHeadersTrackingConfigured.Mode.DEFAULT_HEADERS
    } else {
        InternalTelemetryEvent.ResourceHeadersTrackingConfigured.Mode.CUSTOM
    }
