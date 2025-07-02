/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.sqlite

import android.database.sqlite.SQLiteDatabase
import com.datadog.android.trace.GlobalDatadogTracerHolder
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.withinSpan

/**
 * Run [body] in a transaction marking it as successful if it completes without exception.
 * A [io.opentracing.Span]  will be created around the transaction and will be closed when the
 * transaction finishes.
 *
 * @param T the type of data returned by the traced operation
 * @param operationName the name of the [Span] created around the transaction.
 * @param exclusive Run in `EXCLUSIVE` mode when true, `IMMEDIATE` mode otherwise.
 * @param body the code to be executed inside the transaction.
 */
inline fun <T> SQLiteDatabase.transactionTraced(
    operationName: String,
    exclusive: Boolean = true,
    body: DatadogSpan.(SQLiteDatabase) -> T
): T {
    val parentSpan = GlobalDatadogTracerHolder.get().activeSpan()
    withinSpan(operationName, parentSpan, true) {
        if (exclusive) {
            @Suppress("UnsafeThirdPartyFunctionCall") // we are in a valid state
            beginTransaction()
        } else {
            @Suppress("UnsafeThirdPartyFunctionCall") // we are in a valid state
            beginTransactionNonExclusive()
        }
        try {
            @Suppress("UnsafeThirdPartyFunctionCall") // handled by caller
            val result = this.body(this@transactionTraced)
            @Suppress("UnsafeThirdPartyFunctionCall") // we are in a valid state
            setTransactionSuccessful()
            return result
        } finally {
            @Suppress("UnsafeThirdPartyFunctionCall") // we are in a valid state
            endTransaction()
        }
    }
}

// endregion
