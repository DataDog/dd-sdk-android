/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.datastore

import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.persistence.Serializer

interface DataStoreHandler {

    /**
     * Write data to the datastore.
     *
     * @param T datatype of the data to write to the datastore.
     * @param dataStoreFileName name of the datastore file as there could be multiple such files per feature.
     * @param featureName of the calling feature, to determine the path to the datastore file.
     * @param serializer to use to serialize the data.
     * @param data to write.
     */
    fun <T : Any> write(
        dataStoreFileName: String,
        featureName: String,
        serializer: Serializer<T>,
        data: T
    )

    /**
     * Read data from the datastore.
     *
     * @param T datatype of the data to read from the datastore.
     * @param dataStoreFileName name of the datastore file as there could be multiple such files per feature.
     * @param featureName of the calling feature, to determine the path to the datastore file.
     * @param deserializer to use to deserialize the data.
     * @param version to use when reading from the datastore (to support migrations).
     */
    fun <T : Any> read(
        dataStoreFileName: String,
        featureName: String,
        deserializer: Deserializer<String, T>,
        version: Int
    ): T?

    companion object {
        /**
         * The current version of the datastore.
         */
        const val CURRENT_DATASTORE_VERSION: Int = 0
    }
}
