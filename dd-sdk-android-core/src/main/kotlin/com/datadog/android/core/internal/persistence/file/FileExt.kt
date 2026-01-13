/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
@file:Suppress("TooManyFunctions")

package com.datadog.android.core.internal.persistence.file

import android.util.Log
import com.datadog.android.api.InternalLogger
import com.datadog.android.lint.InternalApi
import java.io.File
import java.io.FileFilter
import java.io.FilenameFilter
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

@Suppress("TooGenericExceptionCaught")
private fun <T> File.safeCall(
    default: T,
    internalLogger: InternalLogger,
    lambda: File.() -> T
): T {
    return try {
        lambda()
    } catch (e: SecurityException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            { "Security exception was thrown for file ${this.path}" },
            e
        )
        default
    } catch (e: Exception) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            { "Unexpected exception was thrown for file ${this.path}" },
            e
        )
        default
    }
}

internal fun File.canWriteSafe(internalLogger: InternalLogger): Boolean {
    return safeCall(default = false, internalLogger) {
        @Suppress("UnsafeThirdPartyFunctionCall")
        canWrite()
    }
}

/**
 * Non-throwing version of [File.canRead]. If exception happens, false is returned.
 */
@InternalApi
fun File.canReadSafe(internalLogger: InternalLogger): Boolean {
    return safeCall(default = false, internalLogger) {
        @Suppress("UnsafeThirdPartyFunctionCall")
        canRead()
    }
}

internal fun File.deleteSafe(internalLogger: InternalLogger): Boolean {
    Log.w("WAHAHA", "deleteSafe $name")
    return safeCall(default = false, internalLogger) {
        @Suppress("UnsafeThirdPartyFunctionCall")
        delete()
    }
}

/**
 * Non-throwing version of [File.exists]. If exception happens, false is returned.
 */
@InternalApi
fun File.existsSafe(internalLogger: InternalLogger): Boolean {
    return safeCall(default = false, internalLogger) {
        @Suppress("UnsafeThirdPartyFunctionCall")
        exists()
    }
}

internal fun File.isFileSafe(internalLogger: InternalLogger): Boolean {
    return safeCall(default = false, internalLogger) {
        @Suppress("UnsafeThirdPartyFunctionCall")
        isFile()
    }
}

internal fun File.isDirectorySafe(internalLogger: InternalLogger): Boolean {
    return safeCall(default = false, internalLogger) {
        @Suppress("UnsafeThirdPartyFunctionCall")
        isDirectory()
    }
}

internal fun File.listFilesSafe(internalLogger: InternalLogger): Array<File>? {
    return safeCall(default = null, internalLogger) {
        @Suppress("UnsafeThirdPartyFunctionCall")
        listFiles()
    }
}

internal fun File.listFilesSafe(filter: FileFilter, internalLogger: InternalLogger): Array<File>? {
    return safeCall(default = null, internalLogger) {
        @Suppress("UnsafeThirdPartyFunctionCall")
        listFiles(filter)
    }
}

/**
 * Non-throwing version of [File.listFiles]. If exception happens, null is returned.
 */
@InternalApi
fun File.listFilesSafe(internalLogger: InternalLogger, filter: FilenameFilter): Array<File>? {
    return safeCall(default = null, internalLogger) {
        @Suppress("UnsafeThirdPartyFunctionCall")
        listFiles(filter)
    }
}

internal fun File.lengthSafe(internalLogger: InternalLogger): Long {
    return safeCall(default = 0L, internalLogger) {
        @Suppress("UnsafeThirdPartyFunctionCall")
        length()
    }
}

internal fun File.mkdirsSafe(internalLogger: InternalLogger): Boolean {
    return safeCall(default = false, internalLogger) {
        @Suppress("UnsafeThirdPartyFunctionCall")
        mkdirs()
    }
}

internal fun File.renameToSafe(dest: File, internalLogger: InternalLogger): Boolean {
    return safeCall(default = false, internalLogger) {
        @Suppress("UnsafeThirdPartyFunctionCall")
        renameTo(dest)
    }
}

internal fun File.deleteDirectoryContentsSafe(internalLogger: InternalLogger) {
    this.listFilesSafe(internalLogger)?.forEach {
        it.deleteSafe(internalLogger)
    }
}

/**
 * Non-throwing version of [File.readText]. If exception happens, null is returned.
 */
@InternalApi
fun File.readTextSafe(charset: Charset = Charsets.UTF_8, internalLogger: InternalLogger): String? {
    return if (existsSafe(internalLogger) && canReadSafe(internalLogger)) {
        safeCall(default = null, internalLogger) {
            @Suppress("UnsafeThirdPartyFunctionCall")
            readText(charset)
        }
    } else {
        null
    }
}

internal fun File.readBytesSafe(internalLogger: InternalLogger): ByteArray? {
    return if (existsSafe(internalLogger) && canReadSafe(internalLogger)) {
        safeCall(default = null, internalLogger) {
            @Suppress("UnsafeThirdPartyFunctionCall")
            readBytes()
        }
    } else {
        null
    }
}

/**
 * Non-throwing version of [File.readLines]. If exception happens, null is returned.
 */
@InternalApi
fun File.readLinesSafe(
    charset: Charset = Charsets.UTF_8,
    internalLogger: InternalLogger
): List<String>? {
    return if (existsSafe(internalLogger) && canReadSafe(internalLogger)) {
        safeCall(default = null, internalLogger) {
            @Suppress("UnsafeThirdPartyFunctionCall")
            readLines(charset)
        }
    } else {
        null
    }
}

internal fun File.writeTextSafe(
    text: String,
    charset: Charset = Charsets.UTF_8,
    internalLogger: InternalLogger
) {
    if (existsSafe(internalLogger) && canWriteSafe(internalLogger)) {
        safeCall(default = null, internalLogger) {
            @Suppress("UnsafeThirdPartyFunctionCall")
            writeText(text, charset)
        }
    }
}
