/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

import android.os.StrictMode

/**
 * This utility function wraps a call to a method that needs to perform a disk read operation
 * on the main thread.
 * This prevents adding LogCat noise when customer enable StrictMode logging.
 * @param T the type returned by the operation
 * @param operation the operation
 * @return the value returned by the operation
 */
fun <T> allowThreadDiskReads(
    operation: () -> T
): T {
    val oldPolicy = StrictMode.allowThreadDiskReads()
    try {
        return operation()
    } finally {
        StrictMode.setThreadPolicy(oldPolicy)
    }
}

/**
 * This utility function wraps a call to a method that needs to perform a disk write operation
 * on the main thread.
 * This prevents adding LogCat noise when customer enable StrictMode logging.
 * @param T the type returned by the operation
 * @param operation the operation
 * @return the value returned by the operation
 */
fun <T> allowThreadDiskWrites(
    operation: () -> T
): T {
    val oldPolicy = StrictMode.allowThreadDiskWrites()
    try {
        return operation()
    } finally {
        StrictMode.setThreadPolicy(oldPolicy)
    }
}
