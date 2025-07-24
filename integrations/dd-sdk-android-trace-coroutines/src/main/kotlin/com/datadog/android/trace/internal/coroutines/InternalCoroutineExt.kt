/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.coroutines

import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.coroutines.CoroutineScopeSpan
import com.datadog.android.trace.withinSpan
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

private const val TAG_DISPATCHER: String = "coroutine.dispatcher"

internal suspend fun <T : Any?> CoroutineScope.withinCoroutineSpan(
    operationName: String,
    parentSpan: DatadogSpan? = null,
    context: CoroutineContext,
    block: suspend CoroutineScopeSpan.() -> T
): T {
    return withinSpan(operationName, parentSpan, context != Dispatchers.Unconfined) {
        if (context is CoroutineDispatcher) {
            setTag(TAG_DISPATCHER, context.toString())
        }
        @Suppress("UnsafeThirdPartyFunctionCall") // handled by caller
        block(CoroutineScopeSpanImpl(this@withinCoroutineSpan, this))
    }
}
