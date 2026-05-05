/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.cronet.internal

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.chromium.net.UrlRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.nio.ByteBuffer

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CronetUrlRequestWrapperTest {

    @Mock
    lateinit var mockDelegate: UrlRequest

    @Test
    fun `M delegate to request W start()`() {
        // Given
        val testedWrapper = createWrapper(mockDelegate)

        // When
        testedWrapper.start()

        // Then
        verify(mockDelegate).start()
    }

    @Test
    fun `M delegate to request W followRedirect()`() {
        // Given
        val testedWrapper = createWrapper(mockDelegate)

        // When
        testedWrapper.followRedirect()

        // Then
        verify(mockDelegate).followRedirect()
    }

    @Test
    fun `M delegate to request W read()`() {
        // Given
        val testedWrapper = createWrapper(mockDelegate)
        val buffer = ByteBuffer.allocate(1024)

        // When
        testedWrapper.read(buffer)

        // Then
        verify(mockDelegate).read(buffer)
    }

    @Test
    fun `M delegate to request W cancel()`() {
        // Given
        val testedWrapper = createWrapper(mockDelegate)

        // When
        testedWrapper.cancel()

        // Then
        verify(mockDelegate).cancel()
    }

    @Test
    fun `M delegate to request W isDone()`(
        @BoolForgery fakeDone: Boolean
    ) {
        // Given
        val testedWrapper = createWrapper(mockDelegate)
        whenever(mockDelegate.isDone).thenReturn(fakeDone)

        // When
        val result = testedWrapper.isDone

        // Then
        assertThat(result).isEqualTo(fakeDone)
    }

    @Test
    fun `M delegate to request W getStatus()`() {
        // Given
        val testedWrapper = createWrapper(mockDelegate)
        val mockListener = mock<UrlRequest.StatusListener>()

        // When
        testedWrapper.getStatus(mockListener)

        // Then
        verify(mockDelegate).getStatus(mockListener)
    }

    @Test
    fun `M not throw W start() { null delegate }`() {
        // Given
        val testedWrapper = createWrapper(null)

        // When / Then
        testedWrapper.start()
    }

    @Test
    fun `M not throw W followRedirect() { null delegate }`() {
        // Given
        val testedWrapper = createWrapper(null)

        // When / Then
        testedWrapper.followRedirect()
    }

    @Test
    fun `M not throw W read() { null delegate }`() {
        // Given
        val testedWrapper = createWrapper(null)

        // When / Then
        testedWrapper.read(ByteBuffer.allocate(1024))
    }

    @Test
    fun `M not throw W cancel() { null delegate }`() {
        // Given
        val testedWrapper = createWrapper(null)

        // When / Then
        testedWrapper.cancel()
    }

    @Test
    fun `M return false W isDone() { null delegate }`() {
        // Given
        val testedWrapper = createWrapper(null)

        // When / Then
        assertThat(testedWrapper.isDone).isFalse()
    }

    @Test
    fun `M not throw W getStatus() { null delegate }`() {
        // Given
        val testedWrapper = createWrapper(null)

        // When / Then
        testedWrapper.getStatus(mock())
    }

    private fun createWrapper(delegate: UrlRequest?): CronetUrlRequestWrapper {
        return object : CronetUrlRequestWrapper() {
            override val delegate: UrlRequest? = delegate
        }
    }
}
