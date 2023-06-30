/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import com.datadog.android.core.SdkInternalLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

internal class SdkInternalLoggerReleaseTest {

    // region sdkLogger

    @Test
    @Suppress("FunctionNaming", "FunctionMaxLength")
    fun `M not build sdkLogger W init()`() {
        // When
        val logger = SdkInternalLogger(
            sdkCore = mock()
        )

        // Then
        assertThat(logger.maintainerLogger).isNull()
    }

    // endregion
}
