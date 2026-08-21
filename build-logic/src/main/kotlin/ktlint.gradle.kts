/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("org.jlleitschuh.gradle.ktlint")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

ktlint {
    version = provider { libs.findVersion("ktlint").get().requiredVersion }
    filter {
        exclude { it.file.invariantSeparatorsPath.contains("/build/generated/") }
    }
}
