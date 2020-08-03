/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.domain

import com.datadog.android.core.internal.data.Reader
import com.datadog.android.core.internal.data.Writer
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface PersistenceStrategy<T : Any> {

    fun getWriter(): Writer<T>

    fun getReader(): Reader

    fun clearAllData()
}
