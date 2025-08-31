/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import com.android.build.api.dsl.ApplicationDefaultConfig
import com.android.build.api.dsl.ApplicationProductFlavor
import com.google.gson.Gson
import org.gradle.api.Project
import java.io.File
import java.util.Locale

fun sampleAppConfig(filePath: String): SampleAppConfig {
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
fun ApplicationDefaultConfig.configureFlavorForBenchmark(
    rootDir: File
) {
    val config = sampleAppConfig("${rootDir.absolutePath}/config/benchmark.json")
    buildConfigField(
        "String",
        "BENCHMARK_RUM_APPLICATION_ID",
        "\"${config.rumApplicationId}\""
    )
    buildConfigField(
        "String",
        "BENCHMARK_CLIENT_TOKEN",
        "\"${config.token}\""
    )
    buildConfigField(
        "String",
        "BENCHMARK_API_KEY",
        "\"${config.apiKey}\""
    )
    buildConfigField(
        "String",
        "BENCHMARK_APPLICATION_KEY",
        "\"${config.applicationKey}\""
    )
}

@Suppress("UnstableApiUsage")
fun ApplicationDefaultConfig.configureFlavorForUiTest(
    rootDir: File
) {
    val config = sampleAppConfig("${rootDir.absolutePath}/config/uitest.json")
    buildConfigField(
        "String",
        "UITEST_RUM_APPLICATION_ID",
        "\"${config.rumApplicationId}\""
    )
    buildConfigField(
        "String",
        "UITEST_CLIENT_TOKEN",
        "\"${config.token}\""
    )
    buildConfigField(
        "String",
        "UITEST_API_KEY",
        "\"${config.apiKey}\""
    )
    buildConfigField(
        "String",
        "UITEST_APPLICATION_KEY",
        "\"${config.applicationKey}\""
    )
}

@Suppress("UnstableApiUsage")
fun configureFlavorForSampleApp(
    project: Project,
    flavor: ApplicationProductFlavor,
    rootDir: File
) {
    val config = sampleAppConfig("${rootDir.absolutePath}/config/${flavor.name}.json")
    project.logger.info("Configuring flavor: [${flavor.name}] with config: [$config]")
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
        "\"${flavor.name.uppercase(Locale.US)}\""
    )
}

@Suppress("UnstableApiUsage")
fun ApplicationDefaultConfig.configureFlavorForTvApp(
    rootDir: File
) {
    val config = sampleAppConfig("${rootDir.absolutePath}/config/tv.json")
    buildConfigField(
        "String",
        "DD_RUM_APPLICATION_ID",
        "\"${config.rumApplicationId}\""
    )
    buildConfigField(
        "String",
        "DD_CLIENT_TOKEN",
        "\"${config.token}\""
    )
}

@Suppress("UnstableApiUsage")
fun ApplicationDefaultConfig.configureFlavorForAutoApp(
    rootDir: File
) {
    val config = sampleAppConfig("${rootDir.absolutePath}/config/auto.json")
    buildConfigField(
        "String",
        "DD_RUM_APPLICATION_ID",
        "\"${config.rumApplicationId}\""
    )
    buildConfigField(
        "String",
        "DD_CLIENT_TOKEN",
        "\"${config.token}\""
    )
}
