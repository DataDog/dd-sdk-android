/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.datastore

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.api.storage.datastore.DataStoreWriteCallback
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.core.persistence.Serializer
import java.util.concurrent.ExecutorService

internal class DataStoreFileHandler(
    private val executorService: ExecutorService,
    private val internalLogger: InternalLogger,
    private val dataStoreFileReader: DatastoreFileReader,
    private val datastoreFileWriter: DatastoreFileWriter
) : DataStoreHandler {

    override fun <T : Any> setValue(
        key: String,
        data: T,
        version: Int,
        callback: DataStoreWriteCallback,
        serializer: Serializer<T>
    ) {
        executorService.executeSafe("dataStoreWrite", internalLogger) {
            datastoreFileWriter.write(key, data, serializer, callback, version)
        }
    }

    override fun removeValue(key: String, callback: DataStoreWriteCallback) {
        executorService.executeSafe("dataStoreRemove", internalLogger) {
            datastoreFileWriter.delete(key, callback)
        }
    }

    override fun <T : Any> value(
        key: String,
        version: Int?,
        callback: DataStoreReadCallback<T>,
        deserializer: Deserializer<String, T>
    ) {
        executorService.executeSafe("dataStoreRead", internalLogger) {
            dataStoreFileReader.read(key, deserializer, version, callback)
        }
    }
}
