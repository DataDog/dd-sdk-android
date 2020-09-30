/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ktx.sqlite

import android.database.sqlite.SQLiteDatabase
import com.datadog.android.ktx.tracing.withinSpan
import io.opentracing.Span
import io.opentracing.util.GlobalTracer

/**
 * Run [body] in a transaction marking it as successful if it completes without exception.
 * A [io.opentracing.Span]  will be created around the transaction and will be closed when the
 * transaction finishes.
 *
 * @param operationName the name of the [Span] created around the transaction.
 * @param exclusive Run in `EXCLUSIVE` mode when true, `IMMEDIATE` mode otherwise.
 * @param body the code to be executed inside the transaction.
 */
inline fun <T> SQLiteDatabase.transactionTraced(
    operationName: String,
    exclusive: Boolean = true,
    body: Span.(SQLiteDatabase) -> T
): T {
    val parentSpan = GlobalTracer.get().activeSpan()
    withinSpan(operationName, parentSpan, true) {
        if (exclusive) {
            beginTransaction()
        } else {
            beginTransactionNonExclusive()
        }
        try {
            val result = this.body(this@transactionTraced)
            setTransactionSuccessful()
            return result
        } finally {
            endTransaction()
        }
    }
}

// endregion
