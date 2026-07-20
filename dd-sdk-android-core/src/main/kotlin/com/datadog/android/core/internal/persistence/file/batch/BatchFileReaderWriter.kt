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

internal interface BatchFileReaderWriter : BatchFileReader {

    /**
     * Serializes an event into the exact binary payload that would be written to disk (TLV-wrapped,
     * and encrypted if encryption is configured). The size of the returned array is the exact number
     * of bytes [writeBinaryData] would append for this event.
     * @param data the event to serialize
     * @return the on-disk binary representation of the event, or null if the event could not be
     * serialized (e.g. encryption produced an empty result for non-empty data). Callers must skip
     * the write when null is returned.
     */
    fun serializeToBytes(data: RawBatchEvent): ByteArray?

    /**
     * Writes a pre-serialized payload (as returned by [serializeToBytes]) to a file.
     * @param file the file to write to
     * @param bytes the pre-serialized payload to write
     * @param append whether to append data at the end of the file or overwrite
     * @return whether the write operation was successful
     */
    @WorkerThread
    fun writeBinaryData(file: File, bytes: ByteArray, append: Boolean): Boolean

    companion object {
        /**
         * Creates either plain [PlainBatchFileReaderWriter] or [PlainBatchFileReaderWriter] wrapped in
         * [EncryptedBatchReaderWriter] if encryption is provided.
         */
        fun create(internalLogger: InternalLogger, encryption: Encryption?): BatchFileReaderWriter {
            val readerWriter = PlainBatchFileReaderWriter(internalLogger)
            return if (encryption == null) {
                readerWriter
            } else {
                EncryptedBatchReaderWriter(
                    encryption,
                    readerWriter,
                    internalLogger
                )
            }
        }
    }
}
