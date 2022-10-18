/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
@file:Suppress("TooManyFunctions")

package com.datadog.android.core.internal.persistence.file

import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.log.internal.utils.errorWithTelemetry
import java.io.File
import java.io.FileFilter
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
    lambda: File.() -> T
): T {
    return try {
        lambda()
    } catch (e: SecurityException) {
        sdkLogger.errorWithTelemetry("Security exception was thrown for file ${this.path}", e)
        default
    } catch (e: Exception) {
        sdkLogger.errorWithTelemetry("Unexpected exception was thrown for file ${this.path}", e)
        default
    }
}

internal fun File.canWriteSafe(): Boolean {
    return safeCall(default = false) {
        @Suppress("UnsafeThirdPartyFunctionCall")
        canWrite()
    }
}

internal fun File.canReadSafe(): Boolean {
    return safeCall(default = false) {
        @Suppress("UnsafeThirdPartyFunctionCall")
        canRead()
    }
}

internal fun File.deleteSafe(): Boolean {
    return safeCall(default = false) {
        @Suppress("UnsafeThirdPartyFunctionCall")
        delete()
    }
}

internal fun File.existsSafe(): Boolean {
    return safeCall(default = false) {
        @Suppress("UnsafeThirdPartyFunctionCall")
        exists()
    }
}

internal fun File.isFileSafe(): Boolean {
    return safeCall(default = false) {
        @Suppress("UnsafeThirdPartyFunctionCall")
        isFile()
    }
}

internal fun File.isDirectorySafe(): Boolean {
    return safeCall(default = false) {
        @Suppress("UnsafeThirdPartyFunctionCall")
        isDirectory()
    }
}

internal fun File.listFilesSafe(): Array<File>? {
    return safeCall(default = null) {
        @Suppress("UnsafeThirdPartyFunctionCall")
        listFiles()
    }
}

internal fun File.listFilesSafe(filter: FileFilter): Array<File>? {
    return safeCall(default = null) {
        @Suppress("UnsafeThirdPartyFunctionCall")
        listFiles(filter)
    }
}

internal fun File.lengthSafe(): Long {
    return safeCall(default = 0L) {
        @Suppress("UnsafeThirdPartyFunctionCall")
        length()
    }
}

internal fun File.mkdirsSafe(): Boolean {
    return safeCall(default = false) {
        @Suppress("UnsafeThirdPartyFunctionCall")
        mkdirs()
    }
}

internal fun File.renameToSafe(dest: File): Boolean {
    return safeCall(default = false) {
        @Suppress("UnsafeThirdPartyFunctionCall")
        renameTo(dest)
    }
}

internal fun File.readTextSafe(charset: Charset = Charsets.UTF_8): String? {
    return if (existsSafe() && canReadSafe()) {
        safeCall(default = null) {
            @Suppress("UnsafeThirdPartyFunctionCall")
            readText(charset)
        }
    } else {
        null
    }
}

internal fun File.readBytesSafe(): ByteArray? {
    return if (existsSafe() && canReadSafe()) {
        safeCall(default = null) {
            @Suppress("UnsafeThirdPartyFunctionCall")
            readBytes()
        }
    } else {
        null
    }
}

internal fun File.readLinesSafe(charset: Charset = Charsets.UTF_8): List<String>? {
    return if (existsSafe() && canReadSafe()) {
        safeCall(default = null) {
            @Suppress("UnsafeThirdPartyFunctionCall")
            readLines(charset)
        }
    } else {
        null
    }
}
