/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.core.internal.data.file

import com.datadog.android.core.internal.data.DataMigrator
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.threading.LazyHandlerThread

internal class DeferredWriter<T : Any>(
    private val dataMigrator: DataMigrator,
    private val writer: Writer<T>
) : LazyHandlerThread(THREAD_NAME),
    Writer<T> {

    init {
        start()
        post(Runnable { dataMigrator.migrateData() })
    }

    // region Writer

    override fun write(model: T) {
        post(Runnable {
            writer.write(model)
        })
    }

    // endregion

    companion object {
        private const val THREAD_NAME = "ddog_w"
    }
}
