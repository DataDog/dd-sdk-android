/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.core.configuration.BackPressureStrategy

internal class BackPressureExecutorServiceTest :
    AbstractLoggingExecutorServiceTest<BackPressureExecutorService>() {

    override fun createTestedExecutorService(backPressureStrategy: BackPressureStrategy): BackPressureExecutorService {
        return BackPressureExecutorService(
            mockInternalLogger,
            backPressureStrategy
        )
    }
}
