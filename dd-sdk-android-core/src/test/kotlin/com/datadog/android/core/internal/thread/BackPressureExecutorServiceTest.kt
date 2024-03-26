/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.core.configuration.BackPressureMitigation
import com.datadog.android.core.configuration.BackPressureStrategy
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import org.mockito.Mockito.mock

internal class BackPressureExecutorServiceTest :
    AbstractLoggingExecutorServiceTest<BackPressureExecutorService>() {

    @IntForgery(128, 1024)
    var fakeBackPressureCapacity: Int = 0

    @Forgery
    lateinit var fakeBackPressureMitigation: BackPressureMitigation

    override fun createTestedExecutorService(): BackPressureExecutorService {
        return BackPressureExecutorService(
            mockInternalLogger,
            BackPressureStrategy(
                fakeBackPressureCapacity,
                mock(),
                mock(),
                fakeBackPressureMitigation
            )
        )
    }
}
