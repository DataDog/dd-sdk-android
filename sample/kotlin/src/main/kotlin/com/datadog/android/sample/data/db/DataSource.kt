/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.data.db

import com.datadog.android.sample.data.model.Log
import com.datadog.android.sample.datalist.DataSourceType
import io.reactivex.rxjava3.core.SingleSource

internal interface DataSource {
    fun fetchLogs(): SingleSource<List<Log>>

    fun persistLogs(logs: List<Log>)

    val type: DataSourceType
}
