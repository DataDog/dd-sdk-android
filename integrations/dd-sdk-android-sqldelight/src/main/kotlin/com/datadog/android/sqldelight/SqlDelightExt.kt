/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sqldelight

import com.datadog.android.sqldelight.internal.TransactionWithSpanAndWithReturnImpl
import com.datadog.android.sqldelight.internal.TransactionWithSpanAndWithoutReturnImpl
import com.datadog.android.sqldelight.internal.withinSpan
import com.datadog.android.trace.GlobalDatadogTracer
import com.squareup.sqldelight.Transacter
import com.squareup.sqldelight.Transacter.Transaction

/**
 * Starts a [Transaction] and runs [body] in that transaction.
 * A span will be created around the transaction code and sent to Datadog.
 *
 * @param T the type of the wrapped SQL [Transacter]
 * @param operationName the name of the [Span] created around the coroutine code.
 * @param noEnclosing in case we want the currently opened transaction to be automatically closed
 * @param body the code to be executed inside the transaction
 * @throws IllegalStateException if [noEnclosing] is true and there is already an active
 *   [Transaction] on this thread.
 */
fun <T : Transacter> T.transactionTraced(
    operationName: String,
    noEnclosing: Boolean = false,
    body: TransactionWithSpanAndWithoutReturn.() -> Unit
) {
    withinSpan(operationName, GlobalDatadogTracer.get().activeSpan()) {
        @Suppress("UnsafeThirdPartyFunctionCall") // handled by caller
        transaction(noEnclosing = noEnclosing) {
            @Suppress("UnsafeThirdPartyFunctionCall") // handled by caller
            body.invoke(TransactionWithSpanAndWithoutReturnImpl(this@withinSpan, this))
        }
    }
}

/**
 * Starts a [Transaction] and runs [body] in that transaction.
 * A span will be created around the transaction code and sent to Datadog.
 *
 * @param T the type of the wrapped SQL [Transacter]
 * @param R the type of the data returned by the SQL transaction
 * @param operationName the name of the [Span] created around the coroutine code.
 * @param noEnclosing in case we want the currently opened transaction to be automatically closed
 * @param body the code to be executed inside the transaction
 * @throws IllegalStateException if [noEnclosing] is true and there is already an active
 *   [Transaction] on this thread.
 */
fun <T : Transacter, R> T.transactionTracedWithResult(
    operationName: String,
    noEnclosing: Boolean = false,
    body: TransactionWithSpanAndWithReturn<R>.() -> R
): R {
    withinSpan(operationName, GlobalDatadogTracer.get().activeSpan()) {
        @Suppress("UnsafeThirdPartyFunctionCall") // handled by caller
        return transactionWithResult(noEnclosing = noEnclosing) {
            @Suppress("UnsafeThirdPartyFunctionCall") // handled by caller
            body.invoke(TransactionWithSpanAndWithReturnImpl(this@withinSpan, this))
        }
    }
}
