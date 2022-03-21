/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file

import android.util.Base64
import com.datadog.android.core.internal.utils.copyTo
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.log.Logger
import com.datadog.android.security.Encryption
import java.io.File

internal class EncryptedFileHandler(
    internal val encryption: Encryption,
    internal val delegate: FileHandler,
    private val internalLogger: Logger,
    private val base64Encoder: (ByteArray) -> ByteArray = { Base64.encode(it, ENCODING_FLAGS) },
    private val base64Decoder: (ByteArray) -> ByteArray = {
        // lambda call is safe-guarded at the call site
        @Suppress("UnsafeThirdPartyFunctionCall")
        Base64.decode(it, ENCODING_FLAGS)
    }
) : FileHandler {

    // region FileHandler

    override fun writeData(
        file: File,
        data: ByteArray,
        append: Boolean,
        separator: ByteArray?
    ): Boolean {
        if (separator != null && !checkSeparator(separator)) {
            internalLogger.e(INVALID_SEPARATOR_MESSAGE)
            return false
        }

        if (append && separator == null) {
            internalLogger.e(MISSING_SEPARATOR_MESSAGE)
            return false
        }

        val encryptedData = encryption.encrypt(data)

        if (data.isNotEmpty() && encryptedData.isEmpty()) {
            devLogger.e(BAD_ENCRYPTION_RESULT_MESSAGE)
            return false
        }

        return delegate.writeData(
            file,
            // Base64 produces bytes per US-ASCII encoding, while separator may be in UTF-8 encoding
            // but this is fine, because UTF-8 is backward compatible with US-ASCII (char in
            // US-ASCII encoding has the same byte value as char in UTF-8 encoding)
            base64Encoder(encryptedData),
            append,
            separator
        )
    }

    override fun readData(
        file: File,
        prefix: ByteArray?,
        suffix: ByteArray?,
        separator: ByteArray?
    ): ByteArray {
        if (separator != null && !checkSeparator(separator)) {
            internalLogger.e(INVALID_SEPARATOR_MESSAGE)
            return EMPTY_BYTE_ARRAY
        }

        val data = delegate.readData(file, prefix, suffix, separator)

        val rawData = removeSuffixAndPrefix(data, prefix, suffix)

        return if (separator != null) {
            val decrypted = decryptBatchData(rawData, separator)
            if (decrypted.isEmpty()) {
                assemble(EMPTY_BYTE_ARRAY, prefix, suffix)
            } else {
                assemble(decrypted, prefix, suffix, separator)
            }
        } else {
            val decrypted = decryptSingleItemData(rawData)
            assemble(decrypted, prefix, suffix)
        }
    }

    override fun delete(target: File) = delegate.delete(target)

    override fun moveFiles(srcDir: File, destDir: File) = delegate.moveFiles(srcDir, destDir)

    // endregion

    // region private

    private fun decryptSingleItemData(data: ByteArray): ByteArray {
        val decoded = safeDecodeBase64(data)
        return if (decoded.isNotEmpty()) {
            encryption.decrypt(decoded)
        } else {
            decoded
        }
    }

    private fun decryptBatchData(data: ByteArray, separator: ByteArray): List<ByteArray> {
        return data
            .splitBy(separator)
            .map {
                decryptSingleItemData(it)
            }
            .filter { it.isNotEmpty() }
    }

    private fun removeSuffixAndPrefix(
        data: ByteArray,
        prefix: ByteArray?,
        suffix: ByteArray?
    ): ByteArray {
        return if (prefix != null || suffix != null) {
            val prefixSize = prefix?.size ?: 0
            val suffixSize = suffix?.size ?: 0

            if (data.size < prefixSize + suffixSize) {
                internalLogger.e(BAD_DATA_READ_MESSAGE)
                devLogger.e(BAD_DATA_READ_MESSAGE)
                EMPTY_BYTE_ARRAY
            } else {
                // we check indexes validity just above, plus prefix size and suffix size
                // cannot be negative
                @Suppress("UnsafeThirdPartyFunctionCall")
                data.copyOfRange(prefixSize, data.size - suffixSize)
            }
        } else {
            data
        }
    }

    private fun safeDecodeBase64(encoded: ByteArray): ByteArray {
        return try {
            base64Decoder(encoded)
        } catch (iae: IllegalArgumentException) {
            internalLogger.e(BASE64_DECODING_ERROR_MESSAGE, iae)
            devLogger.e(BASE64_DECODING_ERROR_MESSAGE, iae)
            EMPTY_BYTE_ARRAY
        }
    }

    private fun assemble(
        items: List<ByteArray>,
        prefix: ByteArray?,
        suffix: ByteArray?,
        separator: ByteArray?
    ): ByteArray {
        val prefixSize = prefix?.size ?: 0
        val suffixSize = suffix?.size ?: 0
        val separatorSize = separator?.size ?: 0

        val result = ByteArray(
            items.sumOf { it.size } +
                prefixSize + suffixSize + separatorSize * (items.size - 1)
        )

        var offset = 0

        if (prefix != null) {
            prefix.copyTo(0, result, 0, prefix.size)
            offset += prefix.size
        }

        for (item in items.withIndex()) {
            item.value.copyTo(0, result, offset, item.value.size)
            offset += item.value.size
            if (separator != null && item.index != items.size - 1) {
                separator.copyTo(0, result, offset, separator.size)
                offset += separator.size
            }
        }

        suffix?.copyTo(0, result, offset, suffix.size)

        return result
    }

    private fun assemble(item: ByteArray, prefix: ByteArray?, suffix: ByteArray?): ByteArray {
        if (prefix == null && suffix == null) {
            return item
        }
        return assemble(listOf(item), prefix, suffix, null)
    }

    private fun checkSeparator(separator: ByteArray): Boolean {
        // Separator MAY include chars of BASE64 encoding, we cannot allow just ALL chars to
        // be BASE64, because in that case separator bytes sequence can be found in the encoded
        // item, leading to a wrong split
        return separator.any { it.toInt().toChar() !in BASE_64_CHARS }
    }

    private fun ByteArray.splitBy(separator: ByteArray): List<ByteArray> {
        val result = mutableListOf<ByteArray>()

        var chunkStart = 0
        var current = 0

        while (current < this.size) {
            var separatorFound = true
            for (separatorIndex in separator.indices) {
                if (this[separatorIndex + current] != separator[separatorIndex]) {
                    separatorFound = false
                    break
                }
            }
            if (separatorFound && chunkStart != current) {
                // indices are safe
                @Suppress("UnsafeThirdPartyFunctionCall")
                result.add(this.copyOfRange(chunkStart, current))
                chunkStart += (current - chunkStart) + separator.size
                current = chunkStart
            } else {
                current++
            }
        }

        if (chunkStart < this.size) {
            // indices are safe
            @Suppress("UnsafeThirdPartyFunctionCall")
            result.add(this.copyOfRange(chunkStart, this.size))
        }

        return result
    }

    // endregion

    companion object {
        const val ENCODING_FLAGS = Base64.DEFAULT or Base64.NO_WRAP

        private val EMPTY_BYTE_ARRAY = ByteArray(0)
        private val BASE_64_CHARS =
            (('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('+', '/', '=')).toSet()

        internal const val INVALID_SEPARATOR_MESSAGE = "Illegal separator is provided," +
            " it cannot be empty or in the Base64 characters set."
        internal const val MISSING_SEPARATOR_MESSAGE =
            "Separator should be provided in the append mode."
        internal const val BAD_ENCRYPTION_RESULT_MESSAGE = "Encryption of non-empty data produced" +
            " empty result, aborting write operation."
        internal const val BASE64_DECODING_ERROR_MESSAGE =
            "Failure to decode encrypted data from Base64 format. Will return empty item instead."
        internal const val BAD_DATA_READ_MESSAGE =
            "Corrupted data read: data size should be more than prefix size + suffix size."
    }
}
