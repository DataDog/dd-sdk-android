/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.internal.thread

import com.datadog.android.internal.thread.NamedRunnable
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(value = BaseConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NamedRunnableTest {

    @Test
    fun `M execute the run() from original runnable W initialize with a runnable`(forge: Forge) {
        // Given
        val mockRunnable = mock<Runnable>()
        val fakeName = forge.aString()

        // When
        val testedNamedRunnable = NamedRunnable(fakeName, mockRunnable)
        testedNamedRunnable.run()

        // Then
        verify(mockRunnable).run()
        verifyNoMoreInteractions(mockRunnable)
    }

    @Test
    fun `M return the sanitized name W given not sanitized name`(forge: Forge) {
        // Given
        val fakeSectionNumber = forge.anInt(1, 10)
        val fakeStringList = mutableListOf<String>()
        val originalStringBuilder = StringBuilder()
        repeat(fakeSectionNumber) {
            val fakeName = forge.anAlphabeticalString()
            originalStringBuilder.append(fakeName)
            val symbol = forge.anElementFrom(
                " ",
                ",",
                ".",
                ":"
            )
            originalStringBuilder.append(symbol)
            fakeStringList.add(fakeName)
        }
        val expectedStringBuilder = StringBuilder()
        repeat(fakeSectionNumber) { index ->
            expectedStringBuilder.append(fakeStringList[index])
            expectedStringBuilder.append("_")
        }
        val mockRunnable = Mockito.mock<Runnable>()

        // When
        val testedRunnable = NamedRunnable(originalStringBuilder.toString(), mockRunnable)

        // Then
        assertThat(testedRunnable.name).isEqualTo(expectedStringBuilder.toString())
    }
}
