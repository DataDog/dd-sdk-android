/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class VersionTest {

    @Test(expected = IllegalArgumentException::class)
    fun checkMajorInRange() {
        Version(Version.MAX_MAJOR, 0, 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun checkMinorInRange() {
        Version(0, Version.MAX_MINOR, 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun checkHotfixInRange() {
        Version(0, 0, Version.MAX_HOTFIX)
    }

    @Test
    fun computesName() {
        // When
        val name = Version(3, 12, 7).name

        // Then
        assertThat(name).isEqualTo("3.12.7")
    }

    @Test
    fun computesCode() {
        // When
        val code = Version(3, 12, 7).code

        // Then
        assertThat(code).isEqualTo(3127)
    }

    @Test
    fun ensureCodeSequenceHotfix() {
        // When
        val code = Version(3, 12, Version.MAX_HOTFIX - 1).code
        val next = Version(3, 13, 0).code

        // Then
        assertThat(code).isEqualTo(next - 1)
    }

    @Test
    fun ensureCodeSequenceMinor() {
        // When
        val code = Version(3, Version.MAX_MINOR - 1, Version.MAX_HOTFIX - 1).code
        val next = Version(4, 0, 0).code

        // Then
        assertThat(code).isEqualTo(next - 1)
    }

    @Test
    fun addNoSuffixForRelease() {
        // When
        val name = Version(3, 12, 7, Version.Type.Release).name

        // Then
        val expected = "3.12.7"
        assertThat(name).isEqualTo(expected)
    }

    @Test
    fun addSuffixForRC() {
        // When
        val name = Version(3, 12, 7, Version.Type.ReleaseCandidate(1)).name

        // Then
        val expected = "3.12.7-rc1"
        assertThat(name).isEqualTo(expected)
    }

    @Test
    fun addSuffixForBeta() {
        // When
        val name = Version(3, 12, 7, Version.Type.Beta(5)).name

        // Then
        val expected = "3.12.7-beta5"
        assertThat(name).isEqualTo(expected)
    }

    @Test
    fun addSuffixForAlpha() {
        // When
        val name = Version(3, 12, 7, Version.Type.Alpha(3)).name

        // Then
        val expected = "3.12.7-alpha3"
        assertThat(name).isEqualTo(expected)
    }

    @Test
    fun addSuffixForDev() {
        // When
        val name = Version(3, 12, 7, Version.Type.Dev).name

        // Then
        val expected = "3.12.7-dev"
        assertThat(name).isEqualTo(expected)
    }

    @Test
    fun addSuffixForSnapshot() {
        // When
        val name = Version(4, 11, 5, Version.Type.Snapshot).name

        // Then
        val expected = "4.11.5-SNAPSHOT"
        assertThat(name).isEqualTo(expected)
    }
}
