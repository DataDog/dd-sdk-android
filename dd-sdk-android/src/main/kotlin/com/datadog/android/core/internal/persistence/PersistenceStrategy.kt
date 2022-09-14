/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence

import com.datadog.android.v2.core.internal.data.upload.Flusher
import com.datadog.android.v2.core.internal.storage.Storage
import com.datadog.tools.annotation.NoOpImplementation

/**
 * A class that can coordinate matching [DataWriter] and [DataReader].
 * @param T the type of data to persist.
 */
@NoOpImplementation
internal interface PersistenceStrategy<T : Any> {

    fun getWriter(): DataWriter<T>

    fun getReader(): DataReader

    fun getFlusher(): Flusher

    fun getStorage(): Storage
}
