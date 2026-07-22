/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.internal.heatmaps

import com.datadog.android.internal.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class HeatmapIdentifierTest {

    @Test
    fun `M return non-null identifier with SHA-256 rawValue W create`(
        forge: Forge
    ) {
        // Given
        val fakeAppPackageName = forge.anAlphabeticalString()
        val fakeScreenName = forge.anAlphabeticalString()
        val fakeSegments = forge.aList(size = forge.anInt(min = 1, max = 20)) { forge.anAlphabeticalString() }

        // When
        val identifier = HeatmapIdentifier.create(fakeSegments, fakeScreenName, fakeAppPackageName)

        // Then
        assertThat(identifier).isNotNull
        assertThat(identifier!!.rawValue).matches("[0-9a-f]{64}")
    }

    @Test
    fun `M return stable identifier W create {same inputs}`(
        forge: Forge
    ) {
        // Given
        val fakeAppPackageName = forge.anAlphabeticalString()
        val fakeScreenName = forge.anAlphabeticalString()
        val fakeSegments = forge.aList(size = forge.anInt(min = 1, max = 20)) { forge.anAlphabeticalString() }

        // When
        val first = HeatmapIdentifier.create(fakeSegments, fakeScreenName, fakeAppPackageName)
        val second = HeatmapIdentifier.create(fakeSegments, fakeScreenName, fakeAppPackageName)

        // Then
        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `M not collide W create {literal percent-encoded slash vs actual slash}`() {
        // When
        val identifierWithLiteralEncoding = HeatmapIdentifier.create(
            elementPath = listOf("a%2Fb"),
            screenName = "screen",
            appPackageName = "com.example.app"
        )
        val identifierWithActualSlash = HeatmapIdentifier.create(
            elementPath = listOf("a/b"),
            screenName = "screen",
            appPackageName = "com.example.app"
        )

        // Then
        assertThat(identifierWithLiteralEncoding).isNotNull
        assertThat(identifierWithActualSlash).isNotNull
        assertThat(identifierWithLiteralEncoding).isNotEqualTo(identifierWithActualSlash)
    }

    @Test
    fun `M scope identifier to view namespace W create {raw screen name is prefixed with view colon}`() {
        // When
        val identifierForRawScreenName = HeatmapIdentifier.create(
            elementPath = listOf("btn"),
            screenName = "MyScreen",
            appPackageName = "com.example.app"
        )
        val identifierForPrePrefixedScreenName = HeatmapIdentifier.create(
            elementPath = listOf("btn"),
            screenName = "view:MyScreen",
            appPackageName = "com.example.app"
        )

        // Then
        assertThat(identifierForRawScreenName).isNotNull
        assertThat(identifierForPrePrefixedScreenName).isNotNull
        assertThat(identifierForRawScreenName).isNotEqualTo(identifierForPrePrefixedScreenName)
    }

    @Test
    fun `M not collide W create {slash in screen name vs path separator}`() {
        // When
        val identifierWithSlashInScreenName = HeatmapIdentifier.create(
            elementPath = listOf("c"),
            screenName = "a/b",
            appPackageName = "com.example.app"
        )
        val identifierWithSlashAsSegmentBoundary = HeatmapIdentifier.create(
            elementPath = listOf("b", "c"),
            screenName = "a",
            appPackageName = "com.example.app"
        )

        // Then
        assertThat(identifierWithSlashInScreenName).isNotNull
        assertThat(identifierWithSlashAsSegmentBoundary).isNotNull
        assertThat(identifierWithSlashInScreenName).isNotEqualTo(identifierWithSlashAsSegmentBoundary)
    }
}
