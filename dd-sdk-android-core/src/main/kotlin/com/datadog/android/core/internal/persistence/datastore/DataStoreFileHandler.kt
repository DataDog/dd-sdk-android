/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.datastore

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.datastore.DataStoreCallback
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.internal.utils.submitSafe
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
        serializer: Serializer<T>
    ) {
        executorService.submitSafe("dataStoreWrite", internalLogger) {
            datastoreFileWriter.write(key, data, serializer, version)
        }
    }

    override fun removeValue(key: String) {
        executorService.submitSafe("dataStoreRemove", internalLogger) {
            datastoreFileWriter.delete(key)
        }
    }

    override fun <T : Any> value(
        key: String,
        version: Int,
        callback: DataStoreCallback<T>,
        deserializer: Deserializer<String, T>
    ) {
        executorService.submitSafe("dataStoreRead", internalLogger) {
            dataStoreFileReader.read(key, deserializer, version, callback)
        }
    }
}
