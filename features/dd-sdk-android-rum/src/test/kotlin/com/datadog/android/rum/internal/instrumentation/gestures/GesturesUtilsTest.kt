/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.app.Application
import android.content.res.Resources
import android.view.View
import com.datadog.android.internal.utils.toHexString
import com.datadog.android.rum.tracking.InteractionPredicate
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class GesturesUtilsTest {

    @Mock
    lateinit var mockAppContext: Application

    @Mock
    lateinit var mockResources: Resources

    @Mock
    lateinit var mockInteractionPredicate: InteractionPredicate

    @Mock
    lateinit var mockTarget: Any

    @Test
    fun `M return the custom name W resolveTargetName { custom name provided }`(
        @StringForgery fakeTargetName: String
    ) {
        // Given
        whenever(mockInteractionPredicate.getTargetName(mockTarget)).thenReturn(fakeTargetName)

        // Then
        assertThat(resolveTargetName(mockInteractionPredicate, mockTarget))
            .isEqualTo(fakeTargetName)
    }

    @Test
    fun `M return empty string W resolveTargetName { custom name empty }`() {
        // Given
        whenever(mockInteractionPredicate.getTargetName(mockTarget)).thenReturn("")

        // Then
        assertThat(resolveTargetName(mockInteractionPredicate, mockTarget)).isEmpty()
    }

    @Test
    fun `M return  empty string  W resolveTargetName { custom name null }`() {
        assertThat(resolveTargetName(mockInteractionPredicate, mockTarget)).isEmpty()
    }

    @Test
    fun `it will return resource entry name if found`(forge: Forge) {
        // Given
        val resourceId = forge.anInt()
        val resourceName = forge.aString()
        whenever(mockAppContext.resources).thenReturn(mockResources)
        whenever(mockResources.getResourceEntryName(resourceId)).thenReturn(resourceName)

        // When
        assertThat(mockAppContext.resourceIdName(resourceId)).isEqualTo(resourceName)
    }

    @Test
    fun `it will return the resource id as String hexa if Context resources are null`(
        forge: Forge
    ) {
        // Given
        val resourceId = forge.anInt()
        whenever(mockAppContext.resources).thenReturn(null)

        // When
        assertThat(mockAppContext.resourceIdName(resourceId))
            .isEqualTo("0x${resourceId.toHexString()}")
    }

    @Test
    fun `it will return the resource id as String hexa if resource name could not be found`(
        forge: Forge
    ) {
        // Given
        val resourceId = forge.anInt()
        whenever(mockAppContext.resources).thenReturn(mockResources)
        whenever(mockResources.getResourceEntryName(resourceId)).thenThrow(
            Resources.NotFoundException(
                forge.aString()
            )
        )

        // When
        assertThat(mockAppContext.resourceIdName(resourceId))
            .isEqualTo("0x${resourceId.toHexString()}")
    }

    @Test
    fun `it will return the resource id as String hexa if resource name was null`(
        forge: Forge
    ) {
        // Given
        val resourceId = forge.anInt()
        whenever(mockAppContext.resources).thenReturn(mockResources)
        whenever(mockResources.getResourceEntryName(resourceId)).thenReturn(null)

        // When
        assertThat(mockAppContext.resourceIdName(resourceId))
            .isEqualTo("0x${resourceId.toHexString()}")
    }

    @Test
    fun `M return the canonicalName W targetClassName() { canonicalName not null }`() {
        // Given
        val fakeView = View(mock())

        // Then
        assertThat(fakeView.targetClassName()).isEqualTo(fakeView.javaClass.canonicalName)
    }

    @Test
    fun `M return the simpleName W targetClassName() { canonicalName is null }`() {
        // Given
        // Inner classes have null canonicalName
        val fakeView = object : View(mock()) {}

        // Then
        assertThat(fakeView.targetClassName()).isEqualTo(fakeView.javaClass.simpleName)
    }
}
