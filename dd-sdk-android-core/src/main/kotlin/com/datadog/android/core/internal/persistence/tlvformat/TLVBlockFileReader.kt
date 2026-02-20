/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.tlvformat

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.generated.DdSdkAndroidCoreLogger
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.utils.copyOfRangeSafe
import com.datadog.android.core.internal.utils.toInt
import com.datadog.android.core.internal.utils.toShort
import java.io.File

internal class TLVBlockFileReader(
    val internalLogger: InternalLogger,
    val fileReaderWriter: FileReaderWriter
) {
    @WorkerThread
    internal fun read(
        file: File
    ): List<TLVBlock> {
        val byteArray = fileReaderWriter.readData(file)
        val blocks = mutableListOf<TLVBlock>()
        var currentIndex = 0

        while (currentIndex < byteArray.size) {
            val result = readBlock(byteArray, currentIndex) ?: break
            blocks.add(result.data)
            currentIndex = result.newIndex
        }

        return blocks
    }

    @Suppress("ReturnCount")
    private fun readBlock(inputArray: ByteArray, currentIndex: Int): TLVResult<TLVBlock>? {
        val typeResult = readType(inputArray, currentIndex) ?: return null
        val data = readData(inputArray, typeResult.newIndex) ?: return null

        val block = TLVBlock(typeResult.data, data.data, internalLogger)
        return TLVResult(
            data = block,
            newIndex = data.newIndex
        )
    }

    @Suppress("ReturnCount")
    private fun readType(inputArray: ByteArray, currentIndex: Int): TLVResult<TLVBlockType>? {
        val typeBlockSize = UShort.SIZE_BYTES
        var newIndex = currentIndex
        newIndex += typeBlockSize

        if (newIndex > inputArray.size) {
            logFailedToDeserializeError()
            return null
        }

        val bytes = inputArray.copyOfRangeSafe(currentIndex, newIndex)

        val shortValue = bytes.toShort()

        val tlvHeader = TLVBlockType.fromValue(shortValue.toUShort())

        if (tlvHeader == null) {
            logTypeCorruptionError(shortValue)
            return null
        }

        return TLVResult(
            data = tlvHeader,
            newIndex = currentIndex + typeBlockSize
        )
    }

    private fun readData(inputArray: ByteArray, currentIndex: Int): TLVResult<ByteArray>? {
        val lengthBlockSize = Int.SIZE_BYTES
        var newIndex = currentIndex + lengthBlockSize

        if (newIndex > inputArray.size) {
            logFailedToDeserializeError()
            return null
        }

        val lengthInBytes = inputArray.copyOfRangeSafe(currentIndex, newIndex)

        val lengthData = lengthInBytes.toInt()

        val dataBytes =
            inputArray.copyOfRangeSafe(newIndex, newIndex + lengthData)

        newIndex += lengthData

        return TLVResult(
            data = dataBytes,
            newIndex = newIndex
        )
    }

    private fun logTypeCorruptionError(shortValue: Short) {
        DdSdkAndroidCoreLogger(internalLogger).logCorruptTlvHeaderTypeError(shortValue = shortValue.toString())
    }

    private fun logFailedToDeserializeError() {
        DdSdkAndroidCoreLogger(internalLogger).logFailedToDeserializeError()
    }

    private data class TLVResult<T : Any>(
        val data: T,
        val newIndex: Int
    )

}
