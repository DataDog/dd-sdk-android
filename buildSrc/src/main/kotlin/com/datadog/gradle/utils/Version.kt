/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.utils

data class Version(
    val major: Int,
    val minor: Int,
    val hotfix: Int,
    val type: Type = Type.Release
) {

    // region Type

    sealed class Type {

        abstract val suffix: String

        object Release : Type() {
            override val suffix: String = ""
        }

        data class ReleaseCandidate(val number: Int) : Type() {
            override val suffix: String = "-rc$number"
        }

        data class Beta(val number: Int) : Type() {
            override val suffix: String = "-beta$number"
        }

        data class Alpha(val number: Int) : Type() {
            override val suffix: String = "-alpha$number"
        }

        object Dev : Type() {
            override val suffix: String = "-dev"
        }
    }

    // endregion

    init {
        require(major < MAX_MAJOR) { "The minor component must be smaller than $MAX_MAJOR" }
        require(minor < MAX_MINOR) { "The minor component must be smaller than $MAX_MINOR" }
        require(hotfix < MAX_HOTFIX) { "The hotfix component must be smaller than $MAX_HOTFIX" }
    }

    /**
     * @return a human readable Semantic Version name based on the information, with an optional suffix
     * (eg: 1.0.0, 2.3.0-rc1, 0.0.4-alpha1, etc...).
     * ** See also ** [Semantic Versioning](https://semver.org/)
     */
    val name: String
        get() {
            return "$major.$minor.$hotfix${type.suffix}"
        }

    /**
     * @return an Android compatible version code as a unique integer.
     */
    val code: Int
        get() {
            val minPart = minor * MAX_HOTFIX
            val majPart = major * MAX_MINOR * MAX_HOTFIX

            return hotfix + minPart + majPart
        }

    companion object {
        internal const val MAX_HOTFIX = 10
        internal const val MAX_MINOR = 100
        internal const val MAX_MAJOR = 100
    }
}
