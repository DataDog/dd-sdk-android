/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.datastore

internal data class DataStoreContents<T : Any>(
    val lastUpdateDate: Long,
    val versionCode: Int,
    val data: T?
)
