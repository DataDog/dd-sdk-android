/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.di.app

import android.content.Context
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File

/**
 * Applies common OkHttpClient configuration (cache setup).
 * This is shared between flavor-specific OkHttpModule implementations.
 */
fun OkHttpClient.Builder.applyCommonConfiguration(context: Context): OkHttpClient.Builder {
    cache(Cache(File(context.cacheDir, "okhttp-cache"), OKHTTP_CACHE_SIZE_BYTES))
    return this
}

private const val OKHTTP_CACHE_SIZE_BYTES: Long = 10 * 1024 * 1024 // 10mb
