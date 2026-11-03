/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.tests.elmyr

import android.annotation.SuppressLint
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assumptions
import java.io.EOFException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOError
import java.io.IOException
import java.io.UncheckedIOException
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
 * Because Mockito will prevent throwing undeclared checked exceptions,
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

/**
 * Will generate a map with different value types with a possibility to filter out given keys.
 *
 * @param excludedKeys Keys to exclude from generated map.
 * @param filterThreshold Max ratio of keys removed from originally generated map. If ratio
 * is more than that, [Assumptions.assumeTrue] mechanism will be used.
 */
fun Forge.exhaustiveAttributes(
    excludedKeys: Set<String> = emptySet(),
    filterThreshold: Float = 0.5f
): MutableMap<String, Any?> {
    val map = generateMapWithExhaustiveValues(this).toMutableMap()

    map[""] = anHexadecimalString()
    map[aWhitespaceString()] = anHexadecimalString()
    map[anAlphabeticalString()] = generateMapWithExhaustiveValues(this).toMutableMap().apply {
        this[anAlphabeticalString()] = generateMapWithExhaustiveValues(this@exhaustiveAttributes)
    }

    val filtered = map.filterKeys { it !in excludedKeys }

    assumeDifferenceIsNoMore(filtered.size, map.size, filterThreshold)

    return filtered.toMutableMap()
}

private fun assumeDifferenceIsNoMore(result: Int, base: Int, maxDifference: Float) {
    check(result <= base) {
        "Number of elements after filtering cannot exceed the number of original elements."
    }

    val diff = (base - result).toFloat() / base
    Assumptions.assumeTrue(
        diff <= maxDifference,
        "Too many elements removed, condition cannot be satisfied."
    )
}

private fun generateMapWithExhaustiveValues(forge: Forge): MutableMap<String, Any?> {
    return forge.run {
        listOf(
            aBool(),
            anInt(),
            aLong(),
            aFloat(),
            aDouble(),
            anAsciiString(),
            getForgery<Date>(),
            getForgery<Locale>(),
            getForgery<TimeZone>(),
            getForgery<File>(),
            getForgery<JsonObject>(),
            getForgery<JsonArray>(),
            getForgery<JSONObject>(),
            getForgery<JSONArray>(),
            aList { anAlphabeticalString() },
            aList { aDouble() },
            null
        ).associateByTo(mutableMapOf()) { anAlphaNumericalString() }
    }
}
