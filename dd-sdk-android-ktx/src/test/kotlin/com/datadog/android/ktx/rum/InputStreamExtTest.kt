/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ktx.rum

import com.datadog.android.rum.resource.RumResourceInputStream
import com.datadog.android.v2.api.SdkCore
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness
import java.io.InputStream

@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@ForgeConfiguration(value = BaseConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InputStreamExtTest {

    @Test
    fun `M wrap inputStream W asRumResource()`(
        @StringForgery url: String
    ) {
        // Given
        val mockIS: InputStream = mock()
        val mockSdkCore: SdkCore = mock()

        // When
        val result = mockIS.asRumResource(url, mockSdkCore)

        // Then
        assertThat(result).isInstanceOf(RumResourceInputStream::class.java)
        val rumRIS = result as RumResourceInputStream
        assertThat(rumRIS.delegate).isSameAs(mockIS)
        assertThat(rumRIS.sdkCore).isSameAs(mockSdkCore)
        assertThat(rumRIS.url).isEqualTo(url)
    }
}
