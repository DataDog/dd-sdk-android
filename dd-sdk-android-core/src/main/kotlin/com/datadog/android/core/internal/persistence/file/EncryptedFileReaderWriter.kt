/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file

import androidx.annotation.WorkerThread
import com.datadog.android.security.Encryption
import com.datadog.android.v2.api.InternalLogger
import java.io.File

internal class EncryptedFileReaderWriter(
    internal val encryption: Encryption,
    internal val delegate: FileReaderWriter,
    private val internalLogger: InternalLogger
) : FileReaderWriter by delegate {

    @Suppress("ReturnCount")
    @WorkerThread
    override fun writeData(
        file: File,
        data: ByteArray,
        append: Boolean
    ): Boolean {
        if (append) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { APPEND_MODE_NOT_SUPPORTED_MESSAGE }
            )
            return false
        }

        val encryptedData = encryption.encrypt(data)

        if (data.isNotEmpty() && encryptedData.isEmpty()) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { BAD_ENCRYPTION_RESULT_MESSAGE }
            )
            return false
        }

        return delegate.writeData(
            file,
            encryptedData,
            append
        )
    }

    @WorkerThread
    override fun readData(
        file: File
    ): ByteArray {
        return encryption.decrypt(delegate.readData(file))
    }

    companion object {
        internal const val BAD_ENCRYPTION_RESULT_MESSAGE = "Encryption of non-empty data produced" +
            " empty result, aborting write operation."
        internal const val APPEND_MODE_NOT_SUPPORTED_MESSAGE = "Append mode is not supported," +
            " use EncryptedBatchFileReaderWriter instead."
    }
}
