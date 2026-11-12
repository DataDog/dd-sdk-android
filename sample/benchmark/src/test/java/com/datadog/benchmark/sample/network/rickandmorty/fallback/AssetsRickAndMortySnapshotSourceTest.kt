/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.network.rickandmorty.fallback

import android.content.Context
import android.content.res.AssetManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.FileNotFoundException

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class AssetsRickAndMortySnapshotSourceTest {
    private lateinit var testedSource: AssetsRickAndMortySnapshotSource

    @Mock
    lateinit var mockContext: Context

    @Mock
    lateinit var mockAssets: AssetManager

    @BeforeEach
    fun `set up`() {
        whenever(mockContext.assets).thenReturn(mockAssets)
        testedSource = AssetsRickAndMortySnapshotSource(mockContext)
    }

    @Test
    fun `M open relative snapshot path W open()`() {
        // Given
        val fakeStream = "[]".byteInputStream()
        whenever(mockAssets.open("rickandmorty/characters.json")).thenReturn(fakeStream)

        // When
        val result = testedSource.open("characters.json")

        // Then
        assertThat(result).isSameAs(fakeStream)
    }

    @Test
    fun `M return null W open() {asset absent}`() {
        // Given
        whenever(mockAssets.open("rickandmorty/avatars/1.jpeg")).thenThrow(FileNotFoundException())

        // When
        val result = testedSource.open("avatars/1.jpeg")

        // Then
        assertThat(result).isNull()
    }
}
