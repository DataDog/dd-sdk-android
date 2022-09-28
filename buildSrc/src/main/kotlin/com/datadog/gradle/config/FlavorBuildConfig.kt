/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import com.android.build.api.dsl.ApplicationProductFlavor
import com.google.gson.Gson
import java.io.File
import java.util.Locale

private fun sampleAppConfig(filePath: String): SampleAppConfig {
    val file = File(filePath)
    if (!file.exists()) {
        return SampleAppConfig()
    }
    file.inputStream().reader().use {
        val jsonString = it.readText()
        return Gson().fromJson(jsonString, SampleAppConfig::class.java)
    }
}

@Suppress("UnstableApiUsage")
fun configureFlavorForSampleApp(flavor: ApplicationProductFlavor, rootDir: File) {
    val config = sampleAppConfig("${rootDir.absolutePath}/config/${flavor.name}.json")
    println("Configuring flavor: [${flavor.name}] with config: [$config]")
    flavor.buildConfigField(
        "String",
        "DD_OVERRIDE_LOGS_URL",
        "\"${config.logsEndpoint}\""
    )
    flavor.buildConfigField(
        "String",
        "DD_OVERRIDE_TRACES_URL",
        "\"${config.tracesEndpoint}\""
    )
    flavor.buildConfigField(
        "String",
        "DD_OVERRIDE_RUM_URL",
        "\"${config.rumEndpoint}\""
    )
    flavor.buildConfigField(
        "String",
        "DD_OVERRIDE_SESSION_REPLAY_URL",
        "\"${config.sessionReplayEndpoint}\""
    )
    flavor.buildConfigField(
        "String",
        "DD_RUM_APPLICATION_ID",
        "\"${config.rumApplicationId}\""
    )
    flavor.buildConfigField(
        "String",
        "DD_CLIENT_TOKEN",
        "\"${config.token}\""
    )
    flavor.buildConfigField(
        "String",
        "DD_API_KEY",
        "\"${config.apiKey}\""
    )
    flavor.buildConfigField(
        "String",
        "DD_APPLICATION_KEY",
        "\"${config.applicationKey}\""
    )
    flavor.buildConfigField(
            "String",
            "DD_SITE_NAME",
            "\"${flavor.name.toUpperCase(Locale.US)}\""
    )
}
