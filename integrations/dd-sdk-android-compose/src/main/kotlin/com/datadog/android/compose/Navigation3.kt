/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
@file:Suppress("MatchingDeclarationName", "PackageNameVisibility")

package com.datadog.android.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.compose.internal.InstrumentationType
import com.datadog.android.compose.internal.SupportLibrary
import com.datadog.android.compose.internal.sendTelemetry
import com.datadog.android.internal.attributes.ViewScopeInstrumentationType
import com.datadog.android.internal.attributes.enrichWithConstantAttribute
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.tracking.ComponentPredicate

/**
 * A side effect which should be used to track view navigation with the Navigation3
 * for Jetpack Compose setup.
 *
 * @param T the type of the key of navigation back stack.
 * @param backStack backStack of the navigation to watch.
 * @param keyPredicate to accept the backstack key that will be taken into account as
 * valid RUM View events.
 * @param attributesResolver to resolve attributes for the current backstack key.
 * @param sdkCore the SDK instance to use. If not provided, default instance will be used.
 */
@Composable
@NonRestartableComposable
@ExperimentalRumApi
fun <T> Navigation3TrackingEffect(
    backStack: List<T>,
    keyPredicate: ComponentPredicate<T> = AcceptAllNavKeyPredicate(),
    attributesResolver: AttributesResolver<T>? = null,
    sdkCore: SdkCore = Datadog.getInstance()
) {
    LaunchedEffect(Unit) {
        sendTelemetry(
            autoInstrumented = false,
            instrumentationType = InstrumentationType.ViewTracking,
            supportLibrary = SupportLibrary.Navigation3,
            sdkCore = sdkCore
        )
    }
    InternalNavigation3TrackingStrategy(
        backStack = backStack,
        destinationPredicate = keyPredicate,
        attributesResolver = attributesResolver,
        sdkCore = sdkCore
    )
}

@Composable
@NonRestartableComposable
internal fun <T> InstrumentedNavigation3TrackingEffect(
    backStack: List<T>,
    keyPredicate: ComponentPredicate<T> = AcceptAllNavKeyPredicate(),
    attributesResolver: AttributesResolver<T>? = null,
    sdkCore: SdkCore = Datadog.getInstance()
) {
    LaunchedEffect(Unit) {
        sendTelemetry(
            autoInstrumented = true,
            instrumentationType = InstrumentationType.ViewTracking,
            supportLibrary = SupportLibrary.Navigation3,
            sdkCore = sdkCore
        )
    }
    InternalNavigation3TrackingStrategy(
        backStack = backStack,
        destinationPredicate = keyPredicate,
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
