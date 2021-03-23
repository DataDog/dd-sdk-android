/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
@file:Suppress("TooManyFunctions")
package com.datadog.android.core.internal.persistence.file

import com.datadog.android.core.internal.utils.sdkLogger
import java.io.File
import java.io.FileFilter
import java.io.FilenameFilter

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

private fun <T> File.safeCall(
    default: T,
    lambda: File.() -> T
): T {
    return try {
        lambda()
    } catch (e: SecurityException) {
        sdkLogger.e("Security exception was thrown for file ${this.path}", e)
        default
    }
}

internal fun File.canWriteSafe(): Boolean {
    return safeCall(default = false) { canWrite() }
}

internal fun File.canReadSafe(): Boolean {
    return safeCall(default = false) { canRead() }
}

internal fun File.createNewFileSafe(): Boolean {
    return safeCall(default = false) { createNewFile() }
}

internal fun File.deleteSafe(): Boolean {
    return safeCall(default = false) { delete() }
}

internal fun File.deleteOnExitSafe() {
    return safeCall(default = Unit) { deleteOnExit() }
}

internal fun File.existsSafe(): Boolean {
    return safeCall(default = false) { exists() }
}

internal fun File.isFileSafe(): Boolean {
    return safeCall(default = false) { isFile() }
}

internal fun File.isDirectorySafe(): Boolean {
    return safeCall(default = false) { isDirectory() }
}

internal fun File.listSafe(): Array<String>? {
    return safeCall(default = null) { list() }
}

internal fun File.listSafe(filter: FilenameFilter): Array<String>? {
    return safeCall(default = null) { list(filter) }
}

internal fun File.listFilesSafe(): Array<File>? {
    return safeCall(default = null) { listFiles() }
}

internal fun File.listFilesSafe(filter: FilenameFilter): Array<File>? {
    return safeCall(default = null) { listFiles(filter) }
}

internal fun File.listFilesSafe(filter: FileFilter): Array<File>? {
    return safeCall(default = null) { listFiles(filter) }
}

internal fun File.lengthSafe(): Long {
    return safeCall(default = 0L) { length() }
}

internal fun File.mkdirSafe(): Boolean {
    return safeCall(default = false) { mkdir() }
}

internal fun File.mkdirsSafe(): Boolean {
    return safeCall(default = false) { mkdirs() }
}

internal fun File.renameToSafe(dest: File): Boolean {
    return safeCall(default = false) { renameTo(dest) }
}
