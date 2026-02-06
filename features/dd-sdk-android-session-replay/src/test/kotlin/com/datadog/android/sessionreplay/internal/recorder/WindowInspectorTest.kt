/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.lang.reflect.Field

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class WindowInspectorTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockBuildSdkVersionProvider: BuildSdkVersionProvider

    @Test
    fun `M return emptyList W getGlobalWindowViews { null WM instance}`() {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastQ) doReturn true

        // When + Then
        assertThat(WindowInspector.getGlobalWindowViews(mockInternalLogger)).isEmpty()
    }

    @Test
    fun `M return emptyList W getGlobalWindowViewsLegacy { null mViews field}`() {
        assertThat(
            WindowInspector.getGlobalWindowViewsLegacy(
                mock(),
                null
            )
        ).isEmpty()
    }

    @Test
    fun `M return emptyList W getGlobalWindowViewsLegacy { null WM instance and null mViews }`() {
        assertThat(
            WindowInspector.getGlobalWindowViewsLegacy(
                null,
                null
            )
        ).isEmpty()
    }

    @Test
    fun `M return emptyList W getGlobalWindowViewsLegacy { mViews not a List or Array of views}`() {
        // Given
        val mockWmInstance = mock<Any>()
        val mockViewsField = mock<Field>()
        whenever(mockViewsField.get(mockWmInstance)).thenReturn(mock())

        // When
        assertThat(
            WindowInspector.getGlobalWindowViewsLegacy(
                null,
                null
            )
        ).isEmpty()
    }

    @Test
    fun `M return emptyList W getGlobalWindowViewsLegacy { mViews is null}`() {
        // Given
        val mockWmInstance = mock<Any>()
        val mockViewsField = mock<Field>()
        whenever(mockViewsField.get(mockWmInstance)).thenReturn(null)

        // When
        assertThat(
            WindowInspector.getGlobalWindowViewsLegacy(
                null,
                null
            )
        ).isEmpty()
    }

    @Test
    fun `M return list of Views W getGlobalWindowViewsLegacy { mViews is List of views}`(
        forge: Forge
    ) {
        // Given
        val fakeViewsList: List<View> = forge.aList { mock() }
        val mockWmInstance = mock<Any>()
        val mockViewsField = mock<Field>()
        whenever(mockViewsField.get(mockWmInstance)).thenReturn(fakeViewsList)

        // When
        assertThat(
            WindowInspector.getGlobalWindowViewsLegacy(
                mockWmInstance,
                mockViewsField
            )
        )
            .isEqualTo(fakeViewsList)
    }

    @Test
    fun `M return list of Views W getGlobalWindowViewsLegacy { mViews is Array of views}`(
        forge: Forge
    ) {
        // Given
        val fakeExpectedViewsList = forge.aList<View> { mock() }
        val fakeArrayViews: Array<View> = fakeExpectedViewsList.toTypedArray()
        val mockWmInstance = mock<Any>()
        val mockViewsField = mock<Field>()
        whenever(mockViewsField.get(mockWmInstance)).thenReturn(fakeArrayViews)

        // When
        assertThat(
            WindowInspector.getGlobalWindowViewsLegacy(
                mockWmInstance,
                mockViewsField
            )
        )
            .isEqualTo(fakeExpectedViewsList)
    }
}
