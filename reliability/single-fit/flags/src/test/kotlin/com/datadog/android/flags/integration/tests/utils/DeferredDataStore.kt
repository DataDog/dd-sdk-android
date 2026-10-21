/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.integration.tests.utils

import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.core.stub.StubDataStoreHandler

internal class DeferredDataStore(
    private val delegate: DataStoreHandler = StubDataStoreHandler()
) : DataStoreHandler by delegate {
    var deferReads = false
    private val pendingReads = ArrayDeque<() -> Unit>()

    override fun <T : Any> value(
        key: String,
        version: Int?,
        callback: DataStoreReadCallback<T>,
        deserializer: Deserializer<String, T>
    ) {
        if (!deferReads) {
            delegate.value(key, version, callback, deserializer)
            return
        }
        delegate.value(
            key,
            version,
            object : DataStoreReadCallback<T> {
                override fun onSuccess(dataStoreContent: DataStoreContent<T>?) {
                    pendingReads.add { callback.onSuccess(dataStoreContent) }
                }

                override fun onFailure() {
                    pendingReads.add { callback.onFailure() }
                }
            },
            deserializer
        )
    }

    fun completeReads() {
        while (pendingReads.isNotEmpty()) pendingReads.removeFirst().invoke()
    }
}
