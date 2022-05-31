/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.persistence.file.ChunkedFileHandler
import com.datadog.android.core.internal.persistence.file.EventMeta
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.file.isDirectorySafe
import com.datadog.android.core.internal.persistence.file.lengthSafe
import com.datadog.android.core.internal.persistence.file.listFilesSafe
import com.datadog.android.core.internal.persistence.file.mkdirsSafe
import com.datadog.android.core.internal.persistence.file.renameToSafe
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.core.internal.utils.use
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.utils.errorWithTelemetry
import com.datadog.android.security.Encryption
import com.google.gson.JsonParseException
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.max

internal class BatchFileHandler(
    private val internalLogger: Logger,
    private val metaGenerator: (data: ByteArray) -> ByteArray = {
        EventMeta().asBytes
    },
    private val metaParser: (metaBytes: ByteArray) -> EventMeta = {
        EventMeta.fromBytes(it)
    }
) : ChunkedFileHandler {

    // region FileHandler

    @WorkerThread
    override fun writeData(
        file: File,
        data: ByteArray,
        append: Boolean
    ): Boolean {
        return try {
            lockFileAndWriteData(file, append, data)
            true
        } catch (e: IOException) {
            internalLogger.errorWithTelemetry(ERROR_WRITE.format(Locale.US, file.path), e)
            false
        } catch (e: SecurityException) {
            internalLogger.errorWithTelemetry(ERROR_WRITE.format(Locale.US, file.path), e)
            false
        }
    }

    @WorkerThread
    override fun readData(
        file: File
    ): List<ByteArray> {
        return try {
            readFileData(file)
        } catch (e: IOException) {
            internalLogger.errorWithTelemetry(ERROR_READ.format(Locale.US, file.path), e)
            emptyList()
        } catch (e: SecurityException) {
            internalLogger.errorWithTelemetry(ERROR_READ.format(Locale.US, file.path), e)
            emptyList()
        }
    }

    @WorkerThread
    override fun delete(target: File): Boolean {
        return try {
            target.deleteRecursively()
        } catch (e: FileNotFoundException) {
            internalLogger.errorWithTelemetry(ERROR_DELETE.format(Locale.US, target.path), e)
            false
        } catch (e: SecurityException) {
            internalLogger.errorWithTelemetry(ERROR_DELETE.format(Locale.US, target.path), e)
            false
        }
    }

    @WorkerThread
    override fun moveFiles(srcDir: File, destDir: File): Boolean {
        if (!srcDir.existsSafe()) {
            internalLogger.i(INFO_MOVE_NO_SRC.format(Locale.US, srcDir.path))
            return true
        }
        if (!srcDir.isDirectorySafe()) {
            internalLogger.errorWithTelemetry(ERROR_MOVE_NOT_DIR.format(Locale.US, srcDir.path))
            return false
        }
        if (!destDir.existsSafe()) {
            if (!destDir.mkdirsSafe()) {
                internalLogger.errorWithTelemetry(ERROR_MOVE_NO_DST.format(Locale.US, srcDir.path))
                return false
            }
        } else if (!destDir.isDirectorySafe()) {
            internalLogger.errorWithTelemetry(ERROR_MOVE_NOT_DIR.format(Locale.US, destDir.path))
            return false
        }

        val srcFiles = srcDir.listFilesSafe().orEmpty()
        return srcFiles.all { file -> moveFile(file, destDir) }
    }

    // endregion

    // region Internal

    @Throws(IOException::class)
    @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
    private fun lockFileAndWriteData(
        file: File,
        append: Boolean,
        data: ByteArray
    ) {
        FileOutputStream(file, append).use { outputStream ->
            outputStream.channel.lock().use {
                val meta = metaGenerator(data)

                val metaBlockSize = TYPE_SIZE_BYTES + LENGTH_SIZE_BYTES + meta.size
                val dataBlockSize = TYPE_SIZE_BYTES + LENGTH_SIZE_BYTES + data.size

                // ByteBuffer by default has BigEndian ordering, which matches to how Java
                // reads data, so no need to define it explicitly
                val buffer = ByteBuffer
                    .allocate(metaBlockSize + dataBlockSize)
                    .putAsTlv(BlockType.META, meta)
                    .putAsTlv(BlockType.EVENT, data)

                outputStream.write(buffer.array())
            }
        }
    }

    @Throws(IOException::class)
    @Suppress("UnsafeThirdPartyFunctionCall", "ComplexMethod") // Called within a try/catch block
    private fun readFileData(
        file: File
    ): List<ByteArray> {
        val inputLength = file.lengthSafe().toInt()

        val result = mutableListOf<ByteArray>()

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

                // TODO RUMM-2172 bundle meta
                @Suppress("UNUSED_VARIABLE")
                val meta = try {
                    metaParser(metaReadResult.data)
                } catch (e: JsonParseException) {
                    internalLogger.e(ERROR_FAILED_META_PARSE, e)
                    continue
                }

                result.add(eventReadResult.data)
            }
        }

        if (remaining != 0) {
            val message = WARNING_NOT_ALL_DATA_READ.format(Locale.US, file.path)
            devLogger.e(message)
            internalLogger.errorWithTelemetry(message)
        }

        return result
    }

    private fun moveFile(file: File, destDir: File): Boolean {
        val destFile = File(destDir, file.name)
        return file.renameToSafe(destFile)
    }

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
            internalLogger.e(
                "Unexpected block type identifier=$blockType met," +
                    " was expecting $expectedBlockType(${expectedBlockType.identifier})"
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
                internalLogger.e(
                    "Number of bytes read for operation='$operation' doesn't" +
                        " match with expected: expected=$expected, actual=$actual"
                )
            } else {
                internalLogger.e(
                    "Unexpected EOF at the operation=$operation"
                )
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
        META(0x01),
    }

    // endregion

    @Suppress("StringLiteralDuplication")
    companion object {

        // TLV (Type-Length-Value) constants
        internal const val TYPE_SIZE_BYTES: Int = 2
        internal const val LENGTH_SIZE_BYTES: Int = 4
        internal const val HEADER_SIZE_BYTES: Int = TYPE_SIZE_BYTES + LENGTH_SIZE_BYTES

        internal const val ERROR_WRITE = "Unable to write data to file: %s"
        internal const val ERROR_READ = "Unable to read data from file: %s"
        internal const val ERROR_DELETE = "Unable to delete file: %s"
        internal const val INFO_MOVE_NO_SRC = "Unable to move files; " +
            "source directory does not exist: %s"
        internal const val ERROR_MOVE_NOT_DIR = "Unable to move files; " +
            "file is not a directory: %s"
        internal const val ERROR_MOVE_NO_DST = "Unable to move files; " +
            "could not create directory: %s"

        internal const val ERROR_FAILED_META_PARSE =
            "Failed to parse meta bytes, stopping file read."
        internal const val WARNING_NOT_ALL_DATA_READ =
            "File %s is probably corrupted, not all content was read."

        /**
         * Creates either plain [BatchFileHandler] or [BatchFileHandler] wrapped in
         * [EncryptedBatchFileHandler] if encryption is provided.
         */
        fun create(internalLogger: Logger, encryption: Encryption?): ChunkedFileHandler {
            return if (encryption == null) {
                BatchFileHandler(internalLogger)
            } else {
                EncryptedBatchFileHandler(encryption, BatchFileHandler(internalLogger))
            }
        }
    }
}
