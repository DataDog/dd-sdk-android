/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.datastore

import com.datadog.android.api.storage.datastore.DataStoreCallback
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.persistence.Serializer

internal class NoOpDataStoreHandler : DataStoreHandler {
    override fun <T : Any> setValue(
        key: String,
        data: T,
        version: Int,
        serializer: Serializer<T>
    ) {
        // NoOp Implementation
    }

    override fun <T : Any> value(
        key: String,
        version: Int,
        callback: DataStoreCallback<T>,
        deserializer: Deserializer<String, T>
    ) {
        // NoOp Implementation
    }

    override fun removeValue(key: String) {
        // NoOp Implementation
    }
}
