/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import java.util.Locale

@Extensions(
    ExtendWith(ForgeExtension::class),
    ExtendWith(MockitoExtension::class)
)
@ForgeConfiguration(ForgeConfigurator::class)
internal class MobileSegmentExtTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    // region MobileSegment.Source

    @Test
    fun `M resolve the MobileSegment source W tryFromSource`(
        forge: Forge
    ) {
        // Given
        val fakeValidSource = forge.aValueFrom(MobileSegment.Source::class.java)

        // When
        val source = MobileSegment.Source.tryFromSource(fakeValidSource.toJson().asString, mockInternalLogger)

        // Then
        assertThat(source).isEqualTo(fakeValidSource)
    }

    @Test
    fun `M return default value W tryFromSource { unknown source }`(
        forge: Forge
    ) {
        // Given
        val fakeInvalidSource = forge.aString()

        // When
        val source = MobileSegment.Source.tryFromSource(fakeInvalidSource, mockInternalLogger)

        // Then
        assertThat(source).isEqualTo(MobileSegment.Source.ANDROID)
    }

    @Test
    fun `M send an error maintainer log W tryFromSource { unknown source }`(
        forge: Forge
    ) {
        // Given
        val fakeInvalidSource = forge.aString()

        // When
        MobileSegment.Source.tryFromSource(fakeInvalidSource, mockInternalLogger)

        // Then
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                level = eq(InternalLogger.Level.ERROR),
                target = eq(InternalLogger.Target.MAINTAINER),
                messageBuilder = capture(),
                throwable = isA<NoSuchElementException>(),
                onlyOnce = eq(false),
                additionalProperties = isNull()
            )

            assertThat(firstValue()).isEqualTo(
                UNKNOWN_MOBILE_SEGMENT_SOURCE_WARNING_MESSAGE_FORMAT.format(
                    Locale.US,
                    fakeInvalidSource
                )
            )
        }
    }

    // endregion

    // region MobileSegment.Wireframe.permanentId()

    @Test
    fun `M return permanentId W permanentId() { ShapeWireframe }`(forge: Forge) {
        val fakeId = forge.anAlphabeticalString()
        val wireframe = forge.getForgery<MobileSegment.Wireframe.ShapeWireframe>()
            .copy(permanentId = fakeId)
        assertThat(wireframe.permanentId()).isEqualTo(fakeId)
    }

    @Test
    fun `M return permanentId W permanentId() { TextWireframe }`(forge: Forge) {
        val fakeId = forge.anAlphabeticalString()
        val wireframe = forge.getForgery<MobileSegment.Wireframe.TextWireframe>()
            .copy(permanentId = fakeId)
        assertThat(wireframe.permanentId()).isEqualTo(fakeId)
    }

    @Test
    fun `M return permanentId W permanentId() { ImageWireframe }`(forge: Forge) {
        val fakeId = forge.anAlphabeticalString()
        val wireframe = forge.getForgery<MobileSegment.Wireframe.ImageWireframe>()
            .copy(permanentId = fakeId)
        assertThat(wireframe.permanentId()).isEqualTo(fakeId)
    }

    @Test
    fun `M return permanentId W permanentId() { PlaceholderWireframe }`(forge: Forge) {
        val fakeId = forge.anAlphabeticalString()
        val wireframe = forge.getForgery<MobileSegment.Wireframe.PlaceholderWireframe>()
            .copy(permanentId = fakeId)
        assertThat(wireframe.permanentId()).isEqualTo(fakeId)
    }

    @Test
    fun `M return permanentId W permanentId() { WebviewWireframe }`(forge: Forge) {
        val fakeId = forge.anAlphabeticalString()
        val wireframe = forge.getForgery<MobileSegment.Wireframe.WebviewWireframe>()
            .copy(permanentId = fakeId)
        assertThat(wireframe.permanentId()).isEqualTo(fakeId)
    }

    @Test
    fun `M return null W permanentId() { permanentId not set }`(forge: Forge) {
        val wireframe = forge.getForgery<MobileSegment.Wireframe.ShapeWireframe>()
            .copy(permanentId = null)
        assertThat(wireframe.permanentId()).isNull()
    }

    // endregion

    // region MobileSegment.Wireframe.copyWithPermanentId

    @Test
    fun `M set permanentId on ShapeWireframe W copyWithPermanentId()`(forge: Forge) {
        // Given
        val fakeWireframe = forge.getForgery<MobileSegment.Wireframe.ShapeWireframe>()
            .copy(permanentId = null)
        val fakePermanentId = forge.anAlphabeticalString()

        // When
        val result = fakeWireframe.copyWithPermanentId(fakePermanentId)

        // Then
        assertThat(result).isInstanceOfSatisfying(MobileSegment.Wireframe.ShapeWireframe::class.java) {
            assertThat(it.permanentId).isEqualTo(fakePermanentId)
            assertThat(it.id).isEqualTo(fakeWireframe.id)
            assertThat(it.x).isEqualTo(fakeWireframe.x)
            assertThat(it.y).isEqualTo(fakeWireframe.y)
        }
    }

    @Test
    fun `M set permanentId on TextWireframe W copyWithPermanentId()`(forge: Forge) {
        // Given
        val fakeWireframe = forge.getForgery<MobileSegment.Wireframe.TextWireframe>()
            .copy(permanentId = null)
        val fakePermanentId = forge.anAlphabeticalString()

        // When
        val result = fakeWireframe.copyWithPermanentId(fakePermanentId)

        // Then
        assertThat(result).isInstanceOfSatisfying(MobileSegment.Wireframe.TextWireframe::class.java) {
            assertThat(it.permanentId).isEqualTo(fakePermanentId)
            assertThat(it.text).isEqualTo(fakeWireframe.text)
        }
    }

    @Test
    fun `M set permanentId on ImageWireframe W copyWithPermanentId()`(forge: Forge) {
        // Given
        val fakeWireframe = forge.getForgery<MobileSegment.Wireframe.ImageWireframe>()
            .copy(permanentId = null)
        val fakePermanentId = forge.anAlphabeticalString()

        // When
        val result = fakeWireframe.copyWithPermanentId(fakePermanentId)

        // Then
        assertThat(result).isInstanceOfSatisfying(MobileSegment.Wireframe.ImageWireframe::class.java) {
            assertThat(it.permanentId).isEqualTo(fakePermanentId)
        }
    }

    @Test
    fun `M set permanentId on PlaceholderWireframe W copyWithPermanentId()`(forge: Forge) {
        // Given
        val fakeWireframe = forge.getForgery<MobileSegment.Wireframe.PlaceholderWireframe>()
            .copy(permanentId = null)
        val fakePermanentId = forge.anAlphabeticalString()

        // When
        val result = fakeWireframe.copyWithPermanentId(fakePermanentId)

        // Then
        assertThat(result).isInstanceOfSatisfying(MobileSegment.Wireframe.PlaceholderWireframe::class.java) {
            assertThat(it.permanentId).isEqualTo(fakePermanentId)
        }
    }

    @Test
    fun `M set permanentId on WebviewWireframe W copyWithPermanentId()`(forge: Forge) {
        // Given
        val fakeWireframe = forge.getForgery<MobileSegment.Wireframe.WebviewWireframe>()
            .copy(permanentId = null)
        val fakePermanentId = forge.anAlphabeticalString()

        // When
        val result = fakeWireframe.copyWithPermanentId(fakePermanentId)

        // Then
        assertThat(result).isInstanceOfSatisfying(MobileSegment.Wireframe.WebviewWireframe::class.java) {
            assertThat(it.permanentId).isEqualTo(fakePermanentId)
        }
    }

    @Test
    fun `M clear permanentId W copyWithPermanentId() { null }`(forge: Forge) {
        // Given — a wireframe that already has a non-null permanentId; a null arg should clear it.
        val fakeOriginalId = forge.anAlphabeticalString()
        val fakeWireframe = forge.getForgery<MobileSegment.Wireframe.ShapeWireframe>()
            .copy(permanentId = fakeOriginalId)

        // When
        val result = fakeWireframe.copyWithPermanentId(null)

        // Then
        assertThat(result).isInstanceOfSatisfying(MobileSegment.Wireframe.ShapeWireframe::class.java) {
            assertThat(it.permanentId).isNull()
        }
    }

    // endregion
}
