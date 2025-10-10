/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.tools.unit

import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import java.util.concurrent.Future

/**
 * Utility function that helps to create mock for completed future.
 *
 * @param T Future result type.
 * @param value - value that should be returned when [get] is called.
 * @return mock instance that will emulate completed future.
 */
fun <T> completedFutureMock(value: T): Future<T?> = mock<Future<T?>> {
    on { isDone }.thenReturn(true)
    on { get() }.thenReturn(value)
    on { get(anyLong(), any()) }.thenReturn(value)
}

/**
 * Utility function that helps to create mock for incompleted future.
 *
 * @param T Future result type.
 * @return mock instance that will emulate incompleted future.
 */
fun <T> incompleteFutureMock(): Future<T?> = mock<Future<T?>> {
    on { isDone }.thenReturn(false)
    on { get() }.thenReturn(null)
}

/**
 * Utility function that helps to create mock for future that completed with an exception.
 *
 * @param T Future result type.
 * @param throwable - a throwable that will be thrown when [get] method is called.
 * @return mock instance that will emulate completed with error future.
 */
fun <T> completedWithErrorFutureMock(throwable: Throwable): Future<T?> = mock<Future<T?>> {
    on { get() }.thenThrow(throwable)
    on { get(anyLong(), any()) }.thenThrow(throwable)
    on { isDone }.thenReturn(true)
}
