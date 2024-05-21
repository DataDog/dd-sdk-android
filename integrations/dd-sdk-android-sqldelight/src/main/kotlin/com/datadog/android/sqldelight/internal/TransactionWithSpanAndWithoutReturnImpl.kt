/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sqldelight.internal

import com.datadog.android.sqldelight.TransactionWithSpanAndWithoutReturn
import com.squareup.sqldelight.TransactionWithoutReturn
import io.opentracing.Span

internal class TransactionWithSpanAndWithoutReturnImpl(
    private val span: Span,
    private val transaction: TransactionWithoutReturn
) : TransactionWithSpanAndWithoutReturn, Span by span, TransactionWithoutReturn by transaction
