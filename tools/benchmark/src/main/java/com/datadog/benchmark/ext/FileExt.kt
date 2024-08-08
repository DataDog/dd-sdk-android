/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.ext

import java.io.File
import java.nio.charset.Charset

/*
 * The java.lang.File class throws a SecurityException for the following calls:
 * - canRead()
 * - canWrite()
 * - delete()
 * - exists()
 * - isFile()
 * - isDir()
 * - listFiles(…)
 * - length()
 * The following set of extension make sure that every call to those methods
 * is safeguarded to avoid crashing the customer's app.
 */

@Suppress("TooGenericExceptionCaught", "SwallowedException")
private fun <T> File.safeCall(
    default: T,
    lambda: File.() -> T
): T {
    return try {
        lambda()
    } catch (e: SecurityException) {
        default
    } catch (e: Exception) {
        default
    }
}

/**
 * Non-throwing version of [File.canRead]. If exception happens, false is returned.
 */

fun File.canReadSafe(): Boolean {
    return safeCall(default = false) {
        @Suppress("UnsafeThirdPartyFunctionCall")
        canRead()
    }
}

/**
 * Non-throwing version of [File.exists]. If exception happens, false is returned.
 */

fun File.existsSafe(): Boolean {
    return safeCall(default = false) {
        @Suppress("UnsafeThirdPartyFunctionCall")
        exists()
    }
}

/**
 * Non-throwing version of [File.readText]. If exception happens, null is returned.
 */

fun File.readTextSafe(charset: Charset = Charsets.UTF_8): String? {
    return if (existsSafe() && canReadSafe()) {
        safeCall(default = null) {
            @Suppress("UnsafeThirdPartyFunctionCall")
            readText(charset)
        }
    } else {
        null
    }
}

/**
 * Non-throwing version of [File.readLines]. If exception happens, null is returned.
 */
fun File.readLinesSafe(
    charset: Charset = Charsets.UTF_8
): List<String>? {
    return if (existsSafe() && canReadSafe()) {
        safeCall(default = null) {
            @Suppress("UnsafeThirdPartyFunctionCall")
            readLines(charset)
        }
    } else {
        null
    }
}
