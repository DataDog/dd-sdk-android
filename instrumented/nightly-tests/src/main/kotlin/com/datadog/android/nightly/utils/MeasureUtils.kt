/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.utils

import com.datadog.opentracing.DDTracer
import io.opentracing.util.GlobalTracer
import java.util.concurrent.TimeUnit

internal const val INITIALIZE_SDK_TEST_METHOD_NAME = "sdk_initialize"
internal const val INITIALIZE_LOGGER_TEST_METHOD_NAME = "logs_logger_initialize"
internal const val SET_TRACKING_CONSENT_METHOD_NAME = "sdk_set_tracking_consent"

internal inline fun <reified R> measure(methodName: String, codeBlock: () -> R): R {
    val builder = GlobalTracer.get().buildSpan("perf_measure") as DDTracer.DDSpanBuilder
    val span = builder.withResourceName(methodName).start()
    val result = codeBlock()
    span.finish()
    return result
}

internal fun <T> measureSdkInitialize(codeBlock: () -> T): T {
    val start = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis())
    val result: T = codeBlock()
    val stop = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis())

    val span =
        GlobalTracer.get()
            .buildSpan(INITIALIZE_SDK_TEST_METHOD_NAME)
            .withStartTimestamp(start)
            .start()
    span.finish(stop)
    return result
}

internal fun measureSetTrackingConsent(codeBlock: () -> Unit) {
    measure(SET_TRACKING_CONSENT_METHOD_NAME, codeBlock)
}

internal fun measureLoggerInitialize(codeBlock: () -> Unit) {
    measure(INITIALIZE_LOGGER_TEST_METHOD_NAME, codeBlock)
}
