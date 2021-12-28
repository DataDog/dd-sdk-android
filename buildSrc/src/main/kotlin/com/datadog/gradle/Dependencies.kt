/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle

object Dependencies {

    object Versions {

        // NDK
        const val Ndk = "22.1.7171670"
        // TODO RUMM-1660. Check if Cmake >= 3.20.4 is available when doing AGP 7 migration
        // Cannot use 3.18.1 here, because it has a bug in the File API.
        const val CMake = "3.10.2"
    }

    object Repositories {
        const val Gradle = "https://plugins.gradle.org/m2/"
        const val Google = "https://maven.google.com"
        const val Jitpack = "https://jitpack.io"
    }
}
