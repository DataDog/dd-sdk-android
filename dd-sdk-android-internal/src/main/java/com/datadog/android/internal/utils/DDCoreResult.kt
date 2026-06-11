/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

sealed interface DDCoreResult<out TResult : Any, out TError : Any> {
    data class Result<TResult : Any>(val result: TResult): DDCoreResult<TResult, Nothing>
    data class Error<TError : Any>(val error: TError): DDCoreResult<Nothing, TError>
}

fun <TResult : Any, TError : Any> DDCoreResult<TResult, TError>.optionalResult(): TResult? {
    return when (this) {
        is DDCoreResult.Error<TError> -> null
        is DDCoreResult.Result<TResult> -> result
    }
}
