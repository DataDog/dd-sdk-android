/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import com.datadog.android.core.LogcatLogHandler
import com.datadog.android.core.SdkInternalLogger
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.kotlin.mock

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class SdkInternalLoggerDebugTest {

    // region sdkLogger

    @Test
    @Suppress("FunctionNaming")
    fun `M build LogCat sdkLogger W init()`(
        forge: Forge
    ) {
        // When
        val logger = SdkInternalLogger(forge.aNullable { mock() })

        // Then
        val handler: LogcatLogHandler? = logger.maintainerLogger
        assertThat(handler).isNotNull
        assertThat(handler?.tag).isEqualTo(SdkInternalLogger.SDK_LOG_TAG)
    }

    // endregion
}
