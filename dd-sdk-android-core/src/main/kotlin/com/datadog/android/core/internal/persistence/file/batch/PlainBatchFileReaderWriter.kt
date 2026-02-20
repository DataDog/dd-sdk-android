/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.internal.generated.DdSdkAndroidCoreLogger
import com.datadog.android.core.internal.persistence.file.lengthSafe
import com.datadog.android.core.internal.utils.use
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.max

/**
 * Stores data in the TLV format as meta+data, use only for RUM/Log/Trace events.
 */
internal class PlainBatchFileReaderWriter(
    private val internalLogger: InternalLogger
) : BatchFileReaderWriter {

    private val logger = DdSdkAndroidCoreLogger(internalLogger)

    // region FileWriter

    @WorkerThread
    override fun writeData(
        file: File,
        data: RawBatchEvent,
        append: Boolean
    ): Boolean {
        return try {
            lockFileAndWriteData(file, append, data)
            true
        } catch (e: IOException) {
            logger.logPlainBatchWriteIoError(
                filePath = file.path,
                throwable = e
            )
            false
        } catch (e: SecurityException) {
            logger.logPlainBatchWriteSecurityError(
                filePath = file.path,
                throwable = e
            )
            false
        }
    }

    // endregion

    // region FileReader

    @WorkerThread
    override fun readData(
        file: File
    ): List<RawBatchEvent> {
        return try {
            readFileData(file)
        } catch (e: IOException) {
            logger.logPlainBatchReadIoError(
                filePath = file.path,
                throwable = e
            )
            emptyList()
        } catch (e: SecurityException) {
            logger.logReadSecurityError(
                filePath = file.path,
                throwable = e
            )
            emptyList()
        }
    }

    // endregion

    // region Internal

    @Throws(IOException::class)
    @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
    private fun lockFileAndWriteData(
        file: File,
        append: Boolean,
        data: RawBatchEvent
    ) {
        FileOutputStream(file, append).use { outputStream ->
            outputStream.channel.lock().use {
                val meta = data.metadata

                val metaBlockSize = TYPE_SIZE_BYTES + LENGTH_SIZE_BYTES + meta.size
                val dataBlockSize = TYPE_SIZE_BYTES + LENGTH_SIZE_BYTES + data.data.size

                // ByteBuffer by default has BigEndian ordering, which matches to how Java
                // reads data, so no need to define it explicitly
                val buffer = ByteBuffer
                    .allocate(metaBlockSize + dataBlockSize)
                    .putAsTlv(BlockType.META, meta)
                    .putAsTlv(BlockType.EVENT, data.data)

                outputStream.write(buffer.array())
            }
        }
    }

    @Throws(IOException::class)
    @Suppress("UnsafeThirdPartyFunctionCall", "ComplexMethod", "LoopWithTooManyJumpStatements")
    // Called within a try/catch block
    private fun readFileData(
        file: File
    ): List<RawBatchEvent> {
        val inputLength = file.lengthSafe(internalLogger).toInt()

        val result = mutableListOf<RawBatchEvent>()

        // Read file iteratively
        var remaining = inputLength
        file.inputStream().buffered().use {
            while (remaining > 0) {
                val metaReadResult = readBlock(it, BlockType.META)
                if (metaReadResult.data == null) {
                    remaining -= metaReadResult.bytesRead
                    break
                }

                val eventReadResult = readBlock(it, BlockType.EVENT)
                remaining -= metaReadResult.bytesRead + eventReadResult.bytesRead

                if (eventReadResult.data == null) break

                result.add(RawBatchEvent(eventReadResult.data, metaReadResult.data))
            }
        }

        if (remaining != 0 || (inputLength > 0 && result.isEmpty())) {
            logger.logNotAllDataRead(filePath = file.path)
        }

        return result
    }

    @Suppress("ReturnCount")
    @Throws(IOException::class)
    private fun readBlock(stream: InputStream, expectedBlockType: BlockType): BlockReadResult {
        @Suppress("UnsafeThirdPartyFunctionCall") // allocation size is always positive
        val headerBuffer = ByteBuffer.allocate(HEADER_SIZE_BYTES)

        @Suppress("UnsafeThirdPartyFunctionCall") // method declares throwing IOException
        val headerReadBytes = stream.read(headerBuffer.array())

        if (!checkReadExpected(
                HEADER_SIZE_BYTES,
                headerReadBytes,
                "Block(${expectedBlockType.name}): Header read"
            )
        ) {
            return BlockReadResult(null, max(0, headerReadBytes))
        }

        val blockType = headerBuffer.short
        if (blockType != expectedBlockType.identifier) {
            logger.logUnexpectedBlockType(
                blockType = blockType.toInt(),
                expectedBlockTypeName = expectedBlockType.name,
                expectedBlockTypeId = expectedBlockType.identifier.toInt()
            )
            // in theory we could continue reading, because we still know data size,
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
                "Block(${expectedBlockType.name}):Data read"
            )
        ) {
            BlockReadResult(dataBuffer, headerReadBytes + dataReadBytes)
        } else {
            BlockReadResult(null, headerReadBytes + max(0, dataReadBytes))
        }
    }

    private fun checkReadExpected(expected: Int, actual: Int, operation: String): Boolean {
        return if (expected != actual) {
            if (actual != -1) {
                logger.logBytesReadMismatch(
                    operation = operation,
                    expected = expected,
                    actual = actual
                )
            } else {
                logger.logPlainBatchUnexpectedEof(operation = operation)
            }
            false
        } else {
            true
        }
    }

    @Suppress("UnsafeThirdPartyFunctionCall")
    // all calls here are safe: buffer is writable and it has a proper size calculated before
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
    }
}
