/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.net

import com.datadog.android._InternalProxy
import com.datadog.android.api.SdkCore
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import com.datadog.android.internal.telemetry.InternalTelemetryEvent.ResourceHeadersTrackingConfigured
import com.datadog.android.lint.InternalApi
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.internal.monitor.AdvancedNetworkRumMonitor
import com.datadog.android.rum.resource.ResourceHeadersExtractor
import com.datadog.android.rum.resource.ResourceHeadersExtractor.Companion.DEFAULT_REQUEST_HEADERS
import com.datadog.android.rum.resource.ResourceHeadersExtractor.Companion.DEFAULT_RESPONSE_HEADERS
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent.TrackResourceHeaders

/**
 * Retained for binary compatibility with older network integrations until the next major release.
 */
@InternalApi
@Deprecated("Use the SdkCore overload to retain configuration before RUM is enabled.")
fun AdvancedNetworkRumMonitor.reportNetworkInstrumentationConfigured(
    type: InternalTelemetryEvent.ApiUsage.NetworkInstrumentation.LibraryType,
    resourceHeadersExtractor: ResourceHeadersExtractor?
) {
    notifyInterceptorInstantiated()
    reportNetworkingLibraryType(type)
    resourceHeadersExtractor?.toTrackingMode()?.let(::notifyResourceHeadersTrackingConfigured)
}

/** Retains network setup even when RUM has not been enabled yet. */
@InternalApi
fun SdkCore.reportNetworkInstrumentationConfigured(
    type: InternalTelemetryEvent.ApiUsage.NetworkInstrumentation.LibraryType,
    resourceHeadersExtractor: ResourceHeadersExtractor?
) {
    val sdkCore = this as? InternalSdkCore ?: return
    _InternalProxy.updateNetworkInstrumentationConfiguration(
        sdkCore,
        InternalTelemetryEvent.NetworkInstrumentationConfigured(resourceHeadersExtractor?.toTrackingMode())
    )
    (GlobalRumMonitor.get(this) as? AdvancedNetworkRumMonitor)?.reportNetworkingLibraryType(type)
}

private fun ResourceHeadersExtractor.toTrackingMode(): ResourceHeadersTrackingConfigured.Mode =
    if (
        allowedRequestHeaders == DEFAULT_REQUEST_HEADERS &&
        allowedResponseHeaders == DEFAULT_RESPONSE_HEADERS
    ) {
        ResourceHeadersTrackingConfigured.Mode.DEFAULT_HEADERS
    } else {
        ResourceHeadersTrackingConfigured.Mode.CUSTOM
    }

internal fun ResourceHeadersTrackingConfigured.Mode?.toTelemetryValue(): TrackResourceHeaders? = when (this) {
    ResourceHeadersTrackingConfigured.Mode.DEFAULT_HEADERS -> TrackResourceHeaders.DEFAULT_HEADERS
    ResourceHeadersTrackingConfigured.Mode.CUSTOM -> TrackResourceHeaders.CUSTOM
    null -> null
}
