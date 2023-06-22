/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core.internal

import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore
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
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class SdkCoreRegistryTest {

    lateinit var testedRegistry: SdkCoreRegistry

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockSdkCore: SdkCore

    @BeforeEach
    fun setUp() {
        testedRegistry = SdkCoreRegistry(mockInternalLogger)
    }

    @Test
    fun `M return sdkCore W register+getInstance`(
        @StringForgery name: String
    ) {
        // When
        testedRegistry.register(name, mockSdkCore)
        val instance = testedRegistry.getInstance(name)

        // Then
        assertThat(instance).isSameAs(mockSdkCore)
    }

    @Test
    fun `M return sdkCore W register+getInstance {default name}`() {
        // When
        testedRegistry.register(null, mockSdkCore)
        val instance = testedRegistry.getInstance()

        // Then
        assertThat(instance).isSameAs(mockSdkCore)
    }

    @Test
    fun `M return null W register+getInstance {different names}`(
        @StringForgery name1: String,
        @StringForgery name2: String
    ) {
        // When
        testedRegistry.register(name1, mockSdkCore)
        val instance = testedRegistry.getInstance(name2)

        // Then
        assertThat(instance).isNull()
    }

    @Test
    fun `M return null W getInstance {nothing registered}`(
        @StringForgery name: String
    ) {
        // When
        val instance = testedRegistry.getInstance(name)

        // Then
        assertThat(instance).isNull()
    }

    @Test
    fun `M return null W getInstance {no default registered}`() {
        // When
        val instance = testedRegistry.getInstance()

        // Then
        assertThat(instance).isNull()
    }

    @Test
    fun `M warn and ignore W register {name already registered}`(
        @StringForgery name: String
    ) {
        // Given
        val otherMockSdkCore = mock<SdkCore>()

        // When
        testedRegistry.register(name, mockSdkCore)
        testedRegistry.register(name, otherMockSdkCore)
        val instance = testedRegistry.getInstance(name)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            "An SdkCode with name $name has already been registered."
        )
        assertThat(instance).isSameAs(mockSdkCore)
    }

    @Test
    fun `M return null W register+unregister+getInstance`(
        @StringForgery name: String
    ) {
        // When
        testedRegistry.register(name, mockSdkCore)
        val unregistered = testedRegistry.unregister(name)
        val instance = testedRegistry.getInstance(name)

        // Then
        assertThat(unregistered).isSameAs(mockSdkCore)
        assertThat(instance).isNull()
    }

    @Test
    fun `M return null W register+unregister+getInstance {default name name}`() {
        // When
        testedRegistry.register(null, mockSdkCore)
        val unregistered = testedRegistry.unregister()
        val instance = testedRegistry.getInstance()

        // Then
        assertThat(unregistered).isSameAs(mockSdkCore)
        assertThat(instance).isNull()
    }

    @Test
    fun `M clear registered instances W register+clear+getInstance`(
        @StringForgery name: String
    ) {
        // When
        testedRegistry.register(name, mockSdkCore)
        testedRegistry.clear()
        val instance = testedRegistry.getInstance()

        // Then
        assertThat(instance).isNull()
    }

    @Test
    fun `M clear registered instances W register+clear+getInstance {default name name}`() {
        // When
        testedRegistry.register(null, mockSdkCore)
        testedRegistry.clear()
        val instance = testedRegistry.getInstance()

        // Then
        assertThat(instance).isNull()
    }
}
