/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.internal.attributes.ViewScopeInstrumentationType
import com.datadog.android.internal.attributes.enrichWithConstantAttribute
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.tracking.ComponentPredicate

/**
 * A side effect which should be used to track view navigation with the Navigation3
 * for Jetpack Compose setup.
 *
 * @param backStack backStack of the navigation to watch
 * @param destinationPredicate to accept the backstack key that will be taken into account as
 * valid RUM View events.
 * @param attributesResolver to resolve attributes for the current backstack key.
 * @param sdkCore the SDK instance to use. If not provided, default instance will be used.
 */
@Composable
@NonRestartableComposable
@ExperimentalRumApi
fun <T> Navigation3TrackingEffect(
    backStack: List<T>,
    destinationPredicate: ComponentPredicate<T> = AcceptAllNavKeyPredicate(),
    attributesResolver: AttributesResolver<T>? = null,
    sdkCore: SdkCore = Datadog.getInstance()
) {
    sendTelemetry(autoInstrumented = false, sdkCore = sdkCore)
    InternalNavigation3TrackingStrategy(
        backStack = backStack,
        destinationPredicate = destinationPredicate,
        attributesResolver = attributesResolver,
        sdkCore = sdkCore
    )
}

@Composable
@NonRestartableComposable
private fun <T> InternalNavigation3TrackingStrategy(
    backStack: List<T>,
    destinationPredicate: ComponentPredicate<T> = AcceptAllNavKeyPredicate(),
    attributesResolver: AttributesResolver<T>? = null,
    sdkCore: SdkCore = Datadog.getInstance()
) {
    val internalLogger = (sdkCore as? FeatureSdkCore)?.internalLogger ?: InternalLogger.UNBOUND
    val topKey = backStack.lastOrNull()
    LaunchedEffect(topKey) {
        topKey?.takeIf { destinationPredicate.accept(it) }?.let { current ->
            try {
                val attributes =
                    attributesResolver?.resolveAttributes(current)?.toMutableMap()
                        ?.enrichWithConstantAttribute(ViewScopeInstrumentationType.COMPOSE)
                        ?: emptyMap()

                GlobalRumMonitor.get(sdkCore).startView(
                    name = destinationPredicate.getViewName(current)
                        ?: resolveDefaultViewName(current),
                    key = current.toString(),
                    attributes = attributes
                )
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                    { "Internal operation failed on ComponentPredicate" },
                    e
                )
            }
        }
    }
}

private fun resolveDefaultViewName(key: Any): String {
    return key.javaClass.canonicalName ?: key.javaClass.simpleName
}

private fun sendTelemetry(
    autoInstrumented: Boolean = false,
    sdkCore: SdkCore = Datadog.getInstance()
) {
    val message = "$DATADOG_SEMANTICS_TELEMETRY_LOG: $VALUE_VIEW_TRACKING"
    val attributes = mapOf(
        KEY_COMPOSE_INSTRUMENTATION to mapOf(
            KEY_ENABLED to autoInstrumented,
            KEY_INSTRUMENTATION_TYPE to VALUE_VIEW_TRACKING,
            KEY_SUPPORT_LIBRARY to VALUE_NAVIGATION3
        )
    )
    (sdkCore as? FeatureSdkCore)?.internalLogger?.log(
        level = InternalLogger.Level.INFO,
        target = InternalLogger.Target.TELEMETRY,
        messageBuilder = { message },
        onlyOnce = true,
        additionalProperties = attributes
    )
}

private const val KEY_COMPOSE_INSTRUMENTATION = "compose_instrumentation"

private const val KEY_ENABLED = "enabled"

private const val KEY_INSTRUMENTATION_TYPE = "instrumentation_type"

private const val KEY_SUPPORT_LIBRARY = "support_library"

private const val VALUE_VIEW_TRACKING = "ViewTracking"
private const val VALUE_NAVIGATION3 = "Navigation3"

private const val DATADOG_SEMANTICS_TELEMETRY_LOG =
    "Datadog Compose Integration Telemetry"
