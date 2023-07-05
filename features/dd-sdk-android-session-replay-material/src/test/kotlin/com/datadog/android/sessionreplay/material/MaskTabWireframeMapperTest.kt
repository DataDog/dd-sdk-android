/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.material

import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskTextViewMapper
import com.datadog.android.sessionreplay.material.forge.ForgeConfigurator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class MaskTabWireframeMapperTest : BaseTabWireframeMapperTest() {

    override fun provideTestInstance(): TabWireframeMapper {
        return MaskTabWireframeMapper(
            viewUtils = mockViewUtils,
            uniqueIdentifierGenerator = mockUniqueIdentifierGenerator,
            textViewMapper = mockTextWireframeMapper
        )
    }

    @Test
    fun `M use a MaskTextViewMapper when initialized`() {
        // Given
        val maskTabWireframeMapper = MaskTabWireframeMapper()

        // Then
        assertThat(maskTabWireframeMapper.textViewMapper)
            .isInstanceOf(MaskTextViewMapper::class.java)
    }
}
