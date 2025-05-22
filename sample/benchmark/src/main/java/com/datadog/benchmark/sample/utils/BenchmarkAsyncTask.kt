/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.utils

import kotlinx.coroutines.Job

internal sealed interface BenchmarkAsyncTask<out TResult, out TKey> {
    val key: TKey

    data class Result<TResult, TKey>(val result: TResult, override val key: TKey): BenchmarkAsyncTask<TResult, TKey>
    data class Loading<TKey>(val job: Job, override val key: TKey): BenchmarkAsyncTask<Nothing, TKey>

    val optionalResult: TResult? get() = (this as? Result<TResult, TKey>)?.result
}
