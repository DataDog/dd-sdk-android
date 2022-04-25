/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file

import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.security.Encryption
import java.io.File

internal class EncryptedFileHandler(
    internal val encryption: Encryption,
    internal val delegate: FileHandler
) : FileHandler by delegate {

    override fun writeData(
        file: File,
        data: ByteArray,
        append: Boolean
    ): Boolean {
        val encryptedData = encryption.encrypt(data)

        if (data.isNotEmpty() && encryptedData.isEmpty()) {
            devLogger.e(BAD_ENCRYPTION_RESULT_MESSAGE)
            return false
        }

        return delegate.writeData(
            file,
            encryptedData,
            append
        )
    }

    override fun readData(
        file: File
    ): List<ByteArray> {
        return delegate.readData(file)
            .map {
                encryption.decrypt(it)
            }
    }

    companion object {
        internal const val BAD_ENCRYPTION_RESULT_MESSAGE = "Encryption of non-empty data produced" +
            " empty result, aborting write operation."
    }
}
