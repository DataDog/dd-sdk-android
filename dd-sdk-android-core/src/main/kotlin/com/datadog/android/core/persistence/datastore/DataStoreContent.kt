/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.persistence.datastore

/**
 * Datastore entry contents and metadata.
 *
 * @param T type of data used by this entry in the datastore.
 * @property versionCode version used by the entry.
 * @property data content of the entry.
 */
data class DataStoreContent<T : Any>(
    val versionCode: Int,
    val data: T?
)
