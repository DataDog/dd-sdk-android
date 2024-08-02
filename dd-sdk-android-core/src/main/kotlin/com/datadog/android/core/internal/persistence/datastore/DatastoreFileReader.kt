/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.datastore

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.internal.persistence.datastore.ext.toInt
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.tlvformat.TLVBlock
import com.datadog.android.core.internal.persistence.tlvformat.TLVBlockFileReader
import com.datadog.android.core.internal.persistence.tlvformat.TLVBlockType
import com.datadog.android.core.persistence.datastore.DataStoreContent
import java.io.File
import java.util.Locale

internal class DatastoreFileReader(
    private val dataStoreFileHelper: DataStoreFileHelper,
    private val featureName: String,
    private val storageDir: File,
    private val internalLogger: InternalLogger,
    private val tlvBlockFileReader: TLVBlockFileReader
) {
    @WorkerThread
    internal fun <T : Any> read(
        key: String,
        deserializer: Deserializer<String, T>,
        version: Int? = null,
        callback: DataStoreReadCallback<T>
    ) {
        val datastoreFile = dataStoreFileHelper.getDataStoreFile(
            featureName = featureName,
            storageDir = storageDir,
            key = key
        )

        if (!datastoreFile.existsSafe(internalLogger)) {
            callback.onSuccess(null)
            return
        }

        readFromDataStoreFile(datastoreFile, deserializer, tlvBlockFileReader, version, callback)
    }

    @Suppress("ReturnCount", "ThreadSafety")
    private fun <T : Any> readFromDataStoreFile(
        datastoreFile: File,
        deserializer: Deserializer<String, T>,
        tlvBlockFileReader: TLVBlockFileReader,
        requestedVersion: Int?,
        callback: DataStoreReadCallback<T>
    ) {
        val tlvBlocks = tlvBlockFileReader.read(datastoreFile)

        // there should be as many blocks read as there are block types
        val numberBlocksFound = tlvBlocks.size
        val numberBlocksExpected = TLVBlockType.values().size
        if (numberBlocksFound != numberBlocksExpected) {
            logInvalidNumberOfBlocksError(numberBlocksFound, numberBlocksExpected)
            callback.onFailure()
            return
        }

        val dataStoreContent = mapToDataStoreContents(deserializer, tlvBlocks)

        if (dataStoreContent == null) {
            callback.onFailure()
            return
        }

        // if an optional version is specified then only return data if the entry version exactly matches
        if (requestedVersion != null && requestedVersion != dataStoreContent.versionCode) {
            callback.onSuccess(null)
            return
        }

        callback.onSuccess(dataStoreContent)
    }

    private fun <T : Any> mapToDataStoreContents(
        deserializer: Deserializer<String, T>,
        tlvBlocks: List<TLVBlock>
    ): DataStoreContent<T>? {
        if (tlvBlocks[0].type != TLVBlockType.VERSION_CODE &&
            tlvBlocks[1].type != TLVBlockType.DATA
        ) {
            logBlocksInUnexpectedBlocksOrderError()
            return null
        }

        val versionCodeBlock = tlvBlocks[0]
        val dataBlock = tlvBlocks[1]

        return DataStoreContent(
            versionCode = versionCodeBlock.data.toInt(),
            data = deserializer.deserialize(String(dataBlock.data))
        )
    }

    private fun logInvalidNumberOfBlocksError(numberBlocksFound: Int, numberBlocksExpected: Int) {
        internalLogger.log(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.MAINTAINER,
            messageBuilder = {
                INVALID_NUMBER_OF_BLOCKS_ERROR
                    .format(Locale.US, numberBlocksFound, numberBlocksExpected)
            }
        )
    }

    private fun logBlocksInUnexpectedBlocksOrderError() {
        internalLogger.log(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.MAINTAINER,
            messageBuilder = { UNEXPECTED_BLOCKS_ORDER_ERROR }
        )
    }

    internal companion object {
        internal const val INVALID_NUMBER_OF_BLOCKS_ERROR =
            "Read error - datastore entry has invalid number of blocks. Was: %d, expected: %d"
        internal const val UNEXPECTED_BLOCKS_ORDER_ERROR =
            "Read error - blocks are in an unexpected order"
    }
}
