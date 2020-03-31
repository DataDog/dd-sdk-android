/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data

import com.datadog.android.core.internal.domain.Batch

/**
 * Reads logs from a persistent location, when they can be sent.
 * @see [Writer]
 */
internal interface Reader {

    fun readNextBatch(): Batch?

    /**
     * Marks that a batch couldn't be set and should be retried later.
     */
    fun releaseBatch(batchId: String)

    fun dropBatch(batchId: String)

    fun dropAllBatches()
}
