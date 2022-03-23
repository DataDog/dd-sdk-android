/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit.forge

import android.annotation.SuppressLint
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryException
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.IOError
import java.io.IOException
import java.io.UncheckedIOException
import java.lang.ArithmeticException
import java.lang.IndexOutOfBoundsException
import java.lang.RuntimeException

/**
 * @return a random [Throwable] instance with a forged message.
 */
fun Forge.aThrowable(): Throwable {
    return anElementFrom(
        anError(),
        anException()
    )
}

/**
 * @return a random [Error] instance with a forged message.
 */
fun Forge.anError(): Error {
    val errorMessage = anAlphabeticalString()
    return anElementFrom(
        UnknownError(errorMessage),
        IOError(RuntimeException(errorMessage)),
        NotImplementedError(errorMessage),
        StackOverflowError(errorMessage),
        OutOfMemoryError(errorMessage)
    )
}

/**
 * @return a random [Exception] instance with a forged message.
 * Because mockito will prevent throwing undeclared checked exceptions,
 * all the exceptions here must extend [RuntimeException].
 * [UncheckedIOException] is a wrapper around a checked [IOException], and is considered a new
 * API (only available in Android 24+); we ignore the warning since tests are run on the JVM.
 */
@SuppressLint("NewApi")
fun Forge.anException(): Exception {
    val errorMessage = anAlphabeticalString()
    return anElementFrom(
        IndexOutOfBoundsException(errorMessage),
        ArithmeticException(errorMessage),
        IllegalStateException(errorMessage),
        ArrayIndexOutOfBoundsException(errorMessage),
        NullPointerException(errorMessage),
        ForgeryException(errorMessage),
        UnsupportedOperationException(errorMessage),
        SecurityException(errorMessage),
        UncheckedIOException(IOException(errorMessage)),
        UncheckedIOException(FileNotFoundException(errorMessage)),
        UncheckedIOException(EOFException(errorMessage))
    )
}
