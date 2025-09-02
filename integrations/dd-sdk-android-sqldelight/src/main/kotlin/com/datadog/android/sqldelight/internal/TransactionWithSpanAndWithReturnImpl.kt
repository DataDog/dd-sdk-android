/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sqldelight.internal

import com.datadog.android.sqldelight.TransactionWithSpanAndWithReturn
import com.datadog.android.trace.api.span.DatadogSpan
import com.squareup.sqldelight.TransactionWithReturn

internal class TransactionWithSpanAndWithReturnImpl<R>(
    private val span: DatadogSpan,
    private val transaction: TransactionWithReturn<R>
) : TransactionWithSpanAndWithReturn<R>, DatadogSpan by span, TransactionWithReturn<R> by transaction
