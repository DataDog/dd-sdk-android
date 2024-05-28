/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.utils

import android.content.ComponentName
import androidx.navigation.ActivityNavigator
import androidx.navigation.fragment.DialogFragmentNavigator
import androidx.navigation.fragment.FragmentNavigator
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ViewUtilsTest {

    @Test
    fun `M return class name W resolveViewUrl() {FragmentNavigator}`(
        @StringForgery name: String
    ) {
        // Given
        val destination = mock<FragmentNavigator.Destination>().apply {
            whenever(className) doReturn name
        }

        // When
        val output = destination.resolveViewUrl()

        // Then
        assertThat(output).isEqualTo(name)
    }

    @Test
    fun `M return class name W resolveViewUrl() {DialogFragmentNavigator}`(
        @StringForgery name: String
    ) {
        // Given
        val destination = mock<DialogFragmentNavigator.Destination>().apply {
            whenever(className) doReturn name
        }

        // When
        val output = destination.resolveViewUrl()

        // Then
        assertThat(output).isEqualTo(name)
    }

    @Test
    fun `M return class name W resolveViewUrl() {ActivityNavigator}`(
        @StringForgery packageName: String,
        @StringForgery name: String
    ) {
        // Given
        val destination = mock<ActivityNavigator.Destination>().apply {
            whenever(component) doReturn ComponentName(packageName, name)
        }

        // When
        val output = destination.resolveViewUrl()

        // Then
        assertThat(output).isEqualTo("$packageName.$name")
    }

    @Test
    fun `M return class name W resolveViewUrl() {ActivityNavigator redundant package}`(
        @StringForgery packageName: String,
        @StringForgery name: String
    ) {
        // Given
        val fullClassName = "$packageName.$name"
        val destination = mock<ActivityNavigator.Destination>().apply {
            whenever(component) doReturn ComponentName(packageName, fullClassName)
        }

        // When
        val output = destination.resolveViewUrl()

        // Then
        assertThat(output).isEqualTo(fullClassName)
    }

    @Test
    fun `M return class name W resolveViewUrl() {ActivityNavigator conflicting package}`(
        @StringForgery packageName: String,
        @StringForgery(regex = "\\w+\\.\\w+") name: String
    ) {
        // Given
        val destination = mock<ActivityNavigator.Destination>().apply {
            whenever(component) doReturn ComponentName(packageName, name)
        }

        // When
        val output = destination.resolveViewUrl()

        // Then
        assertThat(output).isEqualTo(name)
    }

    @Test
    fun `M return unknown name W resolveViewUrl() {ActivityNavigator no component}`() {
        // Given
        val destination = mock<ActivityNavigator.Destination>().apply {
            whenever(component) doReturn null
        }

        // When
        val output = destination.resolveViewUrl()

        // Then
        assertThat(output).isEqualTo(UNKNOWN_DESTINATION_URL)
    }

    @Test
    fun `M return the key value W resolveViewUrl() { destination of type String }`(
        @StringForgery destination: String
    ) {
        // When
        val output = destination.resolveViewUrl()

        // Then
        assertThat(output).isEqualTo(destination)
    }
}
