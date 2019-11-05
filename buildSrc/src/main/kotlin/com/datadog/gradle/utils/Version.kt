/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.gradle.utils

data class Version(
    val major: Int,
    val minor: Int,
    val hotfix: Int
) {

    init {
        require(major < MAX_MAJOR) { "The minor component must be smaller than $MAX_MAJOR" }
        require(minor < MAX_MINOR) { "The minor component must be smaller than $MAX_MINOR" }
        require(hotfix < MAX_HOTFIX) { "The hotfix component must be smaller than $MAX_HOTFIX" }
    }

    val name: String
        get() = "$major.$minor.$hotfix"

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
