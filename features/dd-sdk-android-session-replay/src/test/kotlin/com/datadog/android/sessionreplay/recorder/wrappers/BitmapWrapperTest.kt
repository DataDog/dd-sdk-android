/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.wrappers

import android.graphics.Bitmap
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class BitmapWrapperTest {

    private lateinit var testedWrapper: BitmapWrapper

    @Mock
    lateinit var mockLogger: InternalLogger

    @Mock
    lateinit var mockBitmap: Bitmap

    @BeforeEach
    fun `set up`() {
        testedWrapper = BitmapWrapper(mockLogger)
    }

    @Test
    fun `M return null and log W wrapHardwareBuffer() { bitmap recycled }`() {
        // Given
        val fakeError = IllegalStateException()
        whenever(mockBitmap.hardwareBuffer).thenThrow(fakeError)

        // When
        val result = testedWrapper.wrapHardwareBuffer(mockBitmap)

        // Then
        assertThat(result).isNull()
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            "Failed to wrap the buffer of a hardware bitmap",
            throwable = fakeError,
            onlyOnce = true
        )
    }
}
