/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.material

import com.datadog.android.sessionreplay.internal.recorder.mapper.TextViewMapper
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
internal class TabWireframeMapperTest : BaseTabWireframeMapperTest() {

    override fun provideTestInstance(): TabWireframeMapper {
        return TabWireframeMapper(
            viewIdentifierResolver = mockViewIdentifierResolver,
            viewBoundsResolver = mockViewBoundsResolver,
            textViewMapper = mockTextWireframeMapper
        )
    }

    @Test
    fun `M use a TextViewMapper when initialized`() {
        // Given
        val tabWireframeMapper = TabWireframeMapper(
            mockViewIdentifierResolver,
            mockColorStringFormatter,
            mockViewBoundsResolver,
            mockDrawableToColorMapper
        )

        // Then
        assertThat(tabWireframeMapper.textViewMapper)
            .isInstanceOf(TextViewMapper::class.java)
    }
}
