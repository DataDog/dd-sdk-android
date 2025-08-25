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
 * @param backStack backStack of the navigation to watch.
 * @param keyPredicate to accept the backstack key that will be taken into account as
 * valid RUM View events.
 * @param backstackKeyResolver to resolve stable keys for the backstack keys.
 * @param attributesResolver to resolve attributes for the current backstack key.
 * @param sdkCore the SDK instance to use. If not provided, default instance will be used.
 */
@Composable
@NonRestartableComposable
@ExperimentalTrackingApi
fun <T> Navigation3TrackingEffect(
    backStack: List<T>,
    keyPredicate: ComponentPredicate<T> = AcceptAllNavKeyPredicate(),
    backstackKeyResolver: BackstackKeyResolver<T> = HashcodeBackstackKeyResolver(),
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
        backstackKeyResolver = backstackKeyResolver,
        attributesResolver = attributesResolver,
        sdkCore = sdkCore
    )
}

@Composable
@NonRestartableComposable
internal fun <T> InstrumentedNavigation3TrackingEffect(
    backStack: List<T>,
    keyPredicate: ComponentPredicate<T> = AcceptAllNavKeyPredicate(),
    backstackKeyResolver: BackstackKeyResolver<T> = HashcodeBackstackKeyResolver(),
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
        backstackKeyResolver = backstackKeyResolver,
        sdkCore = sdkCore
    )
}

@Composable
@NonRestartableComposable
private fun <T> InternalNavigation3TrackingStrategy(
    backStack: List<T>,
    destinationPredicate: ComponentPredicate<T> = AcceptAllNavKeyPredicate(),
    backstackKeyResolver: BackstackKeyResolver<T> = HashcodeBackstackKeyResolver(),
    attributesResolver: AttributesResolver<T>? = null,
    sdkCore: SdkCore = Datadog.getInstance()
) {
    val topKey = backStack.lastOrNull() ?: return
    val isResumed by rememberIsResumed()
    val internalLogger = (sdkCore as? FeatureSdkCore)?.internalLogger ?: InternalLogger.UNBOUND
    LaunchedEffect(isResumed, topKey) {
        trackBackstack(
            topKey = topKey,
            isResumed = isResumed,
            keyPredicate = destinationPredicate,
            backstackKeyResolver = backstackKeyResolver,
            attributesResolver = attributesResolver,
            rumMonitor = GlobalRumMonitor.get(sdkCore),
            internalLogger = internalLogger
        )
    }
}

internal fun <T> trackBackstack(
    topKey: T,
    isResumed: Boolean,
    keyPredicate: ComponentPredicate<T> = AcceptAllNavKeyPredicate(),
    backstackKeyResolver: BackstackKeyResolver<T>,
    attributesResolver: AttributesResolver<T>? = null,
    rumMonitor: RumMonitor,
    internalLogger: InternalLogger
) {
    val viewKey = backstackKeyResolver.getStableKey(topKey)
    if (isResumed) {
        if (keyPredicate.accept(topKey)) {
            try {
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
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                    { "Internal operation failed on ComponentPredicate" },
                    e
                )
            }
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

private fun resolveDefaultViewName(key: Any?): String {
    return key?.let {
        it.javaClass.canonicalName ?: it.javaClass.simpleName
    } ?: ""
}
