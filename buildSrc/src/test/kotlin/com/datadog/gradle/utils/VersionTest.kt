/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.utils

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
        val name = Version(3, 12, 7).name
        assert(name == "3.12.7")
    }

    @Test
    fun computesCode() {
        val code = Version(3, 12, 7).code

        assert(code == 3127) { "expected code to be 3127 but was $code" }
    }

    @Test
    fun ensureCodeSequenceHotfix() {
        val code = Version(3, 12, Version.MAX_HOTFIX - 1).code
        val next = Version(3, 13, 0).code

        assert(code == next - 1) { "expected code to be next - 1 = ${next - 1} but was $code (@next:$next)" }
    }

    @Test
    fun ensureCodeSequenceMinor() {
        val code = Version(3, Version.MAX_MINOR - 1, Version.MAX_HOTFIX - 1).code
        val next = Version(4, 0, 0).code

        assert(code == next - 1) { "expected code to be next - 1 = ${next - 1} but was $code (@next:$next)" }
    }

    @Test
    fun addNoSuffixForRelease() {
        val name = Version(3, 12, 7, Version.Type.Release).name
        val expected = "3.12.7"
        assert(name == expected) { " expected name to be $expected but was $name" }
    }

    @Test
    fun addSuffixForRC() {
        val name = Version(3, 12, 7, Version.Type.ReleaseCandidate(1)).name
        val expected = "3.12.7-rc1"
        assert(name == expected) { " expected name to be $expected but was $name" }
    }

    @Test
    fun addSuffixForBeta() {
        val name = Version(3, 12, 7, Version.Type.Beta(5)).name
        val expected = "3.12.7-beta5"
        assert(name == expected) { " expected name to be $expected but was $name" }
    }

    @Test
    fun addSuffixForAlpha() {
        val name = Version(3, 12, 7, Version.Type.Alpha(3)).name
        val expected = "3.12.7-alpha3"
        assert(name == expected) { " expected name to be $expected but was $name" }
    }

    @Test
    fun addSuffixForDev() {
        val name = Version(3, 12, 7, Version.Type.Dev).name
        val expected = "3.12.7-dev"
        assert(name == expected) { " expected name to be $expected but was $name" }
    }
}
