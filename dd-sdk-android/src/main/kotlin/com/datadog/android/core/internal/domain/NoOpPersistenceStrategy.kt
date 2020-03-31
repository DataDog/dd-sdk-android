/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.domain

import com.datadog.android.core.internal.data.Reader
import com.datadog.android.core.internal.data.Writer

internal class NoOpPersistenceStrategy<T : Any> :
    PersistenceStrategy<T> {

    override fun getWriter(): Writer<T> {
        return object : Writer<T> {
            override fun write(model: T) {
                // No Op
            }

            override fun write(models: List<T>) {
                // No Op
            }
        }
    }

    override fun getReader(): Reader {
        return object : Reader {
            override fun readNextBatch(): Batch? {
                return null
            }

            override fun releaseBatch(batchId: String) {
                // No Op
            }

            override fun dropBatch(batchId: String) {
                // No Op
            }

            override fun dropAllBatches() {
                // No Op
            }
        }
    }
}
