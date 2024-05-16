/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.datastore

import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.persistence.Serializer

internal class NoOpDataStoreHandler : DataStoreHandler {
    override fun <T : Any> write(
        dataStoreFileName: String,
        featureName: String,
        serializer: Serializer<T>,
        data: T
    ) {
        // NoOp Implementation
    }

    override fun <T : Any> read(
        dataStoreFileName: String,
        featureName: String,
        deserializer: Deserializer<String, T>,
        version: Int
    ): T? {
        // NoOp Implementation
        return null
    }
}
