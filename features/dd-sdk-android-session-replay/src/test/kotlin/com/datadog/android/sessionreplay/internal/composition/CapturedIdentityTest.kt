/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframeKind
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CapturedIdentityTest {

    @StringForgery
    lateinit var fakeScopeValue: String

    @StringForgery
    lateinit var fakeWindowName: String

    @StringForgery
    lateinit var fakeLocalId: String

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Test
    fun `M return stable identity W view() + window() { identical inputs }`(
        @StringForgery fakeWindowId: String,
        @StringForgery fakeViewId: String
    ) {
        // Given
        val testedFactory = factory()

        // When
        val first = testedFactory.view(testedFactory.window(fakeWindowId), fakeViewId)
        val repeated = testedFactory.view(testedFactory.window(fakeWindowId), fakeViewId)

        // Then
        assertThat(repeated).isSameAs(first)
    }

    @Test
    fun `M allocate new identity W window() { separate factories }`(
        @StringForgery fakeWindowId: String
    ) {
        // Given
        val fakeGenerator = AutoIncrementingCapturedReplayIdGenerator()
        val fakeScope = RumViewIdentityScope(fakeScopeValue)
        val testedFirstFactory = DefaultCapturedIdentityFactory(fakeScope, fakeGenerator)
        val testedSecondFactory = DefaultCapturedIdentityFactory(fakeScope, fakeGenerator)

        // When
        val first = testedFirstFactory.window(fakeWindowId)
        val second = testedSecondFactory.window(fakeWindowId)

        // Then
        assertThat(second).isNotEqualTo(first)
        assertThat(second.wireId).isEqualTo(first.wireId + 1)
    }

    @Test
    fun `M namespace view identity W view { identical local IDs in different windows }`() {
        // Given
        val testedFactory = factory()

        // When
        val first = testedFactory.view(testedFactory.window(fakeWindowName), fakeLocalId)
        val second = testedFactory.view(testedFactory.window(fakeWindowName + "-other"), fakeLocalId)

        // Then
        assertThat(first).isNotEqualTo(second)
        assertThat(first.wireId).isNotEqualTo(second.wireId)
    }

    @Test
    fun `M namespace compose identity W composeNode { identical local IDs in different hosts }`() {
        // Given
        val testedFactory = factory()
        val fakeWindow = testedFactory.window(fakeWindowName)

        // When
        val first = testedFactory.composeNode(testedFactory.composeHost(fakeWindow, fakeLocalId), fakeLocalId)
        val second = testedFactory.composeNode(
            testedFactory.composeHost(fakeWindow, fakeLocalId + "-other"),
            fakeLocalId
        )

        // Then
        assertThat(first).isNotEqualTo(second)
        assertThat(first.wireId).isNotEqualTo(second.wireId)
    }

    @Test
    fun `M separate identity kinds W view() + composeHost() + layer() + shapeWireframe()`() {
        // Given
        val testedFactory = factory()
        val fakeWindow = testedFactory.window(fakeLocalId)

        // When
        val view = testedFactory.view(fakeWindow, fakeLocalId)
        val host = testedFactory.composeHost(fakeWindow, fakeLocalId)
        val layer = testedFactory.layer(fakeWindow, fakeLocalId)
        val wireframe = testedFactory.shapeWireframe(fakeWindow)

        // Then
        assertThat(listOf(view, host, layer, wireframe).map { it.wireId }.toSet()).hasSize(4)
    }

    @Test
    fun `M isolate identity W window() { different RUM view scopes }`() {
        // Given
        val fakeGenerator = AutoIncrementingCapturedReplayIdGenerator()
        val testedFirstFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeScopeValue), fakeGenerator)
        val testedSecondFactory =
            DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeScopeValue + "-other"), fakeGenerator)

        // When
        val first = testedFirstFactory.window(fakeWindowName)
        val second = testedSecondFactory.window(fakeWindowName)

        // Then
        assertThat(first).isNotEqualTo(second)
        assertThat(first.wireId).isNotEqualTo(second.wireId)
    }

    @Test
    fun `M log warning and still create identity W view() + window() { owner belongs to another scope }`() {
        // Given
        val testedFirstFactory = factory(fakeScopeValue)
        val testedSecondFactory = factory(fakeScopeValue + "-other")

        // When
        val view = testedSecondFactory.view(testedFirstFactory.window(fakeWindowName), fakeLocalId)

        // Then
        assertThat(view).isNotNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            "Identity owner belongs to a different RUM view scope."
        )
    }

    @Test
    fun `M log warning and still create identity W view() { owner kind mismatch }`() {
        // Given
        val testedFactory = factory()
        val fakeNonWindowOwner = testedFactory.layer(testedFactory.window(fakeWindowName), fakeLocalId)

        // When
        val view = testedFactory.view(fakeNonWindowOwner, fakeLocalId)

        // Then
        assertThat(view).isNotNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            "Identity owner must be a WINDOW."
        )
    }

    @Test
    fun `M log warning and still create identity W layer() { wireframe used as layer owner }`() {
        // Given
        val testedFactory = factory()
        val fakeWireframeOwner = testedFactory.shapeWireframe(testedFactory.window(fakeWindowName))

        // When
        val layer = testedFactory.layer(fakeWireframeOwner, fakeLocalId)

        // Then
        assertThat(layer).isNotNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            "Identity owner must be a captured layer."
        )
    }

    @Test
    fun `M create namespaced IDs W shapeWireframe() + textWireframe() + imageWireframe() + placeholderWireframe() `(
        @LongForgery(min = 0, max = Int.MAX_VALUE.toLong()) fakeInitialId: Long
    ) {
        // Given
        val testedFactory = DefaultCapturedIdentityFactory(
            scope = RumViewIdentityScope(fakeScopeValue),
            replayIdGenerator = AutoIncrementingCapturedReplayIdGenerator(initialId = fakeInitialId)
        )
        val fakeLayer = testedFactory.layer(testedFactory.window(fakeWindowName), fakeLocalId)

        // When
        val shape = testedFactory.shapeWireframe(fakeLayer)
        val text = testedFactory.textWireframe(fakeLayer)
        val image = testedFactory.imageWireframe(fakeLayer)
        val placeholder = testedFactory.placeholderWireframe(fakeLayer)

        // Then
        assertThat(shape.wireId).isEqualTo((1L shl NAMESPACE_SHIFT) or fakeLayer.wireId)
        assertThat(text.wireId).isEqualTo((2L shl NAMESPACE_SHIFT) or fakeLayer.wireId)
        assertThat(image.wireId).isEqualTo((3L shl NAMESPACE_SHIFT) or fakeLayer.wireId)
        assertThat(placeholder.wireId).isEqualTo((4L shl NAMESPACE_SHIFT) or fakeLayer.wireId)
    }

    @Test
    fun `M preserve raw slot ID W webViewWireframe()`(
        @IntForgery fakeSlotId: Int
    ) {
        // Given
        val testedFactory = factory()
        val fakeLayer = testedFactory.layer(testedFactory.window(fakeWindowName), fakeLocalId)

        // When
        val wireframe = testedFactory.webViewWireframe(fakeLayer, slotId = fakeSlotId.toLong())

        // Then
        assertThat(wireframe.wireId).isEqualTo(fakeSlotId.toLong())
        assertThat(wireframe.wireframeKind).isEqualTo(CapturedWireframeKind.WEB_VIEW)
    }

    @Test
    fun `M keep layer IDs outside slot ID range W window() { any Int slotId }`() {
        // Given
        val testedFactory = DefaultCapturedIdentityFactory(
            scope = RumViewIdentityScope(fakeScopeValue),
            replayIdGenerator = AutoIncrementingCapturedReplayIdGenerator(initialId = Int.MAX_VALUE - 1L)
        )

        // When
        val layerIds = listOf(
            testedFactory.window(fakeWindowName + "-before-max").wireId,
            testedFactory.window(fakeWindowName + "-max").wireId,
            testedFactory.window(fakeWindowName + "-wrapped").wireId,
            testedFactory.window(fakeWindowName + "-min").wireId
        )

        // Then
        assertThat(layerIds).allMatch { it < Int.MIN_VALUE || it > Int.MAX_VALUE.toLong() }
    }

    @Test
    fun `M wrap replay IDs W window() { maximum ID reached }`() {
        // Given
        val testedFactory = DefaultCapturedIdentityFactory(
            scope = RumViewIdentityScope(fakeScopeValue),
            replayIdGenerator = AutoIncrementingCapturedReplayIdGenerator(initialId = Int.MAX_VALUE - 1L)
        )

        // When
        val beforeMax = testedFactory.window(fakeWindowName + "-before-max")
        val max = testedFactory.window(fakeWindowName + "-max")
        val wrapped = testedFactory.window(fakeWindowName + "-wrapped")

        // Then
        assertThat(beforeMax.wireId).isEqualTo(LAYER_ID_OFFSET + (Int.MAX_VALUE - 1L))
        assertThat(max.wireId).isEqualTo(LAYER_ID_OFFSET + Int.MAX_VALUE.toLong())
        assertThat(wrapped.wireId).isEqualTo(LAYER_ID_OFFSET)
    }

    @Test
    fun `M create player-safe IDs W view() + placeholderWireframe() `() {
        // Given
        val testedFactory = factory()
        val fakeWindow = testedFactory.window(fakeWindowName)

        // When
        val identities = List(1_000) { testedFactory.view(fakeWindow, "$fakeLocalId-$it") } +
            testedFactory.placeholderWireframe(fakeWindow)

        // Then
        assertThat(identities.map { it.wireId })
            .allMatch { it in 0..MAX_SAFE_JAVASCRIPT_INTEGER }
            .doesNotHaveDuplicates()
    }

    private fun factory(scope: String = fakeScopeValue) = DefaultCapturedIdentityFactory(
        scope = RumViewIdentityScope(scope),
        replayIdGenerator = AutoIncrementingCapturedReplayIdGenerator(),
        internalLogger = mockInternalLogger
    )

    private companion object {
        const val NAMESPACE_SHIFT = 32
        const val MAX_SAFE_JAVASCRIPT_INTEGER = (1L shl 53) - 1
    }
}
