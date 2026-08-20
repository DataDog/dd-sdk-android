/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.webkit.WebView
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.DefaultCapturedIdentityFactory
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
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
internal class CapturedWebViewMapperTest {

    private val mockViewIdentifierResolver: ViewIdentifierResolver = mock()
    private val mockViewBoundsResolver: ViewBoundsResolver = mock()
    private val testedMapper = CapturedWebViewMapper(mockViewIdentifierResolver, mockViewBoundsResolver)

    @Test
    fun `M emit a WebView wireframe with id equal to slotId W map()`(
        @StringForgery fakeScope: String,
        @LongForgery(min = 0L) fakeSlotId: Long,
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val mockWebView: WebView = mock()
        val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeScope))
        val window = factory.window("window")
        val owner = factory.view(window, "webview-owner")
        val mappingContext = CapturedMappingContext(
            factory,
            owner,
            screenDensity = 2f,
            imagePrivacy = ImagePrivacy.MASK_NONE,
            textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS
        )
        whenever(mockViewIdentifierResolver.resolveViewId(mockWebView)).thenReturn(fakeSlotId)
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockWebView, 2f)).thenReturn(fakeBounds)

        // When
        val result = testedMapper.map(mockWebView, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        val wireframe = result.wireframes.single() as CapturedWireframe.WebView
        assertThat(wireframe.identity.wireId).isEqualTo(fakeSlotId)
        assertThat(wireframe.isVisible).isTrue()
        assertThat(wireframe.bounds.x).isEqualTo(fakeBounds.x)
        assertThat(wireframe.bounds.y).isEqualTo(fakeBounds.y)
        assertThat(wireframe.bounds.width).isEqualTo(fakeBounds.width)
        assertThat(wireframe.bounds.height).isEqualTo(fakeBounds.height)
    }
}
