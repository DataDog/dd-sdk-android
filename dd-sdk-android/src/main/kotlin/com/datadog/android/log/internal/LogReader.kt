/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal

/**
 * Reads logs from a persistent location, when they can be sent.
 * @see [LogWriter]
 */
internal interface LogReader {

    fun readNextBatch(): Batch?

    fun dropBatch(batchId: String)

    fun dropAllBatches()
}
