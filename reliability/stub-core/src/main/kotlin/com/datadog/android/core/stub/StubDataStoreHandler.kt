/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.stub

import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.api.storage.datastore.DataStoreWriteCallback
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.core.persistence.datastore.DataStoreContent

/**
 * An in-memory implementation of [DataStoreHandler].
 *
 * Values go through the provided [Serializer] and [Deserializer], so the persisted format is exercised the same
 * way as with the real file-based datastore. All the callbacks are invoked synchronously.
 *
 * Sharing an instance between two [StubSDKCore] simulates an application restart.
 */
class StubDataStoreHandler : DataStoreHandler {

    private val entries = mutableMapOf<String, Pair<Int, String>>()

    override fun <T : Any> setValue(
        key: String,
        data: T,
        version: Int,
        callback: DataStoreWriteCallback?,
        serializer: Serializer<T>
    ) {
        val serialized = serializer.serialize(data)
        if (serialized == null) {
            callback?.onFailure()
        } else {
            synchronized(entries) { entries[key] = version to serialized }
            callback?.onSuccess()
        }
    }

    override fun <T : Any> value(
        key: String,
        version: Int?,
        callback: DataStoreReadCallback<T>,
        deserializer: Deserializer<String, T>
    ) {
        val entry = synchronized(entries) { entries[key] }
        if (entry == null || (version != null && entry.first != version)) {
            callback.onSuccess(null)
        } else {
            callback.onSuccess(DataStoreContent(entry.first, deserializer.deserialize(entry.second)))
        }
    }

    override fun removeValue(key: String, callback: DataStoreWriteCallback?) {
        synchronized(entries) { entries.remove(key) }
        callback?.onSuccess()
    }

    override fun clearAllData() {
        synchronized(entries) { entries.clear() }
    }
}
