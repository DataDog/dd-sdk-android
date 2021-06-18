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
import com.datadog.android.nightly.BuildConfig
import com.datadog.android.nightly.ENV_NAME
import com.datadog.android.nightly.INITIALIZE_LOGGER_TEST_METHOD_NAME
import com.datadog.android.nightly.INITIALIZE_SDK_TEST_METHOD_NAME
import com.datadog.android.nightly.SET_TRACKING_CONSENT_METHOD_NAME
import com.datadog.android.nightly.TEST_METHOD_NAME_KEY
import com.datadog.android.nightly.aResourceMethod
import com.datadog.android.nightly.exhaustiveAttributes
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.tracing.AndroidTracer
import com.datadog.tools.unit.forge.aThrowable
import com.datadog.tools.unit.getStaticValue
import com.datadog.tools.unit.invokeMethod
import com.datadog.tools.unit.setStaticValue
import fr.xgouchet.elmyr.Forge
import io.opentracing.Tracer
import io.opentracing.util.GlobalTracer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

inline fun <reified R> measure(methodName: String, codeBlock: () -> R): R {
    val span = GlobalTracer.get().buildSpan(methodName).start()
    val result = codeBlock()
    span.finish()
    return result
}

fun measureSdkInitialize(codeBlock: () -> Unit) {

    val start = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis())
    codeBlock()
    val stop = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis())

    val span =
        GlobalTracer.get()
            .buildSpan(INITIALIZE_SDK_TEST_METHOD_NAME)
            .withStartTimestamp(start)
            .start()
    span.finish(stop)
}

fun measureSetTrackingConsent(codeBlock: () -> Unit) {
    measure(SET_TRACKING_CONSENT_METHOD_NAME, codeBlock)
}

fun measureLoggerInitialize(codeBlock: () -> Unit) {
    measure(INITIALIZE_LOGGER_TEST_METHOD_NAME, codeBlock)
}

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
    Datadog.invokeMethod("stop")
    GlobalTracer::class.java.setStaticValue("isRegistered", false)
    val isRumRegistered: AtomicBoolean = GlobalRum::class.java.getStaticValue("isRegistered")
    isRumRegistered.set(false)
}

fun flushAndShutdownExecutors() {
    Datadog.invokeMethod("flushAndShutdownExecutors")
}

fun initializeSdk(
    targetContext: Context,
    consent: TrackingConsent = TrackingConsent.GRANTED,
    config: Configuration = createDatadogDefaultConfiguration(),
    tracerProvider: () -> Tracer = { createDefaultAndroidTracer() },
    rumMonitorProvider: () -> RumMonitor = { createDefaultRumMonitor() }
) {
    Datadog.initialize(
        targetContext,
        createDatadogCredentials(),
        config,
        consent
    )
    Datadog.setVerbosity(Log.VERBOSE)
    GlobalTracer.registerIfAbsent(tracerProvider.invoke())
    GlobalRum.registerIfAbsent(rumMonitorProvider.invoke())
}

fun cleanStorageFiles() {
    InstrumentationRegistry
        .getInstrumentation()
        .targetContext
        .filesDir.deleteRecursively()
}

fun cleanGlobalAttributes() {
    GlobalRum.removeAttribute(TEST_METHOD_NAME_KEY)
}

private fun createDatadogCredentials(): Credentials {
    return Credentials(
        clientToken = BuildConfig.NIGHTLY_TESTS_TOKEN,
        envName = ENV_NAME,
        variant = "",
        rumApplicationId = BuildConfig.NIGHTLY_TESTS_RUM_APP_ID
    )
}

private fun createDatadogDefaultConfiguration(): Configuration {
    val configBuilder = Configuration.Builder(
        logsEnabled = true,
        tracesEnabled = true,
        crashReportsEnabled = true,
        rumEnabled = true
    )
    return configBuilder.build()
}

private fun createDefaultAndroidTracer(): Tracer = AndroidTracer.Builder().build()

private fun createDefaultRumMonitor() = RumMonitor.Builder().build()
