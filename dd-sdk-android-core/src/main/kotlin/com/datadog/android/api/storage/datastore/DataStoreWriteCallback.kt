/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.storage.datastore

/**
 * Callback for asynchronous write operations on the datastore.
 */
interface DataStoreWriteCallback {
    /**
     * Triggered on successfully writing data to the datastore.
     */
    fun onSuccess()

    /**
     * Triggered on failing to write data to the datastore.
     */
    fun onFailure()
}
