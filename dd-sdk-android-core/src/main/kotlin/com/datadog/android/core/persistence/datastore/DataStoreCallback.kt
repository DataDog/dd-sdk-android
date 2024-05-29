/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.persistence.datastore

/**
 * Callback for asynchronous operations on the datastore.
 */
interface DataStoreCallback<T : Any> {

    /**
     * Called on successfully fetching data from the datastore.
     *
     * @param dataStoreContent contains the datastore data, version and lastUpdateDate.
     */
    fun onSuccess(dataStoreContent: DataStoreContent<T>)

    /**
     * Called when an exception occurred getting data from the datastore.
     */
    fun onFailure()

    /**
     * Called when no data is found for the given key.
     */
    fun onNoData()
}
