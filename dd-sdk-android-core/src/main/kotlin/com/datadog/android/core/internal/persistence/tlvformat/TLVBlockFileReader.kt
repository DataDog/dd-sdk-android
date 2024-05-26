/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.tlvformat

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.datastore.ext.toInt
import com.datadog.android.core.internal.persistence.datastore.ext.toShort
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.file.lengthSafe
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale

internal class TLVBlockFileReader(
    val internalLogger: InternalLogger,
    val fileReaderWriter: FileReaderWriter
) {
    @WorkerThread
    internal fun read(file: File): List<TLVBlock> {
        if (!file.existsSafe(internalLogger) || file.lengthSafe(internalLogger) == 0L) {
            return arrayListOf()
        }

        val blocks = mutableListOf<TLVBlock>()
        val stream = fileReaderWriter.readData(file).inputStream()

        var nextBlock: TLVBlock?
        do {
            nextBlock = readBlock(stream)
            if (nextBlock != null) blocks.add(nextBlock)
        } while (nextBlock != null)

        return blocks
    }

    private fun readBlock(stream: InputStream): TLVBlock? {
        val type = readType(stream) ?: return null

        val data = readData(stream)

        return TLVBlock(type, data)
    }

    private fun readType(stream: InputStream): TLVBlockType? {
        val typeBlockSize = UShort.SIZE_BYTES
        val bytes = read(stream, typeBlockSize)

        if (bytes.size != typeBlockSize) {
            return null
        }

        val shortValue = bytes.toShort()
        val tlvHeader = TLVBlockType.fromValue(shortValue.toUShort())

        if (tlvHeader == null) {
            logTypeCorruptionError(shortValue)
        }

        return tlvHeader
    }

    private fun readData(stream: InputStream): ByteArray {
        val lengthBlockSize = Int.SIZE_BYTES
        val lengthInBytes = read(stream, lengthBlockSize)
        val length = lengthInBytes.toInt()

        return read(stream, length)
    }

    private fun read(stream: InputStream, length: Int): ByteArray {
        val buffer = ByteArray(length)
        val status = safeReadFromStream(stream, buffer)
        return if (status == -1) { // stream is finished (or error)
            ByteArray(0)
        } else {
            buffer
        }
    }

    private fun logTypeCorruptionError(shortValue: Short) {
        internalLogger.log(
            target = InternalLogger.Target.MAINTAINER,
            level = InternalLogger.Level.ERROR,
            messageBuilder = {
                CORRUPT_TLV_HEADER_TYPE_ERROR.format(
                    Locale.US,
                    shortValue
                )
            }
        )
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught", "UnsafeThirdPartyFunctionCall")
    private fun safeReadFromStream(stream: InputStream, buffer: ByteArray): Int {
        return try {
            stream.read(buffer)
        } catch (e: IOException) {
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                messageBuilder = { FAILED_TO_READ_FROM_INPUT_STREAM_ERROR },
                e
            )
            -1
        } catch (e: NullPointerException) {
            // cannot happen - buffer is not null
            -1
        }
    }

    internal companion object {
        internal const val CORRUPT_TLV_HEADER_TYPE_ERROR = "TLV header corrupt. Invalid type"
        internal const val FAILED_TO_READ_FROM_INPUT_STREAM_ERROR =
            "Failed to read from input stream"
    }
}
