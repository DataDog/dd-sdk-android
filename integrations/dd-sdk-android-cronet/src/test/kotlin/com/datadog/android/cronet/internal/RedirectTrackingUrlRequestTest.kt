/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.cronet.internal

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.chromium.net.UrlRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RedirectTrackingUrlRequestTest {
    @Mock
    lateinit var mockDelegate: UrlRequest
    private lateinit var testedRequest: RedirectTrackingUrlRequest

    @BeforeEach
    fun `set up`() {
        testedRequest = RedirectTrackingUrlRequest(delegate = mockDelegate)
    }

    @Test
    fun `M call onFollowRedirect and delegate W followRedirect()`() {
        // When
        testedRequest.followRedirect()

        // Then
        assertThat(testedRequest.wasFollowRedirectCalled).isTrue()
        verify(mockDelegate).followRedirect()
    }

    @Test
    fun `M delegate start W start()`() {
        // When
        testedRequest.start()

        // Then
        verify(mockDelegate).start()
    }

    @Test
    fun `M delegate cancel W cancel()`() {
        // When
        testedRequest.cancel()

        // Then
        verify(mockDelegate).cancel()
    }

    @Test
    fun `M return delegate W delegate property`() {
        // Then
        assertThat(testedRequest.delegate).isSameAs(mockDelegate)
    }
}
