/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import com.datadog.android.core.internal.persistence.file.EncryptedFileHandler
import com.datadog.android.core.internal.persistence.file.EventMeta
import com.datadog.android.core.internal.persistence.file.FileHandler
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.file.isDirectorySafe
import com.datadog.android.core.internal.persistence.file.lengthSafe
import com.datadog.android.core.internal.persistence.file.listFilesSafe
import com.datadog.android.core.internal.persistence.file.mkdirsSafe
import com.datadog.android.core.internal.persistence.file.renameToSafe
import com.datadog.android.core.internal.utils.copyTo
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
import java.util.Locale

internal class BatchFileHandler(
    private val internalLogger: Logger,
    private val metaGenerator: (data: ByteArray) -> ByteArray = {
        EventMeta(it.size).asBytes
    },
    private val metaParser: (metaBytes: ByteArray) -> EventMeta = {
        EventMeta.fromBytes(it)
    }
) : FileHandler {

    // region FileHandler

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

                if (meta.size > MAX_META_SIZE_BYTES) {
                    @Suppress("ThrowingInternalException")
                    throw MetaTooBigException(
                        "Meta size is bigger than limit of $MAX_META_SIZE_BYTES" +
                            " bytes, cannot write data."
                    )
                }

                // 1 byte for version
                // 1 byte for meta.size value
                // rest is meta
                val header = ByteArray(2 + meta.size).apply {
                    set(0, HEADER_VERSION)
                    // in Kotlin and Java byte type is signed, meaning it goes from -127 to 128.
                    // It is completely fine to have size more than 128, it will be just stored
                    // as negative value. Later at read() byte step we will get strictly positive
                    // value, because read() returns int.
                    set(1, meta.size.toByte())
                    meta.copyTo(0, this, 2, meta.size)
                }

                outputStream.write(header)
                outputStream.write(data)
            }
        }
    }

    @Throws(IOException::class)
    @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
    private fun readFileData(
        file: File
    ): List<ByteArray> {
        val inputLength = file.lengthSafe().toInt()

        val result = mutableListOf<ByteArray>()

        // Read file iteratively
        var remaining = inputLength
        file.inputStream().buffered().use {
            while (remaining > 0) {
                val (meta, headerSize) = readEventHeader(it) ?: break

                val eventBytes = ByteArray(meta.eventSize)
                val readEventSize = it.read(eventBytes, 0, meta.eventSize)

                if (!checkReadSizeExpected(meta.eventSize, readEventSize, "read event")) {
                    break
                }

                result.add(eventBytes)
                val read = headerSize + readEventSize
                remaining -= read
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

    @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
    private fun readEventHeader(stream: InputStream): Pair<EventMeta, Int>? {
        val version = stream.read()
        if (version < 0) {
            internalLogger.e(ERROR_EOF_AT_VERSION_BYTE)
            return null
        }

        val metaSize = stream.read()
        if (metaSize < 0) {
            internalLogger.e(ERROR_EOF_AT_META_SIZE_BYTE)
            return null
        }

        val metaBytes = ByteArray(metaSize)
        val readMetaSize = stream.read(metaBytes, 0, metaBytes.size)

        if (!checkReadSizeExpected(metaSize, readMetaSize, "read meta")) {
            return null
        }

        val meta = try {
            metaParser(metaBytes)
        } catch (e: JsonParseException) {
            internalLogger.e(ERROR_FAILED_META_PARSE, e)
            return null
        }

        return meta to 2 + readMetaSize
    }

    private fun checkReadSizeExpected(expected: Int, actual: Int, operation: String): Boolean {
        return if (expected != actual) {
            internalLogger.e(
                "Number of bytes read for operation='$operation' doesn't" +
                    " match with expected: expected=$expected, actual=$actual"
            )
            false
        } else {
            true
        }
    }

    internal class MetaTooBigException(message: String) : IOException(message)

    // endregion

    @Suppress("StringLiteralDuplication")
    companion object {

        internal const val HEADER_VERSION: Byte = 1
        internal const val MAX_META_SIZE_BYTES = 255

        internal const val ERROR_WRITE = "Unable to write data to file: %s"
        internal const val ERROR_READ = "Unable to read data from file: %s"
        internal const val ERROR_DELETE = "Unable to delete file: %s"
        internal const val INFO_MOVE_NO_SRC = "Unable to move files; " +
            "source directory does not exist: %s"
        internal const val ERROR_MOVE_NOT_DIR = "Unable to move files; " +
            "file is not a directory: %s"
        internal const val ERROR_MOVE_NO_DST = "Unable to move files; " +
            "could not create directory: %s"

        internal const val ERROR_EOF_AT_META_SIZE_BYTE =
            "Cannot read meta size byte, because EOF reached."
        internal const val ERROR_EOF_AT_VERSION_BYTE =
            "Cannot read version byte, because EOF reached."
        internal const val ERROR_FAILED_META_PARSE =
            "Failed to parse meta bytes, stopping file read."
        internal const val WARNING_NOT_ALL_DATA_READ =
            "File %s is probably corrupted, not all content was read."

        /**
         * Creates either plain [BatchFileHandler] or [BatchFileHandler] wrapped in
         * [EncryptedFileHandler] if encryption is provided.
         */
        fun create(internalLogger: Logger, encryption: Encryption?): FileHandler {
            return if (encryption == null) {
                BatchFileHandler(internalLogger)
            } else {
                EncryptedFileHandler(encryption, BatchFileHandler(internalLogger))
            }
        }
    }
}
