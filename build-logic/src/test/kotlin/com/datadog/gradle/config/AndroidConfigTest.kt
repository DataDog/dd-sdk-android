/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import com.datadog.gradle.utils.Version
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AndroidConfigTest {

    private val baseVersion = Version(4, 11, 5, Version.Type.Snapshot)

    @Test
    fun `M return dogfood version W forCi() {dogfooding branch, short sha}`() {
        // When
        val version = baseVersion.forCi(ciBranch = "dogfooding", ciShortSha = "a1b2c3d")

        // Then
        assertThat(version).isEqualTo(Version(4, 11, 5, Version.Type.Dogfood("a1b2c3d")))
    }

    @Test
    fun `M return unchanged version W forCi() {other branch}`() {
        // When
        val version = baseVersion.forCi(ciBranch = "develop", ciShortSha = "a1b2c3d")

        // Then
        assertThat(version).isEqualTo(baseVersion)
    }

    @Test
    fun `M return unchanged version W forCi() {no branch}`() {
        // When
        val version = baseVersion.forCi(ciBranch = null, ciShortSha = "a1b2c3d")

        // Then
        assertThat(version).isEqualTo(baseVersion)
    }

    @Test
    fun `M return unchanged version W forCi() {dogfooding branch, no short sha}`() {
        // When
        val version = baseVersion.forCi(ciBranch = "dogfooding", ciShortSha = null)

        // Then
        assertThat(version).isEqualTo(baseVersion)
    }

    @Test
    fun `M return unchanged version W forCi() {dogfooding branch, blank short sha}`() {
        // When
        val version = baseVersion.forCi(ciBranch = "dogfooding", ciShortSha = "   ")

        // Then
        assertThat(version).isEqualTo(baseVersion)
    }

    @Test
    fun `M declare VERSION on a single line with forCi() W reading AndroidConfig source`() {
        // Given
        val source = java.io.File("src/main/kotlin/com/datadog/gradle/config/AndroidConfig.kt").readText()
        // A single line keeps ci/scripts/merge-release-develop.sh able to resolve version conflicts;
        // .forCi() keeps dogfooding builds from being published under the plain snapshot version.
        val versionLine = Regex("^\\s*val VERSION = Version\\([^)]*\\)\\.forCi\\(\\)$", RegexOption.MULTILINE)

        // Then
        assertThat(versionLine.findAll(source).count()).isEqualTo(1)
    }
}
