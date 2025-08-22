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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.compose.internal.InstrumentationType
import com.datadog.android.compose.internal.SupportLibrary
import com.datadog.android.compose.internal.sendTelemetry
import com.datadog.android.internal.attributes.ViewScopeInstrumentationType
import com.datadog.android.internal.attributes.enrichWithConstantAttribute
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.tracking.ComponentPredicate

/**
 * A side effect which should be used to track view navigation with the Navigation3
 * for Jetpack Compose setup.
 *
 * @param T the type of the key of navigation back stack.
 * @param backStack back stack of the navigation to watch.
 * @param keyPredicate to accept the back stack key that will be taken into account as
 * valid RUM View events.
 * @param backStackKeyResolver to resolve stable keys for the back stack keys.
 * @param attributesResolver to resolve attributes for the current back stack key.
 * @param sdkCore the SDK instance to use. If not provided, default instance will be used.
 */
@Composable
@NonRestartableComposable
@ExperimentalTrackingApi
fun <T : Any> Navigation3TrackingEffect(
    backStack: List<T>,
    keyPredicate: ComponentPredicate<T> = AcceptAllNavKeyPredicate(),
    backStackKeyResolver: BackStackKeyResolver<T> = HashcodeBackStackKeyResolver(),
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
        backStackKeyResolver = backStackKeyResolver,
        attributesResolver = attributesResolver,
        sdkCore = sdkCore
    )
}

@Composable
@NonRestartableComposable
internal fun <T : Any> InstrumentedNavigation3TrackingEffect(
    backStack: List<T>,
    keyPredicate: ComponentPredicate<T> = AcceptAllNavKeyPredicate(),
    backStackKeyResolver: BackStackKeyResolver<T> = HashcodeBackStackKeyResolver(),
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
        backStackKeyResolver = backStackKeyResolver,
        sdkCore = sdkCore
    )
}

@Composable
@NonRestartableComposable
private fun <T : Any> InternalNavigation3TrackingStrategy(
    backStack: List<T>,
    destinationPredicate: ComponentPredicate<T> = AcceptAllNavKeyPredicate(),
    backStackKeyResolver: BackStackKeyResolver<T> = HashcodeBackStackKeyResolver(),
    attributesResolver: AttributesResolver<T>? = null,
    sdkCore: SdkCore = Datadog.getInstance()
) {
    val topKey = backStack.lastOrNull() ?: return
    val isResumed by rememberIsResumed()
    val internalLogger = (sdkCore as? FeatureSdkCore)?.internalLogger ?: InternalLogger.UNBOUND
    LaunchedEffect(isResumed, topKey) {
        trackBackStack(
            topKey = topKey,
            isResumed = isResumed,
            keyPredicate = destinationPredicate,
            backStackKeyResolver = backStackKeyResolver,
            attributesResolver = attributesResolver,
            rumMonitor = GlobalRumMonitor.get(sdkCore),
            internalLogger = internalLogger
        )
    }
}

internal fun <T : Any> trackBackStack(
    topKey: T,
    isResumed: Boolean,
    keyPredicate: ComponentPredicate<T> = AcceptAllNavKeyPredicate(),
    backStackKeyResolver: BackStackKeyResolver<T>,
    attributesResolver: AttributesResolver<T>? = null,
    rumMonitor: RumMonitor,
    internalLogger: InternalLogger
) {
    val viewKey = backStackKeyResolver.getStableKey(topKey)
    if (isResumed) {
        try {
            if (keyPredicate.accept(topKey)) {
                val attributes =
                    attributesResolver?.resolveAttributes(topKey)?.toMutableMap()
                        ?.enrichWithConstantAttribute(ViewScopeInstrumentationType.COMPOSE)
                        ?: emptyMap()

                rumMonitor.startView(
                    name = keyPredicate.getViewName(topKey)
                        ?: resolveDefaultViewName(topKey),
                    key = viewKey,
                    attributes = attributes
                )
            } else {
                internalLogger.log(
                    InternalLogger.Level.DEBUG,
                    InternalLogger.Target.USER,
                    { "The provided keyPredicate did not accept the back stack top key: $topKey" }
                )
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { "Internal operation failed on ComponentPredicate" },
                e
            )
        }
    } else {
        rumMonitor.stopView(viewKey)
    }
}

@Composable
private fun rememberIsResumed(): State<Boolean> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(
        initialValue = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
        key1 = lifecycle
    ) {
        val observer = LifecycleEventObserver { _, _ ->
            value = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        @Suppress("ThreadSafety") // TODO RUM-525 check composable threading rules
        lifecycle.addObserver(observer)
        awaitDispose {
            @Suppress("ThreadSafety") // TODO RUM-525 check composable threading rules
            lifecycle.removeObserver(observer)
        }
    }
}

private fun resolveDefaultViewName(key: Any): String {
    return key.javaClass.canonicalName ?: key.javaClass.simpleName
}
