/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.internal

internal data class DdConfiguration(
    val site: Site,
    val apiKey: String
) {
    fun buildUrl(): String {
        return "https://sourcemap-intake.${site.host}/v1/input/$apiKey"
    }

    internal enum class Site(val host: String) {
        US("datadoghq.com"),
        EU("datadoghq.eu"),
        GOV("ddog-gov.com")
    }
}
