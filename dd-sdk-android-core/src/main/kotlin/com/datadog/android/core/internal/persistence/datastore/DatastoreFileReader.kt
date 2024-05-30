/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.datastore

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.internal.persistence.datastore.ext.toInt
import com.datadog.android.core.internal.persistence.datastore.ext.toLong
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.tlvformat.TLVBlock
import com.datadog.android.core.internal.persistence.tlvformat.TLVBlockFileReader
import com.datadog.android.core.internal.persistence.tlvformat.TLVBlockType
import com.datadog.android.core.persistence.datastore.DataStoreCallback
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
        version: Int,
        callback: DataStoreCallback<T>
    ) {
        if (dataStoreFileHelper.isKeyInvalid(key)) {
            dataStoreFileHelper.logInvalidKeyException()
            return
        }

        val datastoreFile = dataStoreFileHelper.getDataStoreFile(
            featureName = featureName,
            storageDir = storageDir,
            key = key
        )

        if (!datastoreFile.existsSafe(internalLogger)) {
            callback.onNoData()
            return
        }

        readFromDataStoreFile(datastoreFile, deserializer, tlvBlockFileReader, version, callback)
    }

    @Suppress("ReturnCount", "ThreadSafety")
    private fun <T : Any> readFromDataStoreFile(
        datastoreFile: File,
        deserializer: Deserializer<String, T>,
        tlvBlockFileReader: TLVBlockFileReader,
        requestedVersion: Int,
        callback: DataStoreCallback<T>
    ) {
        val tlvBlocks = tlvBlockFileReader.read(datastoreFile)

        // there should be as many blocks read as there are block types
        if (tlvBlocks.size != TLVBlockType.values().size) {
            logInvalidNumberOfBlocksError(tlvBlocks.size)
            callback.onFailure()
            return
        }

        val dataStoreContent = tryToMapToDataStoreContents(deserializer, tlvBlocks)

        if (dataStoreContent == null) {
            callback.onFailure()
            return
        }

        if (requestedVersion != 0 && dataStoreContent.versionCode != requestedVersion) {
            callback.onNoData()
            return
        }

        callback.onSuccess(dataStoreContent)
    }

    private fun <T : Any> tryToMapToDataStoreContents(
        deserializer: Deserializer<String, T>,
        tlvBlocks: List<TLVBlock>
    ): DataStoreContent<T>? {
        if (tlvBlocks[0].type != TLVBlockType.LAST_UPDATE_DATE &&
            tlvBlocks[1].type != TLVBlockType.VERSION_CODE
        ) {
            logBlocksInUnexpectedBlocksOrderError()
            return null
        }

        val lastUpdateBlock = tlvBlocks[0]
        val versionCodeBlock = tlvBlocks[1]
        val dataBlock = tlvBlocks[2]

        return DataStoreContent(
            lastUpdateDate = lastUpdateBlock.data.toLong(),
            versionCode = versionCodeBlock.data.toInt(),
            data = deserializer.deserialize(String(dataBlock.data))
        )
    }

    private fun logInvalidNumberOfBlocksError(numberOfBlocks: Int) {
        internalLogger.log(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.MAINTAINER,
            messageBuilder = { INVALID_NUMBER_OF_BLOCKS_ERROR.format(Locale.US, numberOfBlocks) }
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
            "Read error - datastore file contains an invalid number of blocks. Was: %s"
        internal const val UNEXPECTED_BLOCKS_ORDER_ERROR =
            "Read error - blocks are in an unexpected order"
    }
}
