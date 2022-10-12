/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ktx.rum

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import com.datadog.android.rum.resource.RumResourceInputStream
import com.datadog.tools.unit.forge.BaseConfigurator
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.io.InputStream

@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@ForgeConfiguration(value = BaseConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContextExtTest {

    @Mock
    lateinit var mockContext: Context

    @Mock
    lateinit var mockAssetManager: AssetManager

    @Mock
    lateinit var mockResources: Resources

    @BeforeEach
    fun `set up`() {
        whenever(mockContext.assets) doReturn mockAssetManager
        whenever(mockContext.resources) doReturn mockResources
    }

    @Test
    fun `M wrap asset W getAssetAsRumResource()`(
        @StringForgery fileName: String,
        @IntForgery(0, 4) accessMode: Int
    ) {
        // Given
        val mockIS: InputStream = mock()
        whenever(mockAssetManager.open(fileName, accessMode)) doReturn mockIS

        // When
        val result = mockContext.getAssetAsRumResource(fileName, accessMode)

        // Then
        assertThat(result).isInstanceOf(RumResourceInputStream::class.java)
        val rumRIS = result as RumResourceInputStream
        assertThat(rumRIS.delegate).isSameAs(mockIS)
        assertThat(rumRIS.url).isEqualTo("assets://$fileName")
    }

    @Test
    fun `M wrap asset W getAssetAsRumResource() {default access mode)`(
        @StringForgery fileName: String
    ) {
        // Given
        val mockIS: InputStream = mock()
        whenever(mockAssetManager.open(fileName, AssetManager.ACCESS_STREAMING)) doReturn mockIS

        // When
        val result = mockContext.getAssetAsRumResource(fileName)

        // Then
        assertThat(result).isInstanceOf(RumResourceInputStream::class.java)
        val rumRIS = result as RumResourceInputStream
        assertThat(rumRIS.delegate).isSameAs(mockIS)
        assertThat(rumRIS.url).isEqualTo("assets://$fileName")
    }

    @Test
    fun `M wrap resource W getRawResAsRumResource()`(
        @IntForgery resourceId: Int,
        @StringForgery resourceName: String
    ) {
        // Given
        val mockIS: InputStream = mock()
        whenever(mockResources.getResourceName(resourceId)) doReturn resourceName
        whenever(mockResources.openRawResource(resourceId)) doReturn mockIS

        // When
        val result = mockContext.getRawResAsRumResource(resourceId)

        // Then
        assertThat(result).isInstanceOf(RumResourceInputStream::class.java)
        val rumRIS = result as RumResourceInputStream
        assertThat(rumRIS.delegate).isSameAs(mockIS)
        assertThat(rumRIS.url).isEqualTo(resourceName)
    }

    @Test
    fun `M wrap resource W getRawResAsRumResource() {res not found}`(
        @IntForgery resourceId: Int
    ) {
        // Given
        val mockIS: InputStream = mock()
        whenever(mockResources.getResourceName(resourceId)) doThrow Resources.NotFoundException()
        whenever(mockResources.openRawResource(resourceId)) doReturn mockIS

        // When
        val result = mockContext.getRawResAsRumResource(resourceId)

        // Then
        assertThat(result).isInstanceOf(RumResourceInputStream::class.java)
        val rumRIS = result as RumResourceInputStream
        assertThat(rumRIS.delegate).isSameAs(mockIS)
        assertThat(rumRIS.url).isEqualTo("res/0x${resourceId.toString(16)}")
    }
}
