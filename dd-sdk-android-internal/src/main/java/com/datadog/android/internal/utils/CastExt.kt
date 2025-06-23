/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.internal.utils

/**
 * Handy function aimed to improve readability with casting logic
 * E.g:
 * ```kotlin
 * val writer1 = (
 *      featuredSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)
 *          ?.unwrap<Feature>() as? com.datadog.android.trace.InternalCoreWriterProvider
 *      )
 *     ?.getCoreTracerWriter()
 * ```
 * now could be written as:
 * ```kotlin
 val writer = featuredSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)
 ?.unwrap<Feature>()
 ?.tryCastTo<com.datadog.android.trace.InternalCoreWriterProvider>()
 ?.getCoreTracerWriter()
 *  ```
 */
inline fun <reified R> Any.tryCastTo(): R? {
    return this as R
}
