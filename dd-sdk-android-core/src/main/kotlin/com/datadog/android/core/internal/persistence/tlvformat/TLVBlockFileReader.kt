/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.tlvformat

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.utils.copyOfRangeSafe
import com.datadog.android.core.internal.utils.toInt
import com.datadog.android.core.internal.utils.toShort
import com.datadog.android.internal.telemetry.TelemetryContext
import com.datadog.android.internal.telemetry.TelemetryContext.Companion.TELEMETRY_TLV_DATA_LENGTH
import com.datadog.android.internal.telemetry.TelemetryContext.Companion.TELEMETRY_TLV_DATA_LENGTH_LIMIT
import com.datadog.android.internal.telemetry.TelemetryContext.Companion.TELEMETRY_TLV_HEADER_LENGTH
import com.datadog.android.internal.telemetry.TelemetryContext.Companion.TELEMETRY_TLV_HEADER_LENGTH_LIMIT
import com.datadog.android.internal.telemetry.TelemetryContext.Companion.TELEMETRY_TLV_TYPE
import java.io.File

internal class TLVBlockFileReader(
    val internalLogger: InternalLogger,
    val fileReaderWriter: FileReaderWriter,
    private val maxEntrySize: Int = TLVBlock.MAXIMUM_DATA_SIZE_MB
) {
    @WorkerThread
    internal fun read(
        file: File,
        telemetryContext: TelemetryContext
    ): List<TLVBlock> {
        val byteArray = fileReaderWriter.readData(file, telemetryContext)
        val blocks = mutableListOf<TLVBlock>()
        var currentIndex = 0

        while (currentIndex < byteArray.size) {
            val result = readBlock(byteArray, currentIndex, telemetryContext) ?: break
            blocks.add(result.data)
            currentIndex = result.newIndex
        }

        return blocks
    }

    @Suppress("ReturnCount")
    private fun readBlock(
        inputArray: ByteArray,
        currentIndex: Int,
        telemetryContext: TelemetryContext
    ): TLVResult<TLVBlock>? {
        val typeResult = readType(inputArray, currentIndex, telemetryContext) ?: return null
        val data = readData(
            inputArray = inputArray,
            currentIndex = typeResult.newIndex,
            bytesLeft = inputArray.size - currentIndex,
            telemetryContext = telemetryContext
        ) ?: return null

        val block = TLVBlock(typeResult.data, data.data, internalLogger)
        return TLVResult(
            data = block,
            newIndex = data.newIndex
        )
    }

    @Suppress("ReturnCount")
    private fun readType(
        inputArray: ByteArray,
        currentIndex: Int,
        telemetryContext: TelemetryContext
    ): TLVResult<TLVBlockType>? {
        val typeBlockSize = UShort.SIZE_BYTES
        var newIndex = currentIndex
        newIndex += typeBlockSize

        if (newIndex > inputArray.size) {
            internalLogger.log(
                level = InternalLogger.Level.WARN,
                targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                messageBuilder = { WARN_CORRUPT_HEADER_LENGTH_ERROR },
                additionalProperties = telemetryContext.asAttributesMap(
                    inputArray.size - currentIndex,
                    TELEMETRY_TLV_HEADER_LENGTH to typeBlockSize,
                    TELEMETRY_TLV_HEADER_LENGTH_LIMIT to (inputArray.size - currentIndex)
                )
            )
            return null
        }

        val bytes = inputArray.copyOfRangeSafe(currentIndex, newIndex)

        val shortValue = bytes.toShort()

        val tlvHeader = TLVBlockType.fromValue(shortValue.toUShort())

        if (tlvHeader == null) {
            internalLogger.log(
                level = InternalLogger.Level.WARN,
                targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                messageBuilder = { WARN_CORRUPT_TLV_HEADER_TYPE },
                additionalProperties = telemetryContext.asAttributesMap(
                    bytesLost = inputArray.size - currentIndex,
                    TELEMETRY_TLV_TYPE to shortValue
                )
            )
            return null
        }

        return TLVResult(
            data = tlvHeader,
            newIndex = currentIndex + typeBlockSize
        )
    }

    @Suppress("ReturnCount")
    private fun readData(
        inputArray: ByteArray,
        currentIndex: Int,
        bytesLeft: Int,
        telemetryContext: TelemetryContext
    ): TLVResult<ByteArray>? {
        val lengthBlockSize = Int.SIZE_BYTES
        var newIndex = currentIndex + lengthBlockSize

        if (newIndex > inputArray.size) {
            internalLogger.log(
                level = InternalLogger.Level.WARN,
                targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                messageBuilder = { WARN_CORRUPT_DATA_LENGTH_ERROR },
                additionalProperties = telemetryContext.asAttributesMap(
                    bytesLost = bytesLeft,
                    TELEMETRY_TLV_DATA_LENGTH to lengthBlockSize,
                    TELEMETRY_TLV_DATA_LENGTH_LIMIT to (inputArray.size - currentIndex)
                )
            )

            return null
        }

        val lengthData = inputArray.copyOfRangeSafe(currentIndex, newIndex).toInt()
        if (lengthData !in 0..maxEntrySize) {
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                messageBuilder = { WARN_CORRUPT_DATA_LENGTH_ERROR },
                additionalProperties = telemetryContext.asAttributesMap(
                    bytesLeft,
                    TELEMETRY_TLV_DATA_LENGTH to lengthData,
                    TELEMETRY_TLV_DATA_LENGTH_LIMIT to maxEntrySize
                )
            )
            return null
        } else if (newIndex + lengthData > inputArray.size) {
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                messageBuilder = { WARN_CORRUPT_DATA_LENGTH_ERROR },
                additionalProperties = telemetryContext.asAttributesMap(
                    bytesLeft,
                    TELEMETRY_TLV_DATA_LENGTH to lengthData,
                    TELEMETRY_TLV_DATA_LENGTH_LIMIT to inputArray.size - newIndex
                )
            )
            return null
        }

        val dataBytes = inputArray.copyOfRangeSafe(newIndex, newIndex + lengthData)

        newIndex += lengthData

        return TLVResult(data = dataBytes, newIndex = newIndex)
    }

    private data class TLVResult<T : Any>(
        val data: T,
        val newIndex: Int
    )

    internal companion object {
        internal const val WARN_CORRUPT_TLV_HEADER_TYPE = "TLV header corrupt. Invalid type."
        internal const val WARN_CORRUPT_HEADER_LENGTH_ERROR =
            "Header block length read from file is invalid or exceeds limit"
        internal const val WARN_CORRUPT_DATA_LENGTH_ERROR =
            "Data block length read from file is invalid or exceeds limit"
    }
}
