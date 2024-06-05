/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.storage.datastore

import com.datadog.android.core.persistence.datastore.DataStoreContent

/**
 * Callback for asynchronous operations on the datastore.
 */
interface DataStoreCallback<T : Any> {

    /**
     * Triggered on successfully fetching data from the datastore.
     *
     * @param dataStoreContent contains the datastore content if there was data to fetch.
     */
    fun onSuccess(dataStoreContent: DataStoreContent<T>?)

    /**
     * Triggered when an exception occurred getting data from the datastore.
     */
    fun onFailure()
}
