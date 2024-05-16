/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.feature

import androidx.annotation.AnyThread
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.persistence.Serializer

/**
 * Represents a Datadog feature.
 */
interface FeatureScope {

    /**
     * Utility to write an event, asynchronously.
     * @param forceNewBatch if `true` forces the [EventBatchWriter] to write in a new file and
     * not reuse the already existing pending data persistence file. By default it is `false`.
     * @param callback an operation called with an up-to-date [DatadogContext]
     * and an [EventBatchWriter]. Callback will be executed on a worker thread from I/O pool.
     * [DatadogContext] will have a state created at the moment this method is called, before the
     * thread switch for the callback invocation.
     */
    @AnyThread
    fun withWriteContext(
        forceNewBatch: Boolean = false,
        callback: (DatadogContext, EventBatchWriter) -> Unit
    )

    /**
     * Write data to the datastore.
     *
     * @param dataStoreFileName name of the datastore file as there could be multiple such files per feature.
     * @param featureName of the calling feature, to determine the path to the datastore file.
     * @param serializer to use to serialize the data.
     * @param data to write.
     */
    fun <T : Any> writeToDataStore(
        dataStoreFileName: String,
        featureName: String,
        serializer: Serializer<T>,
        data: T
    )

    /**
     * Read data from the datastore.
     *
     * @param dataStoreFileName name of the datastore file as there could be multiple such files per feature.
     * @param featureName of the calling feature, to determine the path to the datastore file.
     * @param deserializer to use to deserialize the data.
     * @param version to use when reading from the datastore (to support migrations).
     */
    fun <T : Any> readFromDataStore(
        dataStoreFileName: String,
        featureName: String,
        deserializer: Deserializer<String, T>,
        version: Int
    ): T?

    /**
     * Return the current version of the datastore.
     */
    fun getDataStoreCurrentVersion(): Int

    /**
     * Send event to a given feature. It will be sent in a synchronous way.
     *
     * @param event Event to send.
     */
    fun sendEvent(event: Any)

    /**
     * Returns the original feature.
     */
    fun <T : Feature> unwrap(): T
}
