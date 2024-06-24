/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.storage.datastore

import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.persistence.Serializer

/**
 * Interface for the datastore.
 */
interface DataStoreHandler {

    /**
     * Write data to the datastore.
     * This executes on a worker thread and not on the caller thread.
     *
     * @param T datatype of the data to write to the datastore.
     * @param key name of the datastore entry.
     * @param data to write.
     * @param version optional version for the entry.
     * If not specified will give the entry version 0 - even if that would be a downgrade from the previous version.
     * @param serializer to use to serialize the data.
     */
    fun <T : Any> setValue(
        key: String,
        data: T,
        version: Int = 0,
        serializer: Serializer<T>
    )

    /**
     * Read data from the datastore.
     * This executes on a worker thread and not on the caller thread.
     *
     * @param T datatype of the data to read from the datastore.
     * @param key name of the datastore entry.
     * @param version optional version to use when reading from the datastore.
     * If specified, will only return data if the persistent entry exactly matches this version number.
     * @param callback to return result asynchronously.
     * @param deserializer to use to deserialize the data.
     */
    fun <T : Any> value(
        key: String,
        version: Int? = null,
        callback: DataStoreCallback<T>,
        deserializer: Deserializer<String, T>
    )

    /**
     * Remove an entry from the datastore.
     * This executes on a worker thread and not on the caller thread.
     *
     * @param key name of the datastore entry
     */
    fun removeValue(
        key: String
    )

    companion object {
        /**
         * The current version of the datastore.
         */
        const val CURRENT_DATASTORE_VERSION: Int = 0
    }
}
