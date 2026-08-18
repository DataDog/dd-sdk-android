/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.view.View
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.DefaultCapturedIdentityFactory
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CapturedHiddenViewMapperTest {

    private val mockViewBoundsResolver: ViewBoundsResolver = mock()
    private val testedMapper = CapturedHiddenViewMapper(mockViewBoundsResolver)

    @Test
    fun `M emit a placeholder labeled Hidden W map()`(
        @StringForgery fakeScope: String,
        @Forgery fakeBounds: GlobalBounds,
        @FloatForgery(min = 0f, max = 4f) fakeDensity: Float
    ) {
        // Given
        val mockView: View = mock()
        val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeScope))
        val owner = factory.view(factory.window("window"), "hidden-owner")
        val mappingContext = CapturedMappingContext(factory, owner, screenDensity = fakeDensity)
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockView, fakeDensity)).thenReturn(fakeBounds)

        // When
        val result = testedMapper.map(mockView, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        val wireframe = result.wireframes.single() as CapturedWireframe.PrivacyPlaceholder
        assertThat(wireframe.label).isEqualTo("Hidden")
        assertThat(wireframe.bounds.x).isEqualTo(fakeBounds.x)
    }
}
