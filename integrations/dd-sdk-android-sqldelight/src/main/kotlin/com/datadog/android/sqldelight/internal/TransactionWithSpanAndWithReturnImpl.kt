/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sqldelight.internal

import com.datadog.android.sqldelight.TransactionWithSpanAndWithReturn
import com.squareup.sqldelight.TransactionWithReturn
import io.opentracing.Span

internal class TransactionWithSpanAndWithReturnImpl<R>(
    private val span: Span,
    private val transaction: TransactionWithReturn<R>
) : TransactionWithSpanAndWithReturn<R>, Span by span, TransactionWithReturn<R> by transaction
