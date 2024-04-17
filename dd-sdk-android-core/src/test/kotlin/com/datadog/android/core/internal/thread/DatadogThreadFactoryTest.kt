/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class DatadogThreadFactoryTest {

    @Test
    fun `M create a new thread W newThread()`(
        @StringForgery fakeNewThreadContext: String
    ) {
        // Given
        val testedFactory = DatadogThreadFactory(fakeNewThreadContext)

        // When
        val thread = testedFactory.newThread {}

        // Then
        assertThat(thread.name).isEqualTo("datadog-$fakeNewThreadContext-thread-1")
        assertThat(thread.isDaemon).isFalse()
        assertThat(thread.priority).isEqualTo(Thread.NORM_PRIORITY)
    }

    @Test
    fun `M create a new thread with incremented index W newThread()`(
        @StringForgery fakeNewThreadContext: String
    ) {
        // Given
        val testedFactory = DatadogThreadFactory(fakeNewThreadContext)

        // When
        testedFactory.newThread {}
        val thread = testedFactory.newThread {}

        // Then
        assertThat(thread.name).isEqualTo("datadog-$fakeNewThreadContext-thread-2")
        assertThat(thread.isDaemon).isFalse()
        assertThat(thread.priority).isEqualTo(Thread.NORM_PRIORITY)
    }
}
