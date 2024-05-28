/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.datastore

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.internal.persistence.datastore.ext.toByteArray
import com.datadog.android.core.internal.persistence.datastore.ext.toInt
import com.datadog.android.core.internal.persistence.datastore.ext.toLong
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.file.deleteSafe
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.tlvformat.TLVBlock
import com.datadog.android.core.internal.persistence.tlvformat.TLVBlockFileReader
import com.datadog.android.core.internal.persistence.tlvformat.TLVBlockType
import com.datadog.android.core.internal.utils.join
import com.datadog.android.core.internal.utils.submitSafe
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.core.persistence.datastore.DataStoreCallback
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.core.persistence.datastore.DataStoreHandler
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService

@Suppress("TooManyFunctions")
internal class DataStoreFileHandler(
    private val executorService: ExecutorService,
    private val storageDir: File,
    private val featureName: String,
    private val internalLogger: InternalLogger,
    private val fileReaderWriter: FileReaderWriter,
    private val tlvBlockFileReader: TLVBlockFileReader,
    private val dataStoreFileHelper: DataStoreFileHelper
) : DataStoreHandler {
    @WorkerThread
    override fun <T : Any> setValue(
        key: String,
        data: T,
        version: Int,
        serializer: Serializer<T>
    ) {
        executorService.submitSafe("dataStoreWrite", internalLogger) {
            writeEntry(key, data, serializer, version)
        }
    }

    @WorkerThread
    override fun <T : Any> value(
        key: String,
        version: Int,
        callback: DataStoreCallback,
        deserializer: Deserializer<String, T>
    ) {
        executorService.submitSafe("dataStoreRead", internalLogger) {
            readEntry(key, deserializer, version, callback)
        }
    }

    @WorkerThread
    override fun removeValue(key: String) {
        executorService.submitSafe("dataStoreRemove", internalLogger) {
            deleteFromDataStore(key)
        }
    }

    private fun deleteFromDataStore(key: String) {
        val datastoreFile = dataStoreFileHelper.getDataStoreFile(
            featureName = featureName,
            storageDir = storageDir,
            internalLogger = internalLogger,
            key = key
        )

        if (datastoreFile.existsSafe(internalLogger)) {
            datastoreFile.deleteSafe(internalLogger)
        }
    }

    private fun <T : Any> readEntry(
        key: String,
        deserializer: Deserializer<String, T>,
        version: Int,
        callback: DataStoreCallback
    ) {
        val datastoreFile = dataStoreFileHelper.getDataStoreFile(
            featureName = featureName,
            storageDir = storageDir,
            internalLogger = internalLogger,
            key = key
        )

        if (!datastoreFile.existsSafe(internalLogger)) {
            callback.onNoData()
            return
        }

        readFromDataStoreFile(datastoreFile, deserializer, tlvBlockFileReader, version, callback)
    }

    private fun <T : Any> writeEntry(
        key: String,
        data: T,
        serializer: Serializer<T>,
        version: Int
    ) {
        val datastoreFile = dataStoreFileHelper.getDataStoreFile(
            featureName = featureName,
            storageDir = storageDir,
            internalLogger = internalLogger,
            key = key
        )

        val lastUpdateBlock = getLastUpdateDateBlock()
        val versionCodeBlock = getVersionCodeBlock(version)
        val dataBlock = getDataBlock(data, serializer)

        if (lastUpdateBlock == null || versionCodeBlock == null || dataBlock == null) {
            return
        }

        val dataToWrite = listOf(lastUpdateBlock, versionCodeBlock, dataBlock).join(
            separator = byteArrayOf(),
            internalLogger = internalLogger
        )

        writeToFile(
            datastoreFile,
            dataToWrite
        )
    }

    @Suppress("ThreadSafety")
    private fun writeToFile(dataStoreFile: File, data: ByteArray) {
        fileReaderWriter.writeData(
            file = dataStoreFile,
            data = data,
            append = false
        )
    }

    private fun <T : Any> getDataBlock(
        data: T,
        serializer: Serializer<T>
    ): ByteArray? {
        val serializedData = serializer.serialize(data)?.toByteArray()

        if (serializedData == null) {
            logFailedToSerializeDataError()
            return null
        }

        val dataBlock = TLVBlock(
            type = TLVBlockType.DATA,
            data = serializedData,
            internalLogger = internalLogger
        )

        return dataBlock.serialize()
    }

    private fun getLastUpdateDateBlock(): ByteArray? {
        val now = System.currentTimeMillis()
        val lastUpdateDateByteArray = now.toByteArray()
        val lastUpdateDateBlock = TLVBlock(
            type = TLVBlockType.LAST_UPDATE_DATE,
            data = lastUpdateDateByteArray,
            internalLogger = internalLogger
        )

        return lastUpdateDateBlock.serialize()
    }

    private fun getVersionCodeBlock(version: Int): ByteArray? {
        val versionCodeByteArray = version.toByteArray()
        val versionBlock = TLVBlock(
            type = TLVBlockType.VERSION_CODE,
            data = versionCodeByteArray,
            internalLogger = internalLogger
        )

        return versionBlock.serialize()
    }

    @Suppress("ReturnCount", "ThreadSafety")
    private fun <T : Any> readFromDataStoreFile(
        datastoreFile: File,
        deserializer: Deserializer<String, T>,
        tlvBlockFileReader: TLVBlockFileReader,
        requestedVersion: Int,
        callback: DataStoreCallback
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

    @Suppress("ReturnCount")
    private fun <T : Any> tryToMapToDataStoreContents(
        deserializer: Deserializer<String, T>,
        tlvBlocks: List<TLVBlock>
    ): DataStoreContent<T>? {
        // map the blocks to the actual types
        val typesToBlocks = mutableMapOf<Any, TLVBlock>()
        for (block in tlvBlocks) {
            val type = block.type
            val blockTypeAlreadyExists = typesToBlocks[type] != null

            // verify that the same block doesn't appear more than once
            if (blockTypeAlreadyExists) {
                logSameBlockAppearsTwiceError(type)
                return null
            }

            typesToBlocks[type] = block
        }

        val lastUpdateBlock = typesToBlocks[TLVBlockType.LAST_UPDATE_DATE] ?: return null
        val versionCodeBlock = typesToBlocks[TLVBlockType.VERSION_CODE] ?: return null
        val dataBlock = typesToBlocks[TLVBlockType.DATA] ?: return null

        return DataStoreContent(
            lastUpdateDate = lastUpdateBlock.data.toLong(),
            versionCode = versionCodeBlock.data.toInt(),
            data = deserializer.deserialize(String(dataBlock.data))
        )
    }

    private fun logSameBlockAppearsTwiceError(type: TLVBlockType) {
        internalLogger.log(
            target = InternalLogger.Target.MAINTAINER,
            level = InternalLogger.Level.ERROR,
            messageBuilder = { SAME_BLOCK_APPEARS_TWICE_ERROR.format(Locale.US, type) }
        )
    }

    private fun logInvalidNumberOfBlocksError(numberOfBlocks: Int) {
        internalLogger.log(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.MAINTAINER,
            messageBuilder = { INVALID_NUMBER_OF_BLOCKS_ERROR.format(Locale.US, numberOfBlocks) }
        )
    }

    private fun logFailedToSerializeDataError() {
        internalLogger.log(
            target = InternalLogger.Target.MAINTAINER,
            level = InternalLogger.Level.ERROR,
            messageBuilder = { FAILED_TO_SERIALIZE_DATA_ERROR }
        )
    }

    internal companion object {
        internal const val FAILED_TO_SERIALIZE_DATA_ERROR =
            "Write error - Failed to serialize data for the datastore"
        internal const val INVALID_NUMBER_OF_BLOCKS_ERROR =
            "Read error - datastore file contains an invalid number of blocks. Was: %s"
        internal const val SAME_BLOCK_APPEARS_TWICE_ERROR =
            "Read error - same block appears twice in the datastore. Type: %s"
    }
}
