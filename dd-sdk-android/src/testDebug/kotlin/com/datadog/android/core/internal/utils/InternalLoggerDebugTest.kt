/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import com.datadog.android.v2.core.LogcatLogHandler
import com.datadog.android.v2.core.SdkInternalLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class InternalLoggerDebugTest {

    // region sdkLogger

    @Test
    @Suppress("FunctionNaming", "FunctionMaxLength")
    fun `M build LogCat sdkLogger W init()`() {
        // When
        val logger = SdkInternalLogger()

        // Then
        val handler: LogcatLogHandler? = logger.sdkLogger
        assertThat(handler).isNotNull
        assertThat(handler?.tag).isEqualTo(SdkInternalLogger.SDK_LOG_TAG)
    }

    // endregion
}
