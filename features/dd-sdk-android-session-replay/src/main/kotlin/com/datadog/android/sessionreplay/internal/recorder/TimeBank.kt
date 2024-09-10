/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface TimeBank {

    /**
     * Called to consume execution time from the bank.
     */
    fun consume(executionTime: Long)

    /**
     * Called to update time bank balance and check if the given timestamp
     * is allowed according to the current time balance.
     *
     * @return true if the given timestamp is allowed by time bank to execute a task, false otherwise.
     */
    fun updateAndCheck(timestamp: Long): Boolean
}
