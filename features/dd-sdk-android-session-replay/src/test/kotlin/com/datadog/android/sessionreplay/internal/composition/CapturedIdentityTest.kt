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
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CapturedIdentityTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Test
    fun `M return stable identity W create identity { identical inputs }`(
        @StringForgery fakeWindowId: String,
        @StringForgery fakeViewId: String
    ) {
        // Given
        val factory = factory()

        // When
        val first = factory.view(factory.window(fakeWindowId), fakeViewId)
        val repeated = factory.view(factory.window(fakeWindowId), fakeViewId)

        // Then
        assertThat(repeated).isSameAs(first)
    }

    @Test
    fun `M allocate new identity W create identity { separate factories }`(
        @StringForgery fakeWindowId: String
    ) {
        // Given
        val generator = AutoIncrementingCapturedReplayIdGenerator()
        val scope = RumViewIdentityScope("view")
        val firstFactory = DefaultCapturedIdentityFactory(scope, generator)
        val secondFactory = DefaultCapturedIdentityFactory(scope, generator)

        // When
        val first = firstFactory.window(fakeWindowId)
        val second = secondFactory.window(fakeWindowId)

        // Then
        assertThat(second).isNotEqualTo(first)
        assertThat(second.wireId).isEqualTo(first.wireId + 1)
    }

    @Test
    fun `M namespace view identity W view { identical local IDs in different windows }`() {
        // Given
        val factory = factory()

        // When
        val first = factory.view(factory.window("first-window"), "shared-id")
        val second = factory.view(factory.window("second-window"), "shared-id")

        // Then
        assertThat(first).isNotEqualTo(second)
        assertThat(first.wireId).isNotEqualTo(second.wireId)
    }

    @Test
    fun `M namespace compose identity W composeNode { identical local IDs in different hosts }`() {
        // Given
        val factory = factory()
        val window = factory.window("window")

        // When
        val first = factory.composeNode(factory.composeHost(window, "first-host"), "shared-id")
        val second = factory.composeNode(factory.composeHost(window, "second-host"), "shared-id")

        // Then
        assertThat(first).isNotEqualTo(second)
        assertThat(first.wireId).isNotEqualTo(second.wireId)
    }

    @Test
    fun `M allocate distinct identity W create identity { identical numeric IDs }`() {
        // Given
        val factory = factory()
        val window = factory.window("42")

        // When
        val view = factory.view(window, "42")
        val host = factory.composeHost(window, "42")
        val layer = factory.layer(window, "42")
        val wireframe = factory.shapeWireframe(window)

        // Then
        assertThat(listOf(view, host, layer, wireframe).map { it.wireId }.toSet()).hasSize(4)
    }

    @Test
    fun `M isolate identity W create identity { different RUM view scopes }`() {
        // Given
        val generator = AutoIncrementingCapturedReplayIdGenerator()
        val firstFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope("first-view"), generator)
        val secondFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope("second-view"), generator)

        // When
        val first = firstFactory.window("window")
        val second = secondFactory.window("window")

        // Then
        assertThat(first).isNotEqualTo(second)
        assertThat(first.wireId).isNotEqualTo(second.wireId)
    }

    @Test
    fun `M log warning and still create identity W create identity { owner belongs to another scope }`() {
        // Given
        val firstFactory = factory("first-view")
        val secondFactory = factory("second-view")

        // When
        val view = secondFactory.view(firstFactory.window("window"), "view")

        // Then
        assertThat(view).isNotNull()
        verify(mockInternalLogger).log(
            level = eq(InternalLogger.Level.WARN),
            target = eq(InternalLogger.Target.MAINTAINER),
            messageBuilder = any(),
            throwable = isNull(),
            onlyOnce = eq(false),
            additionalProperties = isNull()
        )
    }

    @Test
    fun `M create namespaced IDs W create wireframe identities`(
        @LongForgery(min = 0, max = Int.MAX_VALUE.toLong()) fakeInitialId: Long
    ) {
        // Given
        val factory = DefaultCapturedIdentityFactory(
            scope = RumViewIdentityScope("view"),
            replayIdGenerator = AutoIncrementingCapturedReplayIdGenerator(initialId = fakeInitialId)
        )
        val layer = factory.layer(factory.window("window"), "layer")

        // When
        val shape = factory.shapeWireframe(layer)
        val text = factory.textWireframe(layer)
        val image = factory.imageWireframe(layer)
        val placeholder = factory.placeholderWireframe(layer)

        // Then
        assertThat(shape.wireId).isEqualTo((1L shl NAMESPACE_SHIFT) or layer.wireId)
        assertThat(text.wireId).isEqualTo((2L shl NAMESPACE_SHIFT) or layer.wireId)
        assertThat(image.wireId).isEqualTo((3L shl NAMESPACE_SHIFT) or layer.wireId)
        assertThat(placeholder.wireId).isEqualTo((4L shl NAMESPACE_SHIFT) or layer.wireId)
    }

    @Test
    fun `M preserve raw slot ID W create webViewWireframe`(
        @IntForgery fakeSlotId: Int
    ) {
        // Given
        val factory = factory()
        val layer = factory.layer(factory.window("window"), "layer")

        // When
        val wireframe = factory.webViewWireframe(layer, slotId = fakeSlotId.toLong())

        // Then
        assertThat(wireframe.wireId).isEqualTo(fakeSlotId.toLong())
        assertThat(wireframe.wireframeKind).isEqualTo(CapturedWireframeKind.WEB_VIEW)
    }

    @Test
    fun `M keep layer IDs outside slot ID range W create identity { any Int slotId }`() {
        // Given
        val factory = DefaultCapturedIdentityFactory(
            scope = RumViewIdentityScope("view"),
            replayIdGenerator = AutoIncrementingCapturedReplayIdGenerator(initialId = Int.MAX_VALUE - 1L)
        )

        // When
        val layerIds = listOf(
            factory.window("before-max").wireId,
            factory.window("max").wireId,
            factory.window("wrapped").wireId,
            factory.window("min").wireId
        )

        // Then
        // A web-view slotId is caller-supplied and preserved verbatim as its wire id (see
        // `M preserve raw slot ID`), so it may be anywhere in the Int range. Layer ids must never
        // land in that range, or a colliding slotId could be mistaken for an unrelated layer.
        assertThat(layerIds).allMatch { it < Int.MIN_VALUE || it > Int.MAX_VALUE.toLong() }
    }

    @Test
    fun `M wrap replay IDs W create identity { maximum ID reached }`() {
        // Given
        val factory = DefaultCapturedIdentityFactory(
            scope = RumViewIdentityScope("view"),
            replayIdGenerator = AutoIncrementingCapturedReplayIdGenerator(initialId = Int.MAX_VALUE - 1L)
        )

        // When
        val beforeMax = factory.window("before-max")
        val max = factory.window("max")
        val wrapped = factory.window("wrapped")

        // Then
        assertThat(beforeMax.wireId).isEqualTo(LAYER_ID_OFFSET + (Int.MAX_VALUE - 1L))
        assertThat(max.wireId).isEqualTo(LAYER_ID_OFFSET + Int.MAX_VALUE.toLong())
        assertThat(wrapped.wireId).isEqualTo(LAYER_ID_OFFSET)
    }

    @Test
    fun `M create player-safe IDs W create identities`() {
        // Given
        val factory = factory()
        val window = factory.window("window")

        // When
        val identities = List(1_000) { factory.view(window, "view-$it") } +
            factory.placeholderWireframe(window)

        // Then
        assertThat(identities.map { it.wireId })
            .allMatch { it in 0..MAX_SAFE_JAVASCRIPT_INTEGER }
            .doesNotHaveDuplicates()
    }

    private fun factory(scope: String = "view") = DefaultCapturedIdentityFactory(
        scope = RumViewIdentityScope(scope),
        replayIdGenerator = AutoIncrementingCapturedReplayIdGenerator(),
        internalLogger = mockInternalLogger
    )

    private companion object {
        const val NAMESPACE_SHIFT = 32
        const val MAX_SAFE_JAVASCRIPT_INTEGER = (1L shl 53) - 1
    }
}
