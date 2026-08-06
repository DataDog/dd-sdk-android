/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.internal.persistence.file.lengthSafe
import com.datadog.android.core.internal.utils.use
import com.datadog.android.internal.telemetry.TelemetryContext
import com.datadog.android.internal.telemetry.TelemetryContext.Companion.TELEMETRY_BATCH_BYTES_ACTUAL
import com.datadog.android.internal.telemetry.TelemetryContext.Companion.TELEMETRY_BATCH_BYTES_EXPECTED
import com.datadog.android.internal.telemetry.TelemetryContext.Companion.TELEMETRY_BATCH_OPERATION
import com.datadog.android.internal.telemetry.TelemetryContext.Companion.TELEMETRY_BLOCK_TYPE_ACTUAL_IDENTIFIER
import com.datadog.android.internal.telemetry.TelemetryContext.Companion.TELEMETRY_BLOCK_TYPE_EXPECTED
import com.datadog.android.internal.telemetry.TelemetryContext.Companion.TELEMETRY_BLOCK_TYPE_EXPECTED_IDENTIFIER
import com.datadog.android.internal.telemetry.TelemetryContext.Companion.TELEMETRY_FILE_PATH
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.max

/**
 * Stores data in the TLV format as meta+data, use only for RUM/Log/Trace events.
 */
