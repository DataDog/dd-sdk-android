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
import com.datadog.android.api.SdkCore
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.log.Logs
import com.datadog.android.log.LogsConfiguration
import com.datadog.android.nightly.BuildConfig
import com.datadog.android.nightly.ENV_NAME
import com.datadog.android.nightly.TEST_METHOD_NAME_KEY
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.trace.AndroidTracer
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration
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
    sdkCore: SdkCore,
    codeBlock: () -> Unit
) {
    GlobalRumMonitor.get(sdkCore).startView(viewKey, viewName, attributes = defaultTestAttributes(testMethodName))
    codeBlock()
    GlobalRumMonitor.get(sdkCore).stopView(viewKey)
}

fun sendRandomActionOutcomeEvent(forge: Forge, sdkCore: SdkCore) {
    if (forge.aBool()) {
        val key = forge.anAlphabeticalString()
        GlobalRumMonitor.get(sdkCore).startResource(
            key,
            forge.aResourceMethod(),
            key,
            forge.exhaustiveAttributes()
        )
        GlobalRumMonitor.get(sdkCore).stopResource(
            key,
            forge.anInt(min = 200, max = 500),
            forge.aLong(min = 1),
            forge.aValueFrom(RumResourceKind::class.java),
            forge.exhaustiveAttributes()
        )
    } else {
        GlobalRumMonitor.get(sdkCore).addError(
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
    val isRumRegistered: AtomicBoolean = GlobalRumMonitor::class.java.getStaticValue("isRegistered")
    isRumRegistered.set(false)
}

fun flushAndShutdownExecutors() {
    Datadog.invokeMethod("flushAndShutdownExecutors")
}

fun initializeSdk(
    targetContext: Context,
    forgeSeed: Long,
    consent: TrackingConsent = TrackingConsent.GRANTED,
    config: Configuration = createDatadogDefaultConfiguration(),
    logsConfigProvider: () -> LogsConfiguration? = { LogsConfiguration.Builder().build() },
    tracesConfigProvider: () -> TraceConfiguration? = { TraceConfiguration.Builder().build() },
    rumConfigProvider: (RumConfiguration.Builder) -> RumConfiguration? = {
        it.build()
    },
    tracerProvider: (SdkCore) -> Tracer = { createDefaultAndroidTracer(it) }
): SdkCore {
    Datadog.setVerbosity(Log.VERBOSE)
    val sdkCore = Datadog.initialize(
        targetContext,
        config,
        consent
    )
    checkNotNull(sdkCore)
    mutableListOf(
        rumConfigProvider(
            RumConfiguration.Builder(BuildConfig.NIGHTLY_TESTS_RUM_APP_ID)
                .setTelemetrySampleRate(100f)
        ),
        logsConfigProvider(),
        tracesConfigProvider()
    )
        .filterNotNull()
        .shuffled(Random(forgeSeed))
        .forEach {
            when (it) {
                is RumConfiguration -> Rum.enable(it, sdkCore)
                is LogsConfiguration -> Logs.enable(it, sdkCore)
                is TraceConfiguration -> Trace.enable(it, sdkCore)
                else -> throw IllegalArgumentException(
                    "Unknown configuration of type ${it::class.qualifiedName}"
                )
            }
        }
    GlobalTracer.registerIfAbsent(tracerProvider.invoke(sdkCore))
    return sdkCore
}

/**
 * Default builder for nightly runs with telemetry set to 100%.
 */
fun defaultConfigurationBuilder(
    crashReportsEnabled: Boolean = true
): Configuration.Builder {
    return Configuration.Builder(
        clientToken = DEFAULT_CLIENT_TOKEN,
        env = DEFAULT_ENV_NAME,
        variant = DEFAULT_VARIANT_NAME
    )
        .setCrashReportsEnabled(crashReportsEnabled)
}

const val DEFAULT_CLIENT_TOKEN = BuildConfig.NIGHTLY_TESTS_TOKEN

const val DEFAULT_ENV_NAME = ENV_NAME

const val DEFAULT_VARIANT_NAME = ""

fun cleanStorageFiles() {
    InstrumentationRegistry
        .getInstrumentation()
        .targetContext
        .cacheDir.deleteRecursively()
}

private fun createDatadogDefaultConfiguration(): Configuration {
    return defaultConfigurationBuilder().build()
}

private fun createDefaultAndroidTracer(sdkCore: SdkCore): Tracer =
    AndroidTracer.Builder(sdkCore).build()
