/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.utils

import com.datadog.android.nightly.TEST_METHOD_NAME_KEY
import io.opentracing.util.GlobalTracer

inline fun measure(methodName: String, codeBlock: () -> Unit) {
    val span = GlobalTracer.get().buildSpan(methodName).start()
    codeBlock()
    span.finish()
}

fun defaultTestAttributes(testMethodName: String) = mapOf(
    TEST_METHOD_NAME_KEY to testMethodName
)