internal class PlainBatchFileReaderWriter(
    private val internalLogger: InternalLogger
) : BatchFileReaderWriter {

    // region BatchFileReaderWriter

    @Suppress("UnsafeThirdPartyFunctionCall", "UNUSED_PARAMETER")
    override fun serializeToBytes(
        data: RawBatchEvent,
        telemetryContext: TelemetryContext
    ): ByteArray {
        val meta = data.metadata
        val metaBlockSize = TYPE_SIZE_BYTES + LENGTH_SIZE_BYTES + meta.size
        val dataBlockSize = TYPE_SIZE_BYTES + LENGTH_SIZE_BYTES + data.data.size

        // ByteBuffer by default has BigEndian ordering, which matches to how Java
        // reads data, so no need to define it explicitly
        return ByteBuffer
            .allocate(metaBlockSize + dataBlockSize)
            .putAsTlv(BlockType.META, meta)
            .putAsTlv(BlockType.EVENT, data.data)
            .array()
    }

    @WorkerThread
    override fun writeBinaryData(
        file: File,
        bytes: ByteArray,
        append: Boolean,
        telemetryContext: TelemetryContext
    ): Boolean {
        return try {
            lockFileAndWriteData(file, append, bytes)
            true
        } catch (e: IOException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { ERROR_WRITE },
                e,
                additionalProperties = telemetryContext.asAttributesMap(
                    bytesLost = bytes.size,
                    TELEMETRY_FILE_PATH to file.path
                )
            )
            false
        } catch (e: SecurityException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { ERROR_WRITE },
                e,
                additionalProperties = telemetryContext.asAttributesMap(
                    bytesLost = bytes.size,
                    TELEMETRY_FILE_PATH to file.path
                )
            )
            false
        }
    }

    // endregion

    // region FileReader

    @WorkerThread
    override fun readData(
        file: File,
        telemetryContext: TelemetryContext
    ): List<RawBatchEvent> {
        val inputLength = file.lengthSafe(internalLogger).toInt()
        return try {
            readFileData(file, inputLength, telemetryContext)
        } catch (e: IOException) {
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                messageBuilder = { ERROR_READ },
                throwable = e,
                additionalProperties = telemetryContext.asAttributesMap(
                    bytesLost = inputLength,
                    TELEMETRY_FILE_PATH to file.path
                )
            )
            emptyList()
        } catch (e: SecurityException) {
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                messageBuilder = { ERROR_READ },
                throwable = e,
                additionalProperties = telemetryContext.asAttributesMap(
                    bytesLost = inputLength,
                    TELEMETRY_FILE_PATH to file.path
                )
            )
            emptyList()
        }
    }

    // endregion

    // region Internal

    @Throws(IOException::class)
    @Suppress("UnsafeThirdPartyFunctionCall", "NestedBlockDepth") // Called within a try/catch block
    private fun lockFileAndWriteData(
        file: File,
        append: Boolean,
        bytes: ByteArray
    ) {
        RandomAccessFile(file, "rw").use { raf ->
            val channel = raf.channel
            channel.lock().use {
                // Snapshot the pre-write length so a failed write can be rolled back
                // to the last good record boundary (TLV records would otherwise become
                // misaligned and corrupt every subsequent append).
                val rollbackLength = if (append) channel.size() else 0L
                channel.truncate(rollbackLength)
                channel.position(rollbackLength)

                val buffer = ByteBuffer.wrap(bytes)

                try {
                    while (buffer.hasRemaining()) {
                        channel.write(buffer)
                    }
                } catch (e: IOException) {
                    // Truncating to a smaller size frees blocks rather than allocating,
                    // so it succeeds even when the failure was ENOSPC.
                    try {
                        channel.truncate(rollbackLength)
                    } catch (fallbackException: IOException) {
                        internalLogger.log(
                            level = InternalLogger.Level.ERROR,
                            target = InternalLogger.Target.USER,
                            messageBuilder = { ERROR_WRITE_FALLBACK.format(Locale.US, file.path) },
                            throwable = fallbackException
                        )
                    }
                    @Suppress("ThrowingInternalException") // we are just propagating existing one
                    throw e
                }
            }
        }
    }

    @Throws(IOException::class)
    @Suppress("UnsafeThirdPartyFunctionCall", "ComplexMethod", "LoopWithTooManyJumpStatements")
    // Called within a try/catch block
    private fun readFileData(
        file: File,
        inputLength: Int,
        telemetryContext: TelemetryContext
    ): List<RawBatchEvent> {
        val result = mutableListOf<RawBatchEvent>()

        // Read file iteratively
        var remaining = inputLength
        file.inputStream().buffered().use {
            while (remaining > 0) {
                val metaReadResult = readBlock(it, BlockType.META, telemetryContext, remaining)
                if (metaReadResult.data == null) {
                    remaining -= metaReadResult.bytesRead
                    break
                }

                val eventReadResult = readBlock(it, BlockType.EVENT, telemetryContext, remaining)
                remaining -= metaReadResult.bytesRead + eventReadResult.bytesRead

                if (eventReadResult.data == null) break

                result.add(RawBatchEvent(eventReadResult.data, metaReadResult.data))
            }
        }

        if (remaining != 0 || (inputLength > 0 && result.isEmpty())) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.USER, InternalLogger.Target.MAINTAINER),
                { WARNING_NOT_ALL_DATA_READ.format(Locale.US, file.path) }
            )
        }

        return result
    }

    @Suppress("ReturnCount")
    @Throws(IOException::class)
    private fun readBlock(
        stream: InputStream,
        expectedBlockType: BlockType,
        telemetryContext: TelemetryContext,
        remaining: Int
    ): BlockReadResult {
        @Suppress("UnsafeThirdPartyFunctionCall") // allocation size is always positive
        val headerBuffer = ByteBuffer.allocate(HEADER_SIZE_BYTES)

        @Suppress("UnsafeThirdPartyFunctionCall") // method declares throwing IOException
        val headerReadBytes = stream.read(headerBuffer.array())

        if (!checkReadExpected(
                HEADER_SIZE_BYTES,
                headerReadBytes,
                "Block(${expectedBlockType.name}): Header read",
                telemetryContext,
                remaining
            )
        ) {
            return BlockReadResult(null, max(0, headerReadBytes))
        }

        val blockType = headerBuffer.short
        if (blockType != expectedBlockType.identifier) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { ERROR_UNEXPECTED_BLOCK_TYPE_MET },
                additionalProperties = telemetryContext.asAttributesMap(
                    bytesLost = remaining,
                    TELEMETRY_BLOCK_TYPE_ACTUAL_IDENTIFIER to blockType,
                    TELEMETRY_BLOCK_TYPE_EXPECTED_IDENTIFIER to expectedBlockType.identifier,
                    TELEMETRY_BLOCK_TYPE_EXPECTED to expectedBlockType.name
                )
            )
            // in theory, we could continue reading, because we still know data size,
            // but unexpected type says that at least relationship between blocks is broken,
            // so to not establish the wrong one, it is better to stop reading
            return BlockReadResult(null, headerReadBytes)
        }

        val dataSize = headerBuffer.int
        val dataBuffer = ByteArray(dataSize)

        @Suppress("UnsafeThirdPartyFunctionCall") // method declares throwing IOException
        val dataReadBytes = stream.read(dataBuffer)

        return if (checkReadExpected(
                dataSize,
                dataReadBytes,
                "Block(${expectedBlockType.name}):Data read",
                telemetryContext,
                remaining
            )
        ) {
            BlockReadResult(dataBuffer, headerReadBytes + dataReadBytes)
        } else {
            BlockReadResult(null, headerReadBytes + max(0, dataReadBytes))
        }
    }

    private fun checkReadExpected(
        expected: Int,
        actual: Int,
        operation: String,
        telemetryContext: TelemetryContext,
        bytesLost: Int
    ): Boolean {
        return if (expected != actual) {
            if (actual != -1) {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                    { ERROR_UNEXPECTED_NUMBERS_OF_BYTES },
                    additionalProperties = telemetryContext.asAttributesMap(
                        bytesLost = bytesLost,
                        TELEMETRY_BATCH_OPERATION to operation,
                        TELEMETRY_BATCH_BYTES_EXPECTED to expected,
                        TELEMETRY_BATCH_BYTES_ACTUAL to actual
                    )
                )
            } else {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                    { ERROR_UNEXPECTED_EOF },
                    additionalProperties = telemetryContext.asAttributesMap(
                        bytesLost = bytesLost,
                        TELEMETRY_BATCH_OPERATION to operation
                    )
                )
            }
            false
        } else {
            true
        }
    }

    @Suppress("UnsafeThirdPartyFunctionCall")
    // all calls here are safe: buffer is writable, and it has a proper size calculated before
    // Encoding specification is as following:
    // +-  2 bytes -+-   4 bytes   -+- n bytes -|
    // | block type | data size (n) |    data   |
    // +------------+---------------+-----------+
    // where block type is 0x00 for event, 0x01 for data
    private fun ByteBuffer.putAsTlv(blockType: BlockType, data: ByteArray): ByteBuffer {
        return this
            .putShort(blockType.identifier)
            .putInt(data.size)
            .put(data)
    }

    private class BlockReadResult(
        val data: ByteArray?,
        val bytesRead: Int
    )

    private enum class BlockType(val identifier: Short) {
        EVENT(0x00),
        META(0x01)
    }

    // endregion

    companion object {

        // TLV (Type-Length-Value) constants
        internal const val TYPE_SIZE_BYTES: Int = 2
        internal const val LENGTH_SIZE_BYTES: Int = 4
        internal const val HEADER_SIZE_BYTES: Int = TYPE_SIZE_BYTES + LENGTH_SIZE_BYTES

        internal const val ERROR_WRITE = "Unable to write data to file."
        internal const val ERROR_WRITE_FALLBACK = "Unable to restore file after failed write: %s"
        internal const val ERROR_READ = "Unable to read data from file."

        internal const val ERROR_UNEXPECTED_EOF = "Unexpected EOF"
        internal const val ERROR_UNEXPECTED_BLOCK_TYPE_MET = "Unexpected block type identifier met"
        internal const val ERROR_UNEXPECTED_NUMBERS_OF_BYTES = "Number of bytes read doesn't match with expected"
        internal const val WARNING_NOT_ALL_DATA_READ =
            "File %s is probably corrupted, not all content was read."
    }
}
