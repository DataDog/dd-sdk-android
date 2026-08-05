/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("datadogBuildConfig")
    id("ktlint")
    alias(libs.plugins.sqlDelightGradlePlugin)
}

dependencies {
    implementation(libs.kotlinRuntime)
}

sqldelight {
    databases {
        create("LogsDatabase") {
            packageName.set("com.datadog.android.sample")
            dialect(libs.sqlDelightSqlite324Dialect)
        }
    }
}

datadogBuild {
    applyKotlinConfig()
}
