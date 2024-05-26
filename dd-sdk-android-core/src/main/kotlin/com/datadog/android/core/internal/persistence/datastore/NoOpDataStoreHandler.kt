/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.datastore

import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.core.persistence.datastore.DataStoreCallback
import com.datadog.android.core.persistence.datastore.DataStoreHandler

internal class NoOpDataStoreHandler : DataStoreHandler {
    override fun <T : Any> setValue(
        key: String,
        data: T,
        serializer: Serializer<T>,
        version: Int
    ) {
        // NoOp Implementation
    }

    override fun <T : Any> value(
        key: String,
        deserializer: Deserializer<String, T>,
        version: Int,
        callback: DataStoreCallback
    ) {
        // NoOp Implementation
    }

    override fun removeValue(key: String) {
        // NoOp Implementation
    }
}
