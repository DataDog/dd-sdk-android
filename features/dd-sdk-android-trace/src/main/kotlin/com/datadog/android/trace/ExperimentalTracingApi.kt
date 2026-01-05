/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace

/**
 * Marks declarations that are part of the experimental Tracing API.
 * The API surface may change in future releases without prior notice.
 */
@RequiresOptIn(
    message = "This is an experimental Tracing API." +
        " It may change in the future.",
    level = RequiresOptIn.Level.WARNING
)
@Retention(AnnotationRetention.BINARY)
annotation class ExperimentalTracingApi
