/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.core.configuration.BackPressureStrategy
import fr.xgouchet.elmyr.Forge
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class BackPressureExecutorServiceTest :
    AbstractExecutorServiceTest<BackPressureExecutorService>() {

    override fun createTestedExecutorService(
        forge: Forge,
        backPressureStrategy: BackPressureStrategy
    ): BackPressureExecutorService {
        return BackPressureExecutorService(
            mockInternalLogger,
            forge.anAlphabeticalString(),
            backPressureStrategy
        )
    }

    @Test
    fun `M use DatadogThreadFactory W constructor()`() {
        // Then
        assertThat(testedExecutor.threadFactory).isInstanceOf(DatadogThreadFactory::class.java)
    }
}
