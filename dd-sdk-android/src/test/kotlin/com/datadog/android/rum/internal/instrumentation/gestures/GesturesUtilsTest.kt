/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.app.Application
import android.content.res.Resources
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.lang.ref.WeakReference
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
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

    @BeforeEach
    fun `set up`() {
        CoreFeature.contextRef = WeakReference(mockAppContext)
    }

    @AfterEach
    fun `tear down`() {
        CoreFeature.contextRef = WeakReference(null)
    }

    @Test
    fun `it will return resource entry name if found`(forge: Forge) {
        // given
        val resourceId = forge.anInt()
        val resourceName = forge.aString()
        whenever(mockAppContext.resources).thenReturn(mockResources)
        whenever(mockResources.getResourceEntryName(resourceId)).thenReturn(resourceName)

        // when
        assertThat(resourceIdName(resourceId)).isEqualTo(resourceName)
    }

    @Test
    fun `it will return the resource id as String hexa if Context resources are null`(
        forge: Forge
    ) {
        // given
        val resourceId = forge.anInt()
        whenever(mockAppContext.resources).thenReturn(null)

        // when
        assertThat(resourceIdName(resourceId))
            .isEqualTo("0x${resourceId.toString(16)}")
    }

    @Test
    fun `it will return the resource id as String hexa if resource name could not be found`(
        forge: Forge
    ) {
        // given
        val resourceId = forge.anInt()
        whenever(mockAppContext.resources).thenReturn(mockResources)
        whenever(mockResources.getResourceEntryName(resourceId)).thenThrow(
            Resources.NotFoundException(
                forge.aString()
            )
        )

        // when
        assertThat(resourceIdName(resourceId))
            .isEqualTo("0x${resourceId.toString(16)}")
    }

    @Test
    fun `it will return the resource id as String hexa if resource name was null`(
        forge: Forge
    ) {
        // given
        val resourceId = forge.anInt()
        whenever(mockAppContext.resources).thenReturn(mockResources)
        whenever(mockResources.getResourceEntryName(resourceId)).thenReturn(null)

        // when
        assertThat(resourceIdName(resourceId))
            .isEqualTo("0x${resourceId.toString(16)}")
    }
}
