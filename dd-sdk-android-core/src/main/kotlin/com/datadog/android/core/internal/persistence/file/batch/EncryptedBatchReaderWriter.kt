/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.security.Encryption
import java.io.File

internal class EncryptedBatchReaderWriter(
    internal val encryption: Encryption,
    internal val delegate: BatchFileReaderWriter,
    private val internalLogger: InternalLogger
) : BatchFileReaderWriter by delegate {

    @WorkerThread
    override fun writeData(
        file: File,
        data: RawBatchEvent,
        append: Boolean
    ): Boolean {
        val encryptedRawBatchEvent = RawBatchEvent(
            data = encryption.encrypt(data.data),
            metadata = encryption.encrypt(data.metadata)
        )

        if (data.data.isNotEmpty() && encryptedRawBatchEvent.data.isEmpty()) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { BAD_ENCRYPTION_RESULT_MESSAGE }
            )
            return false
        }

        return delegate.writeData(
            file,
            encryptedRawBatchEvent,
            append
        )
    }

    @WorkerThread
    override fun readData(
        file: File
    ): List<RawBatchEvent> {
        return delegate.readData(file)
            .map {
                RawBatchEvent(
                    data = if (it.data.isNotEmpty()) encryption.decrypt(it.data) else it.data,
                    metadata = if (it.metadata.isNotEmpty()) encryption.decrypt(it.metadata) else it.metadata
                )
            }
    }

    companion object {
        internal const val BAD_ENCRYPTION_RESULT_MESSAGE = "Encryption of non-empty data produced" +
            " empty result, aborting write operation."
    }
}
