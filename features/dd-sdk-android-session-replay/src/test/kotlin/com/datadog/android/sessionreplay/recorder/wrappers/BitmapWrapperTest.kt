/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.wrappers

import android.graphics.Bitmap
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.Mockito.mockStatic
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.quality.Strictness

@Extensions(ExtendWith(MockitoExtension::class), ExtendWith(ForgeExtension::class))
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class BitmapWrapperTest {
    private lateinit var testedWrapper: BitmapWrapper

    @Mock lateinit var mockLogger: InternalLogger

    @Mock lateinit var stubSource: Bitmap

    @IntForgery(min = 1, max = 1024)
    var fakeWidth: Int = 0

    @IntForgery(min = 1, max = 1024)
    var fakeHeight: Int = 0

    @BeforeEach
    fun setup() {
        testedWrapper = BitmapWrapper(mockLogger)
    }

    @Test
    fun `M propagate allocation failure to capture owner W createBitmap { native allocation fails }`() {
        // Given
        val fakeFailure = OutOfMemoryError()
        mockStatic(Bitmap::class.java).use { mockPlatform ->
            mockPlatform.`when`<Bitmap> {
                Bitmap.createBitmap(fakeWidth, fakeHeight, Bitmap.Config.ARGB_8888)
            }.thenThrow(fakeFailure)

            // When
            val actualFailure = assertThrows<OutOfMemoryError> {
                testedWrapper.createBitmap(fakeWidth, fakeHeight, Bitmap.Config.ARGB_8888)
            }

            // Then
            assertThat(actualFailure).isSameAs(fakeFailure)
            verifyNoInteractions(mockLogger)
        }
    }

    @Test
    fun `M propagate allocation failure to capture owner W createScaledBitmap { native allocation fails }`() {
        // Given
        val fakeFailure = OutOfMemoryError()
        mockStatic(Bitmap::class.java).use { mockPlatform ->
            mockPlatform.`when`<Bitmap> {
                Bitmap.createScaledBitmap(stubSource, fakeWidth, fakeHeight, false)
            }.thenThrow(fakeFailure)

            // When
            val actualFailure = assertThrows<OutOfMemoryError> {
                testedWrapper.createScaledBitmap(stubSource, fakeWidth, fakeHeight, false)
            }

            // Then
            assertThat(actualFailure).isSameAs(fakeFailure)
            verifyNoInteractions(mockLogger)
        }
    }
}
