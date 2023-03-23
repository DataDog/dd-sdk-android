/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.utils

import android.content.Context
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.log.LogsFeature
import com.datadog.android.nightly.BuildConfig
import com.datadog.android.nightly.ENV_NAME
import com.datadog.android.nightly.TEST_METHOD_NAME_KEY
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumFeature
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.trace.AndroidTracer
import com.datadog.android.trace.TracingFeature
import com.datadog.android.v2.api.SdkCore
import com.datadog.tools.unit.forge.aThrowable
import com.datadog.tools.unit.getStaticValue
import com.datadog.tools.unit.setStaticValue
import fr.xgouchet.elmyr.Forge
import io.opentracing.Tracer
import io.opentracing.util.GlobalTracer
import java.util.Random
import java.util.concurrent.atomic.AtomicBoolean

fun defaultTestAttributes(testMethodName: String) = mapOf(
    TEST_METHOD_NAME_KEY to testMethodName
)

inline fun executeInsideView(
    viewKey: String,
    viewName: String,
    testMethodName: String,
    codeBlock: () -> Unit
) {
    GlobalRum.get().startView(viewKey, viewName, attributes = defaultTestAttributes(testMethodName))
    codeBlock()
    GlobalRum.get().stopView(viewKey)
}

fun sendRandomActionOutcomeEvent(forge: Forge) {
    if (forge.aBool()) {
        val key = forge.anAlphabeticalString()
        GlobalRum.get().startResource(
            key,
            forge.aResourceMethod(),
            key,
            forge.exhaustiveAttributes()
        )
        GlobalRum.get().stopResource(
            key,
            forge.anInt(min = 200, max = 500),
            forge.aLong(min = 1),
            forge.aValueFrom(RumResourceKind::class.java),
            forge.exhaustiveAttributes()
        )
    } else {
        GlobalRum.get().addError(
            forge.anAlphabeticalString(),
            forge.aValueFrom(RumErrorSource::class.java),
            forge.aNullable { forge.aThrowable() },
            forge.exhaustiveAttributes()
        )
    }
}

fun stopSdk() {
    Datadog.stopInstance()

    // Reset Global states
    GlobalTracer::class.java.setStaticValue("isRegistered", false)
    val isRumRegistered: AtomicBoolean = GlobalRum::class.java.getStaticValue("isRegistered")
    isRumRegistered.set(false)
}

fun flushAndShutdownExecutors() {
    Datadog.invokeMethod("flushAndShutdownExecutors")
}

@Suppress("DEPRECATION") // TODO RUMM-3103 remove deprecated references
fun initializeSdk(
    targetContext: Context,
    forgeSeed: Long,
    consent: TrackingConsent = TrackingConsent.GRANTED,
    config: Configuration = createDatadogDefaultConfiguration(),
    logsFeatureProvider: () -> LogsFeature? = { LogsFeature.Builder().build() },
    tracingFeatureProvider: () -> TracingFeature? = { TracingFeature.Builder().build() },
    rumFeatureProvider: (RumFeature.Builder) -> RumFeature? = {
        it.build()
    },
    tracerProvider: () -> Tracer = { createDefaultAndroidTracer() },
    rumMonitorProvider: (SdkCore) -> RumMonitor = { createDefaultRumMonitor(it) }
): SdkCore {
    val sdkCore = Datadog.initialize(
        targetContext,
        createDatadogCredentials(),
        config,
        consent
    )
    checkNotNull(sdkCore)
    sdkCore.setVerbosity(Log.VERBOSE)
    mutableListOf(
        rumFeatureProvider(
            RumFeature.Builder(BuildConfig.NIGHTLY_TESTS_RUM_APP_ID)
                .sampleTelemetry(100f)
        ),
        logsFeatureProvider(),
        tracingFeatureProvider()
    )
        .filterNotNull()
        .shuffled(Random(forgeSeed))
        .forEach {
            sdkCore.registerFeature(it)
        }
    GlobalTracer.registerIfAbsent(tracerProvider.invoke())
    GlobalRum.registerIfAbsent(rumMonitorProvider.invoke(sdkCore))
    return sdkCore
}

/**
 * Default builder for nightly runs with telemetry set to 100%.
 */
fun defaultConfigurationBuilder(
    crashReportsEnabled: Boolean = true
): Configuration.Builder {
    return Configuration.Builder(
        crashReportsEnabled = crashReportsEnabled
    )
}

fun cleanStorageFiles() {
    InstrumentationRegistry
        .getInstrumentation()
        .targetContext
        .cacheDir.deleteRecursively()
}

fun cleanGlobalAttributes() {
    GlobalRum.removeAttribute(TEST_METHOD_NAME_KEY)
}

private fun createDatadogCredentials(): Credentials {
    return Credentials(
        clientToken = BuildConfig.NIGHTLY_TESTS_TOKEN,
        envName = ENV_NAME,
        variant = ""
    )
}

private fun createDatadogDefaultConfiguration(): Configuration {
    return defaultConfigurationBuilder().build()
}

private fun createDefaultAndroidTracer(): Tracer = AndroidTracer.Builder().build()

private fun createDefaultRumMonitor(sdkCore: SdkCore) = RumMonitor.Builder(sdkCore).build()
